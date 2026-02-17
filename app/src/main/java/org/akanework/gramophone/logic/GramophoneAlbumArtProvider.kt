package org.akanework.gramophone.logic

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Size
import uk.akane.libphonograph.Constants
import uk.akane.libphonograph.utils.MiscUtils
import java.io.File
import java.io.IOException

class GramophoneAlbumArtProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        val segments = uri.pathSegments

        if (segments.size < 3) return null

        val type = segments[0] // song or album
        val id = segments[1]
        val encodedPath = segments[2]
        val realPath = Uri.decode(encodedPath)

        val cacheFile = File(context.cacheDir, uri.toString().hashCode().toString())

        if (!cacheFile.exists()) {
            when (type) {
                "song" -> {
                    generateSongCover(context, realPath, id, cacheFile)
                }
                "album" -> {
                    generateAlbumCover(context, realPath, id, cacheFile)
                }
            }
        }

        if (!cacheFile.exists()) return null

        return ParcelFileDescriptor.open(
            cacheFile,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
    }

    private fun generateSongCover(
        context: Context,
        path: String,
        id: String,
        outFile: File
    ) {
        val file = File(path)

        val bmp = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createAudioThumbnail(
                    file,
                    Size(512, 512),
                    null
                )
            } else null
        } catch (e: IOException) {
            null
        }

        if (bmp != null) {
            saveBitmap(outFile, bmp)
            return
        }

        // fallback MediaStore album art
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            id.toLong()
        ).buildUpon().appendPath("albumart").build()

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun generateAlbumCover(
        context: Context,
        path: String,
        id: String,
        outFile: File
    ) {
        val cover = MiscUtils.findBestCover(File(path))

        if (cover != null) {
            cover.inputStream().use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        val uri = ContentUris.withAppendedId(
            Constants.baseAlbumCoverUri,
            id.toLong()
        )

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun saveBitmap(file: File, bmp: Bitmap) {
        file.outputStream().use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }
    }
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = "image/jpeg"

    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
