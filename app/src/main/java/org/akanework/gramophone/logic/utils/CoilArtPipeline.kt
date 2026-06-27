package org.akanework.gramophone.logic.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.res.AssetFileDescriptor
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.ContentMetadata
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.map.Mapper
import coil3.pathSegments
import coil3.request.Options
import coil3.size.Dimension
import coil3.toCoilUri
import okio.Buffer
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.source
import org.akanework.gramophone.logic.GramophoneAlbumArtProvider
import org.nift4.mediastorecompat.MediaStoreCompat
import java.io.File
import java.io.IOException
import java.util.Locale

object CoilArtPipeline {

    data class LoadThumbnailData(val uri: android.net.Uri)

    class ThumbnailMapper : Mapper<Uri, LoadThumbnailData> {
        override fun map(data: Uri, options: Options): LoadThumbnailData? {
            // TODO(ASAP) unhardcode small threshold
            return if (options.size.width.let { it is Dimension.Pixels && it.px <= 320 } &&
                options.size.height.let { it is Dimension.Pixels && it.px <= 320 } &&
                data.scheme == ContentResolver.SCHEME_CONTENT &&
                data.authority == GramophoneAlbumArtProvider.PROVIDER_AUTHORITY) {
                when (data.pathSegments.first()) {
                    "song" -> {
                        LoadThumbnailData(ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            data.pathSegments[1].toLong()))
                    }
                    "album" -> {
                        // TODO(ASAP) make this secure
                        LoadThumbnailData(ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            data.pathSegments[1].toLong()))
                    }
                    else -> throw IllegalArgumentException("Invalid uri: $data")
                }
            } else null
        }
    }

    class ThumbnailKeyer : Keyer<LoadThumbnailData> {
        override fun key(data: LoadThumbnailData, options: Options): String {
            return data.uri.toString()
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
                        if (height != null && width != null)
                            putParcelable(ContentResolver.EXTRA_SIZE, Point(width, height))
                    })
                checkNotNull(afd) { "Unable to open '${data.uri}' as thumbnail." }

                return@Fetcher SourceFetchResult(
                    source = ImageSource(
                        source = afd.createInputStream().source().buffer(),
                        fileSystem = options.fileSystem,
                        // TODO(ASAP) https://github.com/coil-kt/coil/pull/3485
                        metadata = ContentMetadata(data.uri.toCoilUri(),
                            afd),
                    ),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.DISK,
                )
            }
        }
    }

    class AlbumCoverMapper : Mapper<Uri, Uri> {
        override fun map(data: Uri, options: Options): Uri? {
            return if (data.scheme == ContentResolver.SCHEME_CONTENT &&
                data.authority == GramophoneAlbumArtProvider.PROVIDER_AUTHORITY &&
                data.pathSegments.first() == "album") {
                // TODO(ASAP) make this secure
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    data.pathSegments[1].toLong()).toCoilUri()
            } else null
        }
    }

    data class LoadAudioCoverData(val id: Long, val file: File)

    class AudioCoverKeyer : Keyer<LoadAudioCoverData> {
        override fun key(data: LoadAudioCoverData, options: Options): String {
            return data.toString()
        }
    }

    class AudioCoverMapper : Mapper<Uri, LoadAudioCoverData> {
        override fun map(data: Uri, options: Options): LoadAudioCoverData? {
            return if (data.scheme == ContentResolver.SCHEME_CONTENT &&
                data.authority == GramophoneAlbumArtProvider.PROVIDER_AUTHORITY) {
                if (data.pathSegments.first() != "song")
                    throw IllegalArgumentException("Invalid uri: $data")
                LoadAudioCoverData(data.pathSegments[1].toLong(),
                    File(android.net.Uri.decode(data.pathSegments[2])))
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
                val afd = MediaStoreCompat.openAssetFileDescriptor(options.context,
                    ContentUris.withAppendedId(MediaStore.Audio.Media
                        .EXTERNAL_CONTENT_URI, data.id), "r")!!
                val retriever = MediaMetadataRetriever()
                try {
                    if (afd.declaredLength == AssetFileDescriptor.UNKNOWN_LENGTH &&
                        afd.startOffset == 0L)
                        retriever.setDataSource(afd.fileDescriptor)
                    else
                        retriever.setDataSource(afd.fileDescriptor, afd.startOffset,
                            afd.length)
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
                    } catch (_: Exception) {}
                }
                // Only poke around for files on external storage
                if (Environment.MEDIA_UNKNOWN ==
                    Environment.getExternalStorageState(data.file)) {
                    throw NoAlbumArtException("No embedded album art found")
                }

                // Ignore "Downloads" or top-level directories
                val parent = data.file.parentFile
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
                return@Fetcher SourceFetchResult(
                    source = ImageSource(bestFile.toOkioPath(), options.fileSystem),
                    mimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(bestFile.extension),
                    dataSource = DataSource.DISK,
                )
            }
        }
    }

    class NoAlbumArtException(message: String) : IOException(message)
}