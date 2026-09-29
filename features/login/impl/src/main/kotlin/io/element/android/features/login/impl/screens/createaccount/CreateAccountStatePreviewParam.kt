/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.features.login.impl.screens.createaccount

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class CreateAccountStatePreviewParam : PreviewParameterProvider<CreateAccountState> {
    override val values: Sequence<CreateAccountState>
        get() = sequenceOf(
            aCreateAccountState(),
            aCreateAccountState(username = "vavsta", displayName = "VaVsta", password = "hunter2hunter2", passwordConfirm = "hunter2hunter2"),
            aCreateAccountState(isLoading = true),
            aCreateAccountState(error = CreateAccountError.UsernameTaken),
            aCreateAccountState(error = CreateAccountError.PasswordsDoNotMatch),
            aCreateAccountState(error = CreateAccountError.Unexpected("M_UNKNOWN")),
        )
}

fun aCreateAccountState(
    username: String = "",
    displayName: String = "",
    password: String = "",
    passwordConfirm: String = "",
    isLoading: Boolean = false,
    error: CreateAccountError? = null,
): CreateAccountState = CreateAccountState(
    homeserver = "chat.vavsta.ru",
    username = username,
    displayName = displayName,
    password = password,
    passwordConfirm = passwordConfirm,
    isLoading = isLoading,
    error = error,
    eventSink = {},
)
