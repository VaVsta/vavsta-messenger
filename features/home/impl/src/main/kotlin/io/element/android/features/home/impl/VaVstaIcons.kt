/*
 * VaVsta Messenger — собственные иконки нижней навигации вместо CompoundIcons.
 * AGPL-3.0-or-later.
 */

package io.element.android.features.home.impl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Кастомные иконки нижней навигации VaVsta Messenger.
 * Рисунок: скруглённый пузырь с хвостиком + точки; «пространства» — стопка карточек.
 */
object VaVstaIcons {
    private val LineColor = Color.White
    private val StrokeW = 1.8f

    val Chat: ImageVector by lazy { chatIcon(filled = false) }
    val ChatSolid: ImageVector by lazy { chatIcon(filled = true) }
    val Space: ImageVector by lazy { spaceIcon(filled = false) }
    val SpaceSolid: ImageVector by lazy { spaceIcon(filled = true) }

    private fun chatIcon(filled: Boolean): ImageVector = ImageVector.Builder(
        name = "VaVstaChat",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = if (filled) SolidColor(LineColor) else SolidColor(Color.Transparent),
            stroke = SolidColor(LineColor),
            strokeLineWidth = StrokeW,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // пузырь с хвостиком
            moveTo(5.5f, 3.5f)
            horizontalLineToRelative(13f)
            arcToRelative(2.2f, 2.2f, 0f, false, true, 2.2f, 2.2f)
            verticalLineToRelative(8.4f)
            arcToRelative(2.2f, 2.2f, 0f, false, true, -2.2f, 2.2f)
            horizontalLineToRelative(-8.2f)
            lineToRelative(-3.4f, 3.0f)
            lineToRelative(-0.6f, -3.0f)
            horizontalLineToRelative(-0.8f)
            arcToRelative(2.2f, 2.2f, 0f, false, true, -2.2f, -2.2f)
            verticalLineToRelative(-8.4f)
            arcToRelative(2.2f, 2.2f, 0f, false, true, 2.2f, -2.2f)
            close()
        }
        if (!filled) {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(LineColor),
                strokeLineWidth = StrokeW,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(9f, 9f)
                horizontalLineToRelative(6f)
                moveTo(9f, 12f)
                horizontalLineToRelative(4f)
            }
        }
    }.build()

    private fun spaceIcon(filled: Boolean): ImageVector = ImageVector.Builder(
        name = "VaVstaSpace",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        if (filled) {
            path(fill = SolidColor(LineColor)) {
                moveTo(4f, 7f)
                arcToRelative(2.4f, 2.4f, 0f, false, true, 2.4f, -2.4f)
                horizontalLineToRelative(7.2f)
                arcToRelative(2.4f, 2.4f, 0f, false, true, 2.4f, 2.4f)
                verticalLineToRelative(7.2f)
                arcToRelative(2.4f, 2.4f, 0f, false, true, -2.4f, 2.4f)
                horizontalLineToRelative(-7.2f)
                arcToRelative(2.4f, 2.4f, 0f, false, true, -2.4f, -2.4f)
                close()
                moveTo(13f, 15.6f)
                horizontalLineToRelative(4.4f)
                arcToRelative(2.4f, 2.4f, 0f, false, true, 2.4f, 2.4f)
                verticalLineToRelative(1.6f)
                arcToRelative(2.4f, 2.4f, 0f, false, true, -2.4f, 2.4f)
                horizontalLineToRelative(-7.2f)
                arcToRelative(2.4f, 2.4f, 0f, false, true, -2.4f, -2.4f)
                verticalLineToRelative(-1.6f)
                arcToRelative(2.4f, 2.4f, 0f, false, true, 2.4f, -2.4f)
                close()
            }
        } else {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(LineColor),
                strokeLineWidth = StrokeW,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 6.8f)
                arcToRelative(2.3f, 2.3f, 0f, false, true, 2.3f, -2.3f)
                horizontalLineToRelative(7.2f)
                arcToRelative(2.3f, 2.3f, 0f, false, true, 2.3f, 2.3f)
                verticalLineToRelative(7.4f)
                arcToRelative(2.3f, 2.3f, 0f, false, true, -2.3f, 2.3f)
                horizontalLineToRelative(-7.2f)
                arcToRelative(2.3f, 2.3f, 0f, false, true, -2.3f, -2.3f)
                close()
                moveTo(12.6f, 15.8f)
                horizontalLineToRelative(4.5f)
                arcToRelative(2.3f, 2.3f, 0f, false, true, 2.3f, 2.3f)
                verticalLineToRelative(0.9f)
                arcToRelative(2.3f, 2.3f, 0f, false, true, -2.3f, 2.3f)
                horizontalLineToRelative(-7.2f)
                arcToRelative(2.3f, 2.3f, 0f, false, true, -2.3f, -2.3f)
                verticalLineToRelative(-0.9f)
                arcToRelative(2.3f, 2.3f, 0f, false, true, 2.3f, -2.3f)
            }
        }
    }.build()
}