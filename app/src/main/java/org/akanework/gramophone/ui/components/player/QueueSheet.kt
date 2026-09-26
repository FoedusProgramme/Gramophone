/*
 *     Copyright (C) 2025 Akane Foundation
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
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.utils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.compose.DismissibleRow
import org.akanework.gramophone.ui.components.compose.rememberReorderableListState
import org.akanework.gramophone.ui.components.compose.reorderHandle
import org.akanework.gramophone.ui.components.compose.reorderableRow
import org.akanework.gramophone.ui.components.home.EditableSongRow
import org.akanework.gramophone.ui.components.home.LIBRARY_PLAYING_CORNER
import org.akanework.gramophone.ui.components.home.nowPlayingRowColors
import org.akanework.gramophone.ui.fragments.compose.QueueRoot
import org.akanework.gramophone.ui.fragments.compose.rememberMqState

/*
 * The queue as a modal sheet: the queue head (the multi-queue pages, the actions and the time
 * left) over the songs, which can be dragged into another order or swiped away.
 */

private val WIDE_SHEET_MAX_WIDTH = 900.dp

/** What the multi-queue state asks of the sheet it lives in. */
interface QueueSheetHost {
    val lifecycle: Lifecycle
    val context: Context

    /** The song the player is on, as a position in the queue's order, or null for none. */
    var currentMediaItemIndex: Int?

    fun lockQueue(lock: Boolean)
    fun dismiss()
    fun scrollToPositionWithOffset(position: Int, offsetPx: Int)
    fun smoothScrollTo(position: Int)
    fun notifyListChanged()
    fun updateTimer(totalMs: Long, baseRealtime: Long, running: Boolean)
}

/** The queue's countdown: [baseRealtime] is when it reaches zero, frozen while not [running]. */
@Stable
class QueueTimerState {
    var totalMs by mutableLongStateOf(0L)
    var baseRealtime by mutableLongStateOf(0L)
    var running by mutableStateOf(false)
}

