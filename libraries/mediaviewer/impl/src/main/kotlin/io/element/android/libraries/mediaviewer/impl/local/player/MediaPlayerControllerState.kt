/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.local.player

import androidx.annotation.FloatRange

data class MediaPlayerControllerState(
    val isVisible: Boolean,
    val isPlaying: Boolean,
    val isReady: Boolean,
    val progressInMillis: Long,
    val durationInMillis: Long,
    val canMute: Boolean,
    val isMuted: Boolean,
    val seekingToMillis: Long?,
    // Форк: буферизация, ошибка и скорость. Раньше их не было, и из-за этого на перемотку в
    // небуферизованную зону кнопка play мигала (ExoPlayer.isPlaying == false во время буферизации),
    // а ошибка декодирования выглядела как вечное «видео не грузится».
    val isBuffering: Boolean = false,
    /** `Player.playWhenReady` — пользователь нажал play, даже если кадр пока не поступил. */
    val playWhenReady: Boolean = false,
    @FloatRange(from = 0.0, to = 100.0)
    val bufferedPercentage: Int = 0,
    val hasError: Boolean = false,
    val canChangePlaybackSpeed: Boolean = false,
    val playbackSpeed: Float = 1f,
) {
    /**
     * The progress in milliseconds to display. When [seekingToMillis] is non-null (during a seek operation),
     * this returns the target seek position. Once the player catches up to the seek position,
     * [seekingToMillis] is cleared (set to null) and this returns [progressInMillis] again.
     */
    val displayProgressInMillis: Long
        get() = seekingToMillis ?: progressInMillis

    /** NaN при duration == 0 проглатывать нельзя: Slider с таким значением рисуется криво. */
    @FloatRange(from = 0.0, to = 1.0)
    val progressAsFloat: Float =
        if (durationInMillis <= 0L) {
            0f
        } else {
            (displayProgressInMillis.toFloat() / durationInMillis.toFloat()).coerceIn(0f, 1f)
        }

    @FloatRange(from = 0.0, to = 1.0)
    val bufferedAsFloat: Float = (bufferedPercentage / 100f).coerceIn(0f, 1f)
}
