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

import androidx.appcompat.widget.PopupMenu
import androidx.core.view.iterator
import androidx.fragment.app.Fragment
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.items.Genre

/**
 * [GenreAdapter] is an adapter for displaying genres.
 */
class GenreAdapter(
    fragment: Fragment,
) : BaseAdapter<Genre>
    (
    fragment,
    liveData = (fragment.requireActivity() as MainActivity).reader.genreListFlow,
    sortHelper = StoreGenreHelper,
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.items,
    defaultLayoutType = LayoutType.LIST
) {

    init {
        lateInit()
    }

    override val defaultCover = R.drawable.ic_default_cover_genre

    override fun virtualTitleOf(item: Genre): String {
        return context.getString(R.string.unknown_genre)
    }

    override fun onClick(item: Genre, position: Int) {
        mainActivity.startFragment(GeneralSubFragment()) {
            putString("Id", item.id?.toString())
            putInt("Item", R.id.genre)
        }
    }

    override fun onMenu(item: Genre, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu)
        popupMenu.menu.iterator().forEach {
            it.isVisible = it.itemId == R.id.play_next || it.itemId == R.id.add_to_queue
                    || it.itemId == R.id.add_to_playlist
        }

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        mediaController.currentMediaItemIndex + 1,
                        item.songList,
                    )
                    true
                }

                R.id.add_to_queue -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        item.songList,
                    )
                    true
                }

                R.id.add_to_playlist -> {
                    mainActivity.addToPlaylistDialog(item.songList)
                    true
                }

                else -> false
            }
        }
    }

    object StoreGenreHelper : StoreItemHelper<Genre>()
}
