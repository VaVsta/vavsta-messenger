/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.local.player

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

open class MediaPlayerControllerStatePreviewParam : PreviewParameterProvider<MediaPlayerControllerState> {
    override val values: Sequence<MediaPlayerControllerState> = sequenceOf(
        aMediaPlayerControllerState(),
        aMediaPlayerControllerState(
            isPlaying = true,
            playWhenReady = true,
            isReady = true,
            progressInMillis = 59_000,
            durationInMillis = 83_000,
            bufferedPercentage = 75,
            isMuted = true,
        ),
        aMediaPlayerControllerState(
            canMute = false,
        ),
        // Форк: состояния, которых раньше не существовало — буферизация, ошибка, скорость.
        aMediaPlayerControllerState(
            isBuffering = true,
            playWhenReady = true,
            isReady = true,
            progressInMillis = 12_000,
            bufferedPercentage = 40,
        ),
        aMediaPlayerControllerState(
            hasError = true,
            isReady = true,
        ),
        aMediaPlayerControllerState(
            isPlaying = true,
            playWhenReady = true,
            isReady = true,
            canChangePlaybackSpeed = true,
            playbackSpeed = 1.5f,
            bufferedPercentage = 100,
        ),
    )
}

private fun aMediaPlayerControllerState(
    isVisible: Boolean = true,
    isPlaying: Boolean = false,
    isReady: Boolean = false,
    progressInMillis: Long = 0,
    // Default to 1 minute and 23 seconds
    durationInMillis: Long = 83_000,
    canMute: Boolean = true,
    isMuted: Boolean = false,
    seekingToMillis: Long? = null,
    isBuffering: Boolean = false,
    playWhenReady: Boolean = false,
    bufferedPercentage: Int = 0,
    hasError: Boolean = false,
    canChangePlaybackSpeed: Boolean = false,
    playbackSpeed: Float = 1f,
) = MediaPlayerControllerState(
    isVisible = isVisible,
    isPlaying = isPlaying,
    isReady = isReady,
    progressInMillis = progressInMillis,
    durationInMillis = durationInMillis,
    canMute = canMute,
    isMuted = isMuted,
    seekingToMillis = seekingToMillis,
    isBuffering = isBuffering,
    playWhenReady = playWhenReady,
    bufferedPercentage = bufferedPercentage,
    hasError = hasError,
    canChangePlaybackSpeed = canChangePlaybackSpeed,
    playbackSpeed = playbackSpeed,
)
