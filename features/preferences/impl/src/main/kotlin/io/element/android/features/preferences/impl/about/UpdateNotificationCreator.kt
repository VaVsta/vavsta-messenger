/*
 * Copyright (c) 2026 vavsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.api.UpdateCheckIntents
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.di.annotations.ApplicationContext
import timber.log.Timber

/**
 * Тихое уведомление «Доступно обновление».
 *
 * Только сигнал: ничего не качает и не ставит, по тапу открывает экран «О VaVsta», где уже лежит
 * разобранный манифест (см. [UpdateInfoStore]).
 */
@Inject
class UpdateNotificationCreator(
    @ApplicationContext private val context: Context,
) {
    fun show(info: UpdateInfo) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Timber.i("Update notification skipped: notifications are disabled for the app")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureChannelExists()
        }

        val title = context.getString(R.string.vavsta_update_available_title, info.versionName)
        val text = info.notes?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.vavsta_update_available_text)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openAboutPendingIntent())
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // На Android 13+ без разрешения POST_NOTIFICATIONSnotify() бросает исключение.
            Timber.w(e, "Update notification not posted: missing POST_NOTIFICATIONS permission")
        }
    }

    /**
     * Экран About лежит в модуле настроек, а [io.element.android.x.MainActivity] — в модуле app,
     * откуда этот класс не видится. Поэтому шлём implicit intent по своему action: он ловится
     * intent-filter-ом, объявленным для MainActivity в AndroidManifest.
     */
    private fun openAboutPendingIntent(): PendingIntent {
        val intent = Intent(UpdateCheckIntents.ACTION_OPEN_ABOUT).apply {
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun ensureChannelExists() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.vavsta_update_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.vavsta_update_channel_description)
            }
        )
    }

    private companion object {
        const val CHANNEL_ID = "VAVSTA_UPDATES"
        const val NOTIFICATION_ID = 4201
    }
}
