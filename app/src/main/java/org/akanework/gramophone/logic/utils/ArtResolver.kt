package org.akanework.gramophone.logic.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.media3.common.util.Log
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.hasScopedStorageV1
import uk.akane.libphonograph.Constants
import uk.akane.libphonograph.utils.MiscUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import android.os.CancellationSignal
import android.os.OperationCanceledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import org.nift4.mediastorecompat.ThumbnailUtilsCompat

/**
 * Shared artwork resolution logic used by both the in-process Coil image loader
 * and the cross-process [org.akanework.gramophone.logic.GramophoneAlbumArtProvider].
 *
 * This avoids duplicating the cover art discovery strategy across multiple components.
 *
 * URI schemes handled:
 * - `gramophoneSongCover://<songId>/<filePath>` — per-song embedded art with MediaStore fallback
 * - `gramophoneAlbumCover://<albumId>/<folderPath>` — folder-based cover art with MediaStore fallback
 */
object ArtResolver {

    private const val TAG = "ArtResolver"

    private suspend inline fun <T> runWithCancellationSignal(block: (CancellationSignal) -> T): T {
        val signal = CancellationSignal()
        val job = currentCoroutineContext().job
        val listener = job.invokeOnCompletion { e ->
            if (e != null) signal.cancel()
        }
        try {
            return block(signal)
        } finally {
            listener.dispose()
        }
    }

    /**
     * Represents a canonical artwork source.
     */
    sealed class ArtResource {
        data class SongEmbedded(val songId: String, val path: String) : ArtResource()
        data class AlbumFolder(val folderPath: String) : ArtResource()
        data class SongMediaStore(val songId: String) : ArtResource()
        data class AlbumMediaStore(val albumId: String) : ArtResource()

        /**
         * Returns a stable cache key for this resource.
         */
        fun toCacheKey(size: Int): String = when (this) {
            is SongEmbedded -> "SongEmbedded:$songId:$size"
            is AlbumFolder -> "AlbumFolder:$folderPath:$size"
            is SongMediaStore -> "SongMediaStore:$songId:$size"
            is AlbumMediaStore -> "AlbumMediaStore:$albumId:$size"
        }
    }

    /** Authority for the ContentProvider that serves art to external processes. */
    const val PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.albumart"

    // not actually defined in API, but CTS tested
    // https://cs.android.com/android/platform/superproject/main/+/main:packages/providers/media/LocalUriMatcher.java;drc=ddf0d00b2b84b205a2ab3581df8184e756462e8d;l=182
    private const val MEDIA_ALBUM_ART = "albumart"

    /**
     * Parses a URI into an ordered list of potential [ArtResource] candidates for a given size.
     */
    fun getResolutionList(uri: Uri, size: Int): List<ArtResource> {
        val scheme = uri.scheme
        val authority = uri.authority ?: return emptyList()
        val path = uri.path ?: ""

        return when {
            scheme == "gramophoneSongCover" -> {
                val songId = authority
                val filePath = path
                val parentPath = File(filePath).parent
                val list = mutableListOf<ArtResource>()
                if (size <= 512) {
                    list.add(ArtResource.SongMediaStore(songId))
                    if (parentPath != null) {
                        list.add(ArtResource.AlbumFolder(parentPath))
                    }
                    list.add(ArtResource.SongEmbedded(songId, filePath))
                } else {
                    list.add(ArtResource.SongEmbedded(songId, filePath))
                    if (parentPath != null) {
                        list.add(ArtResource.AlbumFolder(parentPath))
                    }
                    list.add(ArtResource.SongMediaStore(songId))
                }
                list
            }
            scheme == "gramophoneAlbumCover" -> {
                val albumId = authority
                val folderPath = path
                if (size <= 512) {
                    listOf(
                        ArtResource.AlbumMediaStore(albumId),
                        ArtResource.AlbumFolder(folderPath)
                    )
                } else {
                    listOf(
                        ArtResource.AlbumFolder(folderPath),
                        ArtResource.AlbumMediaStore(albumId)
                    )
                }
            }
            scheme == ContentResolver.SCHEME_CONTENT && authority == PROVIDER_AUTHORITY -> {
                val segments = uri.pathSegments
                if (segments.size < 3) return emptyList()
                val type = segments[0]
                val id = segments[1]
                val pathSegment = segments[2]
                val realPath = try {
                    String(android.util.Base64.decode(pathSegment, android.util.Base64.URL_SAFE))
                } catch (e: Exception) { "" }

                if (type == "song") {
                    val parentPath = File(realPath).parent
                    val list = mutableListOf<ArtResource>()
                    if (size <= 512) {
                        list.add(ArtResource.SongMediaStore(id))
                        if (parentPath != null) {
                            list.add(ArtResource.AlbumFolder(parentPath))
                        }
                        list.add(ArtResource.SongEmbedded(id, realPath))
                    } else {
                        list.add(ArtResource.SongEmbedded(id, realPath))
                        if (parentPath != null) {
                            list.add(ArtResource.AlbumFolder(parentPath))
                        }
                        list.add(ArtResource.SongMediaStore(id))
                    }
                    list
                } else if (type == "album") {
                    if (size <= 512) {
                        listOf(
                            ArtResource.AlbumMediaStore(id),
                            ArtResource.AlbumFolder(realPath)
                        )
                    } else {
                        listOf(
                            ArtResource.AlbumFolder(realPath),
                            ArtResource.AlbumMediaStore(id)
                        )
                    }
                } else emptyList()
            }
            else -> emptyList()
        }
    }

