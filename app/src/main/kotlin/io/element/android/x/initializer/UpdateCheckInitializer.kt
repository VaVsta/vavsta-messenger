/*
 * Copyright (c) 2026 vavsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.initializer

import android.content.Context
import androidx.startup.Initializer
import io.element.android.features.preferences.impl.UpdateCheckBindings
import io.element.android.libraries.architecture.bindings

/**
 * Ставит фоновую проверку обновлений: ежедневный график и одну проверку на старте приложения.
 */
class UpdateCheckInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        context.bindings<UpdateCheckBindings>().updateCheckScheduler().apply {
            scheduleDaily()
            checkOnStart()
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