@Composable
fun QueueTimer(state: QueueTimerState, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.running, state.baseRealtime) {
        now = SystemClock.elapsedRealtime()
        while (state.running) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    val remaining = (state.baseRealtime - now).coerceAtLeast(0L)
    Text(
        stringResource(
            R.string.duration_queue,
            remaining.convertDurationToTimeStamp(true),
            state.totalMs.convertDurationToTimeStamp(true),
        ),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Stable
private class QueueRow(val key: String, val item: MediaItem)

private class QueueSheetState(
    override val context: Context,
    private val onDismissRequest: () -> Unit,
) : QueueSheetHost, LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    val listState = LazyListState()
    val timer = QueueTimerState()
    override var currentMediaItemIndex: Int? by mutableStateOf(null)
    var locked by mutableStateOf(false)
        private set
    var listVersion by mutableIntStateOf(0)
        private set
    var pendingScroll by mutableStateOf<Pair<Int, Int>?>(null)
    var pendingSmoothScroll by mutableStateOf<Int?>(null)

    fun onShow() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun onHide() {
        registry.currentState = Lifecycle.State.DESTROYED
    }

    override fun lockQueue(lock: Boolean) {
        locked = lock
    }

    override fun dismiss() = onDismissRequest()

    override fun scrollToPositionWithOffset(position: Int, offsetPx: Int) {
        pendingScroll = position to offsetPx
    }

    override fun smoothScrollTo(position: Int) {
        pendingSmoothScroll = position
    }

    override fun notifyListChanged() {
        listVersion++
    }

    override fun updateTimer(totalMs: Long, baseRealtime: Long, running: Boolean) {
        timer.totalMs = totalMs
        timer.baseRealtime = baseRealtime
        timer.running = running
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(controller: MediaControllerViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val host = remember { QueueSheetState(context, onDismiss) }
    DisposableEffect(host) {
        host.onShow()
        onDispose { host.onHide() }
    }
    val mqState = rememberMqState(scope, controller, host)
    if (mqState == null) {
        // Not connected to the player: nothing to show.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val mqEnabled = remember { Flags.MQ_PREVIEW && context.defaultPrefs.getBooleanStrict("mq_preview", false) }
    val pagerState = rememberPagerState(initialPage = if (Flags.MQ_PREVIEW) 0 else 1) { 2 }
    val instance = controller.get()

    DisposableEffect(host, mqState) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mqState.isDetached()) return
                val i = instance?.currentMediaItemIndex
                host.currentMediaItemIndex = i?.let { mqState.playlist.first.indexOf(it) }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
            ) {
                if (mqState.isDetached()) return
                mqState.updateTimer()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mqState.updateTimer()
            }
        }
        controller.addRecreationalPlayerListener(host.lifecycle, listener) {
            listener.onMediaItemTransition(
                instance?.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            listener.onIsPlayingChanged(instance?.isPlaying ?: false)
        }
        onDispose {}
    }

    val listState = host.listState
    LaunchedEffect(host.pendingScroll) {
        host.pendingScroll?.let { (position, offset) ->
            if (position >= 0) listState.scrollToItem(position, -offset)
            host.pendingScroll = null
        }
    }
    LaunchedEffect(host.pendingSmoothScroll) {
        host.pendingSmoothScroll?.let { position ->
            if (position >= 0) listState.animateScrollToItem(position)
            host.pendingSmoothScroll = null
        }
    }

    val version = host.listVersion
    val rows = remember(version) {
        val (order, items) = mqState.playlist
        val seen = HashMap<String, Int>()
        order.map { i ->
            val item = items[i]
            val nth = (seen[item.mediaId] ?: 0) + 1
            seen[item.mediaId] = nth
            QueueRow(item.mediaId + "#" + nth, item)
        }
    }
    val editable = !mqState.isDetached()
    val reorder = rememberReorderableListState(listState) { from, to -> mqState.moveRow(from, to) }
    val unknownArtist = stringResource(R.string.unknown_artist)
    val currentArtwork = host.currentMediaItemIndex?.let { rows.getOrNull(it) }?.item?.mediaMetadata?.artworkUri
    val nowPlayingColors = nowPlayingColors(
        rememberArtworkColorScheme(currentArtwork), MaterialTheme.colorScheme.primary,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            // The queue sheet always opens fully, never half way.
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
        sheetMaxWidth = if (mqEnabled) WIDE_SHEET_MAX_WIDTH else BottomSheetDefaults.SheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        // Edge to edge: only keep the drag handle clear of the status bar (and the sides clear of
        // cutouts). The songs scroll on behind the navigation bar, padded by it at the end.
        contentWindowInsets = {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        },
    ) {
        // The sheet's own window enforces a navigation bar contrast scrim by default, which draws
        // a band over the sheet with 3-button navigation. The app's window does not have one.
        val sheetWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                sheetWindow?.isNavigationBarContrastEnforced = false
            }
        }
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            QueueRoot(
                mqState = mqState,
                pagerState = pagerState,
                coroutineScope = scope,
                timer = host.timer,
                mqEnabled = mqEnabled,
                onDismiss = onDismiss,
                onRecyclerScrollTo = {
                    mqState.playlist.first.indexOfFirst { i ->
                        i == (instance?.currentMediaItemIndex ?: 0)
                    }.takeIf { it != -1 }?.let { host.smoothScrollTo(it) }
                },
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    val item = row.item
                    DismissibleRow(
                        onDismissed = { mqState.removeRow(index) },
                        modifier = Modifier.reorderableRow(this, reorder, index),
                        enabled = editable,
                    ) {
                        val duration = item.mediaMetadata.durationMs?.convertDurationToTimeStamp()
                        val artist = item.mediaMetadata.artist?.toString() ?: unknownArtist
                        EditableSongRow(
                            title = item.mediaMetadata.title?.toString().orEmpty(),
                            subtitle = stringResource(R.string.artist_time, duration ?: "", artist),
                            cover = item.mediaMetadata.artworkUri,
                            defaultCover = R.drawable.ic_default_cover,
                            onClick = { mqState.clickRow(index) },
                            onRemove = { mqState.removeRow(index) },
                            handleModifier = if (editable) Modifier.reorderHandle(reorder, index) else Modifier,
                            showControls = editable,
                            colors = nowPlayingRowColors(
                                isCurrent = index == host.currentMediaItemIndex,
                                colors = nowPlayingColors,
                                containerShape = RoundedCornerShape(LIBRARY_PLAYING_CORNER),
                            ),
                        )
                    }
                }
            }
        }
    }
}
