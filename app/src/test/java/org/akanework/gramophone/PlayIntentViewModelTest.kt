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

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.Player
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.akanework.gramophone.logic.library.LibraryReadiness
import org.akanework.gramophone.ui.intent.PlayIntentAction
import org.akanework.gramophone.ui.intent.PlayIntentAction.OpenPlaylist
import org.akanework.gramophone.ui.intent.PlayIntentExecutor
import org.akanework.gramophone.ui.intent.PlayIntentHost
import org.akanework.gramophone.ui.intent.PlayIntentViewModel
import org.akanework.gramophone.ui.nav.AppNavKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The queue of [PlayIntentViewModel]: each action runs once, only after the library is ready, keeps
 * running across a recreated activity, and dies with the view model. The executor is faked; which
 * controller calls each action makes is the old code moved as is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayIntentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeHost : PlayIntentHost {
        override suspend fun awaitController(): Player = error("not used")
        override fun navigateTo(key: AppNavKey) {}
    }

    /** Records actions; an action with a gate in [gates] suspends until it is completed. */
    private class FakeExecutor : PlayIntentExecutor {
        val started = mutableListOf<Pair<PlayIntentAction, PlayIntentHost>>()
        val finished = mutableListOf<PlayIntentAction>()
        val gates = mutableMapOf<PlayIntentAction, CompletableDeferred<Unit>>()

        override suspend fun execute(action: PlayIntentAction, host: PlayIntentHost) {
            started += action to host
            gates[action]?.await()
            finished += action
        }
    }

    private val readiness = LibraryReadiness()
    private val executor = FakeExecutor()
    private val store = ViewModelStore()

    /** What `by viewModel()` gives an activity: the one instance kept in its store. */
    private fun viewModel(store: ViewModelStore = this.store): PlayIntentViewModel =
        ViewModelProvider.create(store, viewModelFactory {
            initializer { PlayIntentViewModel(readiness, executor) }
        })[PlayIntentViewModel::class]

    private fun actions() = executor.started.map { it.first }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun actionRunsExactlyOnce() = runTest(dispatcher) {
        readiness.markReady()
        val vm = viewModel()
        vm.bind(FakeHost())
        vm.enqueue(listOf(OpenPlaylist(1)))
        advanceUntilIdle()
        assertEquals(listOf(OpenPlaylist(1)), actions())

        // A recreated activity gets the same view model, binds again and doesn't enqueue.
        val again = viewModel()
        assertEquals(vm, again)
        again.bind(FakeHost())
        advanceUntilIdle()
        assertEquals(listOf(OpenPlaylist(1)), actions())
    }

    @Test
    fun actionsQueuedBeforeReadinessRunAfterIt() = runTest(dispatcher) {
        val vm = viewModel()
        vm.bind(FakeHost())
        vm.enqueue(listOf(OpenPlaylist(1), OpenPlaylist(2)))
        vm.enqueue(listOf(OpenPlaylist(3)))
        advanceUntilIdle()
        assertEquals(emptyList<PlayIntentAction>(), actions())

        readiness.markReady()
        advanceUntilIdle()
        assertEquals(listOf(OpenPlaylist(1), OpenPlaylist(2), OpenPlaylist(3)), actions())
    }

    @Test
    fun actionsWaitForAHost() = runTest(dispatcher) {
        readiness.markReady()
        val vm = viewModel()
        vm.enqueue(listOf(OpenPlaylist(1)))
        advanceUntilIdle()
        assertEquals(emptyList<PlayIntentAction>(), actions())

        val host = FakeHost()
        vm.bind(host)
        advanceUntilIdle()
        assertEquals(listOf(OpenPlaylist(1) to host), executor.started)
    }

    @Test
    fun emptyEnqueueDoesNothing() = runTest(dispatcher) {
        readiness.markReady()
        val vm = viewModel()
        vm.bind(FakeHost())
        vm.enqueue(emptyList())
        advanceUntilIdle()
        assertEquals(emptyList<PlayIntentAction>(), actions())
    }

    @Test
    fun actionInProgressSurvivesRecreationAndIsNotRepeated() = runTest(dispatcher) {
        readiness.markReady()
        val gate = CompletableDeferred<Unit>()
        executor.gates[OpenPlaylist(1)] = gate
        val vm = viewModel()
        vm.bind(FakeHost())
        vm.enqueue(listOf(OpenPlaylist(1), OpenPlaylist(2)))
        advanceUntilIdle()
        assertEquals(listOf(OpenPlaylist(1)), actions())

        // Rotation: the activity is recreated around the surviving view model.
        val again = viewModel()
        again.bind(FakeHost())
        // A new intent arriving meanwhile (onNewIntent) queues behind.
        again.enqueue(listOf(OpenPlaylist(3)))
        advanceUntilIdle()
        assertEquals(listOf(OpenPlaylist(1)), actions())

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(OpenPlaylist(1), OpenPlaylist(2), OpenPlaylist(3)), actions())
        assertEquals(listOf(OpenPlaylist(1), OpenPlaylist(2), OpenPlaylist(3)), executor.finished)
    }

    @Test
    fun clearedViewModelDropsItsQueue() = runTest(dispatcher) {
        val vm = viewModel()
        vm.bind(FakeHost())
        vm.enqueue(listOf(OpenPlaylist(1)))
        advanceUntilIdle()

        // The activity really finishes (e.g. back pressed while the library still loads).
        store.clear()
        readiness.markReady()
        advanceUntilIdle()
        assertEquals(emptyList<PlayIntentAction>(), actions())

        // The next launch has a new view model that only runs its own intent.
        val nextStore = ViewModelStore()
        val next = viewModel(nextStore)
        next.bind(FakeHost())
        next.enqueue(listOf(OpenPlaylist(2)))
        advanceUntilIdle()
        assertEquals(listOf(OpenPlaylist(2)), actions())
        nextStore.clear()
    }

    @Test
    fun instancesKeepTheirOwnQueues() = runTest(dispatcher) {
        readiness.markReady()
        val otherStore = ViewModelStore()
        val first = viewModel()
        val second = viewModel(otherStore)
        val firstHost = FakeHost()
        val secondHost = FakeHost()
        first.bind(firstHost)
        second.bind(secondHost)
        first.enqueue(listOf(OpenPlaylist(1)))
        second.enqueue(listOf(OpenPlaylist(2)))
        advanceUntilIdle()
        assertEquals(setOf(OpenPlaylist(1) to firstHost, OpenPlaylist(2) to secondHost),
            executor.started.toSet())
        otherStore.clear()
    }
}
