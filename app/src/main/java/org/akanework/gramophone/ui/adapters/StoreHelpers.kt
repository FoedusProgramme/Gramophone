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

import org.akanework.gramophone.ui.adapters.BaseAdapter.StoreItemHelper
import android.net.Uri
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.Genre
import java.util.GregorianCalendar

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
        return item.cover
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

object StoreArtistHelper : StoreItemHelper<Artist>(
    setOf(
        Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
        Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending,
        Sorter.Type.ByAlbumSizeAscending, Sorter.Type.ByAlbumSizeDescending
    )
) {
    override fun getAlbumSize(item: Artist): Int {
        return item.albumList.size
    }
}

object StoreGenreHelper : StoreItemHelper<Genre>()

object StoreDateHelper : StoreItemHelper<Date>()
