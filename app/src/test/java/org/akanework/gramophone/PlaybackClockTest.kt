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

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.akanework.gramophone.ui.components.player.PlaybackClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The shared playback position: what the bars get from each poll, and what the lyrics get when
 * they draw in between, around resumes, seeks, corrections and song changes. The player is a
 * fake whose position, play state and song the tests set, and the time is the tests' own.
 */
@RunWith(RobolectricTestRunner::class)
class PlaybackClockTest {

    /** A player that reports what the test sets, and records seeks. */
    private class FakePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        var position = 0L
        var playing = true
        var song = "a"
        val seeks = mutableListOf<Long>()

        /** Changes what the player reports. */
        fun set(block: FakePlayer.() -> Unit) {
            block()
            invalidateState()
        }

        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(
                listOf(
                    MediaItemData.Builder(song)
                        .setMediaItem(MediaItem.Builder().setMediaId(song).build())
                        .setDurationUs(300_000_000L)
                        .build()
                )
            )
            .setContentPositionMs { position }
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackParameters(PlaybackParameters.DEFAULT)
            .build()

        override fun handleSeek(
            mediaItemIndex: Int,
            positionMs: Long,
            seekCommand: @Player.Command Int,
        ): ListenableFuture<*> {
            seeks += positionMs
            position = positionMs
            return Futures.immediateVoidFuture()
        }
    }

    private var now = 0L
    private val player = FakePlayer()
    private val clock = PlaybackClock({ player }) { now }

    /** Moves time on by [ms]. */
    private fun advance(ms: Long) {
        now += ms
    }

    /** Polls with the player at [position], [ms] after the last poll. */
    private fun poll(position: Long, ms: Long = 100) {
        advance(ms)
        player.set { this.position = position }
        clock.sample()
    }

    @Test
    fun pollIsWhatTheBarsShow() {
        poll(1000)
        assertEquals(1000L, clock.positionMs)
        assertEquals(300_000L, clock.durationMs)
        assertEquals(1000f / 300_000f, clock.fraction)
    }

    @Test
    fun drawingBetweenPollsMovesOnWithPlayback() {
        poll(1000)
        advance(40)
        assertEquals(1040L, clock.positionNow())
    }

    @Test
    fun pausedPositionStaysPut() {
        poll(1000)
        player.set { playing = false }
        clock.onPlayingChanged(false)
        val paused = clock.positionNow()
        advance(300)
        assertEquals(paused, clock.positionNow())
    }

    @Test
    fun resumeMovesOnFromTheMomentPlaybackStarted() {
        player.set { playing = false }
        poll(1000)
        advance(60)
        player.set { playing = true }
        clock.onPlayingChanged(true)
        advance(40)
        // 40 ms of playback since the resume, not the 100 since the last poll
        assertEquals(1040L, clock.positionNow())
    }

    @Test
    fun seekShowsTheTargetUntilThePlayerReportsIt() {
        poll(1000)
        clock.seekTo(30_000)
        assertEquals(listOf(30_000L), player.seeks)
        assertEquals(30_000L, clock.positionMs)
        // The player still says where it was: neither the bar nor the lyrics go back there
        player.set { position = 1000 }
        poll(1000)
        assertEquals(30_000L, clock.positionMs)
        assertEquals(30_000L, clock.positionNow())

        clock.onSeekReported()
        // Played on from the target since the seek, 200 ms ago
        poll(30_200)
        assertEquals(30_200L, clock.positionMs)
    }

    @Test
    fun unreportedSeekStopsHoldingTheTarget() {
        poll(1000)
        clock.seekTo(30_000)
        poll(30_500, ms = 500)
        advance(600)
        // Over a second since the seek: played on from the target
        assertEquals(31_100L, clock.positionNow())
    }

    @Test
    fun smallCorrectionsDontStepBackWhileDrawing() {
        poll(1000)
        advance(90)
        val drawn = clock.positionNow()
        // The next poll finds the player behind where playback was predicted to be
        poll(1050, ms = 10)
        assertTrue(clock.positionNow() >= drawn)
    }

    @Test
    fun afterTheScreenWasOffDrawingTakesThePlayersPosition() {
        poll(1000)
        // The poll stopped: meanwhile playback was paused somewhere else, minutes ago
        advance(180_000)
        player.set {
            playing = false
            position = 42_000
        }
        assertEquals(42_000L, clock.positionNow())
    }

    @Test
    fun anotherSongIsDrawnFromThePlayerUntilThePollSeesIt() {
        poll(200_000)
        player.set {
            song = "b"
            position = 20
        }
        advance(30)
        assertEquals(20L, clock.positionNow())
    }

    @Test
    fun jumpsAndSongChangesShowAtOnce() {
        poll(60_000)
        poll(10_000)
        assertEquals(10_000L, clock.positionNow())

        player.set { song = "b" }
        poll(150)
        assertEquals(150L, clock.positionNow())
    }
}
