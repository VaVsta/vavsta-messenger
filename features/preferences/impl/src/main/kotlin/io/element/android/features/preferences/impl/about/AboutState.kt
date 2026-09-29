/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

enum class UpdateUiStatus {
    /** Проверка ещё не запускалась или обновление недоступно из-за пустого конфига. */
    Unknown,

    /** Идёт запрос к серверу. */
    Checking,

    /** Установлена последняя версия. */
    UpToDate,

    /** Доступна новая версия. */
    UpdateAvailable,

    /** APK качается в фоне. */
    Downloading,

    /** APK скачан и проверен, можно ставить. */
    ReadyToInstall,

    /** Система не разрешает ставить пакеты — нужен тумблер «установка из источника». */
    InstallPermissionNeeded,

    /** Не удалось получить информацию об обновлении. */
    Error,
}

data class AboutState(
    val appName: String = "VaVsta Messenger",
    val versionName: String = "1.0",
    val authorName: String = "Глюк (vavsta)",
    val authorTelegram: String = "@vavsta",
    val telegramUrl: String = "tg://resolve?domain=vavsta",
    val sourceCodeUrl: String = "https://github.com/VaVsta/vavsta-messenger",
    val updateStatus: UpdateUiStatus = UpdateUiStatus.Unknown,
    val latestVersionName: String? = null,
    val updateNotes: String? = null,
    val downloadProgress: Int = 0,
    val onCheckUpdate: () -> Unit = {},
    val onDownloadUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
)
