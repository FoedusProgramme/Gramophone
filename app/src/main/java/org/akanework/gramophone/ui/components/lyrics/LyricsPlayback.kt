/*
 *     Copyright (C) 2024 nift4
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

package org.akanework.gramophone.ui.components.lyrics

import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.player.PlaybackClock

/**
 * What the lyrics need from playback: the position to draw each frame at and seeking, both from
 * the player sheet's [clock] so the lyrics and the progress bar agree, and pausing.
 */
internal class LyricsPlayback(
    private val clock: PlaybackClock,
    private val controller: MediaControllerViewModel,
) {
    /** The position as last polled. Snapshot state: it changes as the poll moves it on. */
    val polledPosition: Long
        get() = clock.positionMs

    fun getCurrentPosition(): ULong = clock.positionNow().coerceAtLeast(0L).toULong()

    fun isPlaying() = clock.isPlaying

    fun seekTo(position: ULong) = clock.seekTo(position.toLong())

    fun setPlayWhenReady(play: Boolean) {
        controller.get()?.playWhenReady = play
    }

    fun speed(): Float = clock.speed
}
