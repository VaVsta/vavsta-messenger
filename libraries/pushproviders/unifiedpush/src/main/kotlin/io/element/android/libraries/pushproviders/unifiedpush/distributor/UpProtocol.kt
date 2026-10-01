/*
 * Copyright (c) 2026 VaVsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.pushproviders.unifiedpush.distributor

/**
 * Константы протокола UnifiedPush для роли дистрибьютора.
 *
 * В AAR connector'а (3.3.3) эти константы объявлены как internal, поэтому
 * из нашего модуля не импортируются. Строки сверены с байткодом
 * org.unifiedpush.android.connector.ConstantsKt — это публичная часть
 * спецификации UnifiedPush, а не деталь реализации, так что дублируем
 * их у себя.
 */
internal object UpProtocol {
    /** connector -> distributor: «дай мне endpoint для этого приложения». */
    const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"

    /** connector -> distributor: «это приложение больше не нужно». */
    const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"

    /** distributor -> connector: «вот endpoint». */
    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"

    /** distributor -> connector: «пришло сообщение». */
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"

    /** distributor -> connector: «сообщение обработано». */
    const val ACTION_MESSAGE_ACK = "org.unifiedpush.android.distributor.MESSAGE_ACK"

    /** Идентификатор регистрации приложения, выданный connector'ом. */
    const val EXTRA_TOKEN = "token"

    /** Имя пакета приложения, регистрирующегося на distributor'е. */
    const val EXTRA_APPLICATION = "application"

    /** PendingIntent для ответа — чтобы не отвечать в эфир. */
    const val EXTRA_PI = "pi"

    /** Наш endpoint (URL, который уходит в Synapse как pushkey). */
    const val EXTRA_ENDPOINT = "endpoint"

    /** Тело сообщения (у нас это JSON с полем notification). */
    const val EXTRA_MESSAGE = "message"

    /** Идентификатор сообщения для ACK. */
    const val EXTRA_MESSAGE_ID = "id"

    /**
     * Тело сообщения в виде байт.
     *
     * UnifiedPushParser принимает ByteArray, поэтому коннектор берёт данные
     * прежде всего отсюда: если поля нет, message.content приходит пустым и
     * парсер возвращает null. Поэтому шлём оба поля.
     */
    const val EXTRA_BYTES_MESSAGE = "bytesMessage"
}
