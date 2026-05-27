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

import android.net.Uri
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import kotlinx.coroutines.flow.Flow
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.GeneralSubFragment
import uk.akane.libphonograph.items.Album
import java.util.GregorianCalendar

class AlbumAdapter(
    fragment: Fragment,
    val queueTitle: Flow<String>,
    liveData: Flow<List<Album>?> = (fragment.requireActivity() as MainActivity).reader.albumListFlow,
    isSubFragment: Int? = null
) : BaseAdapter<Album>
    (
    fragment,
    liveData = liveData,
    sortHelper = StoreAlbumHelper,
    naturalOrderHelper = null,
    initialSortType = Sorter.Type.ByTitleAscending,
    pluralStr = R.plurals.albums,
    defaultLayoutType = LayoutType.GRID,
    isSubFragment = isSubFragment
) {

    init {
        lateInit()
    }

    fun getAlbumList() = list?.second ?: emptyList()

    fun getActivity() = mainActivity

    override fun virtualTitleOf(item: Album): String {
        return context.getString(R.string.unknown_album)
    }

    override fun onClick(item: Album, position: Int) {
        mainActivity.startFragment(GeneralSubFragment()) {
            putString("Id", item.id?.toString())
            putInt("Item", R.id.album)
        }
    }

    override fun onMenu(item: Album, popupMenu: PopupMenu) {
        popupMenu.inflate(R.menu.more_menu_less)

        popupMenu.setOnMenuItemClickListener { it1 ->
            when (it1.itemId) {
                R.id.play_next -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        mediaController.currentMediaItemIndex + 1,
                        item.songList,
                    )
                }

                R.id.add_to_queue -> {
                    val mediaController = mainActivity.getPlayer()
                    mediaController?.addMediaItems(
                        item.songList,
                    )
                }

                /*
				R.id.share -> {
					val builder = ShareCompat.IntentBuilder(mainActivity)
					val mimeTypes = mutableSetOf<String>()
					builder.addStream(viewModel.fileUriList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					mimeTypes.add(viewModel.mimeTypeList.value?.get(songList[holder.bindingAdapterPosition].mediaId.toLong())!!)
					builder.setType(mimeTypes.singleOrNull() ?: "audio/*").startChooser()
				 } */
				 */
            }
            true
        }
    }

    object StoreAlbumHelper : StoreItemHelper<Album>(
        setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
            Sorter.Type.ByArtistYearDescending, Sorter.Type.ByArtistYearAscending,
            Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending,
            Sorter.Type.ByReleaseDateAscending, Sorter.Type.ByReleaseDateDescending,
            Sorter.Type.ByAddDateAscending, Sorter.Type.ByAddDateDescending,
            Sorter.Type.ByModifiedDateAscending, Sorter.Type.ByModifiedDateDescending
        )
    ) {
        override fun getArtist(item: Album): String? {
            return item.albumArtist
        }

        override fun getCover(item: Album): Uri? {
            return item.cover ?: super.getCover(item)
        }

        override fun getReleaseDate(item: Album): Long {
            return GregorianCalendar(item.albumYear ?: 0, 0, 0, 0, 0, 0).timeInMillis
        }

        override fun getAddDate(item: Album): Long {
            return item.albumAddDate ?: -1
        }

        override fun getModifiedDate(item: Album): Long {
            return item.albumModifiedDate ?: -1
        }
    }
}
