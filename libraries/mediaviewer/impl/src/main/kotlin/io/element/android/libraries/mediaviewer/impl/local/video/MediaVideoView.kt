/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.local.video

import android.annotation.SuppressLint
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.audio.api.AudioFocus
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.text.toDp
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.KeepScreenOn
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent
import io.element.android.libraries.mediaviewer.api.local.LocalMedia
import io.element.android.libraries.mediaviewer.impl.R
import io.element.android.libraries.mediaviewer.impl.local.LocalMediaViewState
import io.element.android.libraries.mediaviewer.impl.local.PlayableState
import io.element.android.libraries.mediaviewer.impl.local.player.MediaPlayerControllerState
import io.element.android.libraries.mediaviewer.impl.local.player.MediaPlayerControllerView
import io.element.android.libraries.mediaviewer.impl.local.player.rememberExoPlayer
import io.element.android.libraries.mediaviewer.impl.local.player.seekToEnsurePlaying
import io.element.android.libraries.mediaviewer.impl.local.player.togglePlay
import io.element.android.libraries.mediaviewer.impl.local.rememberLocalMediaViewState
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.utils.a11y.isTalkbackActive
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/** Шаг перемотки двойным тапом, как в привычных мобильных плеерах. */
private const val SEEK_STEP_MILLIS = 10_000L

