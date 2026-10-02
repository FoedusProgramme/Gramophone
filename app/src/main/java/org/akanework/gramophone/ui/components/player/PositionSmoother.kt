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

package org.akanework.gramophone.ui.components.player

import android.os.SystemClock
import kotlin.math.abs

/**
 * Smooths the playback position the player sheet shows. Around a resume the reported position runs
 * ahead by up to ~150 ms and is corrected back about a second later, once the audio output
 * reports its real playback position. Shown as is, the bar jumps forward and back on every
 * pause/resume. Instead the shown position advances at playback speed while playing and eases
 * towards the reported one. Large differences (seeks, song changes) are shown at once. [clock]
 * gives the time in ms.
 */
internal class PositionSmoother(private val clock: () -> Long = SystemClock::uptimeMillis) {
    private var shown = -1L
    private var shownAt = 0L
    private var playing = false
    private var speed = 1f
    private var mediaId: String? = null

    /** Whether the last sample or [setPlaying] said playback runs. */
    val isPlaying: Boolean
        get() = playing

    /**
     * The position at this moment: the last one shown, moved on at playback speed while playing.
     * -1 before the first [update].
     */
    fun now(): Long = when {
        shown < 0 -> -1
        playing -> shown + ((clock() - shownAt) * speed).toLong()
        else -> shown
    }

    fun update(raw: Long, playing: Boolean, speed: Float, mediaId: String?): Long {
        val now = clock()
        shown = if (shown < 0 || mediaId != this.mediaId) raw else {
            val predicted = if (playing) shown + ((now - shownAt) * speed).toLong() else shown
            val error = raw - predicted
            if (abs(error) > SNAP_MS || abs(error) < MIN_EASE_MS) raw
            else predicted + error / EASE_DIVISOR
        }
        shownAt = now
        this.playing = playing
        this.speed = speed
        this.mediaId = mediaId
        return shown
    }

    /**
     * Playback started or stopped just now, between two samples: predictions move on from this
     * moment rather than from the last sample.
     */
    fun setPlaying(playing: Boolean) {
        if (playing == this.playing) return
        if (shown >= 0) {
            shown = now()
            shownAt = clock()
        }
        this.playing = playing
    }

    /** Shows [position] from now on, as after a seek. */
    fun reset(position: Long) {
        shown = position
        shownAt = clock()
    }

    internal companion object {
        /** Differences beyond this are real jumps, shown at once. */
        const val SNAP_MS = 500L
        /** Share of the remaining difference taken per poll. */
        const val EASE_DIVISOR = 8
        /** Differences below this are taken at once: a share of them would round to nothing. */
        const val MIN_EASE_MS = EASE_DIVISOR
    }
}
