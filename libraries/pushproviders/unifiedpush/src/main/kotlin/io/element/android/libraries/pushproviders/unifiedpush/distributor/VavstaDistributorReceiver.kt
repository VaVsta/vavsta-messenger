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
 * Точка входа встроенного UnifiedPush-дистрибьютора.
 *
 * Протокол (connector -> distributor):
 *   1. connector шлёт UpProtocol.ACTION_REGISTER с extras: token, application, features, pi.
 *      Мы обязаны ответить UpProtocol.ACTION_NEW_ENDPOINT с extras: endpoint, token.
 *      endpoint — это наш endpoint, его же мы отдаём в Synapse как pushkey.
 *   2. connector шлёт UpProtocol.ACTION_UNREGISTER — отвечать нечем, достаточно убрать токен.
 *   3. Мы сами шлём UpProtocol.ACTION_MESSAGE с extras: token, message (и id для ACK).
 *   4. После обработки шлём UpProtocol.ACTION_MESSAGE_ACK с extras: token, id.
 *
 * Константы протокола продублированы в UpProtocol: в AAR connector'а они internal.
 */
class VavstaDistributorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Timber.i("VavstaDistributorReceiver: $action")
        when (action) {
            UpProtocol.ACTION_REGISTER -> handleRegister(context, intent)
            UpProtocol.ACTION_UNREGISTER -> handleUnregister(context, intent)
            UpProtocol.ACTION_MESSAGE_ACK -> handleAck(context, intent)
            else -> Timber.w("VavstaDistributorReceiver: неизвестное действие $action")
        }
    }

    private fun handleRegister(context: Context, intent: Intent) {
        val token = intent.getStringExtra(UpProtocol.EXTRA_TOKEN)
        val application = intent.getStringExtra(UpProtocol.EXTRA_APPLICATION)
        if (token.isNullOrEmpty() || application.isNullOrEmpty()) {
            Timber.w("Регистрация без token/application, игнорируем")
            return
        }

        // endpoint указывает на наш relay: https://chat.vavsta.ru/push/<наш токен>
        val endpoint = DistributorStore.endpoint(context, DistributorConfig.GATEWAY_BASE)
        Timber.i("Регистрация $application, отвечаем endpoint=$endpoint")

        // Отвечаем broadcast'ом прямо в пакет приложения: setPackage гарантирует,
        // что NEW_ENDPOINT получит именно наш connector, а не чужой дистрибьютор.
        //
        // PendingIntent из extras (pi) использовать НЕЛЬЗЯ: connector создаёт его
        // на фиктивный пакет org.unifiedpush.dummy_app, поэтому pi.send() уводит
        // ответ в никуда — onNewEndpoint не вызывается и pusher не регистрируется.
        val reply = Intent(UpProtocol.ACTION_NEW_ENDPOINT)
            .setPackage(application)
            .putExtra(UpProtocol.EXTRA_ENDPOINT, endpoint)
            .putExtra(UpProtocol.EXTRA_TOKEN, token)
        context.sendBroadcast(reply)

        // Instance нужен для ACTION_MESSAGE: по нему коннектор находит нашу
        // регистрацию. Без него сообщение не доходит до onMessage.
        DistributorStore.setInstance(context, token)

        // Запоминаем, что наш distributor выбран: этим флагом поднимаем сокет
        // после ребута телефона и обновления приложения.
        DistributorStore.setActive(context, true)

        // Поднимаем сокет, чтобы релей мог слать в этот endpoint.
        DistributorSocketService.start(context)
    }

    private fun handleUnregister(context: Context, intent: Intent) {
        // Пришёл UNREGISTER — значит element-x выбрал другой push-провайдер
        // (например Firebase) и наш distributor больше не нужен: гасим сокет,
        // иначе будем зря держать соединение и WakeLock.
        Timber.i("Unregister получен, останавливаю сокет")
        DistributorStore.setActive(context, false)
        context.stopService(Intent(context, DistributorSocketService::class.java))
    }

    private fun handleAck(context: Context, intent: Intent) {
        // Сейчас ACK чисто декларативный: релей не ждёт подтверждений, а
        // сообщения держит в очереди только пока сокета нет.
        Timber.d("Получен MESSAGE_ACK id=${intent.getStringExtra(UpProtocol.EXTRA_MESSAGE_ID)}")
    }
}
