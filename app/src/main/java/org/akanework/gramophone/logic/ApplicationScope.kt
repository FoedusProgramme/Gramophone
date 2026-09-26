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

package org.akanework.gramophone.logic

import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Process-wide coroutine scope for work that must outlive any UI component (library scans,
 * writes after a consent prompt, preference migrations). UI-bound work belongs in
 * lifecycleScope / viewModelScope / rememberCoroutineScope instead.
 *
 * A failing child does not cancel its siblings. Its exception is logged and then handed to the
 * thread's default uncaught-exception handler, so crashes still reach the bug report screen.
 */
class ApplicationScope : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Uncaught exception in application scope", e)
            val thread = Thread.currentThread()
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, e)
        }

    private companion object {
        const val TAG = "ApplicationScope"
    }
}
