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

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.PlaylistSerializer
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import uk.akane.libphonograph.reader.FlowReader
import java.io.File

/**
 * Playlist edits, favorites, renames and deletes. Writes that need the user's MediaStore consent
 * are queued on [MediaConsentRequester]; the answer comes back through [onConsentResult], possibly
 * in a new process. The writes run on the application scope so they finish even if the screen
 * that asked for them goes away.
 */
class LibraryWriteRepository internal constructor(
    private val scope: CoroutineScope,
    private val requester: MediaConsentRequester,
    private val writes: LibraryWrites,
) {
    constructor(
        context: Context,
        reader: FlowReader,
        scope: CoroutineScope,
        requester: MediaConsentRequester,
    ) : this(scope, requester, MediaStoreLibraryWrites(context.applicationContext, reader))

    /** Adds [songs] to the playlist at [uri], or to a new playlist file [newPlaylist]. */
    fun addToPlaylist(uri: Uri?, newPlaylist: File?, songs: List<Entry>) {
        submit(PendingWrite.AddToPlaylist(songs, uri, newPlaylist?.path))
    }

    fun markFavorite(songs: List<Entry>, favorite: Boolean) {
        scope.launch {
            submitNow(PendingWrite.Favorite(songs, writes.favoritesUri(), favorite))
        }
    }

    fun renamePlaylist(id: Long, path: File) {
        submit(PendingWrite.Rename(id, path.absolutePath))
    }

    fun createPlaylist(file: File) {
        scope.launch { writes.createPlaylist(file) }
    }

    /**
     * Prepares deleting songs. A [DeleteResult.NeedsConsent] is already queued for the system
     * dialog; a [DeleteResult.ConfirmThenRun] deletes nothing until passed to [runConfirmed].
     */
    suspend fun deleteSongs(list: List<Pair<File, Long>>): DeleteResult =
        writes.deleteSongs(list).also(::queueIfNeeded)

    /** Like [deleteSongs], for the playlist with MediaStore [id]. */
    suspend fun deletePlaylist(id: Long): DeleteResult =
        writes.deletePlaylist(id).also(::queueIfNeeded)

    /**
     * Like the suspending [deleteSongs], but runs on the application scope so it finishes even if
     * the screen that asked goes away. [onResult] is called on the main thread.
     */
    fun deleteSongs(list: List<Pair<File, Long>>, onResult: (DeleteResult) -> Unit) {
        scope.launch {
            val result = deleteSongs(list)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /** Like [deleteSongs] with a callback, for the playlist with MediaStore [id]. */
    fun deletePlaylist(id: Long, onResult: (DeleteResult) -> Unit) {
        scope.launch {
            val result = deletePlaylist(id)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /** Runs a delete the user confirmed in the app. */
    fun runConfirmed(result: DeleteResult.ConfirmThenRun) {
        scope.launch { result.run() }
    }

    /**
     * The consent dialog for [write] returned [resultCode]. [write] is null if the host had
     * nothing pending, which is logged and ignored.
     */
    fun onConsentResult(write: PendingWrite?, resultCode: Int, data: Intent?) {
        if (write == null) {
            Log.w(TAG, "consent result $resultCode without a pending write")
            return
        }
        scope.launch {
            when {
                // The system dialog deletes by itself; cancelling is the user's choice.
                write is PendingWrite.Delete -> if (resultCode != Activity.RESULT_OK &&
                    resultCode != Activity.RESULT_CANCELED) {
                    writes.reportFailure(write, resultCode, data)
                }
                resultCode == Activity.RESULT_OK -> writes.perform(write)
                else -> writes.reportFailure(write, resultCode, data)
            }
        }
    }

    private fun submit(write: PendingWrite) {
        scope.launch { submitNow(write) }
    }

    private suspend fun submitNow(write: PendingWrite) {
        val consent = writes.consentFor(write)
        if (consent != null) requester.send(consent, write) else writes.perform(write)
    }

    private fun queueIfNeeded(result: DeleteResult) {
        if (result is DeleteResult.NeedsConsent) requester.request(result.sender, result.payload)
    }

    private companion object {
        const val TAG = "LibraryWriteRepository"
    }
}

/** The MediaStore side of [LibraryWriteRepository]; a seam for tests. */
internal interface LibraryWrites {
    suspend fun favoritesUri(): Uri?

    /** The consent dialog [write] needs first, or null if it can run right away. */
    suspend fun consentFor(write: PendingWrite): IntentSender?
    suspend fun perform(write: PendingWrite)
    suspend fun reportFailure(write: PendingWrite, resultCode: Int, data: Intent?)
    suspend fun createPlaylist(file: File)
    suspend fun deleteSongs(list: List<Pair<File, Long>>): DeleteResult
    suspend fun deletePlaylist(id: Long): DeleteResult
}

private class MediaStoreLibraryWrites(
    private val context: Context,
    private val reader: FlowReader,
) : LibraryWrites {

    override suspend fun favoritesUri(): Uri? =
        reader.playlistListFlow.map { it.find { p -> p is Favorite } }.first()?.id?.let {
            ContentUris.withAppendedId(
                @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, it
            )
        }

    override suspend fun consentFor(write: PendingWrite): IntentSender? = withContext(Dispatchers.IO) {
        val token = when (write) {
            is PendingWrite.AddToPlaylist -> if (write.uri != null) {
                MediaStoreCompat.needRequestBytesWrite(context, write.uri)
            } else {
                MediaStoreCompat.needRequestCreate(context, write.name!!)
            }
            is PendingWrite.Favorite -> if (write.uri != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStoreCompat.needRequestAdoption(context, write.uri)
            } else if (write.uri != null) {
                MediaStoreCompat.needRequestBytesWrite(context, write.uri)
            } else {
                MediaStoreCompat.needRequestCreate(context, favoritesFile().path)
            }
            is PendingWrite.Rename -> MediaStoreCompat.needRequestEfficientMove(
                context, playlistUri(write.id), File(write.path).parent ?: ""
            )
            // Deletes ask through ItemManipulator's delete request instead.
            PendingWrite.Delete -> null
        }
        token?.let { MediaStoreCompat.createWriteRequest(context, listOf(it)).intentSender }
    }

    override suspend fun perform(write: PendingWrite) = withContext(Dispatchers.IO) {
        try {
            when (write) {
                is PendingWrite.AddToPlaylist -> addToPlaylist(write)
                is PendingWrite.Favorite -> markFavorite(write)
                is PendingWrite.Rename ->
                    MediaStoreCompat.efficientMove(context, playlistUri(write.id), write.path)
                PendingWrite.Delete -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            toast(failureMessage(write), e.javaClass.name + ": " + e.message)
        }
        Unit
    }

    override suspend fun reportFailure(write: PendingWrite, resultCode: Int, data: Intent?) {
        if (write is PendingWrite.Delete) {
            toast(R.string.delete_failed, data?.getStringExtra("ErrorMsg") ?: context.getString(
                androidx.media3.session.R.string.error_message_info_cancelled))
        } else {
            toast(failureMessage(write), "$resultCode")
        }
    }

    override suspend fun createPlaylist(file: File) = withContext(Dispatchers.IO) {
        try {
            val uri = ItemManipulator.createPlaylist(context, file)
            ItemManipulator.setPlaylistContent(
                context, uri, PlaylistSerializer.Playlist.create(), true
            )
        } catch (e: Exception) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            toast(R.string.create_failed_playlist, e.javaClass.name + ": " + e.message)
        }
    }

    override suspend fun deleteSongs(list: List<Pair<File, Long>>) = withContext(Dispatchers.IO) {
        ItemManipulator.deleteSongs(context, reader, list)
    }

    override suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        ItemManipulator.deletePlaylist(context, id)
    }

    private suspend fun addToPlaylist(write: PendingWrite.AddToPlaylist) {
        val readback = if (write.uri != null) {
            ItemManipulator.readbackPlaylist(context, reader, write.uri)
        } else PlaylistSerializer.Playlist.create()
        val uri = write.uri ?: ItemManipulator.createPlaylist(context, File(write.name!!))
        ItemManipulator.setPlaylistContent(
            context, uri, readback.copy(entries = readback.entries + write.songs),
            write.uri == null
        )
    }

    private suspend fun markFavorite(write: PendingWrite.Favorite) {
        val uriIn = write.uri?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MediaStoreCompat.adoptFile(context, it)
            } else it
        }
        val uri = uriIn ?: ItemManipulator.createPlaylist(context, favoritesFile())
        val readback = if (uriIn != null) {
            ItemManipulator.readbackPlaylist(context, reader, uri)
        } else PlaylistSerializer.Playlist.create()
        val entries = if (write.favorite) {
            readback.entries + write.songs
        } else {
            readback.entries.filter { write.songs.none { candidate -> candidate.fuzzyEquals(it) } }
        }
        ItemManipulator.setPlaylistContent(context, uri, readback.copy(entries = entries),
            uriIn == null)
    }

    @StringRes
    private fun failureMessage(write: PendingWrite) = when (write) {
        is PendingWrite.AddToPlaylist -> if (write.uri != null) R.string.edit_playlist_failed
            else R.string.create_failed_playlist
        is PendingWrite.Favorite -> R.string.edit_favorites_failed
        is PendingWrite.Rename -> R.string.rename_failed_playlist
        PendingWrite.Delete -> R.string.delete_failed
    }

    private suspend fun toast(@StringRes message: Int, arg: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, context.getString(message, arg), Toast.LENGTH_LONG).show()
    }

    private fun favoritesFile() = ItemManipulator.getDefaultPlaylistFile(ItemManipulator.FAVORITES)

    private fun playlistUri(id: Long) = ContentUris.withAppendedId(
        @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id
    )

    private companion object {
        const val TAG = "LibraryWriteRepository"
    }
}
