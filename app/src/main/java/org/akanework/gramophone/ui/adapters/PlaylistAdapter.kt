/*
 *     Copyright (C) 2024 Akane Foundation
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

package org.akanework.gramophone.ui.adapters

import android.content.ContentResolver
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import kotlinx.coroutines.flow.map
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.ui.PlaylistPickerActivity
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.toUriCompat

class PlaylistAdapter(
    fallbackContext: AppCompatActivity,
) : BaseAdapter<Playlist>
    (
    null,
    liveData = fallbackContext.gramophoneApplication.reader.playlistListFlow.map { playlistsList ->
        playlistsList.filter { p -> p.id != null && p.path != null }
    },
    sortHelper = StorePlaylistHelper,
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.items,
    defaultLayoutType = LayoutType.LIST,
    isSubFragment = R.id.songs,
    hasMenu = false,
    fallbackContext = fallbackContext
) {
    init {
        lateInit()
    }

    override val defaultCover = R.drawable.ic_default_cover_playlist

    override fun virtualTitleOf(item: Playlist): String {
        return when (item) {
            is RecentlyAdded -> context.getString(R.string.recently_added)
            is Favorite -> context.getString(R.string.playlist_favourite)
            else -> {
                context.getString(R.string.unknown_playlist) + " (${item.id} - ${item.path})"
            }
        }
    }

    override fun getPinnedOrder(item: Playlist): Int {
        return when (item) {
            is RecentlyAdded -> 1
            is Favorite -> 4
            else -> 999
        }
    }

    override fun coverOf(item: Playlist): Uri? {
        return if (item.title != null) {
            item.cover?.toUriCompat() ?: super.coverOf(item)
        } else
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.packageName)
                .path(
                    when (item) {
                        is RecentlyAdded -> R.drawable.ic_default_cover_playlist_recently
                        is Favorite -> R.drawable.ic_default_cover_playlist_favorite
                        else -> R.drawable.ic_default_cover_playlist
                    }.toString()
                ).build()
    }

    override fun onClick(item: Playlist, position: Int) {
        (context as PlaylistPickerActivity).onSelected(item)
    }

    override fun onMenu(item: Playlist, popupMenu: PopupMenu) {}

    object StorePlaylistHelper : StoreItemHelper<Playlist>()
}
