/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

@SuppressLint("ServiceCast")
@Inject
class AboutPresenter(
    @ApplicationContext private val context: Context,
    private val buildMeta: BuildMeta,
    private val updateInfoStore: UpdateInfoStore,
) : Presenter<AboutState> {
    @Composable
    override fun present(): AboutState {
        val scope = rememberCoroutineScope()
        var updateStatus by remember { mutableStateOf(UpdateUiStatus.Unknown) }
        var latestVersionName by remember { mutableStateOf<String?>(null) }
        var updateNotes by remember { mutableStateOf<String?>(null) }
        var downloadProgress by remember { mutableStateOf(0) }
        var checkJob by remember { mutableStateOf<Job?>(null) }
        var downloadJob by remember { mutableStateOf<Job?>(null) }
        var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
        var pendingApk by remember { mutableStateOf<File?>(null) }

        fun showUpdate(info: UpdateInfo) {
            availableUpdate = info
            latestVersionName = info.versionName
            updateNotes = info.notes
            updateStatus = UpdateUiStatus.UpdateAvailable
        }

        fun checkUpdate() {
            if (checkJob?.isActive == true) return
            // Пустой URL в конфиге = сервер обновлений не настроен, проверку не запускаем.
            if (!UpdateServerConfig.isConfigured) {
                updateStatus = UpdateUiStatus.Unknown
                return
            }
            updateStatus = UpdateUiStatus.Checking
            checkJob = scope.launch {
                val info = UpdateChecker.fetchUpdateInfo()
                val installed = UpdateChecker.installedVersionCode(context)
                if (info == null) {
                    updateStatus = UpdateUiStatus.Error
                } else if (info.versionCode > installed) {
                    // Кэш нужен и фоновой проверке, чтобы не показывать уведомление дважды.
                    updateInfoStore.put(info)
                    showUpdate(info)
                } else {
                    updateInfoStore.clear()
                    updateStatus = UpdateUiStatus.UpToDate
                }
            }
        }

        fun downloadUpdate() {
            if (updateStatus != UpdateUiStatus.UpdateAvailable) return
            val info = availableUpdate ?: return
            if (downloadJob?.isActive == true) return
            downloadProgress = 0
            updateStatus = UpdateUiStatus.Downloading
            downloadJob = scope.launch {
                val apk = UpdateChecker.downloadUpdate(context, info) { percent ->
                    scope.launch { downloadProgress = percent }
                }
                if (apk == null) {
                    updateStatus = UpdateUiStatus.Error
                } else {
                    pendingApk = apk
                    updateStatus = UpdateUiStatus.ReadyToInstall
                }
            }
        }

        fun installUpdate() {
            val apk = pendingApk ?: return
            if (!UpdateChecker.canRequestInstalls(context)) {
                if (!UpdateChecker.openInstallPermissionSettings(context)) {
                    // Раньше тут был молчаливый runCatching: кнопка ничего не делала и не врала.
                    updateStatus = UpdateUiStatus.Error
                    return
                }
                updateStatus = UpdateUiStatus.InstallPermissionNeeded
                return
            }
            if (!UpdateChecker.installUpdate(context, apk)) {
                Timber.w("Failed to launch the package installer")
                updateStatus = UpdateUiStatus.Error
            }
        }

        // При открытии экрана сначала показываем то, что уже нашла фоновая проверка, — без сетевого запроса.
        // Если в кэше ничего нет, проверяем сами.
        LaunchedEffect(Unit) {
            val installed = UpdateChecker.installedVersionCode(context)
            val cached = updateInfoStore.get()
            if (cached != null && cached.versionCode > installed) {
                showUpdate(cached)
            } else {
                checkUpdate()
            }
        }

        return AboutState(
            versionName = buildMeta.versionName,
            updateStatus = updateStatus,
            latestVersionName = latestVersionName,
            updateNotes = updateNotes,
            downloadProgress = downloadProgress,
            onCheckUpdate = ::checkUpdate,
            onDownloadUpdate = ::downloadUpdate,
            onInstallUpdate = ::installUpdate,
        )
    }
}
