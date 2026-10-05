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
package org.akanework.gramophone.ui.library

import android.net.Uri
import androidx.media3.common.MediaItem
import org.akanework.gramophone.logic.getFile
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.Genre
import uk.akane.libphonograph.items.Item
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.items.addDate
import uk.akane.libphonograph.items.modifiedDate
import java.io.File
import java.util.GregorianCalendar

/* The sorting helpers of each kind of library item: what a sorter can read off them. */

abstract class StoreItemHelper<T : Item>(
    typesSupported: Set<Sorter.Type> = setOf(
        Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
        Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending
    )
) : Sorter.Helper<T>(typesSupported) {
    override fun getId(item: T): String {
        return item.id.toString()
    }

    override fun getTitle(item: T): String? {
        return item.title
    }

    override fun getSize(item: T): Int {
        return item.songList.size
    }

    override fun getCover(item: T): Uri? {
        return item.songList.firstOrNull()?.mediaMetadata?.artworkUri
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

object StorePlaylistHelper : StoreItemHelper<Playlist>()

object MediaItemHelper : Sorter.Helper<MediaItem>(
    setOf(
        Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
        Sorter.Type.ByArtistDescending, Sorter.Type.ByArtistAscending,
        Sorter.Type.ByAlbumTitleDescending, Sorter.Type.ByAlbumTitleAscending,
        Sorter.Type.ByAlbumArtistDescending, Sorter.Type.ByAlbumArtistAscending,
        Sorter.Type.ByAlbumArtistYearDescending, Sorter.Type.ByAlbumArtistYearAscending,
        Sorter.Type.ByAlbumYearDescending, Sorter.Type.ByAlbumYearAscending,
        Sorter.Type.ByAddDateDescending, Sorter.Type.ByAddDateAscending,
        Sorter.Type.ByReleaseDateDescending, Sorter.Type.ByReleaseDateAscending,
        Sorter.Type.ByModifiedDateDescending, Sorter.Type.ByModifiedDateAscending,
        Sorter.Type.ByFilePathDescending, Sorter.Type.ByFilePathAscending,
        Sorter.Type.ByDiscAndTrack
    )
) {
    override fun getId(item: MediaItem): String {
        return item.mediaId
    }

    override fun getFile(item: MediaItem): File {
        return item.getFile()!!
    }

    override fun getTitle(item: MediaItem): String {
        return item.mediaMetadata.title.toString()
    }

    override fun getArtist(item: MediaItem): String? {
        return item.mediaMetadata.artist?.toString()
    }

    override fun getAlbumTitle(item: MediaItem): String {
        return item.mediaMetadata.albumTitle?.toString() ?: ""
    }

    override fun getAlbumArtist(item: MediaItem): String {
        return item.mediaMetadata.albumArtist?.toString() ?: ""
    }

    override fun getAlbumYear(item: MediaItem): Int? {
        return item.mediaMetadata.releaseYear
    }

    override fun getCover(item: MediaItem): Uri? {
        return item.mediaMetadata.artworkUri
    }

    override fun getDiscAndTrack(item: MediaItem): Int {
        return (item.mediaMetadata.discNumber ?: 0) * 1000 + (item.mediaMetadata.trackNumber
            ?: 0)
    }

    override fun getAddDate(item: MediaItem): Long {
        return item.mediaMetadata.addDate ?: -1
    }

    override fun getReleaseDate(item: MediaItem): Long {
        if (item.mediaMetadata.releaseYear == null && item.mediaMetadata.releaseMonth == null
            && item.mediaMetadata.releaseDay == null
        ) {
            return GregorianCalendar(
                item.mediaMetadata.recordingYear ?: 0,
                (item.mediaMetadata.recordingMonth ?: 1) - 1,
                item.mediaMetadata.recordingDay ?: 0, 0, 0, 0
            )
                .timeInMillis
        }
        return GregorianCalendar(
            item.mediaMetadata.releaseYear ?: 0,
            (item.mediaMetadata.releaseMonth ?: 1) - 1,
            item.mediaMetadata.releaseDay ?: 0, 0, 0, 0
        )
            .timeInMillis
    }

    override fun getModifiedDate(item: MediaItem): Long {
        return item.mediaMetadata.modifiedDate ?: -1
    }
}
