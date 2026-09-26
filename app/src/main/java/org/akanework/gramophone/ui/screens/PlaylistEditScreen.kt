/*
 *     Copyright (C) 2025 Akane Foundation
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
package org.akanework.gramophone.ui.screens

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.DismissibleRow
import org.akanework.gramophone.ui.components.compose.rememberReorderableListState
import org.akanework.gramophone.ui.components.compose.reorderHandle
import org.akanework.gramophone.ui.components.compose.reorderableRow
import org.akanework.gramophone.ui.components.home.EditableSongRow
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.GlassTitleBar
import org.akanework.gramophone.ui.components.home.LibraryIconButton
import org.akanework.gramophone.ui.components.home.libraryCellShape
import org.akanework.gramophone.ui.components.home.libraryItemCard
import org.nift4.mediastorecompat.MediaStoreCompat
import org.koin.compose.koinInject
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.PlaylistSerializer
import uk.akane.libphonograph.reader.FlowReader
import uk.akane.libphonograph.reader.Reader
import uk.akane.libphonograph.toUriCompat
import java.io.File
import java.io.IOException
import java.nio.file.Files

/*
 * Editing a playlist: reorder by the handle, remove by the button or a swipe, then apply or
 * discard. Every edit is written to a file in the external cache first, so a session that is
 * killed can be restored the next time the playlist is opened.
 */

private const val TAG = "PlaylistEditScreen"
private const val FOLDER_NAME = "Restored playlists"

private enum class EditDialog { Restore, Empty, Discard }

private class EditRow(val key: Long, val item: MediaItem)

