package uk.akane.libphonograph.manipulator

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.ui.MainActivity
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import uk.akane.libphonograph.reader.Reader
import java.io.File

object ItemManipulator {
    private const val TAG = "ItemManipulator"
    const val FAVORITES = "gramophone_favourite"

    suspend fun deleteSong(context: MainActivity, file: File, id: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri("external"), id
        )
        val uris = mutableSetOf(uri)
        // TODO maybe don't hardcode these extensions twice, here and in LrcUtils?
        uris.addAll(setOf("ttml", "lrc", "srt").asSequence().map {
            file.resolveSibling("${file.nameWithoutExtension}.$it")
        }.filter { it.exists() }.map {
            // It doesn't really make sense to have >1 subtitle file so we don't need to batch the queries.
            getIdForPath(context, it)
        }.filter { it != null }
            .map { ContentUris.withAppendedId(MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI, it!!) }
            .toList())
        return delete(context, uris)
    }

    suspend fun deletePlaylist(context: MainActivity, id: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(
            @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id
        )
        return delete(context, setOf(uri))
    }

    private suspend fun delete(context: MainActivity, uris: Set<Uri>): (() -> Unit)? {
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

    suspend fun continueDeleteFromPendingIntent(context: Context, resultCode: Int, req: Bundle) {
        // this is the callback of createDeleteRequest(), and the delete was already done if
        // resultCode is RESULT_OK. if it's not, then we just show a toast or something.
        if (resultCode == Activity.RESULT_OK) return
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.delete_failed,
                req.getString("UiError")), Toast.LENGTH_LONG).show()
        }
    }

    fun setFavorite(context: Context, uris: Set<Uri>, favorite: Boolean): IntentSender? {
        if (!hasImprovedMediaStore()) {
            // TODO(ASAP) Q- support
            return null
        }
        if (MediaStoreCompat.needRequestFavorite(context, uris)) {
            // This never actually visibly asks the user for permission...
            val pendingIntent = MediaStoreCompat.createFavoriteRequest(
                context, uris.toList(), favorite
            )
            return pendingIntent.intentSender
        } else {
            MediaStoreCompat.markIsFavoriteStatus(context, uris, favorite)
            return null
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
        return File(parent, "$name.m3u")
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
            out = MediaStoreCompat.efficientMove(context, uri, "Music/$name.m3u")
        }
        try {
            PlaylistSerializer.write(context.applicationContext, out!!, uri, songs)
        } catch (_: PlaylistSerializer.UnsupportedPlaylistFormatException) {
            // convert to .m3u to fulfill the user's request
            out = MediaStoreCompat.efficientMove(context, uri, out!!
                .resolveSibling("${out.nameWithoutExtension}.m3u").path)
            PlaylistSerializer.write(context.applicationContext, out, uri, songs)
        }
        if (needToFinishCreate) {
            MediaStoreCompat.finishCreate(context, uri, out)
        } else {
            MediaStoreCompat.scanFile(context, uri, out)
        }
    }
}