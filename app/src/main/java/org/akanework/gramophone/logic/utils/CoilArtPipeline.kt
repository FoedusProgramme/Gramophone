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
import org.nift4.mediastorecompat.MediaStoreCompat
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.min


object CoilArtPipeline {

    private fun getSmallSize(context: Context): Point {
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

    data class AlbumThumbnailData(val songUri: android.net.Uri, val imageFileName: String)

    class AlbumThumbnailMapper : Mapper<Uri, AlbumThumbnailData> {
        override fun map(data: Uri, options: Options): AlbumThumbnailData? {
            return if (data.scheme == ContentResolver.SCHEME_CONTENT &&
                data.authority == GramophoneAlbumArtProvider.PROVIDER_AUTHORITY) {
                if (data.pathSegments.first() != "album")
                    throw IllegalArgumentException("Invalid uri: $data")
                AlbumThumbnailData(ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    data.pathSegments[1].toLong()), data.pathSegments[2])
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
                val songFile = getFileFor(options.context, data.songUri)
                val imgFile = songFile.resolveSibling(data.imageFileName)
                if (imgFile.name != data.imageFileName) // if imageFileName contains ../other/path/
                    throw IllegalArgumentException("Bad data $data")
                val imgUri = MediaStoreCompat.getMediaUriForFile(options.context,
                    imgFile.absolutePath)
                val data = if (isSmallSize(options.context, options.size) && false) // TODO(ASAP)
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
                data.pathSegments.first() == "song" && false && // TODO(ASAP)
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

    data class LoadAudioCoverData(val id: Long)

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
                LoadAudioCoverData(data.pathSegments[1].toLong())
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
                if (hasScopedStorageWithMediaTypes() && !options.context.hasImagePermission()) {
                    return@Fetcher continueFetchingOrFail(LoadThumbnailData(uri),
                        options, imageLoader)
                }
                // We shouldn't trust the uri wrt path of song, otherwise this provider could be
                // misused to get image files from any folder. So do a query here
                val file = getFileFor(options.context, uri)
                // Only poke around for files on external storage
                if (Environment.MEDIA_UNKNOWN ==
                    Environment.getExternalStorageState(file)) {
                    throw NoAlbumArtException("No embedded album art found")
                }

                // Ignore "Downloads" or top-level directories
                val parent = file.parentFile
                val grandParent = parent?.parentFile
                if (parent != null && parent.getName() == Environment.DIRECTORY_DOWNLOADS) {
                    throw NoAlbumArtException("No thumbnails in Downloads directories")
                }
                if (grandParent != null && Environment.MEDIA_UNKNOWN ==
                    Environment.getExternalStorageState(grandParent)) {
                    throw NoAlbumArtException("No thumbnails in top-level directories")
                }

                // If no embedded image found, look around for best standalone file
                val found = parent!!.listFiles { _, name ->
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
                            "albumart") && lower.endsWith(".jpg")) 3
                    else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && lower.startsWith(
                            "albumart") && lower.endsWith("large.jpg")) 3
                    else if (lower.contains("albumart") && lower.endsWith(".jpg")) 2
                    else if (lower.endsWith(".jpg")) 1
                    else 0
                })
                val imgUri = MediaStoreCompat.getMediaUriForFile(options.context,
                    bestFile.absolutePath)
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

    // TODO(ASAP) check if someone answered about how to get rid of internal dep
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