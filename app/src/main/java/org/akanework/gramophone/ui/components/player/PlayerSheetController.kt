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
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getTimer
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.setTimer
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlayState
import org.akanework.gramophone.ui.nav.AlbumKey
import org.akanework.gramophone.ui.nav.ArtistKey
import org.akanework.gramophone.ui.nav.NavViewModel
import org.akanework.gramophone.ui.nav.SongDetailKey
import org.koin.compose.viewmodel.koinActivityViewModel
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.manipulator.PlaylistSerializer
import kotlin.math.roundToInt

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
    val positionMs: Long,
    val durationMs: Long,
    val lyricsVisible: Boolean,
) {
    companion object {
        val Saver: Saver<PlayerSheetSavedState, Any> = listSaver(
            save = { listOf(it.expanded, it.positionMs, it.durationMs, it.lyricsVisible) },
            restore = {
                PlayerSheetSavedState(
                    it[0] as Boolean, it[1] as Long, it[2] as Long, it.getOrNull(3) as? Boolean ?: false,
                )
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
    val controllerViewModel = koinActivityViewModel<MediaControllerViewModel>()
    val navViewModel = koinActivityViewModel<NavViewModel>()
    val currentToggleFavorite by rememberUpdatedState(toggleFavorite)
    val scope = rememberCoroutineScope()
    val create = { restored: PlayerSheetSavedState? ->
        PlayerSheetController(
            context = context,
            parentScope = scope,
            mediaController = controllerViewModel,
            navViewModel = navViewModel,
            lifecycle = lifecycle,
            toggleFavorite = { songs, on -> currentToggleFavorite(songs, on) },
            restored = restored,
        )
    }
    val sheet = rememberSaveable(saver = PlayerSheetController.saver(create)) { create(null) }
    DisposableEffect(sheet) {
        onDispose { sheet.release() }
    }
    return sheet
}

/**
 * The player sheet of the screen. Holds the sheet's state, feeds it from the MediaController and
 * the service, and wires the player's buttons to them. The app root shows the sheet on the pages
 * that want it through [visible], pads its lists by [bottomPadding], skips drawing the pages
 * while the sheet [coversScreen], and draws the sheet with [PlayerSheetHost]. Created by
 * [rememberPlayerSheetController].
 */
@Stable
class PlayerSheetController internal constructor(
    context: Context,
    parentScope: CoroutineScope,
    internal val mediaController: MediaControllerViewModel,
    private val navViewModel: NavViewModel,
    lifecycle: Lifecycle,
    private val toggleFavorite: (List<PlaylistSerializer.Entry>, Boolean) -> Unit,
    restored: PlayerSheetSavedState?,
) : PlayerSheetHandle {

    companion object {
        /** How long a restored expanded state waits for the service to restore the queue. */
        private const val PENDING_EXPANDED_TIMEOUT_MS = 5000L

        /** Saves the sheet state, and restores it into a controller made by [create]. */
        fun saver(
            create: (PlayerSheetSavedState?) -> PlayerSheetController,
        ): Saver<PlayerSheetController, Any> = Saver(
            save = { with(PlayerSheetSavedState.Saver) { save(it.savedState()) } },
            restore = { create(PlayerSheetSavedState.Saver.restore(it)) },
        )
    }

    /** Whether the top page wants the mini player. It shows while there's something to play. */
    var visible = false
        set(value) {
            if (field != value) {
                field = value
                refreshVisibility()
            }
        }

    /** Bottom padding (px) lists need so the mini player does not cover them, 0 when hidden. */
    val bottomPadding: Int by derivedStateOf { if (shown) collapsedFootprint else 0 }

    /**
     * Whether the sheet covers the whole screen, so the pages under it needn't be drawn. Reads
     * snapshot state: read it in the draw phase to be redrawn when the sheet starts to move.
     */
    val coversScreen: Boolean
        get() = sheetState.coversScreen

    override fun open() {
        if (shown) sheetState.expand()
    }

    // The whole expand/collapse animation is driven by a single 0..1 progress. It runs on the
    // composition's frame clock, which the recomposer ticks right before it recomposes, so each
    // frame's progress is laid out and drawn in that same frame. On AndroidUiDispatcher.Main's
    // own clock it ran before or after the recomposer, as their callbacks happened to be queued,
    // and a frame now and then showed the previous progress with the next one jumping two steps.
    private val sheetScope =
        CoroutineScope(parentScope.coroutineContext + Job(parentScope.coroutineContext.job))
    internal val sheetState = NowPlayingSheetState(sheetScope)
    private val clock = PlaybackClock(PlaybackClock.playerFor { mediaController.get() })
    internal val playerState = PlayerSheetPlayerState(clock)
    internal val lyrics = LyricsOverlayState(sheetScope, clock)

    /** Whether the top page is themed from a cover, see [PlayerSheet]'s pageTinted. */
    internal val pageTinted: Boolean by derivedStateOf { navViewModel.topScheme != null }

    internal val queueReveal = QueueRevealState(sheetScope)

    /** The mini bar's [collapsedFootprint] (px), set by [PlayerSheetHost] from the insets. */
    internal var collapsedFootprint by mutableIntStateOf(0)

    /** Whether the sheet shows: [visible], with something to play. */
    private var shown by mutableStateOf(false)

    /**
     * Expanded before the recreation: expanded again if it shows when the player first connects
     * on a page that wants it.
     */
    private var pendingExpanded = false
    private var pendingExpandedTimeout: Job? = null
    private val instance: MediaController?
        get() = mediaController.get()
    private val service: NowPlayingServiceBridge

    init {
        // Restore the expanded state, the lyrics and playback position after activity recreation.
        restored?.let { state ->
            pendingExpanded = state.expanded
            lyrics.restore(state.lyricsVisible)
            clock.restore(state.positionMs, state.durationMs)
        }
        service = NowPlayingServiceBridge(
            controller = mediaController,
            lifecycle = lifecycle,
            prefs = context.defaultPrefs,
            updateLyrics = { lyrics.lyrics = it },
            onTimerChanged = { playerState.timerActive = it },
            onFormatChanged = { playerState.audioFormat = it },
            onQualityChanged = { icon, text ->
                playerState.qualityIcon = icon
                playerState.qualityText = text
            },
        )
        // Keeps playerState and the clock up to date for as long as sheetScope runs
        PlayerStateBridge(
            context = context,
            controller = mediaController,
            lifecycle = lifecycle,
            scope = sheetScope,
            state = playerState,
            clock = clock,
            polling = { shown },
            expanded = { sheetState.expandedTarget },
            onMediaChanged = ::refreshVisibility,
        )
    }

    internal val actions = FullPlayerActions(
        playPause = { instance?.playOrPause() },
        previous = { instance?.seekToPrevious() },
        next = { instance?.seekToNext() },
        seekBack = { instance?.seekBack() },
        seekForward = { instance?.seekForward() },
        seekTo = clock::seekTo,
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
        showLyrics = { lyrics.fadeIn() },
        openAlbum = {
            sheetState.collapse()
            navViewModel.navigateTo(AlbumKey(instance?.currentMediaItem?.mediaMetadata?.albumId))
        },
        openArtist = {
            sheetState.collapse()
            val artistId = instance?.currentMediaItem?.mediaMetadata?.artistId
            navViewModel.navigateTo(ArtistKey(artistId, false))
        },
        openSongDetails = {
            instance?.currentMediaItem?.mediaId?.let { navViewModel.navigateTo(SongDetailKey(it)) }
        },
    )

    /** What the player's dialogs read and set, on the MediaController and in the prefs. */
    internal val dialogCallbacks = context.defaultPrefs.let { prefs ->
        PlayerDialogCallbacks(
            currentSpeed = { instance?.playbackParameters?.speed ?: 1f },
            currentPitch = { instance?.playbackParameters?.pitch ?: 1f },
            setSpeedPitch = { s, p -> instance?.playbackParameters = PlaybackParameters(s, p) },
            timerRemainingMs = { instance?.getTimer()?.first?.toLong() },
            timerEndOfSong = { instance?.getTimer()?.second == true },
            setTimer = { d, eos -> instance?.setTimer(d, eos) },
            getBool = { key, def -> prefs.getBooleanStrict(key, def) },
            putBool = { key, value -> prefs.edit { putBoolean(key, value) } },
            audioFormat = { playerState.audioFormat },
        )
    }

    /** Stops the sheet's work when the composition that owns it goes away. */
    internal fun release() {
        service.release()
        sheetScope.cancel()
    }

    private fun savedState() = PlayerSheetSavedState(
        // Still to be expanded again if saved before the restored sheet has shown
        expanded = sheetState.expandedTarget || pendingExpanded,
        positionMs = clock.positionMs,
        durationMs = clock.durationMs,
        lyricsVisible = lyrics.visible,
    )

    /** Shows the sheet on a page that wants it while there's something to play, or hides it. */
    private fun refreshVisibility() {
        val player = instance
        val show = visible && (player?.mediaItemCount ?: 0) > 0
        val expand = show && pendingExpanded
        // Settled once the player is connected on a page that wants the sheet. With nothing to
        // play yet, the service may still be restoring the queue after process death: given a
        // moment to land, after which the next song played must not open the sheet full screen.
        if (visible && player != null) {
            if (show) {
                pendingExpanded = false
                pendingExpandedTimeout?.cancel()
            } else if (pendingExpanded && pendingExpandedTimeout == null) {
                pendingExpandedTimeout = sheetScope.launch {
                    delay(PENDING_EXPANDED_TIMEOUT_MS)
                    pendingExpanded = false
                }
            }
        }
        if (shown == show) return
        shown = show
        sheetState.slide(show)
        if (!show) {
            sheetState.snapToCollapsed()
        } else if (expand) {
            sheetState.snapToExpanded()
        }
    }
}

/**
 * The sheet of [controller], with the queue under the full player. Composed after the pages so it
 * draws over them and its back callback, added to the dispatcher after theirs, takes priority
 * over them.
 */
@Composable
fun PlayerSheetHost(controller: PlayerSheetController, modifier: Modifier = Modifier) {
    // The mini bar applies the system bar and cutout insets itself. The sheet spans them.
    val windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = SheetInsets(
        left = windowInsets.getLeft(density, direction),
        top = windowInsets.getTop(density),
        right = windowInsets.getRight(density, direction),
        bottom = windowInsets.getBottom(density),
    )
    val footprint = collapsedFootprint(insets.bottom.toFloat(), density).roundToInt()
    SideEffect { controller.collapsedFootprint = footprint }
    PlayerSheetBackHandler(controller.sheetState, controller.lyrics, controller.queueReveal)
    // The player slides back over the queue as it collapses
    LaunchedEffect(controller) {
        snapshotFlow { controller.sheetState.expandedTarget }.collect {
            if (!it) controller.queueReveal.hide()
        }
    }
    // The queue shows the MediaController, which the activity releases when it stops.
    val connection by controller.mediaController.connection.collectAsState()
    PlayerSheet(
        state = controller.sheetState,
        player = controller.playerState,
        insets = insets,
        pageTinted = controller.pageTinted,
        lyrics = controller.lyrics,
        queue = controller.queueReveal,
        actions = controller.actions,
        dialogCallbacks = controller.dialogCallbacks,
        modifier = modifier,
    ) { queueModifier ->
        key(connection) {
            if (connection != null) {
                QueuePanel(
                    controller.mediaController,
                    onDismiss = controller.queueReveal::hide,
                    modifier = queueModifier,
                )
            }
        }
    }
}

/** What a back gesture puts away, see [PlayerSheetBackHandler]. */
private enum class BackTarget { Lyrics, Queue, Sheet }

/**
 * Collapses the expanded [sheet] on back, following the predictive back gesture, or first fades
 * out the [lyrics] over it, or slides it back over the [queue]. Only enabled while the sheet is
 * (going to be) expanded.
 */
@Composable
private fun PlayerSheetBackHandler(
    sheet: NowPlayingSheetState,
    lyrics: LyricsOverlayState,
    queue: QueueRevealState,
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(backDispatcher, lifecycleOwner, sheet, lyrics, queue) {
        val callback = object : OnBackPressedCallback(enabled = false) {
            // Picked when the gesture starts: the queue, say, is gone by the time it ends
            private var target: BackTarget? = null

            private fun target() = target ?: when {
                lyrics.visible -> BackTarget.Lyrics
                queue.shown -> BackTarget.Queue
                else -> BackTarget.Sheet
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                target = target()
                if (target == BackTarget.Lyrics) lyrics.onBackStarted()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                when (target()) {
                    BackTarget.Lyrics -> lyrics.onBackProgressed(backEvent.progress)
                    BackTarget.Queue -> queue.onBackProgress(backEvent.progress)
                    BackTarget.Sheet -> sheet.onBackProgress(backEvent.progress)
                }
            }

            override fun handleOnBackPressed() {
                when (target()) {
                    BackTarget.Lyrics -> lyrics.onBackPressed()
                    BackTarget.Queue -> queue.hide()
                    BackTarget.Sheet -> sheet.collapse()
                }
                target = null
            }

            override fun handleOnBackCancelled() {
                when (target()) {
                    BackTarget.Lyrics -> lyrics.onBackCancelled()
                    BackTarget.Queue -> queue.reveal()
                    BackTarget.Sheet -> sheet.expand()
                }
                target = null
            }
        }
        backDispatcher.addCallback(lifecycleOwner, callback)
        try {
            snapshotFlow { sheet.expandedTarget }.collect { callback.isEnabled = it }
        } finally {
            callback.remove()
        }
    }
}
