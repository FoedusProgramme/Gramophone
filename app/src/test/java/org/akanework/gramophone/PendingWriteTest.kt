/*
 *     Copyright (C) 2026 The Gramophone authors
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

package org.akanework.gramophone

import android.app.Application
import android.net.Uri
import android.os.Parcel
import androidx.core.os.ParcelCompat
import org.akanework.gramophone.logic.library.PendingWrite
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry

@Config(application = Application::class)
@RunWith(RobolectricTestRunner::class)
class PendingWriteTest {

    private val songs = listOf(
        Entry(
            locations = listOf(Uri.parse("file:///storage/emulated/0/Music/a.flac")),
            title = "A",
            tvKeys = listOf("tvg-id" to "1"),
            artist = "Artist",
        ),
        Entry(locations = listOf(Uri.parse("content://media/external/audio/media/42"))),
    )
    private val playlistUri = Uri.parse("content://media/external/audio/playlists/7")

    private fun roundTrip(write: PendingWrite): PendingWrite {
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(write, 0)
            parcel.setDataPosition(0)
            return ParcelCompat.readParcelable(
                parcel, PendingWrite::class.java.classLoader, PendingWrite::class.java
            )!!
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun addToExistingPlaylistRoundTrips() {
        val write = PendingWrite.AddToPlaylist(songs, playlistUri, null)
        assertEquals(write, roundTrip(write))
    }

    @Test
    fun addToNewPlaylistRoundTrips() {
        val write = PendingWrite.AddToPlaylist(songs, null, "/storage/emulated/0/Music/new.m3u")
        assertEquals(write, roundTrip(write))
    }

    @Test
    fun favoriteRoundTrips() {
        val write = PendingWrite.Favorite(songs, playlistUri, favorite = true)
        assertEquals(write, roundTrip(write))
    }

    @Test
    fun unfavoriteWithoutFavoritesPlaylistRoundTrips() {
        val write = PendingWrite.Favorite(emptyList(), null, favorite = false)
        assertEquals(write, roundTrip(write))
    }

    @Test
    fun deleteRoundTrips() {
        assertEquals(PendingWrite.Delete, roundTrip(PendingWrite.Delete))
    }

    @Test
    fun renameRoundTrips() {
        val write = PendingWrite.Rename(7L, "/storage/emulated/0/Music/renamed.m3u")
        assertEquals(write, roundTrip(write))
    }
}
