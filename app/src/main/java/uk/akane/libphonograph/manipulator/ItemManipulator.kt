package uk.akane.libphonograph.manipulator

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.logic.hasScopedStorageV1
import org.akanework.gramophone.ui.MainActivity
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import java.io.File

object ItemManipulator {
    private const val TAG = "ItemManipulator"

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
            .map { ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), it!!) }
            .toList())
        return delete(context, uris)
    }

    suspend fun deletePlaylist(context: MainActivity, id: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(
            @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), id
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
        val parent = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val out = File(parent, "$name.m3u")
        if (out.exists())
            throw IllegalArgumentException("tried to create playlist $out that already exists")
        val uri = MediaStoreCompat.create(context, out.absolutePath)!!
        PlaylistSerializer.write(context.applicationContext, out, uri, listOf())
        MediaStoreCompat.finishCreate(context, uri)
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

    private fun getPathForId(context: Context, id: Long): String? {
        context.contentResolver.query(
            ContentUris.withAppendedId(MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
                id), arrayOf(MediaStore.MediaColumns.DATA),
            null, null, null
        ).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return null
            return cursor.getString(cursor.getColumnIndexOrThrow(
                MediaStore.MediaColumns.DATA))
        }
    }

    fun readPlaylist(context: Context, uri: Uri): List<File> {
        val file = File(context.contentResolver.query(uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst())
                throw IllegalArgumentException("Failed to query $uri")
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
        })
        // Note: because abstract playlists got removed in R, the file must exist on R+ or it's a
        // fatal error.
        val paths = if (hasImprovedMediaStore() || file.exists()) try {
            PlaylistSerializer.read(file)
        } catch (_: PlaylistSerializer.UnsupportedPlaylistFormatException) {
            null
        } else null
        if (paths == null || Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
            MediaStoreCompat.shouldPreferAbstractPlaylistOverFile(context, uri)) {
            return context.contentResolver.query(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members
                    .getContentUri("external", ContentUris
                        .parseId(uri)), arrayOf(
                    @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.DATA,
                ), null, null, @Suppress("DEPRECATION")
                MediaStore.Audio.Playlists.Members.PLAY_ORDER + " ASC"
            ).use { cursor ->
                if (cursor == null) {
                    if (paths != null)
                        return paths
                    throw IllegalStateException("Can't read playlist, null cursor returned")
                }
                val out = mutableListOf<File>()
                val column = cursor.getColumnIndexOrThrow(
                    @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.DATA
                )
                while (cursor.moveToNext()) {
                    out.add(File(cursor.getString(column)))
                }
                out
            }
        }
        // on Q+, we are done here. either we should've preferred abstract (or failed to parse), and
        // already did it above, or (we are now here) we can trust the file and parsed it fine.
        if (hasScopedStorageV1())
            return paths
        // One of the rare cases where we do know the file is the more up-to-date one even on P-.
        if (!MediaStoreCompat.shouldPreferAbstractPlaylistOverFile(context, uri))
            return paths
        // On Android P-, we have no surefire signal whether the file or the abstract playlist is
        // newer, so we use heuristics. This is an imperfect heuristic that doesn't work in 100% of
        // cases. What we do is check every accessible entry in the abstract playlist against the
        // parsed file. If an entry in the abstract playlist is null (it was deleted or moved on
        // disk), the file may contain anything at this position. This is the tightest heuristic we
        // can do that doesn't have false negatives, I hope. But it does have false positives (uses
        // file over abstract playlist even though it should not have) due to missing information
        // about the null entries. One example case where it would fail is: 1. User makes playlist
        // with song A, B and C and writes it to m3u, it gets scanned. 2. User edits playlist in DB
        // only, swapping C for D. 3. Both C and D get moved into another folder. We would falsely
        // read A, B, C from the m3u because we have no way of knowing which song is missing in the
        // abstract playlist (in fact not even MediaProvider knows, which is why abstract playlists
        // rightly got deprecated).
        val out = mutableListOf<Long?>()
        val orders = mutableListOf<Long>()
        // Ensure to trigger the "simpleQuery" path without join which allows us to see ghost ID by
        // not requesting DATA column.
        context.contentResolver.query(
            @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members
                .getContentUri("external", ContentUris
                    .parseId(uri)), arrayOf(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.AUDIO_ID,
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.PLAY_ORDER
            ), null, null, @Suppress("DEPRECATION")
            MediaStore.Audio.Playlists.Members.PLAY_ORDER + " ASC"
        ).use { cursor ->
            if (cursor == null) {
                return paths
            }
            val column = cursor.getColumnIndexOrThrow(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.AUDIO_ID
            )
            val column2 = cursor.getColumnIndexOrThrow(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.PLAY_ORDER
            )
            var first: Long? = null
            while (cursor.moveToNext()) {
                val last = first
                first = cursor.getLong(column2)
                orders.add(first)
                while (last != null && first + 1 < last) {
                    out.add(null)
                    first++
                }
                out.add(cursor.getLong(column))
            }
        }
        val abstract = out.map { it?.let { getPathForId(context, it)
            ?.let { path -> File(path) } } }
        if (orders.isNotEmpty() && orders != (0..orders.max()).toList()) {
            // PLAY_ORDER has a gap, duplicates or other inconsistencies which MediaScanner doesn't
            // generate, which means this playlist was edited in the database after MediaScanner
            // scanned it last time. So we must use abstract playlist.
            Log.i(TAG, "Used abstract playlist due to bad play order: $orders")
            return abstract.filterNotNull()
        }
        abstract.forEachIndexed { i, file ->
            if (paths.size <= i) {
                if (file != null) {
                    // The abstract playlist has more entries, we must use it
                    Log.i(TAG, "Used abstract playlist due to file $file at $i")
                    return abstract.filterNotNull()
                }
                return@forEachIndexed
            }
            if (file != null && paths[i] != file) {
                // The abstract playlist has a different entry, we must use it
                Log.i(TAG, "Used abstract playlist due to a=$file != b=${paths[i]} at $i")
                return abstract.filterNotNull()
            }
        }
        if (paths.size > abstract.size) {
            Log.i(TAG, "Used abstract playlist due to suffix deleted: " +
                    "${paths[abstract.size]}")
            return abstract.filterNotNull()
        }
        Log.i(TAG, "Used parsed playlist, no issues found")
        return paths
    }

    fun addToPlaylist(context: Context, uri: Uri, songs: List<File>) {
        setPlaylistContent(context, uri, readPlaylist(context, uri) + songs)
    }

    fun setPlaylistContent(context: Context, uri: Uri, songs: List<File>) {
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
            MediaStoreCompat.efficientMove(context, uri, "Music/$name.m3u")
            out = File(context.contentResolver.query(uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null, null, null).use { cursor ->
                if (cursor == null || !cursor.moveToFirst())
                    throw IllegalArgumentException("Failed to query $uri")
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
            })
        }
        PlaylistSerializer.write(context.applicationContext, out!!, uri, songs)
        MediaStoreCompat.scanFile(context, uri, out)
    }
}