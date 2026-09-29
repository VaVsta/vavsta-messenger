/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.features.login.impl.screens.createaccount

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.modifiers.bringIntoViewOnImeVisible
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.PasswordVisibilityToggle
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * VaVsta: форма регистрации (логин + пароль) для homeserver'ов без OAuth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountView(
    state: CreateAccountState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)

    fun submit() {
        state.eventSink(CreateAccountEvent.Submit)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                iconStyle = BigIcon.Style.Default(CompoundIcons.UserAddSolid()),
                title = stringResource(R.string.screen_account_provider_signup_title, state.homeserver),
                subTitle = stringResource(R.string.screen_account_provider_signup_subtitle),
            )
            Spacer(Modifier.height(40.dp))

            var username by remember(state.username) { mutableStateOf(state.username) }
            var displayName by remember(state.displayName) { mutableStateOf(state.displayName) }
            var password by remember(state.password) { mutableStateOf(state.password) }
            var passwordConfirm by remember(state.passwordConfirm) { mutableStateOf(state.passwordConfirm) }
            var passwordVisible by remember { mutableStateOf(false) }

            TextField(
                value = username,
                onValueChange = {
                    username = it.filterNot { c -> c == '\n' || c == '\r' }
                    state.eventSink(CreateAccountEvent.SetUsername(username))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnImeVisible(),
                label = stringResource(CommonStrings.common_username),
                enabled = !state.isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(20.dp))
            TextField(
                value = displayName,
                onValueChange = {
                    displayName = it.filterNot { c -> c == '\n' || c == '\r' }
                    state.eventSink(CreateAccountEvent.SetDisplayName(displayName))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnImeVisible(),
                label = stringResource(R.string.vavsta_create_account_display_name),
                supportingText = stringResource(R.string.vavsta_create_account_display_name_hint),
                enabled = !state.isLoading,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(20.dp))
            TextField(
                value = password,
                onValueChange = {
                    password = it.filterNot { c -> c == '\n' || c == '\r' }
                    state.eventSink(CreateAccountEvent.SetPassword(password))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnImeVisible(),
                label = stringResource(CommonStrings.common_password),
                enabled = !state.isLoading,
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    PasswordVisibilityToggle(
                        visible = passwordVisible,
                        onToggle = { passwordVisible = !passwordVisible },
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(20.dp))
            TextField(
                value = passwordConfirm,
                onValueChange = {
                    passwordConfirm = it.filterNot { c -> c == '\n' || c == '\r' }
                    state.eventSink(CreateAccountEvent.SetPasswordConfirm(passwordConfirm))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnImeVisible(),
                label = stringResource(R.string.vavsta_create_account_password_confirm),
                enabled = !state.isLoading,
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )

            state.error?.let { error ->
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = spacedBy(8.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier = Modifier.size(14.dp),
                        imageVector = CompoundIcons.ErrorSolid(),
                        contentDescription = null,
                        tint = ElementTheme.colors.iconCriticalPrimary,
                    )
                    Text(
                        text = stringResource(createAccountErrorText(error)),
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textCriticalPrimary,
                    )
                }
            }

            // Прижимаем кнопку к низу, но на коротких экранах даём ей уехать под поля.
            Spacer(Modifier.height(40.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                ButtonColumnMolecule {
                    Button(
                        text = stringResource(R.string.screen_create_account_title),
                        showProgress = state.isLoading,
                        onClick = ::submit,
                        enabled = state.submitEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(48.dp))
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun CreateAccountViewPreview(
    @PreviewParameter(CreateAccountStatePreviewParam::class) state: CreateAccountState,
) = ElementPreview {
    CreateAccountView(state = state, onBackClick = {})
}
