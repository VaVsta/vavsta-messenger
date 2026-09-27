/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.preferences.impl.about

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class AboutViewTest : RobolectricTest() {
    @Test
    fun `clicking on back invokes back callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setAboutView(
                anAboutState(),
                onBackClick = callback,
            )
            pressBack()
        }
    }

    @Test
    fun `clicking on the open source licenses invokes the expected callback`() = runAndroidComposeUiTest {
        ensureCalledOnce { callback ->
            setAboutView(
                anAboutState(),
                onOpenSourceLicensesClick = callback,
            )
            // Экран стал длиннее иконочки VaVsta, поэтому строка уходит за пределы вьюпорта:
            // без прокрутки performClick тапает мимо и колбэк не срабатывает.
            onNode(hasScrollAction()).performScrollToNode(hasText(activity!!.getString(CommonStrings.common_open_source_licenses)))
            clickOn(CommonStrings.common_open_source_licenses)
        }
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.setAboutView(
    state: AboutState,
    onOpenSourceLicensesClick: () -> Unit = EnsureNeverCalled(),
    onBackClick: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        AboutView(
            state = state,
            onOpenSourceLicensesClick = onOpenSourceLicensesClick,
            onBackClick = onBackClick,
        )
    }
}
