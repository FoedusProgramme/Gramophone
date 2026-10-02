/*
 *     Copyright (C) 2025 nift4
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

package uk.akane.libphonograph.manipulator

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.media3.common.util.Log
import androidx.media3.common.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.logic.queryWithPending
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.library.DeleteResult
import org.akanework.gramophone.logic.library.PendingWrite
import org.nift4.mediastorecompat.MediaStoreCompat
import org.nift4.mediastorecompat.StorageManagerCompat
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import uk.akane.libphonograph.reader.FlowReader
import uk.akane.libphonograph.reader.Reader
import uk.akane.libphonograph.toUriCompat
import java.io.File

object ItemManipulator {
    private const val TAG = "ItemManipulator"
    const val FAVORITES = "gramophone_favourite"
    const val DEFAULT_FORMAT = "m3u"

    suspend fun deleteSongs(context: Context, reader: FlowReader, list: List<Pair<File, Long>>): DeleteResult {
        val faves = reader.playlistListFlow.map { it.find { p ->
            p is Favorite } }.first()
        val songsToUnfave = faves?.let { _ -> list.filter { faves.songList.find { song ->
            song.getFile() == it.first } != null }.map { it.first.toUriCompat() } }
        if (faves?.id != null && !songsToUnfave.isNullOrEmpty()) {
            val uri = ContentUris.withAppendedId(
                    @Suppress("deprecation")
                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, faves.id!!
                )
            val token = MediaStoreCompat.needRequestBytesWrite(context, uri)
            if (token == null) {
                try {
                    val readback = readbackPlaylist(context, reader, uri)
                    val newSongs = readback.copy(entries = readback.entries.filter {
                        it.locations.find { songsToUnfave.contains(it) } == null })
                    setPlaylistContent(context, uri, newSongs, false)
                } catch (e: Exception) {
                    Log.e(TAG, "failed to set unfavorite $songsToUnfave", e)
                }
            }
        }
        val uris = list.flatMap {
            val id = it.second
            val file = it.first
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.getContentUri("external"), id
            )
            // TODO maybe don't hardcode these extensions twice, here and in LrcUtils?
            val extensions = setOf("ttml", "lrc", "srt")
            if (!Flags.MEDIASTORE_IO) {
                extensions.asSequence().map {
                    file.resolveSibling("${file.nameWithoutExtension}.$it")
                }.filter { it.exists() }.map {
                    // It doesn't really make sense to have >1 subtitle file so we don't need to batch the queries.
                    getIdForPath(context, it)
                }.filter { it != null }
                    .map {
                        ContentUris.withAppendedId(
                            MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
                            it!!
                        )
                    }
                    .toList() + uri
            } else {
                val urisToDelete = mutableSetOf(uri)
                context.contentResolver.queryWithPending(MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Files.FileColumns._ID), "${MediaStore
                        .Files.FileColumns.DATA} IN (" + extensions.joinToString { "?" } + ")",
                    extensions.map { file.resolveSibling(
                        file.nameWithoutExtension + ".$it").path }.toTypedArray(),
                    null).use {
                    if (it != null && it.moveToFirst()) {
                        val idColumn = it.getColumnIndexOrThrow(
                            MediaStore.Files.FileColumns._ID)
                        do {
                            urisToDelete.add(ContentUris.withAppendedId(
                                MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
                                it.getLong(idColumn)))
                        } while (it.moveToNext())
                    }
                }
                urisToDelete
            }
        }
        return delete(context, uris)
    }

    fun deletePlaylist(context: Context, id: Long): DeleteResult {
        val uri = ContentUris.withAppendedId(
            @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id
        )
        return delete(context, setOf(uri))
    }

    private fun delete(context: Context, uris: Collection<Uri>): DeleteResult {
        val consent = try {
            if (uris.any { MediaStoreCompat.needRequestDelete(context, it) != null })
                MediaStoreCompat.createDeleteRequest(context, uris.toList()).intentSender
            else null
        } catch (e: Exception) {
            Log.e(TAG, "failed to prepare deleting $uris", e)
            return DeleteResult.Failed(e)
        }
        return deleteResult(consent) {
            withContext(Dispatchers.IO) {
                val notOk = uris.mapNotNull {
                    try {
                        MediaStoreCompat.delete(context, it)
                        null
                    } catch (e: SecurityException) {
                        Log.e(TAG, "failed to delete $it", e)
                        it
                    }
                }
                if (notOk.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context,
                            context.getString(R.string.delete_failed, notOk.first().toString()),
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    /**
     * With a [consent] dialog, the system deletes once the user agrees; without one, [deleteNow]
     * waits for the caller's own confirmation.
     */
    internal fun deleteResult(consent: IntentSender?, deleteNow: suspend () -> Unit): DeleteResult =
        if (consent != null) DeleteResult.NeedsConsent(consent, PendingWrite.Delete)
        else DeleteResult.ConfirmThenRun(deleteNow)

    fun createPlaylist(context: Context, out: File): Uri {
        if (out.exists())
            throw IllegalArgumentException("tried to create playlist $out that already exists")
        val uri = MediaStoreCompat.create(context, out.absolutePath)!!
        PlaylistSerializer.write(context.applicationContext, out, uri,
            PlaylistSerializer.Playlist.create())
        return uri
    }

    private fun getIdForPath(context: Context, file: File): Long? {
        val cursor = context.contentResolver.query(
            MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
            if (hasImprovedMediaStore())
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            else arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.Files.FileColumns.DATA} = ?", arrayOf(file.absolutePath), null
        )
        if (cursor == null) return null
        cursor.use {
            if (!cursor.moveToFirst()) return null
            if (hasImprovedMediaStore()) {
                val typeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val type = cursor.getIntOrNullIfThrow(typeColumn)
                if (type != MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE) {
                    Log.e(TAG, "expected $file to be a subtitle")
                    return null
                }
            }
            val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
            return cursor.getLongOrNullIfThrow(idColumn)
        }
    }

    fun getDefaultPlaylistFile(name: String): File {
        val parent = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        return File(parent, Util.escapeFileName("$name.$DEFAULT_FORMAT"))
    }

    suspend fun readbackPlaylist(context: Context, reader: FlowReader, uri: Uri): PlaylistSerializer.Playlist {
        val pathMap = reader.pathMapFlow.first()
        return Reader.readPlaylist(context, uri).let {
            it.copy(entries = it.entries.map {
                it.updateFromMediaItem(pathMap)
            })
        }
    }

    fun setPlaylistContent(context: Context, uri: Uri, songs: PlaylistSerializer.Playlist,
                           needToFinishCreate: Boolean) {
        var out: File? = null
        var name: String? = null
        context.contentResolver.query(uri,
            if (hasImprovedMediaStore())
                arrayOf(MediaStore.MediaColumns.DATA)
            else arrayOf(MediaStore.MediaColumns.DATA,
                @Suppress("deprecation") MediaStore.Audio.Playlists.NAME),
            null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst())
                throw IllegalArgumentException("Failed to query $uri")
            out = File(cursor.getString(
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)))
            if (!hasImprovedMediaStore())
                name = cursor.getString(cursor.getColumnIndexOrThrow(
                    @Suppress("deprecation") MediaStore.Audio.Playlists.NAME))
        }
        if (!hasImprovedMediaStore() && !out!!.exists()) {
            // Move this playlist to a plausible real path on the same volume because we're about to
            // convert it from abstract to real playlist.
            out = StorageManagerCompat.getVolumeForPath(StorageManagerCompat
                .getStorageVolumes(context), out)
                .requireCanonicalDirectory()
                .resolve(Environment.DIRECTORY_MUSIC)
                .resolve(Util.escapeFileName("$name.$DEFAULT_FORMAT"))
            var i = 1
            while (out!!.exists()) {
                out = out.resolveSibling("${out.nameWithoutExtension} (${i++})" +
                        ".${out.extension}")
            }
            out = MediaStoreCompat.efficientMove(context, uri, out.absolutePath)
        }
        try {
            PlaylistSerializer.write(context.applicationContext, out!!, uri, songs)
        } catch (_: PlaylistSerializer.UnsupportedPlaylistFormatException) {
            // convert to .m3u to fulfill the user's request
            out = out!!.resolveSibling("${out.nameWithoutExtension}.$DEFAULT_FORMAT")
            var i = 1
            while (out!!.exists()) {
                out = out.resolveSibling("${out.nameWithoutExtension} (${i++})" +
                        ".${out.extension}")
            }
            out = MediaStoreCompat.efficientMove(context, uri, out.path)
            PlaylistSerializer.write(context.applicationContext, out, uri, songs)
        }
        if (needToFinishCreate) {
            MediaStoreCompat.finishCreate(context, uri, out)
        } else {
            MediaStoreCompat.scanFile(context, uri, out)
        }
    }
}