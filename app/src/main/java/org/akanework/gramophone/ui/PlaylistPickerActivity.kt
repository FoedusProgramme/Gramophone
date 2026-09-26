/*
 *     Copyright (C) 2025 Akane Foundation
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
package org.akanework.gramophone.ui

import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.ui.screens.PickerEntry
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.toUriCompat

class PlaylistPickerActivity : PickerActivity<Playlist>() {
    override fun itemsFlow(): Flow<List<Playlist>> =
        gramophoneApplication.reader.playlistListFlow.map { list ->
            list.filter { it.id != null && it.path != null }
        }

    override fun entryOf(item: Playlist): PickerEntry<Playlist> {
        val count = item.songList.size
        return PickerEntry(
            item = item,
            title = item.title ?: item.path?.name ?: getString(R.string.unknown_playlist),
            subtitle = resources.getQuantityString(R.plurals.items, count, count),
            cover = item.cover?.toUriCompat() ?: item.songList.firstOrNull()?.mediaMetadata?.artworkUri,
            defaultCover = R.drawable.ic_default_cover_playlist,
        )
    }

    override fun getTitleStr() = getString(R.string.playlist_picker_activity)

    override fun onSelected(item: Playlist) {
        setResult(RESULT_OK, Intent().apply {
            setDataAndType(
                if (action == Intent.ACTION_PICK) ContentUris.withAppendedId(
                    @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, item.id!!
                ) else ContentUris.withAppendedId(MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI, item.id!!),
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(item.path!!.extension),
            )
            setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }
}
