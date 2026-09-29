/*
 * VaVsta Messenger (форк Element X).
 *
 * Регистрация напрямую через client API homeserver'а.
 */

package io.element.android.features.login.impl.screens.createaccount

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ошибка регистрации, пришедшая от homeserver'а.
 *
 * @param errcode код ошибки Matrix ([org.matrix.msc2258] `errcode`), например `M_USER_IN_USE`.
 * @param errorMessage текст ошибки от сервера.
 */
class RegistrationException(
    val errcode: String,
    val errorMessage: String,
) : Exception(errorMessage)

/**
 * VaVsta: регистрация нового пользователя.
 *
 * Апстрим Element X создаёт аккаунты только через OAuth (MAS/SSO) — если у homeserver'а нет OAuth,
 * показывается ошибка «регистрация невозможна». Наш homeserver (chat.vavsta.ru) — голый Synapse без MAS,
 * но `/register` в нём включён, поэтому шлём запрос сами: один POST с `m.login.dummy`, без капчи и почты.
 * Дальше вход идёт штатным кодом клиента ([io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService.login]).
 */
@SingleIn(AppScope::class)
@Inject
class MatrixRegistrationClient {
    /**
     * Регистрирует нового аккаунта.
     *
     * @param homeserverUrl базовый URL homeserver'а, например `https://chat.vavsta.ru`.
     * @param localPart имя пользователя без `@` и домена.
     * @param password пароль (политику паролей сервер может отвергнуть — тогда придёт [RegistrationException]).
     * @param deviceDisplayName имя устройства, которое будет создано вместе с аккаунтом.
     */
    suspend fun register(
        homeserverUrl: String,
        localPart: String,
        password: String,
        deviceDisplayName: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${homeserverUrl.trimEnd('/')}/_matrix/client/v3/register")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                val payload = buildJsonObject {
                    put("username", localPart)
                    put("password", password)
                    put("initial_device_display_name", deviceDisplayName)
                    // Регистрация без создания сессии: вход делаем отдельно, штатным кодом клиента.
                    put("inhibit_login", true)
                    // Наш Synapse не требует капчи, почты и terms — только dummy-аутентификацию.
                    put("auth", buildJsonObject { put("type", "m.login.dummy") })
                }
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                if (status !in 200..299) {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw parseError(errorBody, status)
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseError(errorBody: String, status: Int): RegistrationException {
        val json = runCatching { Json.parseToJsonElement(errorBody) as? JsonObject }.getOrNull()
        val errcode = json?.get("errcode")?.jsonPrimitive?.contentOrNull
            // 401 без errcode — это UIA: сервер хочет ещё один шаг аутентификации.
            ?: if (status == 401) "M_INTERACTIVE_AUTH_REQUIRED" else "M_UNKNOWN"
        val errorMessage = json?.get("error")?.jsonPrimitive?.contentOrNull
        return RegistrationException(
            errcode = errcode,
            errorMessage = errorMessage ?: "Registration failed with HTTP $status",
        )
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
    }
}
