/*
 * Copyright (c) 2026 vavsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import android.content.Context
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.annotations.ApplicationContext

/**
 * Кэш найденного обновления.
 *
 * Нужен, чтобы результат фоновой проверки не терялся: воркер кладёт сюда манифест один раз, а экран
 * «О VaVsta» читает его мгновенно и не делает второй сетевой запрос. Заодно храним факт, что про
 * эту версию уже показывали уведомление, чтобы не спамить им при каждой проверке.
 */
@Inject
class UpdateInfoStore(
    @ApplicationContext private val context: Context,
) {
    private val preferences get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Последнее найденное обновление, если оно ещё актуально. */
    fun get(): UpdateInfo? {
        val preferences = preferences
        val versionCode = preferences.getLong(KEY_VERSION_CODE, -1L)
        if (versionCode <= 0L) return null
        val apkUrl = preferences.getString(KEY_APK_URL, null) ?: return null
        return UpdateInfo(
            versionCode = versionCode,
            versionName = preferences.getString(KEY_VERSION_NAME, null).orEmpty(),
            apkUrl = apkUrl,
            sha256 = preferences.getString(KEY_SHA256, null),
            sizeBytes = preferences.getLong(KEY_SIZE_BYTES, 0L),
            notes = preferences.getString(KEY_NOTES, null),
        )
    }

    fun put(info: UpdateInfo) {
        preferences.edit()
            .putLong(KEY_VERSION_CODE, info.versionCode)
            .putString(KEY_VERSION_NAME, info.versionName)
            .putString(KEY_APK_URL, info.apkUrl)
            .putString(KEY_SHA256, info.sha256)
            .putLong(KEY_SIZE_BYTES, info.sizeBytes)
            .putString(KEY_NOTES, info.notes)
            .apply()
    }

    /** Сбрасывает кэш, когда обновлений больше нет или версия уже установлена. */
    fun clear() {
        preferences.edit().clear().apply()
    }

    fun isNotified(versionCode: Long): Boolean = preferences.getLong(KEY_NOTIFIED_VERSION_CODE, -1L) == versionCode

    fun markNotified(versionCode: Long) {
        preferences.edit().putLong(KEY_NOTIFIED_VERSION_CODE, versionCode).apply()
    }

    private companion object {
        const val PREFS_NAME = "vavsta_update_check"
        const val KEY_VERSION_CODE = "version_code"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_APK_URL = "apk_url"
        const val KEY_SHA256 = "sha256"
        const val KEY_SIZE_BYTES = "size_bytes"
        const val KEY_NOTES = "notes"
        const val KEY_NOTIFIED_VERSION_CODE = "notified_version_code"
    }
}
