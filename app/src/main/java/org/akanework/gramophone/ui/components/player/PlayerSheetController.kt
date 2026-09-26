package org.akanework.gramophone.ui.components.player

import kotlin.math.abs
import android.os.SystemClock
import org.akanework.gramophone.logic.showsPause
import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.NowPlayingController
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlayState
import org.akanework.gramophone.ui.nav.NavViewModel
import org.akanework.gramophone.ui.nav.SongDetailKey
import org.koin.compose.viewmodel.koinActivityViewModel
import uk.akane.libphonograph.manipulator.PlaylistSerializer

/** What the pages may ask of the player sheet. */
interface PlayerSheetHandle {
    /** Expands the sheet, if the mini player is showing. */
    fun open()
}

/** The player sheet of the screen. No-op outside the app root (previews). */
val LocalPlayerSheet = staticCompositionLocalOf<PlayerSheetHandle> {
    object : PlayerSheetHandle {
        override fun open() {}
    }
}

/** The sheet state kept across activity recreation and process death. */
data class PlayerSheetSavedState(
    val expanded: Boolean,
    val fraction: Float,
    val positionMs: Long,
    val durationMs: Long,
) {
    companion object {
        val Saver: Saver<PlayerSheetSavedState, Any> = listSaver(
            save = { listOf(it.expanded, it.fraction, it.positionMs, it.durationMs) },
            restore = {
                PlayerSheetSavedState(it[0] as Boolean, it[1] as Float, it[2] as Long, it[3] as Long)
            },
        )
    }
}

/**
 * The player sheet controller of the screen, owned by the composition: its state survives
 * recreation through rememberSaveable, and it is released when the composition goes away.
 * [toggleFavorite] marks songs as favorite or not.
 */
@Composable
fun rememberPlayerSheetController(
    toggleFavorite: (List<PlaylistSerializer.Entry>, Boolean) -> Unit,
): PlayerSheetController {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val controllerViewModel = koinActivityViewModel<MediaControllerViewModel>()
    val navViewModel = koinActivityViewModel<NavViewModel>()
    val currentToggleFavorite by rememberUpdatedState(toggleFavorite)
    val create = { restored: PlayerSheetSavedState? ->
        PlayerSheetController(
            context = context,
            controller = controllerViewModel,
            navViewModel = navViewModel,
            lifecycle = lifecycle,
            density = density,
            toggleFavorite = { songs, on -> currentToggleFavorite(songs, on) },
            restored = restored,
        )
    }
    val sheet = rememberSaveable(saver = PlayerSheetController.saver(create)) { create(null) }
    DisposableEffect(sheet) {
        lifecycle.addObserver(sheet)
        onDispose { sheet.release() }
    }
    return sheet
}

/**
 * Hosts the player sheet. Bridges the MediaController into the sheet state, owns the back callback
 * and the list padding for the mini bar, and draws the sheet in [Content], which the app root
 * composes above its pages. Created by [rememberPlayerSheetController].
 */