/** Скорости, по которым циклит кнопка. */
private val PlaybackSpeeds = listOf(1f, 1.5f, 2f, 0.5f)

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun MediaVideoView(
    isDisplayed: Boolean,
    localMediaViewState: LocalMediaViewState,
    bottomPaddingInPixels: Int,
    localMedia: LocalMedia?,
    autoplay: Boolean,
    audioFocus: AudioFocus?,
    forPreview: Boolean,
    modifier: Modifier = Modifier,
) {
    val exoPlayer = rememberExoPlayer(forAudioOnly = false)
    ExoPlayerMediaVideoView(
        isDisplayed = isDisplayed,
        localMediaViewState = localMediaViewState,
        bottomPaddingInPixels = bottomPaddingInPixels,
        exoPlayer = exoPlayer,
        localMedia = localMedia,
        autoplay = autoplay,
        audioFocus = audioFocus,
        forPreview = forPreview,
        modifier = modifier,
    )
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun ExoPlayerMediaVideoView(
    isDisplayed: Boolean,
    localMediaViewState: LocalMediaViewState,
    bottomPaddingInPixels: Int,
    exoPlayer: ExoPlayer,
    localMedia: LocalMedia?,
    autoplay: Boolean,
    audioFocus: AudioFocus?,
    forPreview: Boolean,
    modifier: Modifier = Modifier,
) {
    var mediaPlayerControllerState: MediaPlayerControllerState by remember {
        mutableStateOf(
            MediaPlayerControllerState(
                isVisible = true,
                isPlaying = false,
                isReady = false,
                progressInMillis = 0,
                durationInMillis = 0,
                canMute = true,
                isMuted = false,
                seekingToMillis = null,
                // Форк: до первого кадра ExoPlayer в STATE_IDLE, isPlaying == false и никакой обратной
                // связи не видно — поэтому различаем «играет», «буферизует» и «ошибка».
                isBuffering = false,
                playWhenReady = false,
                bufferedPercentage = 0,
                hasError = false,
                canChangePlaybackSpeed = true,
                playbackSpeed = 1f,
            )
        )
    }

    val playableState: PlayableState.Playable by remember {
        derivedStateOf {
            PlayableState.Playable(
                isShowingControls = mediaPlayerControllerState.isVisible,
            )
        }
    }

    localMediaViewState.playableState = playableState

    val playerListener = remember {
        object : Player.Listener {
            override fun onRenderedFirstFrame() {
                localMediaViewState.isReady = true
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mediaPlayerControllerState = mediaPlayerControllerState.copy(
                    isPlaying = isPlaying,
                )
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                mediaPlayerControllerState = mediaPlayerControllerState.copy(
                    playWhenReady = playWhenReady,
                )
            }

            override fun onVolumeChanged(volume: Float) {
                mediaPlayerControllerState = mediaPlayerControllerState.copy(
                    isMuted = volume == 0f,
                )
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                    exoPlayer.duration.takeIf { it >= 0 }
                        ?.let {
                            mediaPlayerControllerState = mediaPlayerControllerState.copy(
                                durationInMillis = it,
                            )
                        }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                mediaPlayerControllerState = mediaPlayerControllerState.copy(
                    isReady = playbackState == STATE_READY,
                    isBuffering = playbackState == STATE_BUFFERING,
                    // Любое нормальное состояние снимает прошлую ошибку
                    hasError = false,
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Timber.w(error, "Player error")
                mediaPlayerControllerState = mediaPlayerControllerState.copy(
                    isBuffering = false,
                    hasError = true,
                )
                if (error is ExoPlaybackException && error.cause is ParserException) {
                    // The cause can be:
                    // androidx.media3.common.ParserException: Invalid NAL length {contentIsMalformed=true, dataType=1}
                    // This has been observed when the user wants to play a second time a recorded video.
                    // Workaround the issue #6956, start the playback again
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }
    }

    var autoHideController by remember { mutableIntStateOf(0) }

    // Форк: подсказка о двойном тапе (±10 с), которая сама гаснет.
    var seekFeedback: SeekFeedback? by remember { mutableStateOf(null) }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(700)
            seekFeedback = null
        }
    }

    val isTalkbackActive = isTalkbackActive()
    LaunchedEffect(autoHideController, isTalkbackActive) {
        if (isTalkbackActive) {
            // Ensure that the controller is always visible when talkback is active
            mediaPlayerControllerState = mediaPlayerControllerState.copy(
                isVisible = true,
            )
        } else {
            delay(5.seconds)
            // playWhenReady, а не isPlaying: при буферизации видео не «идёт», но контролы прятать надо
            if (exoPlayer.playWhenReady) {
                mediaPlayerControllerState = mediaPlayerControllerState.copy(
                    isVisible = false,
                )
            }
        }
    }

    if (localMedia?.uri != null) {
        LaunchedEffect(localMedia.uri) {
            val mediaItem = MediaItem.fromUri(localMedia.uri)
            exoPlayer.setMediaItem(mediaItem)
        }
    } else {
        exoPlayer.setMediaItems(emptyList())
    }
    KeepScreenOn(mediaPlayerControllerState.playWhenReady)
    Box(
        modifier = modifier
            .background(ElementTheme.colors.bgSubtlePrimary),
    ) {
        val context = LocalContext.current
        if (LocalInspectionMode.current) {
            Text(
                modifier = Modifier
                    .background(ElementTheme.colors.bgSubtlePrimary)
                    .align(Alignment.Center),
                text = "A Video Player will render here",
            )
        } else {
            val videoDescription = if (forPreview) {
                stringResource(CommonStrings.a11y_video_preview)
            } else {
                stringResource(CommonStrings.common_video)
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    // Форк: вместо telephoto-зума (он ловил двойной тап на себя и для видео был
                    // бесполезен) — обычные жесты: тап прячет контролы, двойной тап перематывает.
                    .pointerInput(exoPlayer) {
                        detectTapGestures(
                            onTap = {
                                autoHideController++
                                mediaPlayerControllerState = mediaPlayerControllerState.copy(
                                    isVisible = !mediaPlayerControllerState.isVisible,
                                )
                            },
                            onDoubleTap = { position ->
                                val isForward = position.x >= size.width / 2f
                                val duration = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                                val target = (exoPlayer.currentPosition + if (isForward) SEEK_STEP_MILLIS else -SEEK_STEP_MILLIS)
                                    .coerceIn(0L, duration)
                                exoPlayer.seekTo(target)
                                if (exoPlayer.playbackState == STATE_READY) {
                                    exoPlayer.play()
                                }
                                seekFeedback = SeekFeedback(isForward)
                                autoHideController++
                                mediaPlayerControllerState = mediaPlayerControllerState.copy(isVisible = false)
                            },
                        )
                    },
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                        useController = false
                        contentDescription = videoDescription
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
            )
        }

        val controllerState = mediaPlayerControllerState
        val a11yBuffering = stringResource(R.string.vavsta_video_buffering)
        // Форк: центральный блок — кнопка play с кольцом загрузки вокруг неё (как в Telegram),
        // а не отдельный спиннер. Кольцо крутится и показывает, сколько уже скачано.
        if (!LocalInspectionMode.current) {
            AnimatedVisibility(
                visible = controllerState.isReady && !controllerState.playWhenReady && !controllerState.hasError,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                VideoCenterButton(
                    isLoading = false,
                    bufferedProgress = 1f,
                    isPlayingRequested = false,
                    onClick = {
                        autoHideController++
                        exoPlayer.togglePlay()
                    },
                )
            }

            // Загрузка/буферизация: та же кнопка, но с кольцом прогресса вокруг.
            AnimatedVisibility(
                visible = (controllerState.isBuffering || !controllerState.isReady) && !controllerState.hasError,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                VideoCenterButton(
                    isLoading = true,
                    bufferedProgress = controllerState.bufferedAsFloat,
                    isPlayingRequested = controllerState.playWhenReady,
                    onClick = {
                        autoHideController++
                        exoPlayer.togglePlay()
                    },
                )
            }

            if (controllerState.hasError) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = CompoundIcons.Error(),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = stringResource(R.string.vavsta_video_error),
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        style = ElementTheme.typography.fontBodyMdRegular,
                    )
                    IconButton(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        onClick = {
                            mediaPlayerControllerState = mediaPlayerControllerState.copy(hasError = false)
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = CompoundIcons.RotateRight(),
                            contentDescription = stringResource(CommonStrings.action_retry),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            seekFeedback?.let { feedback ->
                SeekFeedbackOverlay(
                    isForward = feedback.isForward,
                    modifier = Modifier.align(
                        if (feedback.isForward) Alignment.CenterEnd else Alignment.CenterStart,
                    ),
                )
            }
        }

        if (localMedia != null) {
            MediaPlayerControllerView(
                state = mediaPlayerControllerState,
                onTogglePlay = {
                    autoHideController++
                    exoPlayer.togglePlay()
                },
                onSeekChange = {
                    autoHideController++
                    mediaPlayerControllerState = mediaPlayerControllerState.copy(
                        seekingToMillis = it.toLong(),
                    )
                    exoPlayer.seekToEnsurePlaying(it.toLong())
                },
                onToggleMute = {
                    autoHideController++
                    exoPlayer.volume = if (exoPlayer.volume == 1f) 0f else 1f
                },
                audioFocus = audioFocus,
                onCyclePlaybackSpeed = {
                    autoHideController++
                    val current = mediaPlayerControllerState.playbackSpeed
                    val next = PlaybackSpeeds[(PlaybackSpeeds.indexOf(current) + 1).mod(PlaybackSpeeds.size)]
                    exoPlayer.setPlaybackSpeed(next)
                    mediaPlayerControllerState = mediaPlayerControllerState.copy(playbackSpeed = next)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPaddingInPixels.toDp()),
            )
        }
    }

    // Форк: раньше цикл крутился только пока isPlaying, то есть ровно тогда, когда он НЕ нужен.
    // При буферизации ExoPlayer отдаёт isPlaying == false, и bufferedPercentage переставал
    // обновляться как раз в тот момент, когда его и надо было рисовать. Теперь цикл живой всегда,
    // а в простое опрашиваем реже.
    LaunchedEffect(exoPlayer) {
        while (true) {
            val position = exoPlayer.currentPosition
            val seekingTo = mediaPlayerControllerState.seekingToMillis
            mediaPlayerControllerState = mediaPlayerControllerState.copy(
                progressInMillis = position,
                seekingToMillis = if (seekingTo != null && position >= seekingTo) null else seekingTo,
                bufferedPercentage = exoPlayer.bufferedPercentage,
            )
            delay(if (exoPlayer.isPlaying) 200 else 600)
        }
    }

    ExoPlayerLifecycleHelper(
        exoPlayer = exoPlayer,
        autoplay = autoplay,
        isDisplayed = isDisplayed,
        playerListener = playerListener,
        mediaPlayerControllerState = mediaPlayerControllerState,
    )
}

private data class SeekFeedback(val isForward: Boolean)

/**
 * Форк: центральная кнопка плеера с кольцом загрузки вокруг неё.
 *
 * Во время буферизации показываем play/pause и кольцо прогресса вокруг — как в Telegram. Кольцо
 * рисует ту же долю, что и seek-бар (буферизованная часть), а при isLoading без известного
 * прогресса просто крутится индикатором.
 */
@Composable
private fun VideoCenterButton(
    isLoading: Boolean,
    bufferedProgress: Float,
    isPlayingRequested: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val a11yLabel = stringResource(if (isPlayingRequested) CommonStrings.a11y_pause else CommonStrings.a11y_play)
    val a11yBuffering = stringResource(R.string.vavsta_video_buffering)

    Box(
        modifier = modifier.size(84.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Фон-кнопка
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                modifier = Modifier.size(72.dp),
                onClick = onClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = if (isPlayingRequested) CompoundIcons.PauseSolid() else CompoundIcons.PlaySolid(),
                    contentDescription = a11yLabel,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // Кольцо загрузки вокруг кнопки. Это декорация без pointerInput — тапы проходят насквозь,
        // так что клик по кнопке под ним работает как раньше.
        if (isLoading) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .semantics { contentDescription = a11yBuffering },
                contentAlignment = Alignment.Center,
            ) {
                val progress = bufferedProgress.coerceIn(0f, 1f)
                if (progress > 0f) {
                    // Прогресс известен — кольцо-дуга с дорожкой
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(84.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 3.dp,
                    )
                } else {
                    // Прогресс неизвестен (0% или доля не пришла) — крутим индикатор
                    CircularProgressIndicator(
                        modifier = Modifier.size(84.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        strokeWidth = 3.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekFeedbackOverlay(
    isForward: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier.padding(28.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = if (isForward) CompoundIcons.ArrowRight() else CompoundIcons.ArrowLeft(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = stringResource(
                    if (isForward) R.string.vavsta_video_seek_forward else R.string.vavsta_video_seek_back,
                    (SEEK_STEP_MILLIS / 1000).toInt(),
                ),
                textAlign = TextAlign.Center,
                color = Color.White,
                style = ElementTheme.typography.fontBodySmMedium,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ExoPlayerLifecycleHelper(
    exoPlayer: ExoPlayer,
    autoplay: Boolean,
    isDisplayed: Boolean,
    playerListener: Player.Listener,
    mediaPlayerControllerState: MediaPlayerControllerState,
) {
    // Prepare and release the exoPlayer with the composable lifecycle
    DisposableEffect(Unit) {
        Timber.d("ExoPlayerMediaVideoView DisposableEffect: initializing exoPlayer")
        exoPlayer.addListener(playerListener)
        exoPlayer.prepare()

        onDispose {
            Timber.d("Disposing exoplayer")
            if (!exoPlayer.isReleased) {
                exoPlayer.removeListener(playerListener)
                exoPlayer.release()
            }
        }
    }

    var needsAutoPlay by remember { mutableStateOf(autoplay) }
    LaunchedEffect(needsAutoPlay, isDisplayed, mediaPlayerControllerState.isReady) {
        val isReadyAndNotPlaying = mediaPlayerControllerState.isReady && !mediaPlayerControllerState.isPlaying
        if (needsAutoPlay && isDisplayed && isReadyAndNotPlaying) {
            // When displayed, start autoplaying
            exoPlayer.play()
            needsAutoPlay = false
        } else if (!isDisplayed && mediaPlayerControllerState.isPlaying) {
            // If not displayed, make sure to pause the video
            exoPlayer.pause()
        }
    }

    // Pause playback when lifecycle is paused
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_PAUSE && exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }
}

@PreviewsDayNight
@Composable
internal fun MediaVideoViewPreview() = ElementPreview {
    MediaVideoView(
        isDisplayed = true,
        modifier = Modifier.fillMaxSize(),
        bottomPaddingInPixels = 0,
        localMediaViewState = rememberLocalMediaViewState(),
        localMedia = null,
        audioFocus = null,
        autoplay = false,
        forPreview = false,
    )
}
