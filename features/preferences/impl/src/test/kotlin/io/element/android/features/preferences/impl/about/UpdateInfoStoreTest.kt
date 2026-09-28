/*
 * Copyright (c) 2026 vavsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.about

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

class UpdateInfoStoreTest {
    private val context = mockk<Context>()
    private val values = mutableMapOf<String, Any?>()

    private val editor = mockk<SharedPreferences.Editor>(relaxed = true).apply {
        every { putLong(any(), any()) } answers {
            values[firstArg()] = secondArg<Long>()
            this@apply
        }
        every { putString(any(), any()) } answers {
            values[firstArg()] = secondArg<String?>()
            this@apply
        }
        every { clear() } answers {
            values.clear()
            this@apply
        }
    }

    private val preferences = mockk<SharedPreferences>(relaxed = true).apply {
        every { edit() } returns editor
        every { getLong(any(), any()) } answers { values[firstArg()] as? Long ?: secondArg<Long>() }
        every { getString(any(), any()) } answers { values[firstArg()] as? String ?: secondArg<String?>() }
    }

    private val store = UpdateInfoStore(context)

    @Before
    fun setUp() {
        every { context.getSharedPreferences(any(), any()) } returns preferences
        store.clear()
    }

    private val anUpdate = UpdateInfo(
        versionCode = 202609012L,
        versionName = "1.0.1",
        apkUrl = "https://chat.vavsta.ru/vavsta-messenger/vavsta-messenger-arm64-202609012.apk",
        sha256 = "dc0d9b943413cb1aec88dab3feef5c5c7b8cf9c94ed1d7227a1314f6e61db770",
        sizeBytes = 117598464L,
        notes = "Фоновая проверка обновлений",
    )

    @Test
    fun `returns null when nothing is cached`() {
        assertThat(store.get()).isNull()
    }

    @Test
    fun `round-trips the update info`() {
        store.put(anUpdate)
        assertThat(store.get()).isEqualTo(anUpdate)
    }

    @Test
    fun `round-trips an update without optional fields`() {
        val minimal = anUpdate.copy(sha256 = null, notes = null)
        store.put(minimal)
        assertThat(store.get()).isEqualTo(minimal)
    }

    @Test
    fun `clear wipes the cache`() {
        store.put(anUpdate)
        store.clear()
        assertThat(store.get()).isNull()
    }

    @Test
    fun `remembers which version was already notified`() {
        assertThat(store.isNotified(anUpdate.versionCode)).isFalse()

        store.markNotified(anUpdate.versionCode)

        assertThat(store.isNotified(anUpdate.versionCode)).isTrue()
        // Соседний versionCode — про него ещё не показывали.
        assertThat(store.isNotified(anUpdate.versionCode + 1)).isFalse()
    }
}
