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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

const val EXTRA_ARTIST_ID = "ArtistId"
const val EXTRA_ALBUM_ID = "AlbumId"
const val EXTRA_ADD_DATE = "AddDate"
const val EXTRA_MODIFIED_DATE = "ModifiedDate"
const val EXTRA_CD_TRACK_NUMBER = "CdTrackNumber"
const val EXTRA_HD_ARTWORK_URI = "HdArtworkUri"
const val EXTRA_FILE = "File"

// TODO(Reader2) remove fully
val MediaMetadata.artistId: Long?
    get() = extras?.getLong(EXTRA_ARTIST_ID, -1).let { if (it == -1L) null else it }

// TODO(Reader2) remove fully
val MediaMetadata.albumId: Long?
    get() = extras?.getLong(EXTRA_ALBUM_ID, -1).let { if (it == -1L) null else it }

val MediaMetadata.artistMusicbrainzId: Uuid?
    get() = extras?.getString("todo123", null)?.let { Uuid.parse(it) } // TODO(Reader2)

val MediaMetadata.albumArtistMusicbrainzId: Uuid?
    get() = extras?.getString("todo123", null)?.let { Uuid.parse(it) } // TODO(Reader2)

val MediaMetadata.albumMusicbrainzId: Uuid?
    get() = extras?.getString("todo123", null)?.let { Uuid.parse(it) } // TODO(Reader2)

val MediaMetadata.addDate: Long?
    get() = extras?.getLong(EXTRA_ADD_DATE, -1).let { if (it == -1L) null else it }

val MediaMetadata.modifiedDate: Long?
    get() = extras?.getLong(EXTRA_MODIFIED_DATE, -1).let { if (it == -1L) null else it }

val MediaMetadata.cdTrackNumber: String?
    get() = extras?.getString(EXTRA_CD_TRACK_NUMBER)

val MediaMetadata.hdArtworkUri: Uri?
    get() = extras?.let { extras -> BundleCompat.getParcelable(extras,
        EXTRA_HD_ARTWORK_URI, Uri::class.java) }