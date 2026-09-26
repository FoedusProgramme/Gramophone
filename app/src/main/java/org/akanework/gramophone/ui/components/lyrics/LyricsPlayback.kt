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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.Player
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.ui.MediaControllerViewModel
import kotlin.math.max

/**
 * What the lyrics need from playback: a position precise enough to draw word highlights from,
 * and a way to seek. Listens to the controller until [destroy] is called.
 */
internal class LyricsPlayback(private val controller: MediaControllerViewModel) : Player.Listener, LifecycleOwner {
    private var waitingForSeek = 0
    private var waitingForSeekPos = 0uL
    override val lifecycle = LifecycleRegistry(this)

    init {
        lifecycle.currentState = Lifecycle.State.CREATED
        controller.addRecreationalPlayerListener(lifecycle, this) {}
    }

    // TODO https://github.com/androidx/media/issues/1578
    fun getCurrentPosition(): ULong =
        if (waitingForSeek > 0) waitingForSeekPos else
            GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                ?.endedWorkaroundPlayer?.currentPosition?.toULong()
                ?: controller.get()?.currentPosition?.toULong() ?: 0uL

    fun isPlaying() = controller.get()?.isPlaying == true

    fun seekTo(position: ULong) {
        waitingForSeek = max(0, waitingForSeek) + 1
        waitingForSeekPos = position
        (GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer
            ?: controller.get())?.seekTo(position.toLong())
    }

    fun setPlayWhenReady(play: Boolean) {
        controller.get()?.playWhenReady = play
    }

    fun speed(): Float = controller.get()?.playbackParameters?.speed ?: 1f

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: @Player.DiscontinuityReason Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            waitingForSeek--
        }
    }

    fun destroy() {
        lifecycle.currentState = Lifecycle.State.DESTROYED
    }
}