    /**
     * Represents a canonical artwork source and its metadata.
     */
    data class ArtStream(val stream: InputStream, val mimeType: String)

    /**
     * Opens an [ArtStream] for the specific [ArtResource] and size.
     */
    suspend fun openResourceStream(context: Context, resource: ArtResource, size: Int): ArtStream? {
        return try {
            when (resource) {
                is ArtResource.SongEmbedded -> {
                    val bmp = extractSongThumbnail(File(resource.path), size, size)
                    if (bmp != null) {
                        val stream = java.io.ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                        bmp.recycle()
                        ArtStream(java.io.ByteArrayInputStream(stream.toByteArray()), "image/jpeg")
                    } else null
                }
                is ArtResource.AlbumFolder -> {
                    val cover = MiscUtils.findBestCover(File(resource.folderPath))
                    if (cover != null) {
                        val bmp = decodeAndResize(cover, size)
                        if (bmp != null) {
                            val stream = java.io.ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                            bmp.recycle()
                            ArtStream(java.io.ByteArrayInputStream(stream.toByteArray()), "image/jpeg")
                        } else {
                            // Should not happen if decodeAndResize is correct
                            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(cover.extension) ?: "image/jpeg"
                            ArtStream(cover.inputStream(), mimeType)
                        }
                    } else null
                }
                is ArtResource.SongMediaStore -> {
                    val songId = resource.songId.toLong()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        runWithCancellationSignal { signal ->
                            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
                            val bmp = context.contentResolver.loadThumbnail(uri,
                                Size(size, size), signal)
                            val stream = java.io.ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                            bmp.recycle()
                            ArtStream(java.io.ByteArrayInputStream(stream.toByteArray()), "image/jpeg")
                        }
                    } else {
                        val mediaStoreUri = buildSongAlbumArtUri(songId)
                        val stream = context.contentResolver.openInputStream(mediaStoreUri)
                        val mimeType = context.contentResolver.getType(mediaStoreUri) ?: "image/jpeg"
                        if (stream != null) ArtStream(stream, mimeType) else null
                    }
                }
                is ArtResource.AlbumMediaStore -> {
                    val albumId = resource.albumId.toLong()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        runWithCancellationSignal { signal ->
                            val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
                            val bmp = context.contentResolver.loadThumbnail(uri, android.util.Size(size, size), signal)
                            val stream = java.io.ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                            bmp.recycle()
                            ArtStream(java.io.ByteArrayInputStream(stream.toByteArray()), "image/jpeg")
                        }
                    } else {
                        val mediaStoreUri = buildAlbumCoverUri(albumId)
                        val stream = context.contentResolver.openInputStream(mediaStoreUri)
                        val mimeType = context.contentResolver.getType(mediaStoreUri) ?: "image/jpeg"
                        if (stream != null) ArtStream(stream, mimeType) else null
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is android.os.OperationCanceledException) throw CancellationException("Cancelled by signal", e)
            Log.d(TAG, "Failed to open resource stream for $resource", e)
            null
        }
    }

