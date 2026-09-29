/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.features.login.impl.screens.createaccount

data class CreateAccountState(
    /**
     * Домен, на котором создаётся аккаунт (для заголовка экрана).
     */
    val homeserver: String,
    val username: String,
    val displayName: String,
    val password: String,
    val passwordConfirm: String,
    val isLoading: Boolean,
    val error: CreateAccountError?,
    val eventSink: (CreateAccountEvent) -> Unit,
) {
    val submitEnabled: Boolean
        get() = !isLoading && username.isNotBlank() && password.isNotBlank() && passwordConfirm.isNotBlank()
}
