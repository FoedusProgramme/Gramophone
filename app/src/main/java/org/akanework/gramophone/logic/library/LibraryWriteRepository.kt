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
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
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
import org.akanework.gramophone.logic.playlistUri
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

    /** Appends [songs] to the playlist with MediaStore [playlistId]. */
    fun addToPlaylist(playlistId: Long, songs: List<Entry>) {
        submit(PendingWrite.AddToPlaylist(songs, playlistId))
    }

    /** Creates the playlist file [file], holding [songs]. */
    fun addToNewPlaylist(file: File, songs: List<Entry>) {
        submit(PendingWrite.AddToNewPlaylist(songs, file.path))
    }

    fun markFavorite(songs: List<Entry>, favorite: Boolean) {
        scope.launch {
            submitNow(PendingWrite.Favorite(songs, writes.favoritesUri(), favorite))
        }
    }

    /**
     * Marks [songs] as favorite or not right away, for a caller that can't ask for consent (the
     * playback service). Returns false if the write needs the user's consent first, in which case
     * nothing is written, or if it failed; either way the user hasn't been told.
     */
    suspend fun markFavoriteNow(songs: List<Entry>, favorite: Boolean): Boolean {
        val write = PendingWrite.Favorite(songs, writes.favoritesUri(), favorite)
        return writes.consentFor(write) == null && writes.perform(write, quiet = true)
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
        if (consent != null) requester.request(consent, write) else writes.perform(write)
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

    /**
     * Runs [write] and returns whether it worked. A failure is logged, and unless [quiet] also
     * shown to the user.
     */
    suspend fun perform(write: PendingWrite, quiet: Boolean = false): Boolean
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
        reader.playlistListFlow.map { it.find { p -> p is Favorite } }.first()?.id
            ?.let(::playlistUri)

    override suspend fun consentFor(write: PendingWrite): IntentSender? = withContext(Dispatchers.IO) {
        val token = when (write) {
            is PendingWrite.AddToPlaylist ->
                MediaStoreCompat.needRequestBytesWrite(context, playlistUri(write.id))
            is PendingWrite.AddToNewPlaylist ->
                MediaStoreCompat.needRequestCreate(context, write.path)
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

    override suspend fun perform(write: PendingWrite, quiet: Boolean) = withContext(Dispatchers.IO) {
        try {
            when (write) {
                is PendingWrite.AddToPlaylist -> addToPlaylist(write)
                is PendingWrite.AddToNewPlaylist -> addToNewPlaylist(write)
                is PendingWrite.Favorite -> markFavorite(write)
                is PendingWrite.Rename ->
                    MediaStoreCompat.efficientMove(context, playlistUri(write.id), write.path)
                PendingWrite.Delete -> Unit
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            if (!quiet) toast(failureMessage(write), e.javaClass.name + ": " + e.message)
            false
        }
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
        val uri = playlistUri(write.id)
        val readback = ItemManipulator.readbackPlaylist(context, reader, uri)
        ItemManipulator.setPlaylistContent(
            context, uri, readback.copy(entries = readback.entries + write.songs), false
        )
    }

    private suspend fun addToNewPlaylist(write: PendingWrite.AddToNewPlaylist) {
        val empty = PlaylistSerializer.Playlist.create()
        val uri = ItemManipulator.createPlaylist(context, File(write.path))
        ItemManipulator.setPlaylistContent(
            context, uri, empty.copy(entries = empty.entries + write.songs), true
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
        is PendingWrite.AddToPlaylist -> R.string.edit_playlist_failed
        is PendingWrite.AddToNewPlaylist -> R.string.create_failed_playlist
        is PendingWrite.Favorite -> R.string.edit_favorites_failed
        is PendingWrite.Rename -> R.string.rename_failed_playlist
        PendingWrite.Delete -> R.string.delete_failed
    }

    private suspend fun toast(@StringRes message: Int, arg: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, context.getString(message, arg), Toast.LENGTH_LONG).show()
    }

    private fun favoritesFile() = ItemManipulator.getDefaultPlaylistFile(ItemManipulator.FAVORITES)

    private companion object {
        const val TAG = "LibraryWriteRepository"
    }
}
