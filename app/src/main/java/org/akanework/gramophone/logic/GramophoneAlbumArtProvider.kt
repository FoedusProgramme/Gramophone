package org.akanework.gramophone.logic

import android.content.ClipDescription
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.os.BundleCompat
import coil3.ColorImage
import coil3.decode.ContentMetadata
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.utils.CoilArtPipeline
import java.io.FileOutputStream
import java.io.IOException

/**
 * ContentProvider that serves album artwork to external processes (e.g. Android Auto).
 *
 * URI format: `content://org.akanework.gramophone.albumart/{type}/{id}/{encodedPath}`
 * where `type` is "song" or "album".
 */
class GramophoneAlbumArtProvider : ContentProvider() {

    companion object {
        private const val TAG = "GramophoneArtProvider"

        /** Authority for the ContentProvider that serves art to external processes. */
        const val PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.albumart"

        /**
         * Builds a `content://` URI pointing to [GramophoneAlbumArtProvider].
         *
         * @param id        the ID of any song (!!! not album ID)
         * @param imageName the image file's name
         */
        fun buildAlbumUri(id: Long, imageName: String): Uri =
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(PROVIDER_AUTHORITY)
                .appendPath("album")
                .appendPath(id.toString())
                .appendPath(imageName)
                .build()

        /**
         * Builds a `content://` URI pointing to [GramophoneAlbumArtProvider].
         *
         * @param id the song ID
         */
        fun buildSongUri(id: Long): Uri =
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(PROVIDER_AUTHORITY)
                .appendPath("song")
                .appendPath(id.toString())
                .build()
    }

    override fun onCreate() = true