@Stable
private class PlaylistEditState(
    context: Context,
    private val reader: FlowReader,
    id: Long,
    private val scope: CoroutineScope,
    private val requestWrite: (IntentSenderRequest) -> Unit,
    private val onBack: () -> Unit,
) {
    private val context: Context = context.applicationContext
    private val uri: Uri = ContentUris.withAppendedId(
        @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id
    )
    var title by mutableStateOf("")
        private set
    var loaded by mutableStateOf(false)
        private set
    var dialog by mutableStateOf<EditDialog?>(null)
    var rows by mutableStateOf<List<EditRow>>(emptyList())
        private set

    private var item: Playlist? = null
    private var tmpName = ""
    private var doneEditing = false
    private val entries = MutableStateFlow(0 to PlaylistSerializer.Playlist.create())
    private var renderedEntries = mapOf<PlaylistSerializer.Entry, MediaItem>()
    private var keys = listOf<Long>()
    private var nextKey = 0L

    private fun refreshRows() {
        val rendered = renderedEntries
        val list = entries.value.second.entries
        if (keys.size != list.size) keys = List(list.size) { nextKey++ }
        rows = list.mapIndexedNotNull { i, entry -> rendered[entry]?.let { EditRow(keys[i], it) } }
    }

    private suspend fun toast(text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    private suspend fun toast(res: Int) = toast(context.getString(res))

    private suspend fun leave() = withContext(Dispatchers.Main) { onBack() }

    fun start() {
        scope.launch(Dispatchers.Default) {
            val id = ContentUris.parseId(uri)
            val found = reader.playlistListFlow.map { it.find { p -> p.id == id } }.first()
            if (found == null || found.path == null) {
                toast(R.string.unknown_playlist)
                leave()
                return@launch
            }
            item = found
            withContext(Dispatchers.Main) {
                title = if (found is Favorite) context.getString(R.string.playlist_favourite)
                else found.title ?: context.getString(R.string.unknown_playlist)
            }
            // The user can visit the folder in DocsUI to get old cached versions if needed.
            tmpName = "${found.path.name}_${found.path.lastModified()}.xspf"
            val hasRestorablePlaylist = try {
                hasChanges()
            } catch (e: Exception) {
                Log.e(TAG, "failed to check for changes", e)
                null
            }
            when (hasRestorablePlaylist) {
                true -> withContext(Dispatchers.Main) { dialog = EditDialog.Restore }
                false -> load(false)
                else -> {
                    toast(R.string.mount_storage)
                    leave()
                }
            }
        }
    }

    fun restore() {
        dialog = null
        scope.launch(Dispatchers.Default) { load(true) }
    }

    fun discardRestorable() {
        dialog = null
        scope.launch(Dispatchers.Default) {
            try {
                discardChanges()
            } catch (e: Exception) {
                Log.e(TAG, "failed to discard", e)
                toast(R.string.mount_storage)
                leave()
                return@launch
            }
            load(false)
        }
    }

    fun cancelAndLeave() {
        dialog = null
        onBack()
    }

    private suspend fun CoroutineScope.load(restore: Boolean) {
        val pathMap = reader.pathMapFlow.first()
        val readback = try {
            if (restore) {
                (try {
                    readChanges()
                } catch (e: Exception) {
                    Log.e(TAG, "failed to restore changes", e)
                    toast(context.getString(R.string.failed_to_restore, e.message))
                    Reader.readPlaylist(context, uri)
                })
            } else Reader.readPlaylist(context, uri)
        } catch (e: Exception) {
            Log.e(TAG, "failed to read changes", e)
            toast(e.toString())
            leave()
            return
        }.let { playlist ->
            playlist.copy(entries = playlist.entries.map { it.updateFromMediaItem(pathMap) })
        }
        if (!restore && readback.entries.isEmpty()) {
            withContext(Dispatchers.Main) { dialog = EditDialog.Empty }
            return
        }
        val rendered = hashMapOf<PlaylistSerializer.Entry, MediaItem>()
        readback.entries.forEach { rendered[it] = it.resolveMediaItem(pathMap) ?: missingItem(it) }
        val renderedFinal = rendered.toMap()
        withContext(Dispatchers.Main) {
            entries.value = 1 to readback
            renderedEntries = renderedFinal
            refreshRows()
            requestWriteIfNeeded()
            loaded = true
        }
        launch {
            reader.pathMapFlow.drop(1).collectLatest { pathMap ->
                // Keep using the old readback set because it will be a superset of current
                // entries, because there's no way to add new songs.
                readback.entries.forEach {
                    rendered[it] = it.resolveMediaItem(pathMap) ?: (
                        if (rendered[it]!!.mediaId.startsWith("Missing:")) rendered[it]!!
                        else missingItem(it)
                    )
                }
                val renderedNow = rendered.toMap()
                while (true) {
                    val entriesTmp = entries.value
                    // Keep the same generation number as this isn't a user triggered edit.
                    val newEntries = entriesTmp.first to entriesTmp.second.let { playlist ->
                        playlist.copy(entries = playlist.entries.map { it.updateFromMediaItem(pathMap) })
                    }
                    val done = withContext(Dispatchers.Main) {
                        if (entries.value.first == entriesTmp.first) {
                            entries.value = newEntries
                            renderedEntries = renderedNow
                            refreshRows()
                            true
                        } else false // The user changed something and raced with us, try again.
                    }
                    if (done) break
                }
            }
        }
        var generation = 1
        entries.drop(1).collectLatest { new ->
            if (!doneEditing && new.first > generation) {
                generation = new.first
                try {
                    writeChanges(new.second)
                } catch (e: Exception) {
                    Log.e(TAG, "failed to write changes", e)
                    toast(e.toString())
                    leave()
                    return@collectLatest
                }
            }
        }
    }

    private fun missingItem(entry: PlaylistSerializer.Entry): MediaItem =
        MediaItem.Builder().setMediaId("Missing:${entry.locations.firstOrNull()}")
            .setUri(entry.locations.firstOrNull())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${entry.locations.firstOrNull()?.lastPathSegment}")
                    .setArtist("${entry.locations.firstOrNull()}")
                    .build()
            )
            .build()

    fun move(from: Int, to: Int) {
        if (from == to) return
        entries.update { i ->
            i.first + 1 to i.second.copy(entries = i.second.entries.toMutableList().also {
                it.add(to, it.removeAt(from))
            })
        }
        keys = keys.toMutableList().also { it.add(to, it.removeAt(from)) }
        refreshRows()
    }

    fun remove(pos: Int) {
        entries.update { i ->
            i.first + 1 to i.second.copy(entries = i.second.entries.toMutableList().also {
                it.removeAt(pos)
            })
        }
        keys = keys.toMutableList().also { it.removeAt(pos) }
        refreshRows()
    }

    /** Back: leave when nothing changed, else ask what to do with the changes. */
    fun maybeGoBack() {
        // Invalid state, maybe the user pressed back while we are still saving.
        if (doneEditing) return
        if (entries.value.first <= 1) {
            onBack()
            return
        }
        dialog = EditDialog.Discard
    }

    fun discardAndLeave() {
        dialog = null
        scope.launch(Dispatchers.Default) {
            try {
                discardChanges()
            } catch (e: Exception) {
                Log.e(TAG, "failed to discard", e)
                toast(R.string.mount_storage)
            }
        }
        onBack()
    }

    fun apply() {
        dialog = null
        if (doneEditing || !loaded) return
        val songs = entries.value.second
        doneEditing = true
        scope.launch(Dispatchers.Default) {
            val ok = try {
                ItemManipulator.setPlaylistContent(context, uri, songs, false)
                true
            } catch (e: Exception) {
                Log.e(TAG, "failed to edit $uri", e)
                toast(context.getString(R.string.edit_playlist_failed, e.message))
                doneEditing = false // Allow the user to save again to try again.
                false
            }
            if (ok) {
                try {
                    discardChanges()
                } catch (e: Exception) {
                    Log.e(TAG, "failed to discard", e)
                    toast(R.string.mount_storage)
                }
                leave()
            }
        }
    }

    fun onResume() {
        if (entries.value.first > 0) requestWriteIfNeeded()
    }

    private fun requestWriteIfNeeded() {
        scope.launch(Dispatchers.Default) {
            val token = MediaStoreCompat.needRequestBytesWrite(context, uri)
            if (token != null) {
                val pi = MediaStoreCompat.createWriteRequest(context, listOf(token))
                withContext(Dispatchers.Main) {
                    requestWrite(IntentSenderRequest.Builder(pi).build())
                }
            }
        }
    }

    fun onWriteRequestResult(resultCode: Int) {
        // If there are any saved edits, don't commit (we can't, the user said no) nor discard them.
        if (resultCode != Activity.RESULT_OK) onBack()
    }

    private fun playlistFile(): File? {
        val filesDir = context.externalCacheDir ?: return null
        val playlistsDir = filesDir.resolve(FOLDER_NAME)
        if (!playlistsDir.exists() && !playlistsDir.mkdirs()) return null
        return playlistsDir.resolve(tmpName)
    }

    private fun hasChanges(): Boolean? = playlistFile()?.exists()

    private fun readChanges(): PlaylistSerializer.Playlist =
        PlaylistSerializer.read(context.externalCacheDir!!.resolve(FOLDER_NAME).resolve(tmpName))

    private fun writeChanges(entries: PlaylistSerializer.Playlist) {
        val playlist = context.externalCacheDir!!.resolve(FOLDER_NAME).resolve(tmpName)
        PlaylistSerializer.write(context, playlist, playlist.toUriCompat(), entries)
    }

    private suspend fun discardChanges() {
        val filesDir = context.externalCacheDir
        if (filesDir == null) {
            toast(R.string.mount_storage)
            return
        }
        val playlist = filesDir.resolve(FOLDER_NAME).resolve(tmpName)
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Files.delete(playlist.toPath())
                } catch (e: Exception) {
                    throw IOException("Failed to delete $playlist", e)
                }
            } else {
                if (!playlist.delete())
                    throw IOException("Failed to delete $playlist (no details available)")
            }
        }
    }
}

