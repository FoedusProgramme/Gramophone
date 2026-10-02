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
import org.akanework.gramophone.ui.intent.DefaultPlayIntentExecutor
import org.akanework.gramophone.ui.intent.PlayIntentExecutor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.android.test.verify.androidVerify
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class KoinGraphTest {

    /** Constructor parameter types the modules fill in themselves rather than through Koin. */
    private val extraTypes = listOf(
        // FlowReader's filter-flow parameters come from LibraryFilterSettings inside the module
        // lambda, not from Koin definitions.
        SharedFlow::class,
        // Constructors take a CoroutineScope so tests can pass their own; the module passes
        // get<ApplicationScope>() explicitly, as ApplicationScope is not bound as a scope.
        CoroutineScope::class,
    )

    @Test
    fun appModuleResolves() {
        appModule.androidVerify(extraTypes = extraTypes)
    }

    @Test
    fun viewModelModuleResolves() {
        // Application (and SavedStateHandle) are whitelisted by androidVerify; FlowReader for
        // HomeViewModel comes from appModule, so verify the two modules together.
        module { includes(appModule, viewModelModule) }.androidVerify(extraTypes = extraTypes)
    }

    @Test
    fun playIntentExecutorResolves() {
        // PlayIntentViewModel asks for the interface, so resolve it the same way.
        val koin = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(appModule, viewModelModule)
        }.koin
        try {
            assertTrue(koin.get<PlayIntentExecutor>() is DefaultPlayIntentExecutor)
        } finally {
            koin.close()
        }
    }
}
