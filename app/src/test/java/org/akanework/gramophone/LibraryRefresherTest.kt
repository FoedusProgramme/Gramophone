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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.akanework.gramophone.logic.library.LibraryRefreshSteps
import org.akanework.gramophone.logic.library.LibraryRefresher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors

class LibraryRefresherTest {

    private val mainExecutor = Executors.newSingleThreadExecutor { Thread(it, "fake-main") }
    private val main = mainExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val steps = object : LibraryRefreshSteps {
        override suspend fun smartScan() {
            calls += "smartScan"
        }

        override suspend fun refresh() {
            calls += "refresh"
        }
    }

    @After
    fun tearDown() {
        mainExecutor.shutdownNow()
    }

    private fun refreshAndWait(smartScanFirst: Boolean): String = runBlocking {
        val done = CompletableDeferred<String>()
        LibraryRefresher(scope, steps, main).refresh(smartScanFirst) {
            calls += "onDone"
            done.complete(Thread.currentThread().name)
        }
        withTimeout(5_000) { done.await() }
    }

    @Test
    fun smartScanRunsBeforeRefreshAndOnDoneLastOnMain() {
        val thread = refreshAndWait(smartScanFirst = true)
        assertEquals(listOf("smartScan", "refresh", "onDone"), calls.toList())
        // Coroutine debug mode appends " @coroutine#n" to the thread name.
        assertTrue(thread, thread.startsWith("fake-main"))
    }

    @Test
    fun noSmartScanWhenNotAsked() {
        refreshAndWait(smartScanFirst = false)
        assertEquals(listOf("refresh", "onDone"), calls.toList())
    }
}
