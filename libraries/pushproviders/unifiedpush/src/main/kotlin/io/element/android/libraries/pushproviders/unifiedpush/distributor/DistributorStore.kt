/*
 * Copyright (c) 2026 VaVsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.pushproviders.unifiedpush.distributor

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.SecureRandom

/**
 * Хранилище встроенного UnifiedPush-дистрибьютора.
 *
 * Токен — это секрет, по которому relay на сервере находит сокет устройства.
 * Он должен переживать перезапуск процесса, иначе после ребута/убийства
 * приложения push перестанут приходить, а токен в Synapse останется старым.
 */
internal object DistributorStore {
    private const val PREFS = "vavsta_up_distributor"
    private const val KEY_TOKEN = "token"
    private const val KEY_ACTIVE = "active"
    private const val KEY_INSTANCE = "instance"

    private val tokenChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Возвращает существующий токен или создаёт новый.
     * 64 символа base64url — с запасом под требование релея (32..128).
     */
    fun getOrCreateToken(context: Context): String {
        val current = prefs(context).getString(KEY_TOKEN, null)
        if (!current.isNullOrEmpty()) return current

        val random = SecureRandom()
        val token = buildString(64) {
            repeat(64) { append(tokenChars[random.nextInt(tokenChars.length)]) }
        }
        prefs(context).edit { putString(KEY_TOKEN, token) }
        return token
    }

    /**
     * Endpoint для регистрации в Synapse. Релей берёт токен из последнего
     * сегмента пути, поэтому путь и должен оканчиваться токеном.
     */
    fun endpoint(context: Context, gatewayBase: String): String {
        // Путь строго от корня: https://chat.vavsta.ru/<token>. UnifiedPushGatewayResolver
        // выкидывает последний сегмент (токен) и получает корень — из него
        // собирается gateway https://chat.vavsta.ru/_matrix/push/v1/notify,
        // который и требует matrix-rust-sdk. Любой префикс (/push, /up) ломает регистрацию.
        val base = gatewayBase.trimEnd('/')
        return "$base/${getOrCreateToken(context)}"
    }

    /**
     * Выбран ли наш distributor как источник push.
     *
     * Флаг нужен, чтобы перезапускать сокет после ребута телефона и обновления
     * приложения: коннектор повторный REGISTER не шлёт, поэтому без флага
     * сервис больше не поднимется и push перестанут приходить.
     */
    fun isActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)

    fun setActive(context: Context, active: Boolean) {
        prefs(context).edit { putBoolean(KEY_ACTIVE, active) }
    }

    /**
     * Instance (token регистрации), который коннектор прислал при REGISTER.
     *
     * Обязателен: коннектор ищет регистрацию именно по этому значению из extras
     * ACTION_MESSAGE. Без него событие не привязывается к сессии, и onMessage
     * не вызывается вовсе — push молча теряется.
     */
    fun getInstance(context: Context): String? = prefs(context).getString(KEY_INSTANCE, null)

    fun setInstance(context: Context, instance: String) {
        prefs(context).edit { putString(KEY_INSTANCE, instance) }
    }
}
