/*
 *     Copyright (C) 2025 The Gramophone authors
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

package uk.akane.libphonograph.items

import android.net.Uri
import androidx.core.os.BundleCompat
import androidx.media3.common.MediaMetadata

const val EXTRA_AUTHOR = "Author"
const val EXTRA_ARTIST_ID = "ArtistId"
const val EXTRA_ALBUM_ID = "AlbumId"
const val EXTRA_ALBUM_YEAR = "AlbumYear"
const val EXTRA_ADD_DATE = "AddDate"
const val EXTRA_MODIFIED_DATE = "ModifiedDate"
const val EXTRA_CD_TRACK_NUMBER = "CdTrackNumber"
const val EXTRA_HD_ARTWORK_URI = "HdArtworkUri"
const val EXTRA_FILE = "File"
const val EXTRA_ARTIST_NAMES = "ArtistNames"
const val EXTRA_GENRE_NAMES = "GenreNames"
const val EXTRA_RAW_ARTIST = "RawArtist"

val MediaMetadata.author: String?
    get() = extras?.getString(EXTRA_AUTHOR)

val MediaMetadata.rawArtist: String?
    get() = extras?.getString(EXTRA_RAW_ARTIST) ?: artist?.toString()

val MediaMetadata.artistId: Long?
    get() = extras?.getLong(EXTRA_ARTIST_ID, -1).let { if (it == -1L) null else it }

val MediaMetadata.albumId: Long?
    get() = extras?.getLong(EXTRA_ALBUM_ID, -1).let { if (it == -1L) null else it }

val MediaMetadata.albumYear: Long?
    get() = extras?.getLong(EXTRA_ALBUM_YEAR, -1).let { if (it == -1L) null else it }

val MediaMetadata.addDate: Long?
    get() = extras?.getLong(EXTRA_ADD_DATE, -1).let { if (it == -1L) null else it }

val MediaMetadata.modifiedDate: Long?
    get() = extras?.getLong(EXTRA_MODIFIED_DATE, -1).let { if (it == -1L) null else it }

val MediaMetadata.cdTrackNumber: String?
    get() = extras?.getString(EXTRA_CD_TRACK_NUMBER)

val MediaMetadata.artistNames: List<String>
    get() = extras?.getStringArrayList(EXTRA_ARTIST_NAMES)?.takeIf { it.isNotEmpty() }?.toList()
        ?: rawArtist?.let { listOf(it) } ?: emptyList()

val MediaMetadata.genreNames: List<String>
    get() = extras?.getStringArrayList(EXTRA_GENRE_NAMES)?.takeIf { it.isNotEmpty() }?.toList()
        ?: genre?.toString()?.let { listOf(it) } ?: emptyList()

val MediaMetadata.hdArtworkUri: Uri?
    get() = extras?.let { extras -> BundleCompat.getParcelable(extras,
        EXTRA_HD_ARTWORK_URI, Uri::class.java) }

/**
 * Checks if a MediaItem matches a search query against title, album, artists, and genres.
 *
 * @author SteveZMTstudios
 */
fun androidx.media3.common.MediaItem.matchesSearch(query: String): Boolean {
    if (query.isEmpty()) return true
    val isMatchingTitle = mediaMetadata.title?.contains(query, ignoreCase = true) == true
    val isMatchingAlbum = mediaMetadata.albumTitle?.contains(query, ignoreCase = true) == true
    val isMatchingArtist = mediaMetadata.artist?.contains(query, ignoreCase = true) == true
        || mediaMetadata.rawArtist?.contains(query, ignoreCase = true) == true
        || mediaMetadata.artistNames.any { it.contains(query, ignoreCase = true) }
    val isMatchingGenre = mediaMetadata.genre?.contains(query, ignoreCase = true) == true
        || mediaMetadata.genreNames.any { it.contains(query, ignoreCase = true) }
    return isMatchingTitle || isMatchingAlbum || isMatchingArtist || isMatchingGenre
}

/**
 * Resolves an Artist from a list with strict priority:
 * 1. Exact ID match (if ID is provided)
 * 2. Exact name match (if name is provided)
 * 3. Case-insensitive name match fallback
 *
 * @author SteveZMTstudios
 */
fun List<Artist>.findArtist(id: Long? = null, name: String? = null): Artist? {
    if (id != null) {
        val byId = find { it.id == id }
        if (byId != null) return byId
    }
    if (name != null) {
        val trimmed = name.trim()
        return find { it.title?.trim() == trimmed }
            ?: find { it.title?.trim().equals(trimmed, ignoreCase = true) }
    }
    return null
}