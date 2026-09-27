/*
 * Copyright (c) 2026 vavsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.element.android.features.preferences.impl.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object UpdateServerConfig {
    val UPDATE_URL: String = BuildConfig.UPDATE_BASE_URL

    /** Обновления разрешены только по HTTPS: иначе APK можно подменить в пути. */
    val isConfigured: Boolean get() = UPDATE_URL.startsWith("https://")
}

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val sizeBytes: Long,
    val notes: String?,
)

object UpdateChecker {
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val BUFFER_SIZE = 64 * 1024

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Читает манифест обновления. Возвращает null, если сервер недоступен, ответил неуспехом,
     * не отдал валидный JSON или в нём лежит не-HTTPS ссылка на APK.
     */
    suspend fun fetchUpdateInfo(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!UpdateServerConfig.isConfigured) {
            Timber.i("Update check skipped: no HTTPS update URL configured")
            return@withContext null
        }
        runCatching {
            val request = Request.Builder()
                .url(UpdateServerConfig.UPDATE_URL)
                .header("User-Agent", "VaVsta-Messenger")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Update check failed: HTTP ${response.code}")
                    return@use null
                }
                val body = response.body.string()
                val json = JSONObject(body)
                val info = UpdateInfo(
                    versionCode = json.getLong("versionCode"),
                    versionName = json.optString("versionName"),
                    apkUrl = json.getString("apkUrl"),
                    sha256 = json.optString("sha256").takeIf { it.isNotBlank() },
                    sizeBytes = json.optLong("sizeBytes", 0L),
                    notes = json.optString("notes").takeIf { it.isNotBlank() },
                )
                if (!info.apkUrl.startsWith("https://")) {
                    Timber.w("Update manifest rejected: apkUrl is not HTTPS")
                    return@use null
                }
                info
            }
        }.onFailure { Timber.w(it, "Update check failed") }
            .getOrNull()
    }

    /**
     * Скачивает APK в кэш, попутно считая sha256 и прогресс.
     * Отдаёт файл только если совпали контрольная сумма, packageName и versionCode,
     * иначе APK удаляется и возвращается null.
     */
    suspend fun downloadUpdate(
        context: Context,
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "updates/vavsta-messenger-${info.versionCode}.apk")
        val part = File(target.parentFile, target.name + ".part")
        runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val request = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", "VaVsta-Messenger")
                .build()
            val downloaded = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("APK download failed: HTTP ${response.code}")
                    return@use false
                }
                val source = response.body
                val total = source.contentLength()
                part.parentFile?.mkdirs()
                source.byteStream().use { input ->
                    part.outputStream().buffered().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytes = 0L
                        var lastPercent = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            bytes += read
                            if (total > 0) {
                                val percent = (bytes * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
                true
            }
            if (!downloaded) {
                part.delete()
                return@runCatching null
            }

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && !sha256.equals(info.sha256, ignoreCase = true)) {
                Timber.w("APK checksum mismatch, discarding download")
                part.delete()
                return@runCatching null
            }

            val archive = context.packageManager.getPackageArchiveInfo(part.absolutePath, 0)
            if (archive?.packageName != context.packageName) {
                Timber.w("APK package mismatch, discarding download")
                part.delete()
                return@runCatching null
            }
            val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archive.longVersionCode
            } else {
                archive.versionCode.toLong()
            }
            if (archiveVersionCode != info.versionCode) {
                Timber.w("APK versionCode mismatch: archive=$archiveVersionCode manifest=${info.versionCode}")
                part.delete()
                return@runCatching null
            }

            target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            target
        }.onFailure {
            Timber.w(it, "APK download failed")
            part.delete()
        }
            .getOrNull()
    }

    /** Открывает системный установщик на скачанный APK. */
    fun installUpdate(context: Context, file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Разрешено ли системе ставить пакеты от этого приложения. */
    fun canRequestInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
    }

    fun installedVersionCode(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
    }
}
