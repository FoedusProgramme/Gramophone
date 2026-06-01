package uk.akane.libphonograph.manipulator

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.media3.common.util.Log
import androidx.media3.common.util.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.ui.MainActivity
import org.nift4.mediastorecompat.MediaStoreCompat
import org.nift4.mediastorecompat.StorageManagerCompat
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import uk.akane.libphonograph.reader.Reader
import java.io.File

object ItemManipulator {
    private const val TAG = "ItemManipulator"
    const val FAVORITES = "gramophone_favourite"
    const val DEFAULT_FORMAT = "m3u"

    suspend fun deleteSongs(context: MainActivity, list: List<Pair<File, Long>>): (() -> Unit)? {
        val faves = context.gramophoneApplication.reader.playlistListFlow.map { it.find { p ->
            p is Favorite } }.first()
        val songsToUnfave = list.filter { faves?.songList?.find { song -> song.getFile() ==
                it.first } != null }.map { it.first }
        if (faves?.id != null && songsToUnfave.isNotEmpty()) {
            val uri = ContentUris.withAppendedId(
                    @Suppress("deprecation")
                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, faves.id!!
                )
            val token = MediaStoreCompat.needRequestBytesWrite(context, uri)
            if (token == null) {
                try {
                    val readback = readbackPlaylist(context, uri)
                    val newSongs = readback.filter { !songsToUnfave.contains(it.file) }
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
            setOf("ttml", "lrc", "srt").asSequence().map {
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
        }
        return delete(context, uris)
    }

    suspend fun deletePlaylist(context: MainActivity, id: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(
            @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id
        )
        return delete(context, setOf(uri))
    }

    private suspend fun delete(context: MainActivity, uris: Collection<Uri>): (() -> Unit)? {
        if (uris.find { MediaStoreCompat.needRequestDelete(context, it) != null } != null) {
            val pendingIntent = MediaStoreCompat.createDeleteRequest(context, uris.toList())
            val req = Bundle().apply {
                putString("UiError", context.getString(
                    androidx.media3.session.R.string.error_message_info_cancelled))
            }
            withContext(Dispatchers.Main) {
                context.runIntentForDelete(pendingIntent.intentSender, req)
            }
            return null
        } else {
            return {
                CoroutineScope(Dispatchers.IO).launch {
                    val urisWithStatus = uris.map {
                        try {
                            MediaStoreCompat.delete(context, it)
                            it to (null)
                        } catch (e: SecurityException) {
                            Log.e("ItemManipulator", "failed to delete $it", e)
                            it to e
                        }
                    }
                    val notOk = urisWithStatus.filter { it.second != null }
                    val ok = notOk.isEmpty()
                    if (!ok) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context,
                                context.getString(R.string.delete_failed,
                                    notOk.first().toString()
                                ),
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    suspend fun continueDeleteFromPendingIntent(context: Context, resultCode: Int, data: Intent?, req: Bundle) {
        // this is the callback of createDeleteRequest(), and the delete was already done if
        // resultCode is RESULT_OK. if it's not, then we just show a toast or something.
        if (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_CANCELED) return
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.delete_failed,
                data?.getStringExtra("ErrorMsg") ?:
                req.getString("UiError")), Toast.LENGTH_LONG).show()
        }
    }

    fun createPlaylist(context: Context, name: String): Uri {
        val out = getDefaultPlaylistFile(name)
        if (out.exists())
            throw IllegalArgumentException("tried to create playlist $out that already exists")
        val uri = MediaStoreCompat.create(context, out.absolutePath)!!
        PlaylistSerializer.write(context.applicationContext, out, uri, listOf())
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
        return File(parent, "$name.$DEFAULT_FORMAT")
    }

    suspend fun readbackPlaylist(context: Context, uri: Uri): List<Entry> {
        val pathMap = context.gramophoneApplication.reader.pathMapFlow.first()
        return Reader.readPlaylist(context, uri).map {
            it.resolveMediaItem(pathMap)?.let { song -> it.copyFromMediaItem(song) } ?: it
        }
    }

    fun setPlaylistContent(context: Context, uri: Uri, songs: List<PlaylistSerializer.Entry>,
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