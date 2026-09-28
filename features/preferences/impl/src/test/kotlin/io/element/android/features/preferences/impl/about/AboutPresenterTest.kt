/*
 * Copyright (c) 2026 vavsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import android.content.Context
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.tests.testutils.WarmUpRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AboutPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private fun aContext() = mockk<Context>(relaxed = true)

    @Test
    fun `present - initial state`() = runTest {
        val presenter = AboutPresenter(
            context = aContext(),
            buildMeta = aBuildMeta(versionName = "1.0"),
            updateInfoStore = emptyUpdateInfoStore(),
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.versionName).isEqualTo("1.0")
            assertThat(initialState.appName).isEqualTo("VaVsta Messenger")
            // Дальше презентер уходит в Checking и стучится в сеть — в юнит-тесте это не нужно.
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - shows the update cached by the background check without a network call`() = runTest {
        val store = mockk<UpdateInfoStore>()
        val cached = UpdateInfo(
            versionCode = 202609012L,
            versionName = "1.0.1",
            apkUrl = "https://chat.vavsta.ru/vavsta-messenger/vavsta-messenger-arm64-202609012.apk",
            sha256 = null,
            sizeBytes = 1L,
            notes = "Фоновая проверка обновлений",
        )
        every { store.get() } returns cached
        // Ни put, ни clear вызываться не должны: сеть не трогаем, кэш фоновой проверки не затираем.
        every { store.put(any()) } throws AssertionError("The cached update should be reused as is")
        every { store.clear() } throws AssertionError("The cached update should be reused as is")

        val presenter = AboutPresenter(
            context = aContext(),
            buildMeta = aBuildMeta(versionName = "1.0"),
            updateInfoStore = store,
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem()
            val state = awaitItem()
            assertThat(state.updateStatus).isEqualTo(UpdateUiStatus.UpdateAvailable)
            assertThat(state.latestVersionName).isEqualTo("1.0.1")
            assertThat(state.updateNotes).isEqualTo("Фоновая проверка обновлений")
        }
    }

    private fun emptyUpdateInfoStore() = mockk<UpdateInfoStore> {
        every { get() } returns null
        every { put(any()) } returns Unit
        every { clear() } returns Unit
    }
}
