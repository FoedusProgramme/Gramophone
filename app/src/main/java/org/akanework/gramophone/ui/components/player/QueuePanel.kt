/*
 *     Copyright (C) 2026 Akane Foundation
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
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.delay
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.utils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.compose.DismissibleRow
import org.akanework.gramophone.ui.components.home.EDITABLE_ROW_HEIGHT
import org.akanework.gramophone.ui.components.home.EditableSongRow
import org.akanework.gramophone.ui.components.home.LIBRARY_PLAYING_CORNER
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.nowPlayingRowColors
import org.akanework.gramophone.ui.fragments.compose.MqContent
import org.akanework.gramophone.ui.fragments.compose.MqState
import org.akanework.gramophone.ui.fragments.compose.QueueTimer
import org.akanework.gramophone.ui.fragments.compose.rememberMqState

/*
 * The queue, under the full player: the queue head (the multi-queue preview and the time left)
 * over the songs, which can be dragged into another order or swiped away, and the actions over
 * them.
 */

/** As wide as the queue gets on large screens, wider with the multi-queue preview. */
private val QUEUE_MAX_WIDTH = 640.dp
private val WIDE_QUEUE_MAX_WIDTH = 900.dp

/** Around the FABs, and between them. */
private val QUEUE_FAB_MARGIN = 16.dp
private val QUEUE_FAB_GAP = 12.dp

/** A FAB's size. */
private val QUEUE_FAB_SIZE = 56.dp

/** Between the time left and the songs under it. */
private val QUEUE_TIME_LEFT_BOTTOM_PADDING = 8.dp

/** What the queue's end leaves for the FABs over it: a small FAB above a FAB, with margins. */
private val QUEUE_FABS_ROOM = QUEUE_FAB_MARGIN * 2 + 40.dp + QUEUE_FAB_GAP + QUEUE_FAB_SIZE

/** What the multi-queue state asks of the queue it shows in. */
interface QueueSheetHost {
    val lifecycle: Lifecycle
    val context: Context

    /** The song the player is on, as a position in the queue's order, or null for none. */
    var currentMediaItemIndex: Int

    fun dismiss()
    fun scrollToPositionWithOffset(position: Int, offsetPx: Int)
    fun smoothScrollTo(position: Int)
    fun notifyListChanged()
}

@Stable
private class QueueRow(val key: Long, val item: MediaItem)


/**
 * The queue of [controller], brought up and put away (as when it's cleared) by [reveal]. The
 * caller keeps it clear of the cutouts and side navigation bars, it pads its songs for the bottom
 * one. It carries on across the controller going away as the activity stops and a new one
 * connecting as it starts again.
 */
