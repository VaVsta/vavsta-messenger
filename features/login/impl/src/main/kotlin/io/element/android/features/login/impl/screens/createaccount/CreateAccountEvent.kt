/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.features.login.impl.screens.createaccount

sealed interface CreateAccountEvent {
    data class SetUsername(val value: String) : CreateAccountEvent
    data class SetDisplayName(val value: String) : CreateAccountEvent
    data class SetPassword(val value: String) : CreateAccountEvent
    data class SetPasswordConfirm(val value: String) : CreateAccountEvent
    data object Submit : CreateAccountEvent
    data object ClearError : CreateAccountEvent
}
