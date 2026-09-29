/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.features.login.impl.screens.createaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.login.impl.accountprovider.SaveAccountProviderToHistory
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import kotlinx.coroutines.launch

/**
 * VaVsta: регистрация нового аккаунта на homeserver'е, который не умеет OAuth (MAS).
 *
 * Схема: спрашиваем у homeserver'а реальный URL ([MatrixAuthenticationService.setHomeserver]), регистрируем
 * пользователя через [MatrixRegistrationClient], затем входим штатным [MatrixAuthenticationService.login] —
 * сессия появится в хранилище, и приложение само уйдёт в залогиненное состояние.
 */
@AssistedInject
class CreateAccountPresenter(
    @Assisted private val params: Params,
    private val authenticationService: MatrixAuthenticationService,
    private val registrationClient: MatrixRegistrationClient,
    private val matrixClientProvider: MatrixClientProvider,
    private val saveAccountProviderToHistory: SaveAccountProviderToHistory,
    private val buildMeta: BuildMeta,
) : Presenter<CreateAccountState> {
    data class Params(
        val homeserverUrl: String,
    )

    @AssistedFactory
    interface Factory {
        fun create(params: Params): CreateAccountPresenter
    }

    @Composable
    override fun present(): CreateAccountState {
        val coroutineScope = rememberCoroutineScope()

        var username by rememberSaveable { mutableStateOf("") }
        var displayName by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }
        var passwordConfirm by rememberSaveable { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<CreateAccountError?>(null) }

        fun handleEvent(event: CreateAccountEvent) {
            when (event) {
                is CreateAccountEvent.SetUsername -> username = event.value
                is CreateAccountEvent.SetDisplayName -> displayName = event.value
                is CreateAccountEvent.SetPassword -> password = event.value
                is CreateAccountEvent.SetPasswordConfirm -> passwordConfirm = event.value
                CreateAccountEvent.ClearError -> error = null
                CreateAccountEvent.Submit -> {
                    val localPart = username.trim()
                    when {
                        !USERNAME_PATTERN.matches(localPart) -> error = CreateAccountError.InvalidUsername
                        password != passwordConfirm -> error = CreateAccountError.PasswordsDoNotMatch
                        else -> {
                            error = null
                            isLoading = true
                            coroutineScope.launch {
                                // Homeserver мог быть указан как домен без схемы —
                                // берём URL, который сам сервер отдал.
                                val homeserver = authenticationService.setHomeserver(params.homeserverUrl)
                                    .getOrElse {
                                        isLoading = false
                                        error = CreateAccountError.Unexpected(it.message)
                                        return@launch
                                    }

                                registrationClient.register(
                                    homeserverUrl = homeserver.url,
                                    localPart = localPart,
                                    password = password,
                                    deviceDisplayName = "${buildMeta.productionApplicationName} (${buildMeta.applicationId})",
                                ).onFailure { throwable ->
                                    isLoading = false
                                    error = throwable.toCreateAccountError()
                                    return@launch
                                }

                                authenticationService.login(localPart, password)
                                    .onSuccess { sessionId ->
                                        saveAccountProviderToHistory()
                                        isLoading = false
                                        // Имя профиля ставим уже после входа: клиент нужен на сессии.
                                        // Ошибка тут не критична — имя можно поменять в настройках.
                                        if (displayName.isNotBlank()) {
                                            coroutineScope.launch {
                                                matrixClientProvider.getOrRestore(sessionId)
                                                    .onSuccess { it.setDisplayName(displayName.trim()) }
                                            }
                                        }
                                    }
                                    .onFailure {
                                        isLoading = false
                                        error = CreateAccountError.LoginFailed
                                    }
                            }
                        }
                    }
                }
            }
        }

        return CreateAccountState(
            homeserver = params.homeserverUrl,
            username = username,
            displayName = displayName,
            password = password,
            passwordConfirm = passwordConfirm,
            isLoading = isLoading,
            error = error,
            eventSink = ::handleEvent,
        )
    }

    private companion object {
        /** Разрешённые Matrix символы в localpart (см. Synapse `REGISTRATION_LOCALPART_REGEX`). */
        val USERNAME_PATTERN = Regex("^[a-z0-9._=/+-]+$")
    }
}

private fun Throwable.toCreateAccountError(): CreateAccountError = when (this) {
    is RegistrationException -> when (errcode) {
        "M_USER_IN_USE" -> CreateAccountError.UsernameTaken
        "M_INVALID_USERNAME" -> CreateAccountError.InvalidUsername
        "M_WEAK_PASSWORD" -> CreateAccountError.WeakPassword
        // Сервер требует докапчу/почту/terms — наш клиент такой UIA не умеет.
        "M_INTERACTIVE_AUTH_REQUIRED" -> CreateAccountError.InteractiveAuthRequired
        "M_FORBIDDEN" -> CreateAccountError.RegistrationDisabled
        else -> CreateAccountError.Unexpected(errorMessage)
    }
    else -> CreateAccountError.Unexpected(message)
}
