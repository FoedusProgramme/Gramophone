package uk.akane.libphonograph.manipulator

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import androidx.media3.common.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.logic.hasMarkIsFavouriteStatus
import org.akanework.gramophone.logic.hasScopedStorageV2
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import uk.akane.libphonograph.getStringOrNullIfThrow
import uk.akane.libphonograph.toUriCompat
import java.io.File
import java.io.IOException

object ItemManipulator {
    private const val TAG = "ItemManipulator"
    // TODO: generally migrate writing IO to MediaStore on R+ (not on legacy!) in order to get rich
	//  error messages instead of FUSE just saying ENOPERM (reading is more complicated because the
	//  database can be out of sync for whatever reasons)
    //  i.e. do not use FUSE for insert, delete, or anything else
    //  also fallback to ContentProvider.delete() with RecoverableSecurityException if
    //   createDeleteRequest throws the all items need to be specified unique id stuff

    fun deleteSong(context: Context, file: File, id: Long): MediaStoreRequest {
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

    fun deletePlaylist(context: Context, id: Long): MediaStoreRequest {
        val uri = ContentUris.withAppendedId(
            @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), id
        )
        return delete(context, setOf(uri))
    }

    // requires requestLegacyExternalStorage for simplicity
    fun delete(context: Context, uris: Set<Uri>): MediaStoreRequest {
        if (needRequestWrite(context, uris)) {
            val pendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver, uris.toList()
            )
            return MediaStoreRequest(pendingIntent.intentSender)
        } else {
            return MediaStoreRequest {
                val urisWithStatus = uris.map {
                    try {
                        it to (context.contentResolver.delete(it, null, null) == 1)
                    } catch (e: SecurityException) {
                        Log.e("ItemManipulator", "failed to delete $it", e)
                        it to false
                    }
                }
                val notOk = urisWithStatus.filter { !it.second }
                val ok = notOk.isEmpty()
                if (!ok && hasScopedStorageV2()) {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver, notOk.map { it.first }
                    )
                    throw DeleteFailedPleaseTryDeleteRequestException(pendingIntent)
                } else if (!ok) {
                    throw IOException("failed to delete" +
                            "${notOk.size} items")
                }
            }
        }
    }

    fun setFavorite(context: Context, uris: Set<Uri>, favorite: Boolean): IntentSender? {
        if (!hasImprovedMediaStore()) {
            // TODO(ASAP) Q- support
            return null
        }
        if (hasMarkIsFavouriteStatus()) {
            MediaStore.markIsFavoriteStatus(
                context.contentResolver, uris.toList(), favorite
            )
            return null
        } else if (needRequestWrite(context, uris)) {
            // This never actually asks the user for permission...
            val pendingIntent = MediaStore.createFavoriteRequest(
                context.contentResolver, uris.toList(), favorite
            )
            return pendingIntent.intentSender
        } else {
            val cv = ContentValues()
            cv.put(MediaStore.MediaColumns.IS_FAVORITE, if (favorite) 1 else 0)
            uris.forEach { uri ->
                if (context.contentResolver.update(uri, cv, null, null) != 1)
                    Log.w(TAG, "failed to favorite $uri")
            }
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
        val cursor = context.contentResolver.query(MediaStore.Files.getContentUri("external"),
            if (hasImprovedMediaStore())
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            else arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.Files.FileColumns.DATA} = ?", arrayOf(file.absolutePath), null)
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

    @ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
    fun needRequestWrite(context: Context, uris: Set<Uri>): Boolean {
        for (uri in uris)
            if (needRequestWrite(context, uri))
                return true
        return false
    }

    @ChecksSdkIntAtLeast(Build.VERSION_CODES.R)
    fun needRequestWrite(context: Context, uri: Uri): Boolean {
        return hasScopedStorageV2() && !checkIfFileAttributedToSelf(context, uri) &&
                context.checkUriPermission(uri, Process.myPid(), Process.myUid(),
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkIfFileAttributedToSelf(context: Context, uri: Uri): Boolean {
        val cursor = context.contentResolver.query(uri,
            arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null)
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
                PlaylistSerializer.write(context.applicationContext, out.resolveSibling(
                    "${out.nameWithoutExtension}_NEW_${System.currentTimeMillis()}.m3u"), songs)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getThrowableString(t)!!)
            }
            try {
                out.resolveSibling("${out.nameWithoutExtension}_BAK_${System.currentTimeMillis()}.${out.extension}").writeBytes(backup)
            } catch (t: Throwable) {
                Log.e(TAG, Log.getThrowableString(t)!!)
            }
            throw t
        }
    }

    fun renamePlaylist(context: Context, out: File, newName: String) {
        val new = out.resolveSibling("$newName.${out.extension}")
        if (new.exists())
            throw IOException("can't rename to existing")
        // don't use normal rename methods as media store caches the old title in that case
        new.writeBytes(out.readBytes())
        if (!out.delete()) {
            MediaScannerConnection.scanFile(context, arrayOf(new.toString()), null) { path, uri ->
                if (uri == null && path == new.toString()) {
                    Log.e(TAG, "failed to scan renamed playlist $path")
                }
            }
            if (!hasScopedStorageV2())
                throw IOException("deletion of old file failed, both old and new files exist")
            throw DeleteFailedPleaseTryDeleteRequestException(MediaStore.createDeleteRequest(
                context.contentResolver, listOf(new.toUriCompat())
            ))
        }
        MediaScannerConnection.scanFile(context, arrayOf(out.toString(), new.toString()), null) { path, uri ->
            if (uri == null && path == new.toString()) {
                Log.e(TAG, "failed to scan renamed playlist $path")
            }
        }
    }
    class DeleteFailedPleaseTryDeleteRequestException(val pendingIntent: PendingIntent) : Exception()

    class MediaStoreRequest {
        val startSystemDialog: IntentSender?
        val continueAction: (suspend () -> Unit)?

        constructor(startSystemDialog: IntentSender) {
            this.startSystemDialog = startSystemDialog
            this.continueAction = null
        }

        constructor(continueAction: (suspend () -> Unit)) {
            this.startSystemDialog = null
            this.continueAction = continueAction
        }
    }
}