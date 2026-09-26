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

import android.content.Context
import android.widget.Toast
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.toMediaStoreId
import org.akanework.gramophone.logic.library.LibraryReadiness
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.actions.SHORTCUT_SHUFFLE_ALL
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.NavViewModel
import org.akanework.gramophone.ui.nav.PlaylistKey
import org.akanework.gramophone.ui.nav.SearchKey
import uk.akane.libphonograph.reader.FlowReader

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
 */
class PlayIntentViewModel(
    readiness: LibraryReadiness,
    private val executor: PlayIntentExecutor,
) : ViewModel() {
    // Only touched on the main thread (enqueue callers and viewModelScope).
    private val pending = ArrayDeque<PlayIntentAction>()
    private val enqueued = Channel<Unit>(Channel.CONFLATED)
    private val host = MutableStateFlow<PlayIntentHost?>(null)

    init {
        viewModelScope.launch {
            readiness.ready.first { it }
            while (true) {
                val action = pending.removeFirstOrNull()
                if (action == null) {
                    enqueued.receive()
                    continue
                }
                executor.execute(action, host.filterNotNull().first())
            }
        }
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
}

/** Runs play intent actions against the loaded library, the controller and navigation. */
class DefaultPlayIntentExecutor internal constructor(
    private val context: Context,
    /** The library's songs by id; [FlowReader.idMapFlow] outside tests. */
    private val idMapFlow: Flow<Map<Long, MediaItem>>,
    private val libraryWrites: LibraryWriteRepository,
) : PlayIntentExecutor {
    constructor(
        context: Context,
        reader: FlowReader,
        libraryWrites: LibraryWriteRepository,
    ) : this(context, reader.idMapFlow, libraryWrites)

    override suspend fun execute(action: PlayIntentAction, host: PlayIntentHost) {
        Log.i(TAG, "execute($action)")
        when (action) {
            is PlayIntentAction.PlayById -> {
                val mediaItem = withContext(Dispatchers.Default) {
                    val col = idMapFlow.firstOrNull()
                    // Search suggestions send "MediaStore:<id>", audio preview a bare id.
                    val id = action.id.toMediaStoreId() ?: action.id.toLongOrNull()
                    val item = id?.let { col?.let { it2 -> it2[it] } }
                    if (item == null) {
                        Log.e(TAG, "can't find file with ID ${action.id} in library with" +
                                " ${col?.size} items")
                    }
                    item
                }
                if (mediaItem != null) {
                    val controller = host.awaitController()
                    controller.setMediaItem(mediaItem, action.positionMs)
                    controller.prepare()
                    controller.play()
                } else {
                    Toast.makeText(context, R.string.cannot_find_file, Toast.LENGTH_LONG).show()
                }
            }
            is PlayIntentAction.MarkFavorite ->
                libraryWrites.markFavorite(listOf(action.entry), action.favorite)
            is PlayIntentAction.OpenPlaylist -> host.navigateTo(PlaylistKey(action.id, null))
            is PlayIntentAction.OpenSearch -> host.navigateTo(SearchKey(action.query))
            is PlayIntentAction.PlayFromSearch -> {
                val controller = host.awaitController()
                controller.setMediaItem(
                    MediaItem.Builder()
                        .setRequestMetadata(
                            MediaItem.RequestMetadata.Builder()
                                .setSearchQuery(action.query) // may be empty
                                .setExtras(action.extras)
                                .build()
                        )
                        .build()
                )
                controller.prepare()
                controller.play()
            }
            is PlayIntentAction.Shuffle -> {
                ShortcutManagerCompat.reportShortcutUsed(context, SHORTCUT_SHUFFLE_ALL)
                val controller = host.awaitController()
                controller.shuffleModeEnabled = true
                controller.setMediaItem(
                    MediaItem.Builder()
                        .setRequestMetadata(
                            MediaItem.RequestMetadata.Builder()
                                .setSearchQuery(action.query) // empty = every song
                                .build()
                        )
                        .build()
                )
                controller.prepare()
                controller.play()
            }
            PlayIntentAction.Autoplay -> {
                val controller = host.awaitController()
                controller.prepare()
                controller.play()
            }
        }
    }

    private companion object {
        const val TAG = "PlayIntentExecutor"
    }
}
