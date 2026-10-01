/*
 * Copyright (c) 2026 VaVsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.libraries.pushproviders.unifiedpush.distributor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.pushproviders.unifiedpush.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.min

private const val NOTIFICATION_ID = 1002
private const val CHANNEL_ID = "vavsta_push_relay_channel"

/** Интервалы переподключения: 2с, 4с, 8с … но не дольше 5 минут. */
private const val RECONNECT_MIN_MS = 2_000L
private const val RECONNECT_MAX_MS = 300_000L

/**
 * Держит WebSocket к relay и раздаёт входящие сообщения в connector.
 *
 * Сервис foreground, потому что соединение должно жить при выключенном экране:
 * иначе релей не сможет доставить push, пока телефон спит.
 */
class DistributorSocketService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var isOnForeground = false

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // долгоживущее соединение, рвём только сами
            .pingInterval(0, TimeUnit.MILLISECONDS) // пинги делает сервер, свой ping не нужен
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        Timber.i("DistributorSocketService создан")
        ensureNotificationChannel()
        goForeground()

        val token = DistributorStore.getOrCreateToken(applicationContext)
        connect(token)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isOnForeground) {
            // На Android 12+ старт FG-сервиса из фона может запретиться. Не падаем.
            Timber.w("Сервис не в foreground, останавливаю")
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Timber.i("DistributorSocketService уничтожается")
        reconnectJob?.cancel()
        webSocket?.close(NORMAL_CLOSURE, "сервис уничтожен")
        webSocket = null
        scope.cancel()
        if (isOnForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------ WebSocket

    private fun connect(token: String) {
        webSocket?.cancel()
        val url = DistributorConfig.webSocketUrl(token)
        Timber.i("Подключаюсь к relay")
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.i("Сокет открыт, релей готов слать push")
                reconnectAttempt = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                deliverToConnector(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.w(t, "Сокет упал, планирую переподключение")
                scheduleReconnect(token)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.i("Сокет закрыт: $code $reason")
                scheduleReconnect(token)
            }
        })
    }

    private fun scheduleReconnect(token: String) {
        if (!scope.isActive) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoff = min(
                RECONNECT_MIN_MS shl reconnectAttempt.coerceAtMost(7),
                RECONNECT_MAX_MS,
            )
            reconnectAttempt++
            Timber.i("Переподключение через ${backoff}мс (попытка $reconnectAttempt)")
            delay(backoff)
            if (scope.isActive) connect(token)
        }
    }

    /**
     * Relay присылает ровно тот JSON, который ждёт UnifiedPushParser:
     * {"notification":{"event_id":...,"room_id":...,"counts":{...}}}.
     *
     * Важно: отдаём ВЕСЬ объект, а не только внутреннее поле notification.
     * Раньше я вырезал обёртку, и PushDataUnifiedPush.notification оставался null —
     * element-x получал «Invalid data received from UnifiedPush» и молчал.
     */
    private fun deliverToConnector(raw: String) {
        val valid = try {
            json.parseToJsonElement(raw).jsonObject["notification"] != null
        } catch (e: Exception) {
            Timber.w(e, "Не разобрал сообщение relay")
            false
        }
        if (!valid) {
            Timber.w("В сообщении нет поля notification")
            return
        }

        val instance = DistributorStore.getInstance(applicationContext)
        if (instance.isNullOrEmpty()) {
            Timber.w("Нет instance от коннектора, сообщение не доставится")
            return
        }

        Timber.i("Отдаю push в connector")
        sendBroadcast(
            Intent(UpProtocol.ACTION_MESSAGE)
                .setPackage(packageName)
                .putExtra(UpProtocol.EXTRA_MESSAGE, raw)
                .putExtra(UpProtocol.EXTRA_BYTES_MESSAGE, raw.toByteArray())
                .putExtra(UpProtocol.EXTRA_TOKEN, instance)
        )
    }

    // ------------------------------------------------------- foreground

    private fun ensureNotificationChannel() {
        NotificationManagerCompat.from(this).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_MIN)
                    .setName(getString(R.string.vavsta_push_relay_channel_name))
                    .setVibrationEnabled(false)
                    .setSound(null, null)
                    .build()
            )
        )
    }

    private fun goForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(CommonDrawables.ic_notification)
            .setContentTitle(getString(R.string.vavsta_push_relay_title))
            .setContentText(getString(R.string.vavsta_push_relay_body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVibrate(longArrayOf(0))
            .setSound(null)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatchingExceptions {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            Unit
        }.onSuccess {
            isOnForeground = true
            Timber.i("Сервис в foreground")
        }.onFailure {
            isOnForeground = false
            Timber.e(it, "Не удалось уйти в foreground")
        }
    }

    companion object {
        private const val NORMAL_CLOSURE = 1000

        fun start(context: Context) {
            val intent = Intent(context, DistributorSocketService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // На Android 12+ фон стартовать FG-сервис не даст. Не критично:
                // при следующей регистрации push попробуем снова.
                Timber.w(e, "Не удалось запустить DistributorSocketService")
            }
        }
    }
}
