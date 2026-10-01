/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.pusher

/**
 * Формат уведомлений, который просит homeserver.
 *
 * [EVENT_ID_ONLY] — Synapse присылает только event_id, приложение само догружает
 * событие через sync. Экономит трафик, поэтому используется для FCM.
 *
 * [NORMAL] — Synapse присылает полный notification (включая room_id и type).
 * Нужен UnifiedPush: наш relay не умеет догружать событие, а парсер
 * UnifiedPushParser требует room_id, иначе приходит «Invalid data» и
 * показывается немое fallback-уведомление.
 */
enum class PusherFormat {
    EVENT_ID_ONLY,
    NORMAL,
}

data class SetHttpPusherData(
    val pushKey: String,
    val appId: String,
    val url: String,
    val appDisplayName: String,
    val deviceDisplayName: String,
    val profileTag: String?,
    val lang: String,
    val defaultPayload: String,
    val append: Boolean,
    val format: PusherFormat = PusherFormat.EVENT_ID_ONLY,
)
