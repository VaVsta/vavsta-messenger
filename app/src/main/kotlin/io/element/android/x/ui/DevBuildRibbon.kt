/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.x.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.x.BuildConfig

/**
 * VaVsta: красная лента «DEV» через правый верхний угол — чтобы dev-сборку, которая ставится
 * рядом с продом под своим `applicationId`, было видно сразу и нельзя было перепутать.
 *
 * Рисуется поверх всего содержимого окна и только в debug-сборке: в релиз попасть не может.
 */
@Composable
fun BoxScope.DevBuildRibbon(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return
    Box(
        modifier = modifier
            .align(Alignment.TopEnd)
            // Чтобы лента не залезала под статус-бар и вырез камеры.
            // Поворот на 45° идёт вокруг центра ленты, а сам центр прижат к углу экрана —
            // без этого отступа надпись уезжает ровно под статус-бар и её не видно.
            .padding(top = 50.dp)
            .rotate(45f)
            .width(190.dp)
            .background(color = MaterialTheme.colorScheme.error)
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // Версию в ленту не втыкаем: длинный текст всё равно уезжает под статус-бар.
            text = "DEV",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
        )
    }
}