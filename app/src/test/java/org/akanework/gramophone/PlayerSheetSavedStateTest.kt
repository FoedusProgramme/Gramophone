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

import androidx.compose.runtime.saveable.SaverScope
import org.akanework.gramophone.ui.components.player.PlayerSheetSavedState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSheetSavedStateTest {

    private val scope = SaverScope { true }

    @Test
    fun roundTripsThroughSaver() {
        val state = PlayerSheetSavedState(
            expanded = true,
            fraction = 0.25f,
            positionMs = 61_000L,
            durationMs = 244_000L,
        )
        val saved = with(PlayerSheetSavedState.Saver) { scope.save(state) }!!
        assertEquals(state, PlayerSheetSavedState.Saver.restore(saved))
    }

    @Test
    fun roundTripsCollapsedDefaults() {
        val state = PlayerSheetSavedState(false, 0f, 0L, 0L)
        val saved = with(PlayerSheetSavedState.Saver) { scope.save(state) }!!
        assertEquals(state, PlayerSheetSavedState.Saver.restore(saved))
    }
}
