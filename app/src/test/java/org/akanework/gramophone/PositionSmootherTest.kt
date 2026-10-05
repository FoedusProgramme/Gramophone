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

import org.akanework.gramophone.ui.components.player.PositionSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSmootherTest {

    private var now = 0L
    private val smoother = PositionSmoother { now }

    /** Polls [raw] [elapsed] ms after the last poll. */
    private fun poll(
        raw: Long,
        elapsed: Long = 100,
        playing: Boolean = true,
        speed: Float = 1f,
        id: String = "a",
    ): Long {
        now += elapsed
        return smoother.update(raw, playing, speed, mediaId = id)
    }

    @Test
    fun firstPositionIsShownAsIs() {
        assertEquals(1234L, poll(1234))
    }

    @Test
    fun overshootAfterResumeEasesBack() {
        poll(1000)
        // Reported 120 ms ahead of where playback at speed 1 would be
        val shown = poll(1220)
        assertEquals(1100L + 120 / PositionSmoother.EASE_DIVISOR, shown)
    }

    @Test
    fun resumeOvershootAndCorrectionConverge() {
        var position = 1000L
        poll(position, playing = false)
        val shown = mutableListOf<Long>()
        // After the resume the reported position runs 150 ms ahead for a second, then it's
        // corrected back to where playback really is.
        repeat(10) {
            position += 100
            shown += poll(position + 150)
        }
        repeat(30) {
            position += 100
            shown += poll(position)
        }
        shown.zipWithNext { before, after ->
            assertTrue("went back from $before to $after", after >= before)
        }
        assertEquals(position, shown.last())
    }

    @Test
    fun playbackSpeedIsPredicted() {
        poll(1000, speed = 2f)
        // At twice the speed, 100 ms move 200 ms through the song
        assertEquals(1200L, poll(1200, speed = 2f))
        assertEquals(1400L + 40 / PositionSmoother.EASE_DIVISOR, poll(1440, speed = 2f))
    }

    @Test
    fun smallDifferencesAreTakenAtOnce() {
        poll(1000)
        assertEquals(1105L, poll(1105))
    }

    @Test
    fun jumpsAreShownAtOnce() {
        poll(1000)
        assertEquals(60_000L, poll(60_000))
        assertEquals(500L, poll(500))
    }

    @Test
    fun songChangeIsShownAtOnce() {
        poll(90_000)
        assertEquals(90_150L, poll(90_150, id = "b"))
    }

    @Test
    fun pausedPositionDoesNotAdvance() {
        poll(1000, playing = false)
        assertEquals(1000L, poll(1000, elapsed = 400, playing = false))
    }

    @Test
    fun resetShowsTheSeekTarget() {
        poll(1000)
        smoother.reset(30_000)
        // Eased from the seek target: from the position before it, this would be a jump
        assertEquals(30_100L + 80 / PositionSmoother.EASE_DIVISOR, poll(30_180))
    }
}
