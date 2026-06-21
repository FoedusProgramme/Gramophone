package org.akanework.gramophone.logic.utils.exoplayer

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.util.concurrent.ListenableFuture
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.QueueBoard
import org.akanework.gramophone.logic.utils.CircularShuffleOrder
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.json.JSONObject
import java.util.Objects


/**
 * If player in STATE_ENDED is resumed, state will be STATE_READY, on play button press it will
 * update to STATE_ENDED and only then media3 will wrap around playlist for us. This is a workaround
 * to restore STATE_ENDED as well and fake it for media3 until it indeed wraps around playlist.
 */
class EndedWorkaroundPlayer(
    exoPlayer: ExoPlayer,
    private val getLyric: () -> SemanticLyrics?,
    val queueBoard: QueueBoard
) : ForwardingSimpleBasePlayer(exoPlayer),
    Player.Listener {

    companion object {
        private const val TAG = "EndedWorkaroundPlayer"

    }

    private val remoteDeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()

    init {
        player.addListener(this)
    }

    val exoPlayer
        get() = player as ExoPlayer

    // TODO: can't we do this in a cleaner way?
    var nextShuffleOrder:
            ((firstIndex: Int, mediaItemCount: Int, EndedWorkaroundPlayer) -> CircularShuffleOrder)? =
        null
    var nextTitle: String? = null
    var currentTitle: String? = null
    var currentIsPinned = false
    var currentIsOriginal = false
    var isEnded = false
        set(value) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "isEnded set to $value (was $field)")
            }
            field = value
        }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == DISCONTINUITY_REASON_SEEK) {
            isEnded = false
        }
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
    }

    fun updateLyricNow() {
        if (BuildConfig.APPLICATION_ID == "com.tencent.qqmusic") {
            invalidateState()
        }
    }

    override fun getState(): State {
        var superState = super.state
        if (BuildConfig.APPLICATION_ID == "com.tencent.qqmusic") {
            // Oplus uses package name whitelist for their lockscreen lyric feature
            val lyric = getLyric()
            if (lyric != null && lyric is SemanticLyrics.SyncedLyrics) {
                superState = superState.buildUpon()
                    .setPlaylist(superState.timeline, superState.currentTracks,
                        superState.currentMetadata.buildUpon()
                            .setExtras((superState.currentMetadata.extras?.let { Bundle(it) }
                                ?: Bundle()).apply {
                                putString("lyricInfo", JSONObject().apply {
                                    put("songName", superState.currentMetadata.title)
                                    put("artist", superState.currentMetadata.artist)
                                    // Put lyric hash code into songId as well to be able to reset
                                    // lyrics if they load late or get changed.
                                    put("songId", superState.playlist.getOrNull(
                                        superState.currentMediaItemIndex)?.mediaItem?.mediaId
                                        .toString() + Objects.toIdentityString(lyric))
                                    // This can parse some odd Netease-specific JSON list or normal
                                    // LRC without bells and whistles (fwiw, the Netease format is
                                    // not even better than plain LRC), no word sync as of right now
                                    put("lyric", lyric.text.joinToString(
                                        "\n") {
                                        val s = it.start.toLong() / 1000
                                        "[%02d:%02d.%02d]".format(s / 60, s % 60,
                                            (it.start.toLong() % 1000) / 10) + it.text
                                    })
                                }.toString())
                            }).build()).build()
            }
        }
        if (isEnded) {
            if (superState.playerError != null) {
                isEnded = false
                return superState
            }
            return superState.buildUpon().setPlaybackState(STATE_ENDED).setIsLoading(false).build()
        }
        if (player.currentTimeline.isEmpty) {
            return superState.buildUpon().setDeviceInfo(remoteDeviceInfo).build()
        }
        return superState
    }

    fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
        title: String,
        pinned: Boolean,
        original: Boolean
    ) {
        cloneQueue(title, pinned, original)
        super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        currentIsOriginal = false
        return super.handleAddMediaItems(index, mediaItems)
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int
    ): ListenableFuture<*> {
        currentIsOriginal = false
        return super.handleMoveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun handleReplaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>
    ): ListenableFuture<*> {
        currentIsOriginal = false
        return super.handleReplaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        currentIsOriginal = false
        if (fromIndex == 0 && toIndex == Int.MAX_VALUE) { // clearMediaItems() -> delete queue
            currentTitle = null
        }
        return super.handleRemoveMediaItems(fromIndex, toIndex)
    }

    fun cloneQueue(newTitle: String, newIsPinned: Boolean, original: Boolean) {
        if (currentTitle == null && !exoPlayer.currentTimeline.isEmpty)
            throw IllegalArgumentException("have media items but current title is null, logic bug")
        else if (currentTitle != null && Flags.MQ_PREVIEW) {
            queueBoard.addQueue(
                currentTitle!!,
                ArrayList<MediaItem>(exoPlayer.mediaItemCount).apply {
                    for (i in 0..<exoPlayer.mediaItemCount) {
                        add(exoPlayer.getMediaItemAt(i))
                    }
                },
                exoPlayer.currentMediaItemIndex,
                exoPlayer.currentPosition,
                currentIsPinned,
                currentIsOriginal,
                CircularShuffleOrder.Persistent(exoPlayer.shuffleOrder as
                        CircularShuffleOrder),
                exoPlayer.playbackState == STATE_ENDED,
            )
        }
        currentTitle = newTitle
        currentIsPinned = newIsPinned
        currentIsOriginal = original
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<*> {
        if (nextTitle == null)
            throw IllegalArgumentException("setMediaItems called but nextTitle is null, logic bug")
        cloneQueue(nextTitle!!, newIsPinned = false, original = true)
        nextTitle = null
        return super.handleSetMediaItems(mediaItems, startIndex, startPositionMs)
    }
}