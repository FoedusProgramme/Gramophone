/*
 *     Copyright (C) 2026 The Gramophone authors
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.OperationCanceledException
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.ContentMetadata
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.pathSegments
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Size
import coil3.toAndroidUri
import coil3.toCoilUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import okio.Buffer
import okio.buffer
import okio.source
import org.akanework.gramophone.logic.GramophoneAlbumArtProvider
import org.akanework.gramophone.logic.hasImagePermission
import org.akanework.gramophone.logic.hasScopedStorageV1
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.queryWithPending
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.utils.MiscUtils
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.min


object CoilArtPipeline {

    fun getSmallSize(context: Context): Point {
        if (hasScopedStorageV1()) {
            // refer to mThumbSize in MediaProvider.java
            val metrics = context.applicationContext.resources.displayMetrics
            val thumbSize = min(metrics.widthPixels, metrics.heightPixels) / 2
            return Point(thumbSize, thumbSize)
        }
        return Point(512, 320)
    }

    private fun isSmallSize(context: Context, size: Size): Boolean {
        if (size.width !is Dimension.Pixels || size.height !is Dimension.Pixels) return false
        val w = (size.width as Dimension.Pixels).px
        val h = (size.height as Dimension.Pixels).px
        val smallSize = getSmallSize(context)
        return w <= smallSize.x && h <= smallSize.y
    }

    data class AlbumThumbnailData(val songUri: android.net.Uri, val songFile: File?)

    class AlbumThumbnailMapper : Mapper<Uri, AlbumThumbnailData> {
        override fun map(data: Uri, options: Options): AlbumThumbnailData? {
            return if (data.scheme == ContentResolver.SCHEME_CONTENT &&
                data.authority == GramophoneAlbumArtProvider.PROVIDER_AUTHORITY) {
                if (data.pathSegments.first() != "album")
                    throw IllegalArgumentException("Invalid uri: $data")
                AlbumThumbnailData(ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    data.pathSegments[1].toLong()),
                    // Security: query parameters are removed before album art provider uses Uri
                    data.toAndroidUri().getQueryParameter("songFile")
                        ?.let { File(it) })
            } else null
        }
    }

    class AlbumThumbnailKeyer : Keyer<AlbumThumbnailData> {
        override fun key(data: AlbumThumbnailData, options: Options): String {
            return data.toString()
        }
    }

    class AlbumThumbnailFetcherFactory : Fetcher.Factory<AlbumThumbnailData> {
        override fun create(
            data: AlbumThumbnailData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return Fetcher {
                val songFile = data.songFile ?: getFileFor(options.context,
                    data.songUri)
                val imgUri = if (!Flags.MEDIASTORE_IO) {
                    val imgFile = MiscUtils.findBestCover(songFile.parentFile!!)
                        ?: return@Fetcher continueFetchingOrFail(
                            LoadAudioCoverData(
                                ContentUris.parseId(data.songUri), songFile
                            ), options, imageLoader
                        )
                    MediaStoreCompat.getMediaUriForFile(options.context, imgFile.absolutePath)
                } else {
                    val sortOrder = """(
                        CASE LOWER(${MediaStore.Images.Media.DISPLAY_NAME})
                            WHEN 'albumart.jpg' THEN 28
                            WHEN 'albumart.png' THEN 27
                            WHEN 'albumart.jpeg' THEN 26
                    
                            WHEN 'cover.jpg' THEN 24
                            WHEN 'cover.png' THEN 23
                            WHEN 'cover.jpeg' THEN 22
                    
                            ELSE
                                (
                                    1
                                    + CASE
                                        WHEN LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE '%.jpg' THEN 3
                                        WHEN LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE '%.png' THEN 2
                                        WHEN LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE '%.jpeg' THEN 1
                                        ELSE 0
                                      END
                                    + CASE
                                        WHEN LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) GLOB 'albumart*.*' THEN 16
                                        WHEN LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) GLOB 'cover*.*' THEN 12
                                        WHEN LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE '%albumart%' THEN 8
                                        WHEN LOWER(${MediaStore.Images.Media.DISPLAY_NAME}) LIKE '%cover%' THEN 4
                                        ELSE 0
                                      END
                                )
                        END
                    ) DESC""".trimIndent()
                    val escaped = songFile.parent!!.replace("\\",
                        "\\\\").replace("%", "\\%")
                        .replace("_", "\\_")
                    options.context.contentResolver.queryWithPending(MediaStore.Images.Media
                        .EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.ImageColumns._ID),
                        "${MediaStore.Images.ImageColumns.DATA} LIKE ? ESCAPE '\\' AND" +
                                " ${MediaStore.Images.ImageColumns.DATA} NOT LIKE ? ESCAPE '\\'",
                        arrayOf("$escaped/%", "$escaped/%/%"), sortOrder, limit = 1).use {
                        if (it == null || !it.moveToFirst()) {
                            return@Fetcher continueFetchingOrFail(
                                LoadAudioCoverData(
                                    ContentUris.parseId(data.songUri), songFile
                                ), options, imageLoader
                            )
                        }
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            it.getLong(it.getColumnIndexOrThrow(
                                MediaStore.Images.ImageColumns._ID)))
                    }
                }
                val data = if (isSmallSize(options.context, options.size))
                    LoadThumbnailData(imgUri)
                else
                    imgUri
                return@Fetcher continueFetchingOrFail(data, options, imageLoader)
            }
        }
    }

    private suspend fun continueFetchingOrFail(data: Any, options: Options,
                                               imageLoader: ImageLoader): FetchResult {
        val fetchResult: FetchResult
        var searchIndex = 0
        val data = imageLoader.components.map(data, options)
        while (true) {
            val pair = imageLoader.components.newFetcher(data, options, imageLoader,
                searchIndex)
            checkNotNull(pair) { "Unable to create a fetcher that supports: $data" }
            val fetcher = pair.first
            searchIndex = pair.second + 1

            val result = fetcher.fetch()

            if (result != null) {
                fetchResult = result
                break
            }
        }
        return fetchResult
    }

    data class LoadThumbnailData(val uri: android.net.Uri)

    class ThumbnailMapper : Mapper<Uri, LoadThumbnailData> {
        override fun map(data: Uri, options: Options): LoadThumbnailData? {
            return if (data.scheme == ContentResolver.SCHEME_CONTENT &&
                data.authority == GramophoneAlbumArtProvider.PROVIDER_AUTHORITY &&
                data.pathSegments.first() == "song" &&
                isSmallSize(options.context, options.size)) {
                LoadThumbnailData(ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    data.pathSegments[1].toLong()))
            } else null
        }
    }

    class ThumbnailKeyer : Keyer<LoadThumbnailData> {
        override fun key(data: LoadThumbnailData, options: Options): String {
            return data.toString()
        }
    }

    class ThumbnailFetcherFactory : Fetcher.Factory<LoadThumbnailData> {
        override fun create(
            data: LoadThumbnailData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return Fetcher {
                val afd = MediaStoreCompat.openTypedAssetFileDescriptor(options.context,
                    data.uri, "image/jpeg", Bundle().apply {
                        val height = options.size.height.let {
                            if (it is Dimension.Pixels) it.px else null
                        }
                        val width = options.size.width.let {
                            if (it is Dimension.Pixels) it.px else null
                        }
                        // Size is REQUIRED to get an image!
                        if (height != null && width != null)
                            putParcelable(ContentResolver.EXTRA_SIZE, Point(width, height))
                        else
                            putParcelable(ContentResolver.EXTRA_SIZE, getSmallSize(options.context))
                    })
                checkNotNull(afd) { "Unable to open '${data.uri}' as thumbnail." }

                return@Fetcher SourceFetchResult(
                    source = ImageSource(
                        source = afd.createInputStream().source().buffer(),
                        fileSystem = options.fileSystem,
                        // TODO: https://github.com/coil-kt/coil/pull/3485
                        metadata = ContentMetadata(data.uri.toCoilUri(),
                            afd),
                    ),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.DISK,
                )
            }
        }
    }

    data class LoadAudioCoverData(val id: Long, val songFile: File?)

    class AudioCoverKeyer : Keyer<LoadAudioCoverData> {
        override fun key(data: LoadAudioCoverData, options: Options): String {
            return data.toString()
        }
    }

    class AudioCoverMapper : Mapper<Uri, LoadAudioCoverData> {
        override fun map(data: Uri, options: Options): LoadAudioCoverData? {
            return if (data.scheme == ContentResolver.SCHEME_CONTENT &&
                data.authority == GramophoneAlbumArtProvider.PROVIDER_AUTHORITY &&
                data.pathSegments.first() == "song") {
                LoadAudioCoverData(data.pathSegments[1].toLong(),
                    // Security: query parameters are removed before album art provider uses Uri
                    data.toAndroidUri().getQueryParameter("songFile")
                        ?.let { File(it) })
            } else null
        }
    }

    class SongCoverFetcherFactory : Fetcher.Factory<LoadAudioCoverData> {
        override fun create(
            data: LoadAudioCoverData,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher {
            return Fetcher {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media
                    .EXTERNAL_CONTENT_URI, data.id)
                MediaStoreCompat.openAssetFileDescriptor(options.context,
                    uri, "r")!!.use { afd ->
                    val retriever = MediaMetadataRetriever()
                    try {
                        if (afd.declaredLength == AssetFileDescriptor.UNKNOWN_LENGTH &&
                            afd.startOffset == 0L
                        )
                            retriever.setDataSource(afd.fileDescriptor)
                        else
                            retriever.setDataSource(
                                afd.fileDescriptor, afd.startOffset,
                                afd.length
                            )
                        retriever.embeddedPicture?.let { raw ->
                            return@Fetcher SourceFetchResult(
                                source = ImageSource(
                                    Buffer().write(raw),
                                    options.fileSystem,
                                    metadata = null,
                                ),
                                mimeType = null,
                                dataSource = DataSource.DISK,
                            )
                        }
                    } catch (e: RuntimeException) {
                        throw IOException("Failed to create thumbnail", e)
                    } finally {
                        try {
                            retriever.close()
                        } catch (_: Exception) {
                        }
                    }
                }
                if (hasScopedStorageWithMediaTypes() && (Flags.REMOVE_IMAGE_PERMISSION ||
                            !options.context.hasImagePermission())) {
                    return@Fetcher continueFetchingOrFail(LoadThumbnailData(uri),
                        options, imageLoader)
                }
                // We shouldn't trust the uri wrt path of song, otherwise this provider could be
                // misused to get image files from any folder. So do a query here
                // (Note: data.songFile is only set for trusted data!)
                val file = data.songFile ?: getFileFor(options.context, uri)
                // Only poke around for files on external storage
                if (Environment.MEDIA_UNKNOWN ==
                    Environment.getExternalStorageState(file)) {
                    throw NoAlbumArtException("No embedded album art found")
                }

                // Ignore "Downloads" or top-level directories
                val parent = file.parentFile ?: throw NoAlbumArtException("Invalid file $file")
                if (parent.name == Environment.DIRECTORY_DOWNLOADS) {
                    throw NoAlbumArtException("No thumbnails in Downloads directories")
                }
                val grandParent = parent.parentFile
                if (grandParent != null && Environment.MEDIA_UNKNOWN ==
                    Environment.getExternalStorageState(grandParent)) {
                    throw NoAlbumArtException("No thumbnails in top-level directories")
                }

                val imgUri = if (!Flags.MEDIASTORE_IO) {
                    // If no embedded image found, look around for best standalone file
                    val found = parent.listFiles { _, name ->
                        val lower = name!!.lowercase(Locale.getDefault())
                        (lower.endsWith(".jpg") || lower.endsWith(".png"))
                    }

                    if (found.isNullOrEmpty()) {
                        throw NoAlbumArtException("No album art found")
                    }
                    val bestFile = found.maxWith(compareBy {
                        val lower = it.name.lowercase(Locale.getDefault())
                        if (lower == "albumart.jpg") 4
                        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && lower.startsWith(
                                "albumart"
                            ) && lower.endsWith(".jpg")
                        ) 3
                        else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && lower.startsWith(
                                "albumart"
                            ) && lower.endsWith("large.jpg")
                        ) 3
                        else if (lower.contains("albumart") && lower.endsWith(".jpg")) 2
                        else if (lower.endsWith(".jpg")) 1
                        else 0
                    })
                    MediaStoreCompat.getMediaUriForFile(
                        options.context,
                        bestFile.absolutePath
                    )
                } else {
                    // Ported from ThumbnailUtils/MediaProvider(<=P) java code. Should NOT be
                    // changed in order to match the choice of the song thumbnail cache logic.
                    val sortOrder = """
                    CASE
                        WHEN LOWER(${MediaStore.Images.ImageColumns.DISPLAY_NAME}) = 'albumart.jpg' THEN 4
                        WHEN LOWER(${MediaStore.Images.ImageColumns.DISPLAY_NAME}) GLOB 'albumart*""" + (if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "large" else "") + """.jpg' THEN 3
                        WHEN LOWER(${MediaStore.Images.ImageColumns.DISPLAY_NAME}) LIKE '%albumart%.jpg' THEN 2
                        WHEN LOWER(${MediaStore.Images.ImageColumns.DISPLAY_NAME}) LIKE '%.jpg' THEN 1
                        ELSE 0
                    END DESC
                    """.trimIndent()
                    val escaped = parent.path.replace("\\",
                        "\\\\").replace("%", "\\%")
                        .replace("_", "\\_")
                    options.context.contentResolver.queryWithPending(MediaStore.Images.Media
                        .EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.ImageColumns._ID),
                        "${MediaStore.Files.FileColumns.MIME_TYPE} IN ('image/jpeg'," +
                                " 'image/png') AND ${MediaStore.Images.ImageColumns.DATA} LIKE ?" +
                                " ESCAPE '\\' AND ${MediaStore.Images.ImageColumns.DATA} NOT LIKE" +
                                " ? ESCAPE '\\'", arrayOf("$escaped/%",
                            "$escaped/%/%"), sortOrder, limit = 1).use {
                        if (it == null || !it.moveToFirst()) {
                            throw NoAlbumArtException("No album art found")
                        }
                        ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            it.getLong(it.getColumnIndexOrThrow(
                                MediaStore.Images.ImageColumns._ID)))
                    }
                }
                return@Fetcher continueFetchingOrFail(imgUri,
                    options, imageLoader)
            }
        }
    }

    class NoAlbumArtException(message: String) : IOException(message)

    private suspend inline fun getFileFor(context: Context, mediaUri: android.net.Uri): File {
        return runWithCancellationSignal { signal ->
            context.contentResolver.query(mediaUri, arrayOf(
                MediaStore.MediaColumns.DATA), null, null,
                null, signal)
        }.use {
            if (it == null || !it.moveToFirst())
                throw IOException("Can't find file $mediaUri")
            File(it.getString(it.getColumnIndexOrThrow(
                MediaStore.MediaColumns.DATA)))
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend inline fun <T> runWithCancellationSignal(block: (CancellationSignal) -> T): T {
        val signal = CancellationSignal()
        val job = currentCoroutineContext().job
        val listener = job.invokeOnCompletion(onCancelling = true) { e ->
            if (e is CancellationException) signal.cancel()
        }
        try {
            return block(signal)
        } catch (e: OperationCanceledException) {
            try {
                job.ensureActive()
            } catch (e2: CancellationException) {
                e2.addSuppressed(e)
                throw e2
            }
            throw IllegalStateException("Canceled but job still active, seems to be a bug?", e)
        } finally {
            listener.dispose()
        }
    }
}
