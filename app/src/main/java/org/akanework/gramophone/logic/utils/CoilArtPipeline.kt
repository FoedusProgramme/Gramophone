package org.akanework.gramophone.logic.utils

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.intercept.Interceptor
import coil3.key.Keyer
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.pxOrElse
import coil3.toAndroidUri
import coil3.toCoilUri
import okio.buffer
import okio.source
import java.io.IOException

object CoilArtPipeline {

    private const val TAG = "CoilArtPipeline"

    fun Options.getArtworkBucketSize(): Int {
        val requestWidth = size.width.pxOrElse { 0 }
        val requestHeight = size.height.pxOrElse { 0 }
        return if (requestWidth > 512 || requestHeight > 512) 1024 else 512
    }

    class ResolutionInterceptor : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val data = request.data
            
            if (data is ArtResolver.ArtResource) {
                Log.d(TAG, "Proceeding with ArtResource: $data")
                return chain.proceed()
            }
            
            val coilUri = when (data) {
                is Uri -> data
                is android.net.Uri -> data.toCoilUri()
                else -> null
            }
            
            if (coilUri != null && isSupportedUri(coilUri)) {
                val size = chain.size
                val androidUri = coilUri.toAndroidUri()
                // Use a stable bucket size for candidates to improve cache hits
                val bucketSize = if (size.width.pxOrElse { 0 } > 512 || size.height.pxOrElse { 0 } > 512) 1024 else 512
                val candidates = ArtResolver.getResolutionList(androidUri, bucketSize)

                Log.d(TAG, "Intercepted artwork URI: $coilUri, bucketSize: $bucketSize, candidates: ${candidates.size}")

                var lastError: Throwable? = null
                for (candidate in candidates) {
                    val newRequest = request.newBuilder()
                        .data(candidate)
                        .build()

                    Log.d(TAG, "  Trying candidate: $candidate")
                    val result = chain.withRequest(newRequest).proceed()
                    if (result is SuccessResult) {
                        Log.d(TAG, "  Successfully loaded candidate: $candidate")
                        return result
                    }
                    if (result is ErrorResult) {
                        Log.d(TAG, "  Failed to load candidate: $candidate, error: ${result.throwable}")
                        lastError = result.throwable
                    }
                }
                Log.w(TAG, "  All candidates failed for $coilUri")
                return ErrorResult(
                    image = null,
                    request = request,
                    throwable = lastError ?: IOException("Unable to resolve $coilUri")
                )
            }
            return chain.proceed()
        }

        private fun isSupportedUri(uri: Uri): Boolean {
            val scheme = uri.scheme
            val authority = uri.authority
            val supported = scheme == "gramophoneSongCover" ||
                    scheme == "gramophoneAlbumCover" ||
                    (scheme == "content" && (authority == ArtResolver.PROVIDER_AUTHORITY || (authority != null && authority.endsWith(".albumart"))))
            if (supported) {
                Log.v(TAG, "isSupportedUri: $uri -> true")
            }
            return supported
        }
    }

    class ArtResourceKeyer : Keyer<ArtResolver.ArtResource> {
        override fun key(data: ArtResolver.ArtResource, options: Options): String {
            val key = data.toCacheKey(options.getArtworkBucketSize())
            Log.v(TAG, "Keyer for $data -> $key")
            return key
        }
    }

    class ArtResourceFetcher(
        private val context: Context,
        private val candidate: ArtResolver.ArtResource,
        private val options: Options
    ) : Fetcher {
        override suspend fun fetch(): FetchResult? {
            val size = options.getArtworkBucketSize()
            Log.v(TAG, "Fetcher.fetch for $candidate (size: $size)")
            val art = ArtResolver.openResourceStream(context, candidate, size) ?: run {
                Log.v(TAG, "  Fetcher.fetch failed to open stream for $candidate")
                return null
            }

            // Only AlbumFolder should be cached on disk.
            // DataSource.LOCAL signals to Coil that it should be written to the disk cache.
            // DataSource.DISK signals that it's already on disk (or shouldn't be cached there again).
            val shouldDiskCache = candidate is ArtResolver.ArtResource.AlbumFolder
            Log.v(TAG, "  Fetcher.fetch success for $candidate, shouldDiskCache: $shouldDiskCache")

            return SourceFetchResult(
                source = ImageSource(
                    source = art.stream.source().buffer(),
                    fileSystem = options.fileSystem
                ),
                mimeType = art.mimeType,
                dataSource = if (shouldDiskCache) DataSource.MEMORY else DataSource.DISK
            )
        }

        class Factory : Fetcher.Factory<ArtResolver.ArtResource> {
            override fun create(
                data: ArtResolver.ArtResource,
                options: Options,
                imageLoader: ImageLoader
            ): Fetcher {
                return ArtResourceFetcher(options.context, data, options)
            }
        }
    }
}
