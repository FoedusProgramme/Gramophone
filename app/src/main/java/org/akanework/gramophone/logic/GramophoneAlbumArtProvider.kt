package org.akanework.gramophone.logic

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.util.Log
import org.akanework.gramophone.logic.utils.GramophoneArtResolver
import java.io.File
import java.security.MessageDigest

/**
 * ContentProvider that serves album artwork to external processes (e.g. Android Auto).
 *
 * External processes cannot resolve Gramophone's internal URI schemes
 * (`gramophoneSongCover://`, `gramophoneAlbumCover://`). This provider acts as a bridge,
 * using the shared [GramophoneArtResolver] to locate and serve the artwork over a
 * standard `content://` URI.
 *
 * URI format: `content://org.akanework.gramophone.albumart/{type}/{id}/{encodedPath}`
 * where `type` is "song" or "album".
 *
 * The provider writes artwork to a temporary cache file and returns a read-only
 * [ParcelFileDescriptor] for it. Cache files are keyed by a hash of the URI to
 * avoid redundant work on repeated requests.
 */
class GramophoneAlbumArtProvider : ContentProvider() {

    private val TAG = "GramoArtProvider"

    companion object {
        private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024L // 50MB
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        val segments = uri.pathSegments

        if (segments.size < 3) {
            Log.w(TAG, "Invalid URI format, expected 3 path segments: $uri")
            return null
        }

        val type = segments[0]       // "song" or "album"
        val id = segments[1]         // songId or albumId
        val encodedPath = segments[2]
        val realPath = Uri.decode(encodedPath)

        val cacheDir = File(context.cacheDir, "albumart")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        // Use SHA-256 as cache key to avoid collisions and re-extracting on repeated requests
        val cacheFileName = sha256(uri.toString())
        val cacheFile = File(cacheDir, "art_$cacheFileName")

        if (!cacheFile.exists()) {
            trimCacheIfNeeded(cacheDir)

            // Write to temp file first to avoid partial reads during write
            // Use random suffix to avoid collisions when multiple threads cache same artwork
            val randomSuffix = System.nanoTime().toString(36) // Unique per-request suffix
            val tempFile = File(cacheDir, "art_${cacheFileName}_${randomSuffix}_tmp")

            val inputStream = when (type) {
                "song" -> GramophoneArtResolver.openSongArtwork(context, id, realPath)
                "album" -> GramophoneArtResolver.openAlbumArtwork(context, id, realPath)
                else -> {
                    Log.w(TAG, "Unknown artwork type: $type")
                    null
                }
            }

            if (inputStream != null) {
                try {
                    inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                            output.flush() // Ensure data is written to buffer
                            output.fd.sync() // Force sync to disk
                        }
                    }
                    // Atomically rename temp file to final cache file
                    if (!tempFile.renameTo(cacheFile)) {
                        Log.w(TAG, "Failed to rename temp artwork cache file: $tempFile")
                        tempFile.delete()
                        return null
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write artwork cache for $uri", e)
                    tempFile.delete()
                    cacheFile.delete()
                    return null
                }
            }
        } else {
            // Update last modified to keep it fresh in LRU
            cacheFile.setLastModified(System.currentTimeMillis())
        }

        if (!cacheFile.exists()) return null

        return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun sha256(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to hashCode if SHA-256 fails (should not happen)
            input.hashCode().toString()
        }
    }

    private fun trimCacheIfNeeded(cacheDir: File) {
        try {
            val files = cacheDir.listFiles() ?: return

            // Clean up stale temp files (older than 1 hour)
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
            for (file in files) {
                if (file.name.endsWith("_tmp") && file.lastModified() < oneHourAgo) {
                    file.delete()
                }
            }

            var currentSize = files.sumOf { it.length() }
            if (currentSize <= MAX_CACHE_SIZE_BYTES) return

            // Sort by last modified (oldest first)
            val sortedFiles = files.sortedBy { it.lastModified() }
            for (file in sortedFiles) {
                val fileSize = file.length()
                if (file.delete()) {
                    currentSize -= fileSize
                }
                if (currentSize <= MAX_CACHE_SIZE_BYTES * 0.8) break // Trim to 80% to avoid immediate re-trim
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trim cache", e)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

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
