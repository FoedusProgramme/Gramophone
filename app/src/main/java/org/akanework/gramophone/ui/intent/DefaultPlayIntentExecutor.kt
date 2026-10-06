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
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.logic.toMediaStoreId
import org.akanework.gramophone.ui.actions.SHORTCUT_SHUFFLE_ALL
import org.akanework.gramophone.ui.nav.PlaylistKey
import org.akanework.gramophone.ui.nav.SearchKey
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.reader.FlowReader

/** Runs play intent actions against the loaded library, the controller and navigation. */
class DefaultPlayIntentExecutor internal constructor(
    private val context: Context,
    /** The library's songs by id; [FlowReader.idMapFlow] outside tests. */
    private val idMapFlow: Flow<Map<Long, MediaItem>>,
    /** The library's playlists; [FlowReader.playlistListFlow] outside tests. */
    private val playlistsFlow: Flow<List<Playlist>>,
    private val libraryWrites: LibraryWriteRepository,
) : PlayIntentExecutor {
    constructor(
        context: Context,
        reader: FlowReader,
        libraryWrites: LibraryWriteRepository,
    ) : this(context, reader.idMapFlow, reader.playlistListFlow, libraryWrites)

    override suspend fun execute(action: PlayIntentAction, host: PlayIntentHost) {
        // Only the kind: the payload can hold search queries and playlist entries.
        Log.i(TAG, "execute(${action::class.simpleName})")
        when (action) {
            is PlayIntentAction.PlayById -> {
                val mediaItem = findSong(action.id)
                if (mediaItem != null) {
                    host.play { setMediaItem(mediaItem, action.positionMs) }
                } else {
                    Toast.makeText(context, R.string.cannot_find_file, Toast.LENGTH_LONG).show()
                }
            }
            is PlayIntentAction.MarkFavorite ->
                libraryWrites.markFavorite(listOf(action.entry), action.favorite)
            is PlayIntentAction.OpenPlaylist ->
                host.navigateTo(PlaylistKey(action.id, playlistClassName(action.id)))
            is PlayIntentAction.OpenSearch -> host.navigateTo(SearchKey(action.query))
            is PlayIntentAction.PlayFromSearch -> host.play {
                setMediaItem(searchItem(action.query, action.extras)) // query may be empty
            }
            is PlayIntentAction.Shuffle -> {
                ShortcutManagerCompat.reportShortcutUsed(context, SHORTCUT_SHUFFLE_ALL)
                host.play {
                    shuffleModeEnabled = true
                    setMediaItem(searchItem(action.query)) // empty query = every song
                }
            }
            PlayIntentAction.Autoplay -> host.play {}
        }
    }

    /**
     * The library's song with [id]: a bare MediaStore id (audio preview) or "MediaStore:<id>"
     * (search suggestions). Null, and logged, if there is none.
     */
    private suspend fun findSong(id: String): MediaItem? = withContext(Dispatchers.Default) {
        val col = idMapFlow.firstOrNull()
        val item = (id.toMediaStoreId() ?: id.toLongOrNull())?.let { col?.get(it) }
        if (item == null) {
            Log.e(TAG, "can't find file with ID $id in library with ${col?.size} items")
        }
        item
    }

    /**
     * Class of the library's playlist with [id], which its page's key carries like when opened
     * from the library: the page's carousel finds the playlist's card by it, and only a plain
     * playlist can be edited. Null if there is none.
     */
    private suspend fun playlistClassName(id: Long): String? = withContext(Dispatchers.Default) {
        playlistsFlow.firstOrNull()?.find { it.id == id }?.javaClass?.name
    }

    /** Waits for the controller, lets [load] set it up, then prepares and plays. */
    private suspend fun PlayIntentHost.play(load: Player.() -> Unit) {
        val controller = awaitController()
        controller.load()
        controller.prepare()
        controller.play()
    }

    /** An item the playback service resolves by searching for [query]. */
    private fun searchItem(query: String, extras: Bundle? = null): MediaItem =
        MediaItem.Builder()
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setSearchQuery(query)
                    .setExtras(extras)
                    .build()
            )
            .build()

    private companion object {
        const val TAG = "PlayIntentExecutor"
    }
}
