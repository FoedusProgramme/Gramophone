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
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.Flow
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.requireMediaStoreId
import org.akanework.gramophone.ui.screens.PickerEntry

class SongPickerActivity : PickerActivity<MediaItem>() {
    override fun itemsFlow(): Flow<List<MediaItem>> = reader.songListFlow

    override fun entryOf(item: MediaItem) = PickerEntry(
        item = item,
        title = item.mediaMetadata.title?.toString() ?: getString(R.string.unknown_title),
        subtitle = item.mediaMetadata.artist?.toString() ?: getString(R.string.unknown_artist),
        cover = item.mediaMetadata.artworkUri,
        defaultCover = R.drawable.ic_default_cover,
    )

    override fun getTitleStr() = getString(R.string.picker_activity)

    override fun onSelected(item: MediaItem) {
        setResult(RESULT_OK, Intent().apply {
            setDataAndType(
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.requireMediaStoreId()
                ),
                item.localConfiguration?.mimeType,
            )
            setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        finish()
    }
}
