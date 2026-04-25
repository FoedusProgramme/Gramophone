package org.akanework.gramophone.logic.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Size
import androidx.media3.common.util.Log
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.hasScopedStorageV1
import uk.akane.libphonograph.Constants
import uk.akane.libphonograph.utils.MiscUtils
import java.io.File
import java.io.IOException
import java.io.InputStream

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
object GramophoneArtResolver {

    private const val TAG = "GramophoneArtResolver"

    /** Authority for the ContentProvider that serves art to external processes. */
    const val PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.albumart"

    // not actually defined in API, but CTS tested
    // https://cs.android.com/android/platform/superproject/main/+/main:packages/providers/MediaProvider/src/com/android/providers/media/LocalUriMatcher.java;drc=ddf0d00b2b84b205a2ab3581df8184e756462e8d;l=182
    private const val MEDIA_ALBUM_ART = "albumart"

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
            .appendPath(Uri.encode(path))
            .build()

    /**
     * Converts a `gramophoneSongCover://` or `gramophoneAlbumCover://` URI into a
     * `content://` URI backed by the album art provider, suitable for cross-process use.
     *
     * Returns `null` if the URI is not one of the custom schemes.
     */
    fun toProviderUri(uri: Uri): Uri? {
        return when (uri.scheme) {
            "gramophoneSongCover" -> buildProviderUri(
                "song",
                uri.authority ?: "0",
                uri.path ?: ""
            )
            "gramophoneAlbumCover" -> buildProviderUri(
                "album",
                uri.authority ?: "0",
                uri.path ?: ""
            )
            else -> null
        }
    }

    /**
     * Attempts to extract embedded artwork from a song file via [ThumbnailUtils].
     * Only available on Android Q+.
     *
     * @param file   the audio file
     * @param width  desired thumbnail width (use 0 for default)
     * @param height desired thumbnail height (use 0 for default)
     * @return the extracted bitmap, or `null` if extraction failed or is unavailable
     */
    fun extractSongThumbnail(file: File, width: Int = 512, height: Int = 512): Bitmap? {
        if (!hasScopedStorageV1()) return null // ThumbnailUtils.createAudioThumbnail requires Q+
        return try {
            ThumbnailUtils.createAudioThumbnail(file, Size(width, height), null)
        } catch (e: IOException) {
            if (e.message != "No embedded album art found" &&
                e.message != "No thumbnails in Downloads directories" &&
                e.message != "No thumbnails in top-level directories" &&
                e.message != "No album art found"
            ) {
                Log.w(TAG, "Unexpected IOException extracting song thumbnail", e)
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

    /**
     * Opens an [InputStream] for the song's artwork, trying embedded art first
     * then falling back to the MediaStore album art URI.
     *
     * @return an [InputStream] for the artwork, or `null` if no artwork is available
     */
    fun openSongArtwork(context: Context, songId: String, filePath: String): InputStream? {
        val file = File(filePath)

        // Try extracting embedded thumbnail and writing to a temp bitmap stream
        val bmp = extractSongThumbnail(file)
        if (bmp != null) {
            val stream = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            bmp.recycle()
            return java.io.ByteArrayInputStream(stream.toByteArray())
        }

        // Fallback to MediaStore album art
        val mediaStoreUri = buildSongAlbumArtUri(songId.toLong())
        return try {
            context.contentResolver.openInputStream(mediaStoreUri)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to open MediaStore song art for id=$songId", e)
            null
        }
    }

    /**
     * Opens an [InputStream] for the album's artwork, trying folder-based cover art first
     * (via [MiscUtils.findBestCover]) then falling back to the MediaStore album cover URI.
     *
     * @return an [InputStream] for the artwork, or `null` if no artwork is available
     */
    fun openAlbumArtwork(context: Context, albumId: String, folderPath: String): InputStream? {
        // Try folder-based cover art (cover.jpg, albumart.png, etc.)
        val cover = MiscUtils.findBestCover(File(folderPath))
        if (cover != null) {
            return try {
                cover.inputStream()
            } catch (e: Exception) {
                Log.d(TAG, "Failed to open folder cover at ${cover.path}", e)
                null
            }
        }

        // Fallback to MediaStore album cover
        val mediaStoreUri = buildAlbumCoverUri(albumId.toLong())
        return try {
            context.contentResolver.openInputStream(mediaStoreUri)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to open MediaStore album art for id=$albumId", e)
            null
        }
    }
}
