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
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.SEEK_SET
import android.util.Log
import androidx.core.content.getSystemService
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.buffer
import okio.sink
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.utils.CoilArtPipeline
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlin.time.Duration.Companion.milliseconds

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

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun openFileCommon(uri: Uri, size: Point?, allowPartial: Boolean): AssetFileDescriptor? {
        val context = context!!
        val cfd = CompletableDeferred<AssetFileDescriptor?>()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        currentCoroutineContext().job.invokeOnCompletion {
            if (it is CancellationException) scope.cancel(it)
            else if (it != null) scope.cancel("Error in parent block", it)
        }
        scope.launch {
            currentCoroutineContext().job.invokeOnCompletion {
                if (!cfd.isCompleted) {
                    cfd.completeExceptionally(it
                        ?: IllegalStateException("Completed job without setting file descriptor"))
                }
            }
            context.imageLoader.execute(
                ImageRequest.Builder(context)
                .data(uri)
                .let {
                    // size will be used to decide if underlying file is small or full size, and if
                    // we can't use the dummy decoder, we will also get the data resized by Coil.
                    if (size != null)
                        it.size(size.x, size.y)
                    // The URI is explicitly designed as the place to get high-quality artwork in
                    // MediaMetadata.METADATA_KEY_ALBUM_ART javadoc, so don't default to thumbnail.
                    // TODO(ASAP): nvm this makes m3ct super laggy, we should split queue and cur
                    //  metadata by making cur metadata append a query param which we use to fall
                    //  back to small version here
                    else it
                }
                // Memory cache stores Bitmap, not compressed data, so we shouldn't read from
                // it (otherwise our dummy decoder wouldn't get any data ever, and we would
                // recompress decoded bitmap, wasting battery). But if we do produce a Bitmap,
                // we can write it there for benefit of UI code somewhere else.
                .memoryCachePolicy(CachePolicy.WRITE_ONLY)
                .allowHardware(false)
                .decoderFactory { result, options, _ ->
                    val src = result.source.source()
                    src.peek().let { peekSrc ->
                        if (peekSrc.readByte() != 0xff.toByte() ||
                            peekSrc.readByte() != 0xd8.toByte() ||
                            peekSrc.readByte() != 0xff.toByte() ||
                            run {
                                val peek = peekSrc.readByte()
                                peek != 0xdb.toByte() && peek !in 0xe0.toByte()..0xef.toByte()
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
                            (result.source.metadata as ContentMetadata).assetFileDescriptor.let {
                                val newFd = it.parcelFileDescriptor.dup()
                                try {
                                    // It's only safe to give the unmodified fd to reader if it is
                                    // seekable, because otherwise (pipe?) no matter when we dup, we
                                    // would eat some data when checking for JPEG header.
                                    Os.lseek(newFd.fileDescriptor, it.startOffset,
                                        SEEK_SET)
                                    cfd.complete(AssetFileDescriptor(newFd, it.startOffset,
                                        it.declaredLength))
                                } catch (_: ErrnoException) {}
                            }
                        }
                        if (!cfd.isCompleted) {
                            writeDataCommon(cfd, scope, options.context) {
                                if (it != null) {
                                    it.sink().buffer().writeAll(src); null
                                } else
                                    src.readByteArray()
                            }
                        }
                        // shareable is false to avoid writing dummy to memory cache
                        DecodeResult(
                            ColorImage(0, shareable = false),
                            false
                        )
                    }
                }
                .target(onSuccess = { image ->
                    if (!cfd.isCompleted) {
                        launch(start = CoroutineStart.ATOMIC) {
                            writeDataCommon(cfd, scope, context) {
                                val os = it ?: ByteArrayOutputStream()
                                image.toBitmap().compress(Bitmap.CompressFormat.JPEG,
                                    95, os)
                                if (it != null)
                                    null
                                else
                                    (os as ByteArrayOutputStream).toByteArray()
                            }
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

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun writeDataCommon(cfd: CompletableDeferred<AssetFileDescriptor?>,
                                        scope: CoroutineScope, context: Context,
                                        callback: (OutputStream?) -> ByteArray?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            currentCoroutineContext().job.ensureActive()
            val bytes = callback(null)!!
            currentCoroutineContext().job.ensureActive()
            val ht = HandlerThread("pfd_${System.currentTimeMillis()}")
            ht.start()
            // Specifically ImageDecoder on Android P or later needs a seekable file descriptor
            val pfd = context.getSystemService<StorageManager>()!!.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                object : ProxyFileDescriptorCallback() {
                    override fun onGetSize(): Long {
                        return bytes.size.toLong()
                    }

                    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                        val offset = offset.toInt()
                        var size = size
                        if (offset + size > bytes.size) {
                            size = bytes.size - offset
                        }
                        System.arraycopy(bytes, offset, data, 0,
                            size)
                        return size
                    }

                    override fun onRelease() {
                        ht.quitSafely()
                    }
                }, Handler(ht.looper)
            )
            cfd.complete(
                AssetFileDescriptor(
                    pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH
                )
            )
            return
        }
        val pipe = ParcelFileDescriptor.createPipe()
        cfd.complete(
            AssetFileDescriptor(
                pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH
            )
        )
        val disposable = scope.coroutineContext[Job]!!.invokeOnCompletion(
            onCancelling = true) {
            if (it is CancellationException) {
                // this will interrupt write to ensure we don't block forever
                pipe[1].close()
            }
        }
        try {
            withTimeout((30 * 1000).milliseconds) {
                launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
                        .use { os ->
                            // only check for cancel here to ensure close
                            ensureActive()
                            try {
                                callback(os)
                            } catch (e: Exception) {
                                try {
                                    ensureActive()
                                } catch (e2: CancellationException) {
                                    Log.w(TAG, "eating exception due" +
                                            " to cancel()", e)
                                    e2.addSuppressed(e)
                                    throw e2
                                }
                                throw e
                            }
                        }
                    disposable.dispose()
                }.join()
            }
        } catch (_: TimeoutCancellationException) {
            // If the other side ain't done reading after 30s, give up
            scope.cancel()
        }
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
    ): Cursor? = null

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
