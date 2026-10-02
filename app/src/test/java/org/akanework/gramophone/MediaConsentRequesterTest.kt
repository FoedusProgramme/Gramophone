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

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.akanework.gramophone.logic.library.ConsentRequest
import org.akanework.gramophone.logic.library.MediaConsentRequester
import org.akanework.gramophone.logic.library.PendingWrite
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The consent queue: what the host gets from [MediaConsentRequester.next], and in which order. */
@RunWith(RobolectricTestRunner::class)
class MediaConsentRequesterTest {

    private val requester = MediaConsentRequester()

    private suspend fun next(): ConsentRequest = withTimeout(1000) { requester.next() }

    @Test
    fun requestsMadeWithoutAHostWaitInOrder() = runTest {
        val first = testIntentSender(1)
        val second = testIntentSender(2)
        requester.request(first, PendingWrite.Delete)
        requester.request(second, PendingWrite.Delete)

        assertSame(first, next().sender)
        assertSame(second, next().sender)
        assertNull(withTimeoutOrNull(50) { requester.next() })
    }

    @Test
    fun requestHandedToCancelledHostIsQueuedAgain() = runTest {
        // The host waits for a request, gets one, and is cancelled (the activity is recreated)
        // before it resumes to take it.
        val host = launch { requester.next() }
        runCurrent()
        val consent = testIntentSender(5)
        requester.request(consent, PendingWrite.Delete)
        host.cancel()
        runCurrent()

        assertSame(consent, next().sender)
    }

    @Test
    fun requestHandedToCancelledHostGoesToTheBack() = runTest {
        val host = launch { requester.next() }
        runCurrent()
        val handed = testIntentSender(1)
        val later = testIntentSender(2)
        requester.request(handed, PendingWrite.Delete)
        requester.request(later, PendingWrite.Delete)
        host.cancel()
        runCurrent()

        assertSame(later, next().sender)
        assertSame(handed, next().sender)
    }
}