@Composable
fun PlaylistEditScreen(playlistId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reader = koinInject<FlowReader>()
    val scope = rememberCoroutineScope()
    var stateHolder by remember { mutableStateOf<PlaylistEditState?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        stateHolder?.onWriteRequestResult(it.resultCode)
    }
    val state = remember(playlistId) {
        PlaylistEditState(context, reader, playlistId, scope, requestWrite = { launcher.launch(it) }, onBack = onBack)
            .also { stateHolder = it }
    }
    LaunchedEffect(state) { state.start() }
    LifecycleResumeEffect(state) {
        state.onResume()
        onPauseOrDispose {}
    }
    BackHandler { state.maybeGoBack() }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableListState(listState) { from, to -> state.move(from, to) }
    val hazeState = remember { HazeState() }
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val barTopPadding = insets.calculateTopPadding() + GLASS_BAR_HEIGHT
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    val rows = state.rows
    val unknownArtist = stringResource(R.string.unknown_artist)

    Box(modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState).background(background)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = libraryContentPadding(top = barTopPadding),
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    val item = row.item
                    DismissibleRow(
                        onDismissed = { state.remove(index) },
                        modifier = Modifier.reorderableRow(this, reorder, index),
                    ) {
                        EditableSongRow(
                            title = item.mediaMetadata.title?.toString().orEmpty(),
                            subtitle = item.mediaMetadata.artist?.toString() ?: unknownArtist,
                            cover = item.mediaMetadata.artworkUri,
                            defaultCover = if (item.mediaId.startsWith("Missing:")) R.drawable.ic_default_cover_error
                                else R.drawable.ic_default_cover,
                            onClick = {},
                            onRemove = { state.remove(index) },
                            handleModifier = Modifier.reorderHandle(reorder, index),
                            modifier = Modifier.libraryItemCard(libraryCellShape(index, rows.size, 1)),
                        )
                    }
                }
            }
        }
        GlassTitleBar(
            hazeState = hazeState,
            title = state.title,
            scrolled = { Float.MAX_VALUE },
            toolbarPaddingStart = 4.dp,
            titlePaddingStart = 4.dp,
            navigationIcon = {
                LibraryIconButton(
                    icon = Icons.Outlined.Close,
                    iconSize = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = { state.maybeGoBack() },
                )
            },
            actions = {
                if (state.loaded) {
                    LibraryIconButton(
                        icon = Icons.Outlined.Check,
                        iconSize = 24.dp,
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = { state.apply() },
                    )
                }
            },
        )
        if (!state.loaded) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    when (state.dialog) {
        EditDialog.Restore -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_edits)) },
            text = { Text(stringResource(R.string.restore_edits_msg, state.title)) },
            confirmButton = {
                TextButton(onClick = { state.restore() }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { state.discardRestorable() }) { Text(stringResource(R.string.discard)) }
                TextButton(onClick = { state.cancelAndLeave() }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        EditDialog.Empty -> AlertDialog(
            onDismissRequest = { state.cancelAndLeave() },
            title = { Text(stringResource(R.string.playlist_empty)) },
            text = { Text(stringResource(R.string.playlist_empty_msg)) },
            confirmButton = {
                TextButton(onClick = { state.cancelAndLeave() }) { Text(stringResource(android.R.string.ok)) }
            },
        )
        EditDialog.Discard -> AlertDialog(
            onDismissRequest = { state.dialog = null },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_msg, state.title)) },
            confirmButton = {
                TextButton(onClick = { state.apply() }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { state.discardAndLeave() }) { Text(stringResource(R.string.discard)) }
                TextButton(onClick = { state.dialog = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
        null -> {}
    }
}
