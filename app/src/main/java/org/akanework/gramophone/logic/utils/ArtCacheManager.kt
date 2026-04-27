package org.akanework.gramophone.logic.utils

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.util.Log
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Unified entry point for album art caching and materialization.
 */
object ArtCacheManager {

    private const val TAG = "ArtCacheManager"
    private const val MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024L // 50MB

    private fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "albumart")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Opens an [InputStream] for the given URI, using the cache if available.
     */
    fun openInputStream(context: Context, uri: Uri, size: Int = 1024): InputStream? {
        return getArt(context, uri, size)?.file?.inputStream()
    }

    /**
     * Opens a [ParcelFileDescriptor] for the given URI, using the cache if available.
     */
    fun openFileDescriptor(context: Context, uri: Uri, size: Int = 1024): ParcelFileDescriptor? {
        val file = getArt(context, uri, size)?.file ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /**
     * Represents a cached artwork and its MIME type.
     */
    data class ArtResult(val file: File, val mimeType: String)

    /**
     * Retrieves the cached artwork for the given URI, materializing it if necessary.
     */
    fun getArt(context: Context, uri: Uri, size: Int = 1024): ArtResult? {
        val candidates = ArtResolver.getResolutionList(uri, size)
        for (candidate in candidates) {
            val result = getOrMaterialize(context, candidate, size)
            if (result != null) return result
        }
        return null
    }

    private fun getOrMaterialize(context: Context, resource: ArtResolver.ArtResource, size: Int): ArtResult? {
        val cacheDir = getCacheDir(context)
        val key = sha256(resource.toCacheKey(size))
        val cacheFile = File(cacheDir, "art_$key")
        val mimeFile = File(cacheDir, "art_$key.mime")

        if (cacheFile.exists()) {
            cacheFile.setLastModified(System.currentTimeMillis())
            val mimeType = if (mimeFile.exists()) mimeFile.readText() else "image/jpeg"
            return ArtResult(cacheFile, mimeType)
        }

        // Materialize
        val artStream = ArtResolver.openResourceStream(context, resource, size) ?: return null
        
        trimCacheIfNeeded(cacheDir)

        val randomSuffix = System.nanoTime().toString(36)
        val tempFile = File(cacheDir, "art_${key}_${randomSuffix}_tmp")

        return try {
            artStream.stream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            if (tempFile.renameTo(cacheFile)) {
                mimeFile.writeText(artStream.mimeType)
                ArtResult(cacheFile, artStream.mimeType)
            } else {
                Log.w(TAG, "Failed to rename temp file to $cacheFile")
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to materialize artwork for $resource", e)
            tempFile.delete()
            null
        }
    }

    private fun sha256(input: String): String {
        return try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }

    private fun trimCacheIfNeeded(cacheDir: File) {
        try {
            val files = cacheDir.listFiles() ?: return

            // Clean up stale temp files
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
            for (file in files) {
                if (file.name.endsWith("_tmp") && file.lastModified() < oneHourAgo) {
                    file.delete()
                }
            }

            var currentSize = files.sumOf { it.length() }
            if (currentSize <= MAX_CACHE_SIZE_BYTES) return

            val sortedFiles = files.sortedBy { it.lastModified() }
            for (file in sortedFiles) {
                val fileSize = file.length()
                if (file.delete()) {
                    currentSize -= fileSize
                }
                if (currentSize <= MAX_CACHE_SIZE_BYTES * 0.8) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trim cache", e)
        }
    }

    /**
     * Clears the entire artwork cache.
     */
    fun clearCache(context: Context) {
        try {
            val cacheDir = getCacheDir(context)
            val files = cacheDir.listFiles() ?: return
            for (file in files) {
                file.delete()
            }
            Log.i(TAG, "Artwork cache cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear artwork cache", e)
        }
    }
}
