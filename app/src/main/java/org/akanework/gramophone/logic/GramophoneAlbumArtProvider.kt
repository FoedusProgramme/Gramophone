package org.akanework.gramophone.logic

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.akanework.gramophone.logic.utils.ArtCacheManager

/**
 * ContentProvider that serves album artwork to external processes (e.g. Android Auto).
 *
 * External processes cannot resolve Gramophone's internal URI schemes
 * (`gramophoneSongCover://`, `gramophoneAlbumCover://`). This provider acts as a bridge,
 * using the shared [ArtCacheManager] to resolve, cache and serve the artwork over a
 * standard `content://` URI.
 *
 * URI format: `content://org.akanework.gramophone.albumart/{type}/{id}/{encodedPath}`
 * where `type` is "song" or "album".
 */
class GramophoneAlbumArtProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        return ArtCacheManager.openFileDescriptor(context, uri)
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
