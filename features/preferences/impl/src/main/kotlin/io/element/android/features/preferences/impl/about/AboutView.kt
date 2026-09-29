/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun AboutView(
    state: AboutState,
    onOpenSourceLicensesClick: () -> Unit,
    onBackClick: () -> Unit,
    onOpenUrl: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = CommonStrings.common_about)
    ) {
        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "VaVsta",
                style = ElementTheme.typography.fontHeadingLgBold,
                color = ElementTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Messenger",
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Версия ${state.versionName}",
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(28.dp))
        ListItem(
            content = { Text("Разработчик: ${state.authorName}") },
            supportingContent = { Text("Telegram: ${state.authorTelegram}") },
            onClick = { onOpenUrl(state.telegramUrl) },
        )
        ListItem(
            content = { Text("Исходный код") },
            supportingContent = { Text(state.sourceCodeUrl) },
            onClick = { state.sourceCodeUrl.takeIf { it.isNotBlank() }?.let(onOpenUrl) },
        )
        ListItem(
            content = {
                Text(stringResource(id = CommonStrings.common_open_source_licenses))
            },
            onClick = onOpenSourceLicensesClick,
        )
        when (state.updateStatus) {
            UpdateUiStatus.Unknown -> ListItem(
                content = { Text("Проверить обновления") },
                supportingContent = { Text("Обновление не настроено") },
                onClick = state.onCheckUpdate,
            )
            UpdateUiStatus.Checking -> ListItem(
                content = { Text("Проверка обновлений…") },
                trailingContent = ListItemContent.Custom {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                },
            )
            UpdateUiStatus.UpToDate -> ListItem(
                content = { Text("Установлена последняя версия") },
                supportingContent = { Text("Нажмите, чтобы проверить ещё раз") },
                onClick = state.onCheckUpdate,
            )
            UpdateUiStatus.UpdateAvailable -> ListItem(
                content = {
                    Text("Доступна версия ${state.latestVersionName ?: ""}", color = ElementTheme.colors.textActionPrimary)
                },
                supportingContent = { Text(state.updateNotes ?: "Нажмите, чтобы скачать") },
                onClick = state.onDownloadUpdate,
            )
            UpdateUiStatus.Downloading -> ListItem(
                content = { Text("Загрузка обновления… ${state.downloadProgress}%") },
                trailingContent = ListItemContent.Custom {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                },
            )
            UpdateUiStatus.ReadyToInstall -> ListItem(
                content = { Text("Обновление скачано", color = ElementTheme.colors.textActionPrimary) },
                supportingContent = { Text("Нажмите, чтобы установить") },
                onClick = state.onInstallUpdate,
            )
            UpdateUiStatus.InstallPermissionNeeded -> ListItem(
                content = { Text("Разрешите установку из источника", color = ElementTheme.colors.textActionPrimary) },
                supportingContent = { Text("Нажмите, чтобы открыть настройки") },
                onClick = state.onInstallUpdate,
            )
            UpdateUiStatus.Error -> ListItem(
                content = { Text("Не удалось проверить обновления") },
                supportingContent = { Text("Нажмите, чтобы повторить") },
                onClick = state.onCheckUpdate,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Форк Element X (element-hq/element-x-android).\nРаспространяется под лицензией AGPL-3.0.",
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 320.dp)
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Иконка приложения, выбранная системой (adaptive icon).
 * Если система не отдаёт иконку (или пакет неизвестен), показываем заглушку, а не падаем.
 */
@Composable
private fun AppIcon(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconBitmap = remember {
        runCatching {
            context.packageManager
                .getApplicationIcon(context.packageName)
                .toBitmap(192, 192)
                .asImageBitmap()
        }.getOrNull()
    }
    val shape = RoundedCornerShape(22.dp)
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = modifier
                .size(96.dp)
                .clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(96.dp)
                .clip(shape)
                .background(ElementTheme.colors.bgSubtlePrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "V",
                style = ElementTheme.typography.fontHeadingLgBold,
                color = ElementTheme.colors.textPrimary,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun AboutViewPreview(@PreviewParameter(AboutStatePreviewParam::class) state: AboutState) = ElementPreview {
    AboutView(
        state = state,
        onOpenSourceLicensesClick = {},
        onBackClick = {},
    )
}
