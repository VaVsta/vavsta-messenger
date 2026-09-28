/*
 * Copyright (c) 2026 vavsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext
import java.util.concurrent.TimeUnit

/**
 * Планировщик фоновой проверки обновлений: раз в сутки по расписанию плюс проверка на старте приложения.
 */
@Inject
class UpdateCheckScheduler(
    @ApplicationContext private val context: Context,
) {
    /** Раз в сутки, только с сетью. KEEP, чтобы перезапуск приложения не сбрасывал таймер заново. */
    fun scheduleDaily() {
        val request = PeriodicWorkRequest.Builder(
            workerClass = UpdateCheckWorker::class.java,
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
        )
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Проверка при запуске приложения. KEEP, чтобы не плодить запросы, если предыдущий ещё в очереди. */
    fun checkOnStart() {
        val request = OneTimeWorkRequest.Builder(UpdateCheckWorker::class.java)
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ON_START_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val DAILY_WORK_NAME = "vavsta_update_check_daily"
        const val ON_START_WORK_NAME = "vavsta_update_check_on_start"
    }
}
