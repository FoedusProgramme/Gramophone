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

package org.akanework.gramophone.logic.utils

import androidx.media3.common.util.Log
import kotlinx.coroutines.channels.Channel

/**
 * An unbounded queue with a single consumer that may go away at any time. Anything may [post],
 * from any thread, whether or not a consumer is waiting; elements posted meanwhile are kept. An
 * element handed to a consumer that is cancelled before it resumes (a recreated activity) is not
 * lost but posted again, to the back of the queue.
 *
 * @param tag log tag for an element that could not be queued.
 */
class RedeliveringQueue<T : Any>(private val tag: String) {
    private val channel = Channel<T>(Channel.UNLIMITED, onUndeliveredElement = ::post)

    /** Queues [element]. Never suspends. */
    fun post(element: T) {
        if (channel.trySend(element).isFailure) Log.e(tag, "dropped $element")
    }

    /** Waits for the next element. Only the one consumer should call this. */
    suspend fun next(): T = channel.receive()
}
