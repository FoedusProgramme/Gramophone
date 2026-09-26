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

import android.Manifest
import org.akanework.gramophone.ui.components.compose.requiredLibraryPermissions
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class LibraryPermissionsTest {

    @Test
    fun api23AsksReadAndWrite() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            requiredLibraryPermissions(23)
        )
    }

    @Test
    fun api29AsksReadAndWrite() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            requiredLibraryPermissions(29)
        )
    }

    @Test
    fun api30AsksReadOnly() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            requiredLibraryPermissions(30)
        )
    }

    @Test
    fun api33AsksReadMediaAudio() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO),
            requiredLibraryPermissions(33)
        )
    }
}
