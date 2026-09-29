/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.features.login.impl.screens.createaccount

/**
 * Ошибки экрана регистрации.
 *
 * Серверные коды отлавливаем в [fromRegistrationException], остальное — локальная валидация формы.
 */
sealed interface CreateAccountError {
    /** Пустое или некорректное имя пользователя. */
    data object InvalidUsername : CreateAccountError

    /** Пароли не совпадают. */
    data object PasswordsDoNotMatch : CreateAccountError

    /** Имя уже занято (`M_USER_IN_USE`). */
    data object UsernameTaken : CreateAccountError

    /** Регистрация на сервере выключена (`M_FORBIDDEN`). */
    data object RegistrationDisabled : CreateAccountError

    /** Пароль не проходит политику сервера (`M_WEAK_PASSWORD`). */
    data object WeakPassword : CreateAccountError

    /** Сервер требует доп. шаг (капча/почта/terms) — такой UIA клиент не реализует. */
    data object InteractiveAuthRequired : CreateAccountError

    /** Вход после регистрации не удался. */
    data object LoginFailed : CreateAccountError

    /** Прочие ошибки: сеть, неожиданный ответ сервера. */
    data class Unexpected(val message: String?) : CreateAccountError
}
