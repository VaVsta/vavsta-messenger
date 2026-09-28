/*
 * Copyright (c) 2026 vavsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.workmanager.api.di.MetroWorkerFactory
import io.element.android.libraries.workmanager.api.di.WorkerKey
import timber.log.Timber

/**
 * Фоновая проверка обновлений: ходит на сервер манифестов, и если версия свежее установленной —
 * показывает тихое уведомление.
 *
 * Ничего не скачивает и не ставит: APK качает пользователь сам с экрана «О VaVsta».
 */
@AssistedInject
class UpdateCheckWorker(
    @Assisted params: WorkerParameters,
    @ApplicationContext private val context: Context,
    private val store: UpdateInfoStore,
    private val notificationCreator: UpdateNotificationCreator,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!UpdateServerConfig.isConfigured) {
            Timber.i("Update check skipped: no HTTPS update URL configured")
            return Result.success()
        }

        val installed = UpdateChecker.installedVersionCode(context)
        val info = UpdateChecker.fetchUpdateInfo()

        if (info == null) {
            // fetchUpdateInfo() отдаёт null и на сетевых ошибках, и на невалидном манифесте.
            // Несколько попыток — и забиваем до следующего планового прогона.
            return if (runAttemptCount < MAX_ATTEMPTS) {
                Timber.w("Update check failed, retry ${runAttemptCount + 1}/$MAX_ATTEMPTS")
                Result.retry()
            } else {
                Timber.w("Update check gave up after $MAX_ATTEMPTS attempts")
                Result.success()
            }
        }

        if (info.versionCode <= installed) {
            Timber.i("No update available: installed=$installed, available=${info.versionCode}")
            store.clear()
            return Result.success()
        }

        // Кэш нужен, чтобы «О VaVsta» показал заметки и кнопку скачивания без второго запроса.
        store.put(info)

        if (!store.isNotified(info.versionCode)) {
            notificationCreator.show(info)
            store.markNotified(info.versionCode)
        } else {
            Timber.i("Update ${info.versionCode} already notified, only cache refreshed")
        }

        return Result.success()
    }

    @ContributesIntoMap(AppScope::class, binding = binding<MetroWorkerFactory.WorkerInstanceFactory<*>>())
    @WorkerKey(UpdateCheckWorker::class)
    @AssistedFactory
    interface Factory : MetroWorkerFactory.WorkerInstanceFactory<UpdateCheckWorker>

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
