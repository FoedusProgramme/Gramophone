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
import androidx.annotation.RequiresApi
import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.ui.MainActivity
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import uk.akane.libphonograph.getStringOrNullIfThrow
import java.io.File

object ItemManipulator {
    private const val TAG = "ItemManipulator"

    suspend fun deleteSong(context: MainActivity, file: File, id: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri("external"), id
        )
        val uris = mutableSetOf(uri)
        // TODO maybe don't hardcode these extensions twice, here and in LrcUtils?
        uris.addAll(setOf("ttml", "lrc", "srt").map {
            file.resolveSibling("${file.nameWithoutExtension}.$it")
        }.filter { it.exists() }.map {
            // It doesn't really make sense to have >1 subtitle file so we don't need to batch the queries.
            getIdForPath(context, it)
        }.filter { it != null }
            .map { ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), it!!) })
        return delete(context, uris)
    }

    suspend fun deletePlaylist(context: MainActivity, id: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(
            @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), id
        )
        return delete(context, setOf(uri))
    }

    // requires requestLegacyExternalStorage for simplicity
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
                            it to (MediaStoreCompat.delete(context, it))
                        } catch (e: SecurityException) {
                            Log.e("ItemManipulator", "failed to delete $it", e)
                            it to e
                        }
                    }
                    val notOk = urisWithStatus.filter { it.second != true }
                    val ok = notOk.isEmpty()
                    if (!ok) {
                        val firstError = notOk.find { it.second is Throwable }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context,
                                context.getString(R.string.delete_failed,
                                    firstError?.toString() ?: context.getString(
                                        androidx.media3.session.R.string.error_message_info_cancelled)),
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

    fun createPlaylist(context: Context, name: String): File {
        val parent = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val out = File(parent, "$name.m3u")
        if (out.exists())
            throw IllegalArgumentException("tried to create playlist $out that already exists")
        PlaylistSerializer.write(context.applicationContext, out, listOf())
        return out
    }

    private fun getIdForPath(context: Context, file: File): Long? {
        val cursor = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkIfFileAttributedToSelf(context: Context, uri: Uri): Boolean {
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null
        )
        if (cursor == null) return false
        cursor.use {
            if (!cursor.moveToFirst()) return false
            val column = cursor.getColumnIndex(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
            val pkg = cursor.getStringOrNullIfThrow(column)
            return pkg == context.packageName
        }
    }

    fun addToPlaylist(context: Context, out: File, songs: List<File>) {
        if (!out.exists())
            throw IllegalArgumentException("tried to change playlist $out that doesn't exist")
        setPlaylistContent(context, out, PlaylistSerializer.read(out) + songs)
    }

    fun setPlaylistContent(context: Context, out: File, songs: List<File>) {
        if (!out.exists())
            throw IllegalArgumentException("tried to change playlist $out that doesn't exist")
        val backup = out.readBytes()
        try {
            PlaylistSerializer.write(context.applicationContext, out, songs)
        } catch (t: Throwable) {
            try {
                PlaylistSerializer.write(
                    context.applicationContext, out.resolveSibling(
                        "${out.nameWithoutExtension}_NEW_${System.currentTimeMillis()}.m3u"
                    ), songs
                )
            } catch (t: Throwable) {
                Log.e(TAG, Log.getThrowableString(t)!!)
            }
            try {
                out.resolveSibling("${out.nameWithoutExtension}_BAK_${System.currentTimeMillis()}.${out.extension}")
                    .writeBytes(backup)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getThrowableString(t)!!)
            }
            throw t
        }
    }
}