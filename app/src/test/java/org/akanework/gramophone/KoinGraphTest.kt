/*
 *     Copyright (C) 2026 The Gramophone authors
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import org.akanework.gramophone.di.appModule
import org.akanework.gramophone.di.viewModelModule
import org.junit.Test
import org.koin.android.test.verify.androidVerify
import org.koin.dsl.module

class KoinGraphTest {

    @Test
    fun appModuleResolves() {
        appModule.androidVerify(
            extraTypes = listOf(
                // FlowReader's filter-flow parameters come from SettingsRepository inside the
                // module lambda, not from Koin definitions.
                SharedFlow::class,
                // SettingsRepository takes its scope as CoroutineScope (ApplicationScope in the
                // module, a direct scope in tests).
                CoroutineScope::class,
            )
        )
    }

    @Test
    fun viewModelModuleResolves() {
        // Application (and SavedStateHandle) are whitelisted by androidVerify; FlowReader for
        // HomeViewModel comes from appModule, so verify the two modules together.
        module { includes(appModule, viewModelModule) }.androidVerify(
            extraTypes = listOf(SharedFlow::class, CoroutineScope::class)
        )
    }
}
