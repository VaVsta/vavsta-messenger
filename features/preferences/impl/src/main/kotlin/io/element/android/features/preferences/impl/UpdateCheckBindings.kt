/*
 * Copyright (c) 2026 vavsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import io.element.android.features.preferences.impl.about.UpdateCheckScheduler

/** Мост в модуль app: оттуда инициализатор приложения ставит фоновую проверку обновлений. */
@ContributesTo(AppScope::class)
interface UpdateCheckBindings {
    fun updateCheckScheduler(): UpdateCheckScheduler
}
