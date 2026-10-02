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
import org.akanework.gramophone.logic.utils.RedeliveringQueue

/**
 * One MediaStore consent prompt: [sender] shows the system dialog, and [payload] is handed back
 * with the user's answer to [LibraryWriteRepository.onConsentResult].
 */
class ConsentRequest(val sender: IntentSender, val payload: PendingWrite)

/**
 * Queue of MediaStore consent prompts. Anything may [request], from any thread, before any UI
 * exists; the root MediaConsentHost takes the requests one at a time with [next] and shows them.
 * Requests made while no host is waiting are kept, and one handed to a host that is gone before
 * it resumes (its activity recreated) is queued again, at the back.
 */
class MediaConsentRequester {
    private val requests = RedeliveringQueue<ConsentRequest>(TAG)

    /** Queues a prompt for [sender]. Never suspends. */
    fun request(sender: IntentSender, payload: PendingWrite) {
        requests.post(ConsentRequest(sender, payload))
    }

    /** Waits for the next request. Only the host should call this. */
    suspend fun next(): ConsentRequest = requests.next()

    private companion object {
        const val TAG = "MediaConsentRequester"
    }
}
