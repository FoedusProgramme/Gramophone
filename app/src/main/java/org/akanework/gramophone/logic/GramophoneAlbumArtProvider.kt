package org.akanework.gramophone.logic

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.utils.ArtResolver
import java.io.IOException

/**
 * ContentProvider that serves album artwork to external processes (e.g. Android Auto).
 *
 * External processes cannot resolve Gramophone's internal URI schemes
 * (`gramophoneSongCover://`, `gramophoneAlbumCover://`). This provider acts as a bridge,
 * using the shared [ArtResolver] and Coil's disk cache to resolve, cache and serve the artwork 
 * over a standard `content://` URI.
 *
 * URI format: `content://org.akanework.gramophone.albumart/{type}/{id}/{encodedPath}`
 * where `type` is "song" or "album".
 */
class GramophoneAlbumArtProvider : ContentProvider() {

    companion object {
        private const val TAG = "GramophoneArtProvider"
    }

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        val app = (context.applicationContext as? GramophoneApplication) ?: return null

        try {
            val pipe = ParcelFileDescriptor.createPipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            app.providerScope.launch(Dispatchers.IO) {
                try {
                    val size = 1024
                    val candidates = ArtResolver.getResolutionList(uri, size)
                    var found = false
                    for (candidate in candidates) {
                        val art = ArtResolver.openResourceStream(context, candidate, size)
                        if (art != null) {
                            art.stream.use { input ->
                                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        Log.w(TAG, "No artwork found for URI: $uri")
                        try { writeSide.close() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error streaming artwork for URI: $uri", e)
                    try { writeSide.close() } catch (_: Exception) {}
                }
            }
            return readSide
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create pipe for URI: $uri", e)
            return null
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

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