    /**
     * Safely decodes and resizes an image file if it is larger than the specified maxSize.
     * Always returns a Bitmap if decoding succeeds, to allow for normalization.
     */
    fun decodeAndResize(file: File, maxSize: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var inSampleSize = 1
            if (options.outHeight > maxSize || options.outWidth > maxSize) {
                val halfHeight: Int = options.outHeight / 2
                val halfWidth: Int = options.outWidth / 2
                while (halfHeight / inSampleSize >= maxSize && halfWidth / inSampleSize >= maxSize) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val sampledBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            if (sampledBitmap.width > maxSize || sampledBitmap.height > maxSize) {
                val scale = maxSize.toFloat() / Math.max(sampledBitmap.width, sampledBitmap.height)
                val width = (sampledBitmap.width * scale).toInt()
                val height = (sampledBitmap.height * scale).toInt()
                val resized = Bitmap.createScaledBitmap(sampledBitmap, width, height, true)
                if (resized != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                resized
            } else {
                sampledBitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode/resize ${file.path}", e)
            null
        }
    }

    /**
     * Builds a `content://` URI pointing to [GramophoneAlbumArtProvider] for the given
     * internal artwork URI scheme. This URI is safe to hand to external processes
     * (e.g. Android Auto) which cannot resolve our custom schemes.
     *
     * @param type "song" or "album"
     * @param id   the song or album ID as a string
     * @param path the file/folder path (will be URI-encoded)
     */
    fun buildProviderUri(type: String, id: String, path: String): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(PROVIDER_AUTHORITY)
            .appendPath(type)
            .appendPath(id)
            .appendPath(android.util.Base64.encodeToString(path.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
            .build()

    /**
     * Converts a `gramophoneSongCover://` or `gramophoneAlbumCover://` URI into a
     * `content://` URI backed by the album art provider, suitable for cross-process use.
     *
     * Returns `null` if the URI is not one of the custom schemes.
     */
    fun toProviderUri(uri: Uri): Uri? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == PROVIDER_AUTHORITY) {
            return uri
        }
        return when (uri.scheme) {
            "gramophoneSongCover" -> buildProviderUri(
                "song",
                uri.authority ?: "0",
                uri.path?.removePrefix("/") ?: ""
            )
            "gramophoneAlbumCover" -> buildProviderUri(
                "album",
                uri.authority ?: "0",
                uri.path?.removePrefix("/") ?: ""
            )
            else -> null
        }
    }

    /**
     * Attempts to extract embedded artwork from a song file, or if none is present, a
     * `albumart.jpg` file or similar, via [ThumbnailUtilsCompat].
     *
     * @param file   the audio file
     * @param width  desired thumbnail width
     * @param height desired thumbnail height
     * @return the extracted bitmap, or `null` if extraction failed or is unavailable
     */
    suspend fun extractSongThumbnail(file: File, width: Int, height: Int): Bitmap? {
        return try {
            runWithCancellationSignal { signal ->
                ThumbnailUtilsCompat.createAudioThumbnail(file, Size(width, height), signal)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is OperationCanceledException) throw CancellationException("Cancelled by signal", e)
            if (e.message != "No embedded album art found" &&
                e.message != "No thumbnails in Downloads directories" &&
                e.message != "No thumbnails in top-level directories" &&
                e.message != "No album art found"
            ) {
                Log.w(TAG, "Unexpected Exception extracting song thumbnail", e)
            }
            null
        }
    }

    /**
     * Returns a MediaStore URI for the song's album art (the `albumart` pseudo-path).
     */
    fun buildSongAlbumArtUri(songId: Long): Uri =
        ContentUris.appendId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon(), songId
        ).appendPath(MEDIA_ALBUM_ART).build()

    /**
     * Returns a MediaStore URI for the album's cover art.
     */
    fun buildAlbumCoverUri(albumId: Long): Uri =
        ContentUris.withAppendedId(Constants.baseAlbumCoverUri, albumId)
}