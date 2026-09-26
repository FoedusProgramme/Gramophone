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
import android.net.Uri
import org.akanework.gramophone.ui.components.player.seedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ArtworkSeedKeyTest {

    private val base = "content://org.akanework.gramophone.albumart/song/78?songFile=%2FMusic%2Fa.flac"

    @Test
    fun hdCoverSharesTheFullCoversKey() {
        assertEquals(Uri.parse(base), seedKey(Uri.parse("$base&hd=1")))
    }

    @Test
    fun coverWithoutHdIsItsOwnKey() {
        val uri = Uri.parse(base)
        assertSame(uri, seedKey(uri))
    }

    @Test
    fun otherParametersAreKept() {
        val key = seedKey(Uri.parse("content://a/song/1?hd=1&x=1&x=2&songFile=f"))
        assertEquals(listOf("1", "2"), key.getQueryParameters("x"))
        assertEquals("f", key.getQueryParameter("songFile"))
        assertEquals(null, key.getQueryParameter("hd"))
    }
}