@Stable
class PlayerSheetController internal constructor(
    private val context: Context,
    private val controller: MediaControllerViewModel,
    private val navViewModel: NavViewModel,
    private val lifecycle: Lifecycle,
    private var density: Float,
    private val toggleFavorite: (List<PlaylistSerializer.Entry>, Boolean) -> Unit,
    restored: PlayerSheetSavedState?,
) : Player.Listener, DefaultLifecycleObserver, PlayerSheetHandle {

    companion object {
        /** Saves the sheet state, and restores it into a controller made by [create]. */
        fun saver(
            create: (PlayerSheetSavedState?) -> PlayerSheetController,
        ): Saver<PlayerSheetController, Any> = Saver(
            save = { with(PlayerSheetSavedState.Saver) { save(it.savedState()) } },
            restore = { create(PlayerSheetSavedState.Saver.restore(it)) },
        )
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
        get() = controller.get()
    private val queueOpen = mutableStateOf(false)
    private val coversScreenCheck = mutableStateOf<() -> Boolean>({ false })

    /**
     * Whether the sheet covers the whole screen, so the pages under it needn't be drawn. Reads
     * snapshot state: read it in the draw phase to be redrawn when the sheet starts to move.
     */
    val coversScreen: Boolean
        get() = coversScreenCheck.value()
    private var pendingExpanded = false
    private val positionSmoother = PositionSmoother()

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
        seekTo = { ms ->
            instance?.seekTo(ms)
            // Show the new position now, not on the next poll
            positionSmoother.reset(ms)
            playerState.positionMs.value = ms
            playerState.durationMs.value.takeIf { it > 0 }?.let {
                playerState.positionFraction.value = (ms.toFloat() / it).coerceIn(0f, 1f)
            }
        },
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
                PlaylistSerializer.Entry.ofMediaItem(song)?.let { toggleFavorite(listOf(it), on) }
            }
        },
        showQueue = { nowPlaying.showQueue() },
        showLyrics = { lyrics.fadeIn() },
        openAlbum = { nowPlaying.openAlbumPage() },
        openArtist = { nowPlaying.openArtistPage() },
    )

    // Callbacks for the timer and speed dialogs (MediaController and prefs)
    private val prefs = context.defaultPrefs

    /** Bottom padding (px) lists need so the mini player does not cover them, 0 when hidden. */
    var bottomPadding by mutableIntStateOf(0)
        private set
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
        restored?.let { state ->
            pendingExpanded = state.expanded
            playerState.positionFraction.value = state.fraction
            playerState.positionMs.value = state.positionMs
            playerState.durationMs.value = state.durationMs
        }

        sheetScope.launch {
            snapshotFlow { lyrics.covering }.collect { playerState.lyricsCovering.value = it }
        }
        nowPlaying = NowPlayingController(
            controller = controller,
            lifecycle = lifecycle,
            prefs = prefs,
            navigate = navViewModel::navigateTo,
            updateLyrics = { lyrics.lyrics = it },
            minimize = { sheetState.collapse() },
            onQualityChanged = { icon, text ->
                playerState.qualityIcon.value = icon
                playerState.qualityText.value = text
            },
            openQueue = { queueOpen.value = true },
            closeQueue = { queueOpen.value = false },
        )

        controller.addRecreationalPlayerListener(lifecycle, this) {
            syncPlayerState()
            refreshVisibility()
            nowPlaying.refreshLyrics()
        }

        sheetScope.launch {
            while (isActive) {
                if (chromeState.value.shown) {
                    val inst = instance
                    val duration = inst?.duration?.takeIf { it > 0 }
                        ?: inst?.currentMediaItem?.mediaMetadata?.durationMs
                    if (inst != null && duration != null && duration > 0) {
                        val raw = inst.currentPosition
                        val position = positionSmoother.update(
                            raw, inst.isPlaying, inst.playbackParameters.speed,
                            inst.currentMediaItem?.mediaId,
                        )
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

    private fun savedState() = PlayerSheetSavedState(
        expanded = sheetState.expandedTarget,
        fraction = playerState.positionFraction.value,
        positionMs = playerState.positionMs.value,
        durationMs = playerState.durationMs.value,
    )

    /**
     * The sheet, plus the queue when open. Composed after the pages so it draws over them and its
     * back callback, added to the dispatcher after theirs, takes priority over them.
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
            if (this.density != density.density) {
                this.density = density.density
                dispatchBottomPadding()
            }
        }
        val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(backDispatcher, lifecycleOwner) {
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
            backDispatcher?.addCallback(lifecycleOwner, callback)
            onDispose {
                callback.remove()
                bottomSheetBackCallback = null
            }
        }
        PlayerSheet(
            state = sheetState,
            player = playerState,
            chrome = chromeState,
            pageTinted = { navViewModel.topScheme != null },
            lyrics = lyrics,
            onPlayPause = { instance?.playOrPause() },
            onNext = { instance?.seekToNext() },
            onCoverClick = {
                instance?.currentMediaItem?.mediaId?.let { navViewModel.navigateTo(SongDetailKey(it)) }
            },
            onExpandedTargetChanged = { expanded ->
                bottomSheetBackCallback?.isEnabled = expanded
            },
            onCoversScreen = { coversScreenCheck.value = it },
            actions = fullPlayerActions,
            dialogCallbacks = dialogCallbacks,
        )
        if (queueOpen.value) {
            QueueSheet(controller, onDismiss = { queueOpen.value = false })
        }
    }

    override fun open() {
        if (chromeState.value.shown) {
            sheetState.expand()
        }
    }

    /** Stops the sheet's work when the composition that owns it goes away. */
    internal fun release() {
        nowPlaying.release()
        lifecycle.removeObserver(this)
        sheetScope.cancel()
        nowPlaying.onStop()
    }

    private fun syncPlayerState() {
        val item = instance?.currentMediaItem
        playerState.isPlaying.value = instance?.isPlaying == true
        playerState.showPause.value = instance?.showsPause == true
        playerState.title.value = item?.mediaMetadata?.title
        playerState.artist.value =
            item?.mediaMetadata?.artist ?: context.getString(R.string.unknown_artist)
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
        playerState.showPause.value = instance?.showsPause == true
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        playerState.showPause.value = instance?.showsPause == true
    }

    /**
     * Height of the floating mini bar from the screen bottom in px: its margin above the navigation
     * bar plus the 56dp card. Used as the bottom padding for lists. Must match
     * PlayerSheetMetrics.collapsedFootprint (MINI_HEIGHT).
     */
    private fun collapsedHeightPx(): Int {
        val d = density
        val nav = chromeState.value.bottom
        val platform = maxOf(nav + (8 * d).toInt(), (24 * d).toInt())
        return platform + (56 * d).toInt()
    }

    /** Publishes the bottom padding the mini bar needs, or 0 when it is hidden. */
    private fun dispatchBottomPadding() {
        bottomPadding = if (chromeState.value.shown) collapsedHeightPx() else 0
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

/**
 * Smooths the position shown by the progress bars. Around a resume the reported position runs
 * ahead by up to ~150 ms and is corrected back about a second later, once the audio output
 * reports its real playback position. Shown as is, the bar jumps forward and back on every
 * pause/resume. Instead the shown position advances at playback speed while playing and eases
 * towards the reported one. Large differences (seeks, song changes) are shown at once.
 */
private class PositionSmoother {
    private var shown = -1L
    private var shownAt = 0L
    private var mediaId: String? = null

    fun update(raw: Long, playing: Boolean, speed: Float, mediaId: String?): Long {
        val now = SystemClock.uptimeMillis()
        shown = if (shown < 0 || mediaId != this.mediaId) raw else {
            val predicted = if (playing) shown + ((now - shownAt) * speed).toLong() else shown
            val error = raw - predicted
            if (abs(error) > SNAP_MS || abs(error) < EASE_DIVISOR) raw
            else predicted + error / EASE_DIVISOR
        }
        shownAt = now
        this.mediaId = mediaId
        return shown
    }

    /** Shows [position] from now on, as after a seek. */
    fun reset(position: Long) {
        shown = position
        shownAt = SystemClock.uptimeMillis()
    }

    private companion object {
        /** Differences beyond this are real jumps, shown at once. */
        const val SNAP_MS = 500L
        /** Share of the remaining difference taken per poll. */
        const val EASE_DIVISOR = 8
    }
}
