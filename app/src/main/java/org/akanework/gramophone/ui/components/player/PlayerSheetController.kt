package org.akanework.gramophone.ui.components.player

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.NowPlayingController
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlayState
import org.akanework.gramophone.ui.nav.SongDetailKey
import uk.akane.libphonograph.manipulator.PlaylistSerializer

/**
 * Hosts the player sheet. Bridges the MediaController into the sheet state, owns the back callback
 * and the list padding for the mini bar, and draws the sheet in [Content], which the activity
 * composes above its pages. Created in the activity's onCreate, released in onDestroy.
 */
class PlayerSheetController(private val activity: MainActivity) :
    Player.Listener, DefaultLifecycleObserver {

    companion object {
        private const val STATE_KEY = "player_sheet"
        private const val STATE_EXPANDED = "expanded"
        private const val STATE_FRACTION = "fraction"
        private const val STATE_POSITION = "position"
        private const val STATE_DURATION = "duration"
    }

    @SuppressLint("RestrictedApi")
    private var bottomSheetBackCallback: OnBackPressedCallback? = null

    // The whole expand/collapse animation is driven by a single 0..1 progress. The scope uses
    // AndroidUiDispatcher.Main because Compose's animate() needs its MonotonicFrameClock (which
    // rememberCoroutineScope() provides inside a composition).
    private val sheetScope = CoroutineScope(AndroidUiDispatcher.Main)
    private val sheetState = NowPlayingSheetState(sheetScope)
    private val playerState = PlayerSheetPlayerState()
    private val chromeState =
        mutableStateOf(SheetChrome(shown = false, left = 0, right = 0, top = 0, bottom = 0))

    private val lyrics = LyricsOverlayState(sheetScope)
    private val nowPlaying: NowPlayingController

    private val instance: MediaController?
        get() = activity.getPlayer()
    private val queueOpen = mutableStateOf(false)
    private var pendingExpanded = false

    var visible = false
        set(value) {
            if (field != value) {
                field = value
                refreshVisibility()
            }
        }

    private val fullPlayerActions = FullPlayerActions(
        playPause = { instance?.playOrPause() },
        previous = { instance?.seekToPrevious() },
        next = { instance?.seekToNext() },
        seekBack = { instance?.seekBack() },
        seekForward = { instance?.seekForward() },
        seekTo = { ms -> instance?.seekTo(ms) },
        minimize = { sheetState.collapse() },
        cycleRepeat = {
            instance?.let { c ->
                c.repeatMode = when (c.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        },
        toggleShuffle = { on -> instance?.shuffleModeEnabled = on },
        toggleFavorite = { on ->
            instance?.currentMediaItem?.let { song ->
                PlaylistSerializer.Entry.ofMediaItem(song)?.let { activity.markIsFavoriteStatus(listOf(it), on) }
            }
        },
        showQueue = { nowPlaying.showQueue() },
        showLyrics = { lyrics.fadeIn() },
        openAlbum = { nowPlaying.openAlbumPage() },
        openArtist = { nowPlaying.openArtistPage() },
    )

    // Callbacks for the timer and speed dialogs (MediaController and prefs)
    private val prefs = activity.defaultPrefs
    private val dialogCallbacks = PlayerDialogCallbacks(
        currentSpeed = { instance?.playbackParameters?.speed ?: 1f },
        currentPitch = { instance?.playbackParameters?.pitch ?: 1f },
        setSpeedPitch = { s, p -> instance?.playbackParameters = PlaybackParameters(s, p) },
        timerRemainingMs = { instance?.getTimer()?.first?.toLong() },
        timerEndOfSong = { instance?.getTimer()?.second == true },
        setTimer = { d, eos -> instance?.setTimer(d, eos) },
        getBool = { key, def -> prefs.getBooleanStrict(key, def) },
        putBool = { key, value -> prefs.edit { putBoolean(key, value) } },
    )

    init {
        // Restore the expanded state and playback position after activity recreation.
        activity.savedStateRegistry.consumeRestoredStateForKey(STATE_KEY)?.let { state ->
            pendingExpanded = state.getBoolean(STATE_EXPANDED, false)
            playerState.positionFraction.value = state.getFloat(STATE_FRACTION, 0f)
            playerState.positionMs.value = state.getLong(STATE_POSITION, 0L)
            playerState.durationMs.value = state.getLong(STATE_DURATION, 0L)
        }
        activity.savedStateRegistry.registerSavedStateProvider(STATE_KEY) {
            Bundle().apply {
                putBoolean(STATE_EXPANDED, sheetState.expandedTarget)
                putFloat(STATE_FRACTION, playerState.positionFraction.value)
                putLong(STATE_POSITION, playerState.positionMs.value)
                putLong(STATE_DURATION, playerState.durationMs.value)
            }
        }

        sheetScope.launch {
            snapshotFlow { lyrics.covering }.collect { playerState.lyricsCovering.value = it }
        }
        nowPlaying = NowPlayingController(
            activity = activity,
            updateLyrics = { lyrics.lyrics = it },
            minimize = { sheetState.collapse() },
            onQualityChanged = { icon, text ->
                playerState.qualityIcon.value = icon
                playerState.qualityText.value = text
            },
            openQueue = { queueOpen.value = true },
            closeQueue = { queueOpen.value = false },
        )

        activity.controllerViewModel.addRecreationalPlayerListener(activity.lifecycle, this) {
            syncPlayerState()
            refreshVisibility()
            nowPlaying.refreshLyrics()
        }
        activity.lifecycle.addObserver(this)

        sheetScope.launch {
            while (isActive) {
                if (chromeState.value.shown) {
                    val inst = instance
                    val duration = inst?.duration?.takeIf { it > 0 }
                        ?: inst?.currentMediaItem?.mediaMetadata?.durationMs
                    if (inst != null && duration != null && duration > 0) {
                        val position = inst.currentPosition
                        playerState.positionMs.value = position
                        playerState.durationMs.value = duration
                        playerState.positionFraction.value =
                            (position.toFloat() / duration).coerceIn(0f, 1f)
                    }
                    val timer = inst?.getTimer()
                    playerState.timerActive.value = timer?.first != null || timer?.second == true
                    if (sheetState.expandedTarget) lyrics.updateLyricPositionFromPlaybackPos()
                }
                delay(if (sheetState.expandedTarget) PlayerUtilities.FULL_POLL_MS else PlayerUtilities.POSITION_POLL_MS)
            }
        }
    }

    /**
     * The sheet, plus the queue when open. Composed after the pages so it draws over them and its
     * back callback takes priority over theirs.
     */
    @Composable
    fun Content() {
        // The mini bar applies the system bar and cutout insets itself. The sheet spans them.
        val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val left = insets.getLeft(density, direction)
        val right = insets.getRight(density, direction)
        val top = insets.getTop(density)
        val bottom = insets.getBottom(density)
        SideEffect {
            val chrome = chromeState.value
            if (chrome.left != left || chrome.right != right || chrome.top != top || chrome.bottom != bottom) {
                chromeState.value = chrome.copy(left = left, right = right, top = top, bottom = bottom)
                dispatchBottomPadding()
            }
        }
        DisposableEffect(Unit) {
            val callback = object : OnBackPressedCallback(enabled = sheetState.expandedTarget) {
                override fun handleOnBackStarted(backEvent: BackEventCompat) {
                    if (lyrics.visible) lyrics.onBackStarted()
                }

                override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                    if (lyrics.visible) {
                        lyrics.onBackProgressed(backEvent.progress)
                    } else {
                        sheetState.onBackProgress(backEvent.progress)
                    }
                }

                override fun handleOnBackPressed() {
                    if (lyrics.visible) {
                        lyrics.onBackPressed()
                    } else {
                        sheetState.collapse()
                    }
                }

                override fun handleOnBackCancelled() {
                    if (lyrics.visible) {
                        lyrics.onBackCancelled()
                    } else {
                        sheetState.expand()
                    }
                }
            }
            bottomSheetBackCallback = callback
            activity.onBackPressedDispatcher.addCallback(activity, callback)
            onDispose {
                callback.remove()
                bottomSheetBackCallback = null
            }
        }
        PlayerSheet(
            state = sheetState,
            player = playerState,
            chrome = chromeState,
            pageAccent = { activity.navViewModel.topAccent },
            lyrics = lyrics,
            onPlayPause = { instance?.playOrPause() },
            onNext = { instance?.seekToNext() },
            onCoverClick = {
                instance?.currentMediaItem?.mediaId?.let { activity.navigateTo(SongDetailKey(it)) }
            },
            onExpandedTargetChanged = { expanded ->
                bottomSheetBackCallback?.isEnabled = expanded
            },
            actions = fullPlayerActions,
            dialogCallbacks = dialogCallbacks,
        )
        if (queueOpen.value) {
            QueueSheet(activity, onDismiss = { queueOpen.value = false })
        }
    }

    fun open() {
        if (chromeState.value.shown) {
            sheetState.expand()
        }
    }

    /** Stops the sheet's work when the activity is destroyed. */
    fun release() {
        nowPlaying.release()
        activity.lifecycle.removeObserver(this)
        sheetScope.cancel()
        onStop(activity)
    }

    private fun syncPlayerState() {
        val item = instance?.currentMediaItem
        playerState.isPlaying.value = instance?.isPlaying == true
        playerState.title.value = item?.mediaMetadata?.title
        playerState.artist.value =
            item?.mediaMetadata?.artist ?: activity.getString(R.string.unknown_artist)
        playerState.artworkUri.value = item?.mediaMetadata?.artworkUri
        playerState.repeatMode.value = instance?.repeatMode ?: Player.REPEAT_MODE_OFF
        playerState.shuffleMode.value = instance?.shuffleModeEnabled == true
        playerState.isFavorite.value =
            (item?.mediaMetadata?.userRating as? HeartRating)?.isHeart == true
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        playerState.repeatMode.value = repeatMode
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        playerState.shuffleMode.value = shuffleModeEnabled
    }

    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
        playerState.isFavorite.value = (mediaMetadata.userRating as? HeartRating)?.isHeart == true
    }

    private fun refreshVisibility() {
        val hasMedia = (instance?.mediaItemCount ?: 0) > 0
        playerState.hasMedia.value = hasMedia
        val show = visible && hasMedia
        if (chromeState.value.shown != show) {
            chromeState.value = chromeState.value.copy(shown = show)
            if (!show) {
                sheetState.snapToCollapsed()
            } else if (pendingExpanded) {
                pendingExpanded = false
                sheetState.snapToExpanded()
            }
        }
        dispatchBottomPadding()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playerState.isPlaying.value = isPlaying
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        playerState.isPlaying.value = instance?.isPlaying == true
    }

    /**
     * Height of the floating mini bar from the screen bottom in px: its margin above the navigation
     * bar plus the 56dp card. Used as the bottom padding for lists. Must match
     * PlayerSheetMetrics.collapsedFootprint (MINI_HEIGHT).
     */
    private fun collapsedHeightPx(): Int {
        val d = activity.resources.displayMetrics.density
        val nav = chromeState.value.bottom
        val platform = maxOf(nav + (8 * d).toInt(), (24 * d).toInt())
        return platform + (56 * d).toInt()
    }

    /** Publishes the bottom padding the mini bar needs, or 0 when it is hidden. */
    private fun dispatchBottomPadding() {
        activity.playerBottomPadding.intValue =
            if (chromeState.value.shown) collapsedHeightPx() else 0
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        syncPlayerState()
        refreshVisibility()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        nowPlaying.onStop()
    }
}
