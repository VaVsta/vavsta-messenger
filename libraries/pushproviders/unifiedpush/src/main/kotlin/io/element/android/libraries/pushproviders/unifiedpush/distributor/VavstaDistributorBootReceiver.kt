/*
 * Copyright (c) 2026 VaVsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.pushproviders.unifiedpush.distributor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Поднимает сокет после событий, при которых процесс приложения был убит.
 *
 * UnifiedPush-коннектор повторный REGISTER не шлёт, если регистрация уже есть,
 * поэтому в [VavstaDistributorReceiver] сервис стартует только при первой
 * регистрации. После перезагрузки телефона или обновления APK процесс умирает,
 * а сокет вместе с ним — и push перестают доходить. Здесь мы это чиним.
 *
 * Про `BOOT_COMPLETED`: на Android 15+ запуск foreground-сервиса типа `dataSync`
 * из `BOOT_COMPLETED` системой запрещён, поэтому на новых версиях сработает
 * только `MY_PACKAGE_REPLACED` (обновление приложения). На старых Android и при
 * перезагрузке — оба.
 */
class VavstaDistributorBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (!DistributorStore.isActive(context)) {
                    Timber.i("Distributor не выбран, сокет не поднимаю")
                    return
                }
                Timber.i("Поднимаю сокет после ${intent.action}")
                DistributorSocketService.start(context)
            }
        }
    }
}
