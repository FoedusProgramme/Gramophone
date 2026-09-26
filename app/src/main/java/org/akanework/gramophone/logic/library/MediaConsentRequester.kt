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

package org.akanework.gramophone.logic.library

import android.content.IntentSender
import androidx.media3.common.util.Log
import kotlinx.coroutines.channels.Channel

class ConsentRequest(val sender: IntentSender, val payload: PendingWrite)

/**
 * Queue of MediaStore consent prompts. Anything may enqueue, from any thread, before any UI
 * exists; the root MediaConsentHost takes the requests one at a time and shows them. A channel
 * (not a SharedFlow) so requests made while no host collects are kept, not dropped.
 */
class MediaConsentRequester {
    private val requests = Channel<ConsentRequest>(Channel.BUFFERED)

    fun request(sender: IntentSender, payload: PendingWrite) {
        val result = requests.trySend(ConsentRequest(sender, payload))
        if (result.isFailure) Log.e(TAG, "dropped consent request for $payload")
    }

    suspend fun send(sender: IntentSender, payload: PendingWrite) {
        requests.send(ConsentRequest(sender, payload))
    }

    /** Waits for the next request. Only the host should call this. */
    suspend fun next(): ConsentRequest = requests.receive()

    private companion object {
        const val TAG = "MediaConsentRequester"
    }
}
