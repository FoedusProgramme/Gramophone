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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import org.akanework.gramophone.logic.GramophonePlaybackService
import kotlin.math.max

/**
 * The playback position the player sheet shows, the lyrics included, taken from one poll: the
 * progress bars and the lyric highlight agree, and seeks are handled in one place.
 *
 * [PlayerStateBridge]'s poll calls [sample], which smooths the player's position (see
 * [PositionSmoother]) into [positionMs]: snapshot state the bars and labels read, and the lyrics
 * watch to know when to draw again. [positionNow] moves it on to this very moment for drawing
 * every frame. A seek shows its target at once and holds it until the player reports the seek.
 * Only touched on the main thread.
 */
@Stable
class PlaybackClock internal constructor(
    private val player: () -> Player?,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private val smoother = PositionSmoother(clock)

    /** The position (ms) as last sampled, smoothed. */
    var positionMs by mutableLongStateOf(0L)
        private set

    /** The song's length (ms), 0 before it's known. */
    var durationMs by mutableLongStateOf(0L)
        private set

    /** How far through the song [positionMs] is, 0 to 1. */
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isPlaying: Boolean
        get() = player()?.isPlaying == true

    val speed: Float
        get() = player()?.playbackParameters?.speed ?: 1f

    // Seeks sent that the player hasn't reported yet, the position to show meanwhile and when
    // the last one was sent. Seeks from elsewhere are reported too, so the count can go below 0;
    // a new seek starts again from 0.
    private var pendingSeeks = 0
    private var seekTarget = 0L
    private var seekAt = 0L

    // The last position handed out by positionNow; the song and time of the last sample
    private var lastNow = -1L
    private var mediaId: String? = null
    private var sampledAt = Long.MIN_VALUE

    /** Shows [positionMs] of [durationMs] until the first sample, as after a recreation. */
    internal fun restore(positionMs: Long, durationMs: Long) {
        this.positionMs = positionMs
        this.durationMs = durationMs
    }

    /** Takes the player's position; called by the poll. */
    internal fun sample() {
        val player = player() ?: return
        val duration = player.duration.takeIf { it > 0 }
            ?: player.currentMediaItem?.mediaMetadata?.durationMs
        // Unknown for a moment at a song change: keep the last one for the bars meanwhile
        if (duration != null && duration > 0) durationMs = duration
        val id = player.currentMediaItem?.mediaId
        if (id != mediaId) {
            mediaId = id
            lastNow = -1
        }
        sampledAt = clock()
        // Until the player reports the seek, its position may still be the one before it
        if (seeking()) return
        positionMs = smoother.update(
            player.currentPosition, player.isPlaying, player.playbackParameters.speed, id,
        )
    }

    /**
     * The position (ms) at this moment, for drawing every frame. While playing it doesn't step
     * back by the small corrections of the smoothing, which would show as the lyrics going back.
     */
    fun positionNow(): Long {
        if (seeking()) return seekTarget
        // Nothing to go by since the poll stopped (the screen was off, the controller away) or
        // since the song changed: the player's own position until the poll catches up
        val player = player()
        if (player != null && (clock() - sampledAt > STALE_SAMPLE_MS ||
                player.currentMediaItem?.mediaId != mediaId)
        ) {
            lastNow = player.currentPosition
            return lastNow
        }
        val predicted = smoother.now()
        val now = if (predicted >= 0) predicted else player()?.currentPosition ?: 0L
        // A little ahead while playing is a correction to sit out, more is a real jump
        val held = smoother.isPlaying && lastNow - now in 1..PositionSmoother.SNAP_MS
        if (!held) lastNow = now
        return lastNow
    }

    /** Whether a seek is on its way. One the player never reports stops counting after a while. */
    private fun seeking(): Boolean {
        if (pendingSeeks > 0 && clock() - seekAt > SEEK_REPORT_TIMEOUT_MS) pendingSeeks = 0
        return pendingSeeks > 0
    }

    /** Seeks to [ms], shown at once rather than on the next poll. */
    fun seekTo(ms: Long) {
        val player = player() ?: return
        pendingSeeks = max(0, pendingSeeks) + 1
        seekTarget = ms
        seekAt = clock()
        player.seekTo(ms)
        smoother.reset(ms)
        positionMs = ms
        lastNow = -1
    }

    /** The player reported a seek, this clock's or anyone else's. */
    internal fun onSeekReported() {
        pendingSeeks--
    }

    /** Playback started or stopped just now. */
    internal fun onPlayingChanged(playing: Boolean) {
        smoother.setPlaying(playing)
        lastNow = -1
    }

    companion object {
        /** How long a seek may take to be reported before the player's position counts again. */
        private const val SEEK_REPORT_TIMEOUT_MS = 1000L

        /** A sample older than this (twice the slowest poll) says nothing of where playback is. */
        private const val STALE_SAMPLE_MS = PlayerUtilities.POSITION_POLL_MS * 2

        /**
         * The player whose position the clock follows: the service's own when it runs in this
         * process, as its position is exact while the controller's can lag
         * (https://github.com/androidx/media/issues/1578), else [controller]'s.
         */
        internal fun playerFor(controller: () -> Player?): () -> Player? = {
            GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer
                ?: controller()
        }
    }
}
