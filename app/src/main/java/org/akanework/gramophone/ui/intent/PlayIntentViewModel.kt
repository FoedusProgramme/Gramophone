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

package org.akanework.gramophone.ui.intent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.library.LibraryReadiness
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.NavViewModel

/** What running a [PlayIntentAction] needs from the screen. */
interface PlayIntentHost {
    /** Suspends until a media controller is connected, then returns it. */
    suspend fun awaitController(): Player
    fun navigateTo(key: AppNavKey)
}

fun interface PlayIntentExecutor {
    suspend fun execute(action: PlayIntentAction, host: PlayIntentHost)
}

/**
 * The play intents of one MainActivity instance. [enqueue]d actions wait until the library is
 * ready and a host is [bind]ed, then run one at a time in [viewModelScope]: each is taken off the
 * queue before it runs, so a recreated activity neither cancels nor repeats it, and clearing the
 * view model (the activity really finishing) drops whatever is still queued.
 *
 * The queue itself is not saved. Only how far the launch intent got is, so that after process
 * death [enqueueLaunchIntent] can run the rest of it.
 */
class PlayIntentViewModel(
    readiness: LibraryReadiness,
    private val executor: PlayIntentExecutor,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    // Only touched on the main thread (enqueue callers and viewModelScope).
    private val pending = ArrayDeque<PlayIntentAction>()
    private val enqueued = Channel<Unit>(Channel.CONFLATED)
    private val host = MutableStateFlow<PlayIntentHost?>(null)
    private var launchIntentTaken = false
    /** How many actions at the head of [pending] come from the launch intent. */
    private var launchActionsQueued = 0

    init {
        viewModelScope.launch {
            readiness.ready.first { it }
            while (true) {
                val action = pending.removeFirstOrNull()
                if (action == null) {
                    enqueued.receive()
                    continue
                }
                val fromLaunchIntent = launchActionsQueued > 0
                if (fromLaunchIntent) launchActionsQueued--
                executor.execute(action, host.filterNotNull().first())
                // Autoplay too: restoring the activity, even in a new process, must not start
                // playback again.
                if (fromLaunchIntent) savedState[KEY_LAUNCH_ACTIONS_DONE] = launchActionsDone() + 1
            }
        }
    }

    private fun launchActionsDone(): Int = savedState[KEY_LAUNCH_ACTIONS_DONE] ?: 0

    /**
     * Enqueues the [actions] of the activity's launch intent, before anything else is enqueued.
     * Only the first call on this view model counts, so an activity recreated around it (rotation)
     * runs nothing again. A view model recreated after process death skips the actions that had
     * finished before, [PlayIntentAction.Autoplay] included, and runs only the rest.
     */
    fun enqueueLaunchIntent(actions: List<PlayIntentAction>) {
        if (launchIntentTaken) return
        launchIntentTaken = true
        val remaining = actions.drop(launchActionsDone())
        launchActionsQueued = remaining.size
        enqueue(remaining)
    }

    fun enqueue(actions: List<PlayIntentAction>) {
        if (actions.isEmpty()) return
        pending.addAll(actions)
        enqueued.trySend(Unit)
    }

    /** Lets actions reach the controller and navigation; both live as long as this. */
    fun bind(controllers: MediaControllerViewModel, navigation: NavViewModel) =
        bind(ViewModelHost(controllers, navigation))

    internal fun bind(host: PlayIntentHost) {
        this.host.value = host
    }

    private class ViewModelHost(
        private val controllers: MediaControllerViewModel,
        private val navigation: NavViewModel,
    ) : PlayIntentHost {
        override suspend fun awaitController(): Player = controllers.awaitController()
        override fun navigateTo(key: AppNavKey) = navigation.navigateTo(key)
    }

    private companion object {
        const val KEY_LAUNCH_ACTIONS_DONE = "launch_actions_done"
    }
}
