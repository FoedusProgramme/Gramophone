/*
 *     Copyright (C) 2026 nift4
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

package org.akanework.gramophone.logic

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.media.Rating
import android.net.Uri
import android.provider.BaseColumns
import android.provider.MediaStore
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.R
import org.koin.android.ext.android.inject
import uk.akane.libphonograph.reader.FlowReader

class SearchSuggestionsProvider : ContentProvider() {
    // Lazy: ContentProvider.onCreate runs before Application.onCreate starts Koin, so this must
    // only be touched from query/getType.
    private val reader: FlowReader by inject()

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int {
        throw UnsupportedOperationException()
    }

    override fun getType(uri: Uri): String? {
        if (uri.path?.startsWith(SearchManager.SUGGEST_URI_PATH_QUERY) == true) {
            return SearchManager.SUGGEST_MIME_TYPE
        }
        if (uri.path?.startsWith(SearchManager.SUGGEST_URI_PATH_SHORTCUT) == true) {
            if (uri.lastPathSegment?.toLong() == null || queryCachedShortcut(ContentUris
                .parseId(uri)).moveToFirst()) {
                return SearchManager.SHORTCUT_MIME_TYPE
            }
        }
        return null
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri? {
        throw UnsupportedOperationException()
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String?>?,
        selection: String?,
        selectionArgs: Array<out String?>?,
        sortOrder: String?
    ): Cursor? {
        if (uri.path!!.startsWith(SearchManager.SUGGEST_URI_PATH_SHORTCUT)) {
            return queryCachedShortcut(ContentUris.parseId(uri))
        }
        if (uri.path!!.startsWith(SearchManager.SUGGEST_URI_PATH_QUERY)) {
            val limit = uri.getQueryParameter(SearchManager.SUGGEST_PARAMETER_LIMIT)?.toInt()
            return querySuggestions(selectionArgs!!.first()!!, limit)
        }
        return null
    }

    private fun querySuggestions(query: String, limit: Int?): Cursor {
        // TODO: artists, albums, etc should be searchable too
        return songsToCursor(runBlocking { searchForMediaItem(query) }.let {
            if (limit != null) it.take(limit) else it
        })
    }

    private suspend fun searchForMediaItem(text: String): List<MediaItem> {
        val text = text.trim()
        val list = reader.songListFlow.first()
        // TODO support focus and sub queries (see PlayIntentParser)
        return if (text == "") list else list.filter {
            // TODO sort results by match quality? (using raw=natural order)
            // TODO this is copied directly from SearchFragment and GramophonePlaybackService,
            //  it should be deduplicated
            val isMatchingTitle =
                it.mediaMetadata.title?.contains(text, true) == true
            val isMatchingAlbum =
                it.mediaMetadata.albumTitle?.contains(text, true) == true
            val isMatchingArtist =
                it.mediaMetadata.artist?.contains(text, true) == true
            isMatchingTitle || isMatchingAlbum || isMatchingArtist
        }
    }

    private fun queryCachedShortcut(id: Long): Cursor {
        val idMap = runBlocking { reader.idMapFlow.first() }
        return songsToCursor(idMap[id]?.let { listOf(it) } ?: emptyList())
    }

    private fun songsToCursor(songs: List<MediaItem>): Cursor {
        val cursor = MatrixCursor(arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_QUERY,
            SearchManager.SUGGEST_COLUMN_ICON_1,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA,
            SearchManager.SUGGEST_COLUMN_SHORTCUT_ID,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,
            SearchManager.SUGGEST_COLUMN_DURATION,
            SearchManager.SUGGEST_COLUMN_IS_LIVE,
            SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
            SearchManager.SUGGEST_COLUMN_RATING_SCORE,
            SearchManager.SUGGEST_COLUMN_RATING_STYLE,
            SearchManager.SUGGEST_COLUMN_AUDIO_CHANNEL_CONFIG,
            SearchManager.SUGGEST_COLUMN_LAST_ACCESS_HINT,
            SearchManager.SUGGEST_COLUMN_FLAGS))
        songs.forEach {
            cursor.addRow(arrayOf(
                it.requireMediaStoreId(),
                it.mediaMetadata.title,
                it.mediaMetadata.artist,
                it.mediaMetadata.title, // query
                it.mediaMetadata.artworkUri?.toString() ?: "android.resource://${context!!
                    .packageName}/${R.drawable.ic_default_cover}",
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    it.requireMediaStoreId()),
                it.requireMediaStoreId(),
                it.localConfiguration?.mimeType,
                it.mediaMetadata.durationMs,
                0,
                it.mediaMetadata.recordingYear,
                if ((it.mediaMetadata.userRating as? HeartRating)?.isHeart == true) 1f else 0f,
                Rating.RATING_HEART,
                null, // when indexing channel count, render as nice user visible string
                null, // UTC time millis in Long of last play of this song, to add when we index it
                0, // when adding artist, this should be set to SearchManager.FLAG_QUERY_REFINEMENT
            ))
        }
        return cursor
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int {
        throw UnsupportedOperationException()
    }

}