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

package org.akanework.gramophone

import android.app.Application
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.akanework.gramophone.ui.screens.songsSpanAlbums
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.akane.libphonograph.items.EXTRA_ALBUM_ID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SongsSpanAlbumsTest {

    private fun song(albumId: Long?): MediaItem = MediaItem.Builder()
        .setMediaId("song:$albumId:${System.nanoTime()}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setExtras(albumId?.let { Bundle().apply { putLong(EXTRA_ALBUM_ID, it) } })
                .build()
        )
        .build()

    @Test
    fun emptyListIsOneAlbum() {
        assertFalse(songsSpanAlbums(emptyList()))
    }

    @Test
    fun sameAlbumIdIsOneAlbum() {
        assertFalse(songsSpanAlbums(listOf(song(7), song(7), song(7))))
    }

    @Test
    fun missingIdsAreSkipped() {
        assertFalse(songsSpanAlbums(listOf(song(null), song(null))))
        assertFalse(songsSpanAlbums(listOf(song(null), song(7), song(null), song(7))))
    }

    @Test
    fun differentAlbumIdsSpanAlbums() {
        assertTrue(songsSpanAlbums(listOf(song(7), song(7), song(8))))
        assertTrue(songsSpanAlbums(listOf(song(null), song(3), song(4))))
    }
}
