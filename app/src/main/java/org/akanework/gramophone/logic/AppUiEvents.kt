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

import org.akanework.gramophone.logic.utils.RedeliveringQueue

/** Something work on [ApplicationScope] has to tell the user once it is done. */
sealed interface AppUiEvent {
    /** A library refresh the user asked for finished with [songCount] songs. */
    data class LibraryRefreshed(val songCount: Int) : AppUiEvent
}

/**
 * Hands [AppUiEvent]s from work on [ApplicationScope] to the root composition of whichever
 * MainActivity is resumed, so that work never holds on to the screen that started it. Events
 * posted while none is resumed wait for the next one; one taken by a root composition that is
 * cancelled before it shows it (its activity paused or recreated) is queued again.
 */
class AppUiEvents {
    private val events = RedeliveringQueue<AppUiEvent>(TAG)

    /** Callable from any thread. */
    fun post(event: AppUiEvent) = events.post(event)

    /** Waits for the next event. Only a resumed activity's root composition should call this. */
    suspend fun next(): AppUiEvent = events.next()

    private companion object {
        const val TAG = "AppUiEvents"
    }
}