@Composable
fun QueuePanel(
    controller: MediaControllerViewModel,
    reveal: QueueRevealState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Not connected to the player yet: nothing to show
    val mqState = rememberMqState(context, scope, controller, reveal::hide) ?: return
    DisposableEffect(mqState) {
        mqState.onShow()
        onDispose { mqState.onHide() }
    }
    val mqEnabled = remember { context.defaultPrefs.getBooleanStrict("mq_preview", false) }

    val listState = mqState.listState
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Scrolls as a dragged row nears the top of the list, or the navigation bar at its end
    val reorder = rememberReorderableLazyListState(
        listState, PaddingValues(bottom = navigationBar),
    ) { from, to -> mqState.moveRow(from.index, to.index) }
    // Loaded, brought up to date and scrolled to the song playing each time it comes up
    LaunchedEffect(mqState) {
        snapshotFlow { reveal.shown }.collect { mqState.shown = it }
    }
    LaunchedEffect(mqState, reorder) {
        snapshotFlow { reorder.isAnyItemDragging }.collect { mqState.dragging = it }
    }
    LaunchedEffect(mqState.pendingScroll) {
        mqState.pendingScroll?.let { (position, offset) ->
            if (position >= 0) listState.scrollToItem(position, -offset)
            mqState.pendingScroll = null
        }
    }
    LaunchedEffect(mqState.pendingSmoothScroll) {
        mqState.pendingSmoothScroll?.let { position ->
            if (position >= 0) listState.animateScrollToItem(position)
            mqState.pendingSmoothScroll = null
        }
    }

    val version = mqState.listVersion
    val rows = remember(mqState, version) {
        val (order, items) = mqState.playlist
        order.map { i -> QueueRow(mqState.songKey(i), items[i]) }
    }
    val editable = !mqState.isDetached()
    val unknownArtist = stringResource(R.string.unknown_artist)
    val currentArtwork = mqState.currentMediaItemIndex?.let { rows.getOrNull(it) }?.item?.mediaMetadata?.artworkUri
    val nowPlayingColors = nowPlayingColors(
        rememberArtworkColorScheme(currentArtwork), MaterialTheme.colorScheme.primary,
    )

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (mqEnabled) WIDE_QUEUE_MAX_WIDTH else QUEUE_MAX_WIDTH)
                .fillMaxSize(),
        ) {
            if (Flags.MQ_PREVIEW && mqEnabled) {
                MqContent(
                    mqState = mqState,
                    mqEnabled = mqEnabled,
                    landscape = false,
                    onDismiss = reveal::hide,
                )
            }
            QueueTimeLeft(
                mqState.timer,
                ticking = reveal.shown,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = QUEUE_FAB_MARGIN)
                    .padding(bottom = QUEUE_TIME_LEFT_BOTTOM_PADDING),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // The last songs scroll up clear of the FABs
                    contentPadding = PaddingValues(bottom = navigationBar + QUEUE_FABS_ROOM),
                ) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                        val item = row.item
                        ReorderableItem(reorder, key = row.key, enabled = editable) {
                            DismissibleRow(
                                // By key: the row may have moved by the time it's settled off screen
                                onDismissed = { mqState.removeEntry(row.key) },
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
                                    handleModifier = if (editable) Modifier.draggableHandle() else Modifier,
                                    showControls = editable,
                                    colors = nowPlayingRowColors(
                                        isCurrent = index == mqState.currentMediaItemIndex,
                                        colors = nowPlayingColors,
                                        containerShape = RoundedCornerShape(LIBRARY_PLAYING_CORNER),
                                    ),
                                )
                            }
                        }
                    }
                }
                LibraryFastScroller(
                    listState = listState,
                    itemCount = rows.size,
                    rowHeightPx = with(LocalDensity.current) { EDITABLE_ROW_HEIGHT.roundToPx() },
                    // Where in the queue: its order is the user's, there's nothing else to go by
                    hintFor = { i -> (i + 1).toString() },
                )
            }
        }
        QueueFabs(
            onClear = {
                reveal.hide()
                mqState.removeQueue()
            },
            onScrollToPlaying = mqState::smoothScrollToCurrent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = navigationBar)
                .padding(QUEUE_FAB_MARGIN),
        )
    }
}

/**
 * The time left in the queue, ticking down on the second while it plays, and how long it is: as
 * the old queue sheet's chronometer had it. Empty, but as tall, until the queue's loaded. Ticks
 * only while [ticking]: with the queue down, its timer isn't kept up to date anyway.
 */
@Composable
private fun QueueTimeLeft(timer: QueueTimer?, ticking: Boolean, modifier: Modifier = Modifier) {
    val now by produceState(SystemClock.elapsedRealtime(), timer, ticking) {
        value = SystemClock.elapsedRealtime()
        if (timer == null || !timer.running || !ticking) return@produceState
        while (true) {
            val left = timer.remainingMs - (value - timer.atRealtime)
            if (left <= 0) break
            // Up to the next whole second, when what's shown changes
            delay(left % 1000 + 1)
            value = SystemClock.elapsedRealtime()
        }
    }
    val text = if (timer == null) {
        ""
    } else {
        val left = if (timer.running) timer.remainingMs - (now - timer.atRealtime) else timer.remainingMs
        // Rounded up, to read 0:00 only once it's over
        val leftSeconds = (left.coerceAtLeast(0) + 999) / 1000
        stringResource(
            R.string.duration_queue,
            (leftSeconds * 1000).convertDurationToTimeStamp(true),
            timer.totalMs.convertDurationToTimeStamp(true),
        )
    }
    Text(
        text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** Over the end of the queue: clearing it, small, and back to the song playing. */
@Composable
private fun QueueFabs(
    onClear: () -> Unit,
    onScrollToPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(QUEUE_FAB_GAP),
    ) {
        SmallFloatingActionButton(
            onClick = onClear,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.clear_queue))
        }
        FloatingActionButton(onClick = onScrollToPlaying) {
            Icon(Icons.Outlined.MyLocation, stringResource(R.string.scroll_to_playing))
        }
    }
}
