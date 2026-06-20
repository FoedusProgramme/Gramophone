package org.akanework.gramophone.logic.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware

object ArtUtils {
    /**
     * Fetches and resizes artwork to a byte array using Coil.
     */
    suspend fun getResizedArtworkBytes(context: Context, uri: Uri, maxSize: Int): ByteArray? {
        val imageLoader = SingletonImageLoader.get(context)
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(maxSize, maxSize)
            .allowHardware(false)
            .build()

        val result = imageLoader.execute(request)
        if (result is SuccessResult) {
            val bitmap = (result.image as? BitmapImage)?.bitmap ?: return null
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            // Note: We don't recycle the bitmap here as it might be from Coil's memory cache
            return stream.toByteArray()
        }
        return null
    }
}