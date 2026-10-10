/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaviewer.impl.local.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Форк: свой seek-бар вместо Material Slider — на стоковом нельзя показать буферизованную часть
 * (то, что уже скачано), из-за чего перемотка вперёд выглядела как зависание без обратной связи.
 */
@Composable
internal fun MediaPlayerSeekBar(
    value: Float,
    buffered: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinish: (() -> Unit)? = null,
    enabled: Boolean = true,
    a11yStateDescription: String = "",
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }
    val displayValue = if (isDragging) dragValue else value

    // Токены и плотность читаем до Canvas: внутри DrawScope @Composable вызовы запрещены.
    val playedColor = ElementTheme.colors.bgCanvasDefault
    val density = LocalDensity.current
    val trackHeightPx = with(density) { 4.dp.toPx() }
    val thumbRadiusPx = with(density) { if (isDragging) 8.dp.toPx() else 5.dp.toPx() }
    val trackColor = Color.White.copy(alpha = 0.25f)
    val bufferedColor = Color.White.copy(alpha = 0.45f)
    val thumbHaloColor = playedColor.copy(alpha = 0.3f)

    Canvas(
        modifier = modifier
            .height(24.dp)
            .semantics {
                if (enabled) {
                    progressBarRangeInfo = ProgressBarRangeInfo(displayValue, 0f..1f)
                    if (a11yStateDescription.isNotEmpty()) stateDescription = a11yStateDescription
                } else {
                    // Молчим, но оставляем элемент узлом семантики, чтобы фокус не терялся
                    contentDescription = ""
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { position ->
                    val width = size.width.coerceAtLeast(1)
                    onValueChange((position.x / width).coerceIn(0f, 1f))
                    onValueChangeFinish?.invoke()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        val width = size.width.coerceAtLeast(1)
                        isDragging = true
                        dragValue = (position.x / width).coerceIn(0f, 1f)
                        onValueChange(dragValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinish?.invoke()
                    },
                    onDragCancel = { isDragging = false },
                ) { change, _ ->
                    val width = size.width.coerceAtLeast(1)
                    change.consume()
                    dragValue = (change.position.x / width).coerceIn(0f, 1f)
                    onValueChange(dragValue)
                }
            },
    ) {
        val trackTop = (size.height - trackHeightPx) / 2f
        val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

        // Незагруженное
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeightPx),
            cornerRadius = cornerRadius,
        )
        // Буферизованное
        val bufferedWidth = size.width * buffered.coerceIn(0f, 1f)
        if (bufferedWidth > 0f) {
            drawRoundRect(
                color = bufferedColor,
                topLeft = Offset(0f, trackTop),
                size = Size(bufferedWidth, trackHeightPx),
                cornerRadius = cornerRadius,
            )
        }
        // Пройденное
        val playedWidth = size.width * displayValue.coerceIn(0f, 1f)
        if (playedWidth > 0f) {
            drawRoundRect(
                color = playedColor,
                topLeft = Offset(0f, trackTop),
                size = Size(playedWidth, trackHeightPx),
                cornerRadius = cornerRadius,
            )
        }
        // Ползунок
        val thumbX = playedWidth.coerceIn(thumbRadiusPx, size.width - thumbRadiusPx)
        if (isDragging) {
            drawCircle(
                color = thumbHaloColor,
                radius = thumbRadiusPx * 2f,
                center = Offset(thumbX, size.height / 2f),
            )
        }
        drawCircle(
            color = playedColor,
            radius = thumbRadiusPx,
            center = Offset(thumbX, size.height / 2f),
        )
    }
}

@Preview
@Composable
private fun MediaPlayerSeekBarPreview() = ElementPreview {
    MediaPlayerSeekBar(
        value = 0.42f,
        buffered = 0.75f,
        onValueChange = {},
        a11yStateDescription = "42%",
    )
    Text(text = "…и с буферизацией", style = ElementTheme.typography.fontBodyXsMedium)
    MediaPlayerSeekBar(
        value = 0.9f,
        buffered = 1f,
        onValueChange = {},
        a11yStateDescription = "90%",
    )
}
