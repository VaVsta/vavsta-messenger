/*
 * Copyright (c) 2026 VaVsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.pushproviders.unifiedpush.distributor

/**
 * Адреса собственной инфраструктуры уведомлений.
 *
 * Gateway обязан быть ровно https://chat.vavsta.ru/_matrix/push/v1/notify:
 * matrix-rust-sdk проверяет путь и отвергает всё, что не равно
 * "/_matrix/push/v1/notify" (ошибка "Config Error: 'url' must have a path of
 * '/_matrix/push/v1/notify'"). Поэтому префикс /push в gateway невозможен.
 *
 * Из этого следует и форма pushkey: UnifiedPushGatewayResolver отбрасывает
 * последний сегмент пути endpoint'а, поэтому pushkey строится от корня сайта —
 * https://chat.vavsta.ru/<token>. Тогда резолвер получает корень и собирает
 * корректный gateway URL. Префикс /push здесь ломал бы регистрацию.
 *
 * WebSocket — наш собственный путь, в валидации SDK не участвует.
 */
internal object DistributorConfig {
    /** Корень сайта: pushkey = https://chat.vavsta.ru/<token>. */
    const val GATEWAY_BASE = "https://chat.vavsta.ru"

    /** Куда шлём discovery. Релей отвечает {"unifiedpush":{"gateway":"matrix"}}. */
    val gatewayUrl: String get() = "$GATEWAY_BASE/_matrix/push/v1/notify"

    /** WebSocket с токеном устройства в query. */
    fun webSocketUrl(token: String): String = "wss://chat.vavsta.ru/push/up/ws?token=$token"
}
