/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.preferences.impl.developer.appsettings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.preferences.impl.developer.tracing.LogLevelItem
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.Config

class AppDeveloperSettingsPageTest : RobolectricTest() {
    @Test
    fun `clicking on back invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<AppDeveloperSettingsEvent>(expectEvents = false)
        ensureCalledOnce {
            setAppDeveloperSettingsView(
                state = anAppDeveloperSettingsState(
                    eventSink = eventsRecorder
                ),
                onBackClick = it
            )
            pressBack()
        }
    }

    @Config(qualifiers = "h1024dp")
    @Test
    fun `clicking on log level emits the expected event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<AppDeveloperSettingsEvent>()
        setAppDeveloperSettingsView(
            state = anAppDeveloperSettingsState(
                eventSink = eventsRecorder
            ),
        )
        onNodeWithText("Tracing log level").performClick()
        onNodeWithText("Debug").performClick()
        eventsRecorder.assertSingle(AppDeveloperSettingsEvent.SetTracingLogLevel(LogLevelItem.DEBUG))
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.setAppDeveloperSettingsView(
    state: AppDeveloperSettingsState,
    onBackClick: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        AppDeveloperSettingsPage(
            state = state,
            onBackClick = onBackClick,
        )
    }
}
