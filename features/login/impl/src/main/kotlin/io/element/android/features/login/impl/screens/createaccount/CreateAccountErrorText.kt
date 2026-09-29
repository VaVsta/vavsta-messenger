/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.features.login.impl.screens.createaccount

import androidx.annotation.StringRes
import io.element.android.features.login.impl.R

/**
 * Текст ошибки регистрации для показа пользователю.
 */
@StringRes
fun createAccountErrorText(error: CreateAccountError): Int = when (error) {
    CreateAccountError.InvalidUsername -> R.string.vavsta_create_account_error_invalid_username
    CreateAccountError.PasswordsDoNotMatch -> R.string.vavsta_create_account_error_passwords_mismatch
    CreateAccountError.UsernameTaken -> R.string.vavsta_create_account_error_username_taken
    CreateAccountError.RegistrationDisabled -> R.string.vavsta_create_account_error_registration_disabled
    CreateAccountError.WeakPassword -> R.string.vavsta_create_account_error_weak_password
    CreateAccountError.InteractiveAuthRequired -> R.string.vavsta_create_account_error_interactive_auth
    CreateAccountError.LoginFailed -> R.string.vavsta_create_account_error_login_failed
    is CreateAccountError.Unexpected -> R.string.vavsta_create_account_error_unexpected
}