    private suspend fun CoroutineScope.openFileCommon(uri: Uri, size: Point?,
                                       allowPartial: Boolean): AssetFileDescriptor? {
        val context = context!!
        val cfd = CompletableDeferred<AssetFileDescriptor?>()
        launch(Dispatchers.IO) {
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                .data(uri)
                .let {
                    // size will be used to decide if underlying file is small or full size, and if
                    // we can't use the dummy decoder, we will also get the data resized by Coil.
                    if (size != null)
                        it.size(size.x, size.y)
                    // If the caller didn't request a specific size, assume it'll fine to use the
                    // thumbnail, because it's a major performance improvement. Calls to this
                    // provider only happen from other apps, of which there are a few (Android Auto,
                    // Bluetooth AVRCP, Media Controller Tester, scrobblers, lyric apps), but those
                    // never display large covers for queue/library media items. Only the active
                    // media item seems to ever be displayed in large format, but that's provided as
                    // ashmem Bitmap to other processes directly, so it doesn't go through here.
                    // TODO(ASAP) potentially regressing notification cover quality...? legacy is
                    //  optimally not fed uri - or HD URI - for MediaMetadata due to precedence
                    // TODO(ASAP) unhardcode small size
                    else it.size(320, 320)
                }
                // Memory cache stores Bitmap, not compressed data, so we shouldn't read from
                // it (otherwise our dummy decoder wouldn't get any data ever, and we would
                // recompress decoded bitmap, wasting battery). But if we do produce a Bitmap,
                // we can write it there for benefit of UI code somewhere else.
                .memoryCachePolicy(CachePolicy.WRITE_ONLY)
                .allowHardware(false)
                .decoderFactory { result, _, _ ->
                    val src = result.source.source()
                    src.peek().let { peekSrc ->
                        if (peekSrc.readByte() != 0xff.toByte() ||
                            peekSrc.readByte() != 0xd8.toByte() ||
                            peekSrc.readByte() != 0xff.toByte() ||
                            run {
                                val peek = peekSrc.readByte().toInt()
                                peek != 0xdb && peek !in 0xe0..0xef
                            }
                        ) {
                            // Not JPEG. We'll have to re-encode to JPEG (here done in target)
                            return@decoderFactory null
                        }
                    }
                    Decoder {
                        // We can send this stream of bytes as is!
                        if (result.source.metadata is ContentMetadata && (allowPartial ||
                                    (result.source.metadata as ContentMetadata)
                                        .assetFileDescriptor.let {
                                            it.declaredLength ==
                                                    AssetFileDescriptor.UNKNOWN_LENGTH &&
                                                    it.startOffset == 0L
                                        })
                        ) {
                            cfd.complete(
                                (result.source.metadata as ContentMetadata)
                                    .assetFileDescriptor
                            )
                        } else {
                            val pipe = ParcelFileDescriptor.createPipe()
                            cfd.complete(
                                AssetFileDescriptor(
                                    pipe[0], 0,
                                    AssetFileDescriptor.UNKNOWN_LENGTH
                                )
                            )
                            try {
                                FileOutputStream(pipe[1].fileDescriptor).sink()
                                    .buffer().writeAll(src)
                            } finally {
                                pipe[1].close()
                            }
                        }
                        // shareable is false to avoid writing dummy to memory cache
                        DecodeResult(
                            ColorImage(0, shareable = false),
                            false
                        )
                    }
                }
                .target(onSuccess = {
                    if (!cfd.isCompleted) {
                        val pipe = ParcelFileDescriptor.createPipe()
                        cfd.complete(AssetFileDescriptor(
                            pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH))
                        try {
                            FileOutputStream(pipe[1].fileDescriptor).use { os ->
                                it.toBitmap().compress(
                                    Bitmap.CompressFormat.JPEG, 95, os)
                            }
                        } finally {
                            pipe[1].close()
                        }
                    }
                })
                .listener(onError = { _, result ->
                    if (cfd.isCompleted) {
                        Log.e(TAG, "Listener errored after successful decode",
                            result.throwable)
                        return@listener
                    }
                    if (result.throwable is CoilArtPipeline.NoAlbumArtException || result.throwable
                                is IOException && (result.throwable.message == "No album art found"
                                || result.throwable.message == "No embedded album art found"
                                || result.throwable.message == "No thumbnails in Downloads directories"
                                || result.throwable.message == "No thumbnails in top-level directories"))
                        cfd.complete(null)
                    else
                        cfd.completeExceptionally(result.throwable)
                })
                .build())
        }
        return cfd.await()
    }

    private fun openFileCommon(uri: Uri, size: Point?, signal: CancellationSignal?,
                               allowPartial: Boolean): AssetFileDescriptor? {
        if (uri.pathSegments.size < 2)
            throw IllegalArgumentException("Invalid uri: $uri")
        return runBlocking {
            signal?.throwIfCanceled()
            val job = currentCoroutineContext().job
            signal?.setOnCancelListener {
                job.cancel()
            }
            openFileCommon(uri, size, allowPartial)
        }
    }

    override fun openFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        try {
            if (mode != "r")
                throw IllegalArgumentException("Unsupported mode $mode, this provider is read-only")
            val afd = openFileCommon(uri, null, signal, false)
            if (afd != null && (afd.declaredLength != AssetFileDescriptor.UNKNOWN_LENGTH ||
                        afd.startOffset != 0L))
                throw IllegalStateException("Logic bug in album art provider, got partial file...")
            return afd?.parcelFileDescriptor
        } catch (_: CancellationException) {
            throw OperationCanceledException()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $uri", e)
            throw e
        }
    }

    override fun openAssetFile(
        uri: Uri,
        mode: String,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        try {
            if (mode != "r")
                throw IllegalArgumentException("Unsupported mode $mode, this provider is read-only")
            return openFileCommon(uri, null, signal, true)
        } catch (_: CancellationException) {
            throw OperationCanceledException()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $uri", e)
            throw e
        }
    }

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: Bundle?,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        try {
            return if (ClipDescription.compareMimeTypes("image/jpeg",
                    mimeTypeFilter)) {
                val size = opts?.let { BundleCompat.getParcelable(it,
                    ContentResolver.EXTRA_SIZE, Point::class.java) }
                openFileCommon(uri, size, signal, true)
            } else throw IllegalArgumentException("Unsupported MIME filter $mimeTypeFilter")
        } catch (_: CancellationException) {
            throw OperationCanceledException()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $uri", e)
            throw e
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null // TODO(ASAP) support OpenableColumns

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException()

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException()
}
