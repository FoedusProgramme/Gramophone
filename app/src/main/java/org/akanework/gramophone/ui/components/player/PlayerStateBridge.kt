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

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.showsPause
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.player.PlayerUtilities.FULL_POLL_MS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.POSITION_POLL_MS

/**
 * Bridges the MediaController into [state]: the song, the play state and the modes from the
 * player's callbacks, and the timer and [clock]'s position from the one poll on [scope], which
 * runs while [polling] and the screen is started, faster while [expanded]. [onMediaChanged] is
 * called on each connection and song change, once [state] is up to date.
 */
internal class PlayerStateBridge(
    private val context: Context,
    private val controller: MediaControllerViewModel,
    private val lifecycle: Lifecycle,
    scope: CoroutineScope,
    private val state: PlayerSheetPlayerState,
    private val clock: PlaybackClock,
    private val polling: () -> Boolean,
    private val expanded: () -> Boolean,
    private val onMediaChanged: () -> Unit,
) : Player.Listener {

    private val instance: MediaController?
        get() = controller.get()

    init {
        controller.addRecreationalPlayerListener(lifecycle, this) {
            sync()
            // Back from the background: the position now, not on the next poll
            clock.sample()
            onMediaChanged()
        }
        scope.launch {
            while (isActive) {
                if (polling() && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) poll()
                delay(if (expanded()) FULL_POLL_MS else POSITION_POLL_MS)
            }
        }
    }

    private fun poll() {
        clock.sample()
        val timer = instance?.getTimer()
        state.timerActive = timer?.first != null || timer?.second == true
    }

    private fun sync() {
        val item = instance?.currentMediaItem
        state.isPlaying = instance?.isPlaying == true
        state.showPause = instance?.showsPause == true
        state.title = item?.mediaMetadata?.title
        state.artist = item?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist)
        state.artworkUri = item?.mediaMetadata?.artworkUri
        state.repeatMode = instance?.repeatMode ?: Player.REPEAT_MODE_OFF
        state.shuffleMode = instance?.shuffleModeEnabled == true
        state.isFavorite = (item?.mediaMetadata?.userRating as? HeartRating)?.isHeart == true
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        sync()
        clock.sample()
        onMediaChanged()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        state.isPlaying = isPlaying
        clock.onPlayingChanged(isPlaying)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: @Player.DiscontinuityReason Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) clock.onSeekReported()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        state.isPlaying = instance?.isPlaying == true
        state.showPause = instance?.showsPause == true
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        state.showPause = instance?.showsPause == true
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        state.repeatMode = repeatMode
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        state.shuffleMode = shuffleModeEnabled
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        state.isFavorite = (mediaMetadata.userRating as? HeartRating)?.isHeart == true
    }
}
