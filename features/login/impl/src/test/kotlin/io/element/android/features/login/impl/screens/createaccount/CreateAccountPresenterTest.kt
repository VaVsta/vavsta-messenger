/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.features.login.impl.screens.createaccount

import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.impl.accountprovider.SaveAccountProviderToHistory
import io.element.android.features.login.impl.accountprovider.anAccountProviderDataSource
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.auth.aMatrixHomeServerDetails
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CreateAccountPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `submit - username with forbidden characters`() = runTest {
        val registration = FakeRegistrationClient()
        val presenter = createPresenter(registrationClient = registration.client)
        presenter.test {
            fillInForm(username = "НЕЛЬЗЯ", password = "12345678", passwordConfirm = "87654321")
            assertThat(awaitState { it.error != null }.error).isEqualTo(CreateAccountError.InvalidUsername)
            // Регистрацию даже не пытались отправить
            assertThat(registration.callCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit - passwords do not match`() = runTest {
        val registration = FakeRegistrationClient()
        val presenter = createPresenter(registrationClient = registration.client)
        presenter.test {
            fillInForm(username = "alice", password = "12345678", passwordConfirm = "87654321")
            assertThat(awaitState { it.error != null }.error).isEqualTo(CreateAccountError.PasswordsDoNotMatch)
            assertThat(registration.callCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit - registration and login are successful`() = runTest {
        val registration = FakeRegistrationClient(registerResult = { Result.success(Unit) })
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = { Result.success(aMatrixHomeServerDetails(url = "https://chat.vavsta.ru")) },
        )
        val presenter = createPresenter(
            authenticationService = authenticationService,
            registrationClient = registration.client,
        )
        presenter.test {
            fillInForm(username = "alice", password = "12345678", passwordConfirm = "12345678")
            assertThat(awaitState { it.isLoading }.isLoading).isTrue()
            val done = awaitState { !it.isLoading }
            assertThat(done.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        // Регистрация ушла на homeserver, который вернул сам сервер, а не на набранный в поле домен
        assertThat(registration.homeserverUrl).isEqualTo("https://chat.vavsta.ru")
        assertThat(registration.localPart).isEqualTo("alice")
        assertThat(registration.password).isEqualTo("12345678")
        assertThat(registration.callCount).isEqualTo(1)
    }

    @Test
    fun `submit - username is already taken`() = runTest {
        val registration = FakeRegistrationClient(
            registerResult = { Result.failure(RegistrationException("M_USER_IN_USE", "Desired username is already taken.")) },
        )
        val presenter = createPresenter(registrationClient = registration.client)
        presenter.test {
            fillInForm(username = "alice", password = "12345678", passwordConfirm = "12345678")
            assertThat(awaitState { it.error != null }.error).isEqualTo(CreateAccountError.UsernameTaken)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit - server requires interactive auth`() = runTest {
        val registration = FakeRegistrationClient(
            registerResult = { Result.failure(RegistrationException("M_INTERACTIVE_AUTH_REQUIRED", "captcha required")) },
        )
        val presenter = createPresenter(registrationClient = registration.client)
        presenter.test {
            fillInForm(username = "alice", password = "12345678", passwordConfirm = "12345678")
            assertThat(awaitState { it.error != null }.error).isEqualTo(CreateAccountError.InteractiveAuthRequired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit - account created but login failed`() = runTest {
        val registration = FakeRegistrationClient(registerResult = { Result.success(Unit) })
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = { Result.success(aMatrixHomeServerDetails()) },
        ).apply { givenLoginError(IllegalStateException("no")) }
        val presenter = createPresenter(
            authenticationService = authenticationService,
            registrationClient = registration.client,
        )
        presenter.test {
            fillInForm(username = "alice", password = "12345678", passwordConfirm = "12345678")
            assertThat(awaitState { it.error != null }.error).isEqualTo(CreateAccountError.LoginFailed)
            cancelAndIgnoreRemainingEvents()
        }
        // Аккаунт на сервере создался, поэтому повторная отправка формы его не создаст заново
        assertThat(registration.callCount).isEqualTo(1)
    }

    /** Заполняет форму и жмёт «Создать аккаунт». */
    private suspend fun ReceiveTurbine<CreateAccountState>.fillInForm(
        username: String,
        password: String,
        passwordConfirm: String,
    ) {
        awaitItem().eventSink(CreateAccountEvent.SetUsername(username))
        awaitState { it.username == username }.eventSink(CreateAccountEvent.SetPassword(password))
        awaitState { it.password == password }.eventSink(CreateAccountEvent.SetPasswordConfirm(passwordConfirm))
        awaitState { it.passwordConfirm == passwordConfirm }.eventSink(CreateAccountEvent.Submit)
    }

    private suspend fun ReceiveTurbine<CreateAccountState>.awaitState(
        predicate: (CreateAccountState) -> Boolean,
    ): CreateAccountState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    /** Обёртка над [MatrixRegistrationClient], которая запоминает аргументы вызова. */
    private class FakeRegistrationClient(
        private val registerResult: () -> Result<Unit> = { Result.success(Unit) },
    ) {
        var homeserverUrl: String? = null
        var localPart: String? = null
        var password: String? = null
        var callCount: Int = 0

        val client: MatrixRegistrationClient = mockk<MatrixRegistrationClient>().also { client ->
            coEvery { client.register(any(), any(), any(), any()) } answers {
                callCount++
                homeserverUrl = firstArg()
                localPart = secondArg()
                password = thirdArg()
                registerResult()
            }
        }
    }

    private fun createPresenter(
        authenticationService: FakeMatrixAuthenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = { Result.success(aMatrixHomeServerDetails()) },
        ),
        registrationClient: MatrixRegistrationClient = FakeRegistrationClient().client,
    ) = CreateAccountPresenter(
        params = CreateAccountPresenter.Params(homeserverUrl = "https://chat.vavsta.ru"),
        authenticationService = authenticationService,
        registrationClient = registrationClient,
        matrixClientProvider = FakeMatrixClientProvider(),
        saveAccountProviderToHistory = SaveAccountProviderToHistory(
            accountProviderDataSource = anAccountProviderDataSource(),
            appPreferencesStore = InMemoryAppPreferencesStore(),
        ),
        buildMeta = aBuildMeta(applicationName = "VaVsta Messenger"),
    )
}
