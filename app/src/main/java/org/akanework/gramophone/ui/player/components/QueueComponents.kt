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

package org.akanework.gramophone.ui.player.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.components.compose.BottomSheetState
import org.akanework.gramophone.ui.components.compose.DismissibleRow
import org.akanework.gramophone.ui.components.home.EDITABLE_ROW_HEIGHT
import org.akanework.gramophone.ui.components.home.EDITABLE_ROW_ICON_END
import org.akanework.gramophone.ui.components.home.EDITABLE_ROW_ICON_START
import org.akanework.gramophone.ui.components.home.EditableSongRow
import org.akanework.gramophone.ui.components.home.FloorCenterVertically
import org.akanework.gramophone.ui.components.home.LIBRARY_PLAYING_CORNER
import org.akanework.gramophone.ui.components.home.LIST_ROUND_CORNER_SIZE
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.SingleLineText
import org.akanework.gramophone.ui.components.home.nowPlayingRowColors
import org.akanework.gramophone.ui.components.home.rememberDefaultCoverPainter
import org.akanework.gramophone.ui.components.player.ActionBar
import org.akanework.gramophone.ui.components.player.AnimatedColorScheme
import org.akanework.gramophone.ui.components.player.FullPlayerActions
import org.akanework.gramophone.ui.components.player.PlayerSheetPlayerState
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_PREVIEW_BOTTOM_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_PREVIEW_PROGRESS_HEIGHT
import org.akanework.gramophone.ui.components.player.QueueFabs
import org.akanework.gramophone.ui.components.player.QueueRow
import org.akanework.gramophone.ui.components.player.QueueTimeLeft
import org.akanework.gramophone.ui.components.player.nowPlayingColors
import org.akanework.gramophone.ui.components.player.rememberArtworkColorScheme
import org.akanework.gramophone.ui.fragments.compose.MqList
import org.akanework.gramophone.ui.fragments.compose.MqState
import org.akanework.gramophone.ui.fragments.compose.QueueInfo
import org.akanework.gramophone.ui.player.INSETS_N
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


/** Around the FABs, and between them. */
private val QUEUE_FAB_MARGIN = 16.dp
private val QUEUE_FAB_GAP = 12.dp

/**
 * The queue of [controller], brought up and put away (as when it's cleared) by [reveal]. The
 * caller keeps it clear of the cutouts and side navigation bars, it pads its songs for the bottom
 * one. It carries on across the controller going away as the activity stops and a new one
 * connecting as it starts again.
 */
@Composable
fun QueueBody(
    mqState: MqState,
    queueSheetState: BottomSheetState,
    onClearQueue: () -> Unit,
    dualPane: Boolean,
    fullHeight: Boolean,
    modifier: Modifier = Modifier,
    queueHeader: @Composable BoxScope.(Boolean) -> Unit,
) {
    val listState = mqState.listState
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Scrolls as a dragged row nears the top of the list, or the navigation bar at its end
    val reorder = rememberReorderableLazyListState(
        listState, PaddingValues(bottom = navigationBar),
    ) { from, to -> mqState.moveRow(from.index, to.index) }
    // Loaded, brought up to date and scrolled to the song playing each time it comes up
    LaunchedEffect(mqState) {
        snapshotFlow { queueSheetState.progress > 0f }.collect { mqState.shown = it }
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
    val currentArtwork = rows.getOrNull(mqState.currentMediaItemIndex)?.item?.mediaMetadata?.artworkUri
    val nowPlayingColors = nowPlayingColors(
        rememberArtworkColorScheme(currentArtwork), MaterialTheme.colorScheme.primary,
    )

    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Row() {
            if (dualPane) {
                Box(Modifier
                    .weight(0.45f, false)
                    .widthIn(max = 400.dp)
                ) {
                    queueHeader(dualPane)
                }
            }

            Column(
                Modifier
                    .weight(0.55f),
            ) {
                QueueTimeLeft(
                    mqState.timer,
                    ticking = queueSheetState.isExpanded,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = QUEUE_FAB_MARGIN)
                        .padding(bottom = 8.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // The last songs scroll up clear of the FABs: a small one over a regular one
                        contentPadding = PaddingValues(
                            bottom = navigationBar + QUEUE_FAB_MARGIN * 2 + 40.dp + QUEUE_FAB_GAP + 56.dp,
                        ),
                    ) {
                        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                            val item = row.item
                            ReorderableItem(reorder, key = row.key, enabled = editable) {
                                DismissibleRow(
                                    // By key: the row may have moved by the time it's settled off screen
                                    onDismissed = { mqState.removeEntry(row.key) },
                                    enabled = editable,
                                ) {
                                    val duration =
                                        item.mediaMetadata.durationMs?.convertDurationToTimeStamp()
                                    val artist =
                                        item.mediaMetadata.artist?.toString() ?: unknownArtist
                                    EditableSongRow(
                                        title = item.mediaMetadata.title?.toString().orEmpty(),
                                        subtitle = stringResource(
                                            R.string.artist_time,
                                            duration ?: "",
                                            artist
                                        ),
                                        cover = item.mediaMetadata.artworkUri,
                                        defaultCover = R.drawable.ic_default_cover,
                                        onClick = { mqState.clickRow(index) },
                                        onRemove = { mqState.removeRow(index) },
                                        handleModifier = if (editable) Modifier.draggableHandle() else Modifier,
                                        showControls = editable,
                                        colors = nowPlayingRowColors(
                                            isCurrent = index == mqState.currentMediaItemIndex,
                                            colors = nowPlayingColors,
                                            containerShape = RoundedCornerShape(
                                                LIBRARY_PLAYING_CORNER
                                            ),
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
        }
        QueueFabs(
            onClear = {
                if (mqState.inactiveQueues.isEmpty()) {
                    onClearQueue()
                }
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

@Composable
fun QueueHeader(
    queueSheetState: BottomSheetState,
    playerState: PlayerSheetPlayerState,
    mqState: MqState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    insets: WindowInsets,
    dualPane: Boolean,
    fullHeight: Boolean,
    mqEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val defaultCover = rememberDefaultCoverPainter(R.drawable.ic_default_cover)

    Column(
        verticalArrangement = if (dualPane) Arrangement.SpaceBetween else Arrangement.Top,
        modifier = modifier
            .windowInsetsPadding(insets.only(INSETS_N))
            // Faded in as one, the status bar's part with it
            .graphicsLayer { alpha = queueSheetState.progress }
            .drawBehind {
                if (!dualPane) {
                    drawRect(scheme.color { surface })
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.expand_less),
                onClick = queueSheetState::collapseSoft,
            )
            // In line with the songs' drag handles and remove buttons under it
            .padding(start = EDITABLE_ROW_ICON_START, end = EDITABLE_ROW_ICON_END)
            .then(
                if (dualPane) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier
                },
            ),
    ) {
        BackHandler(mqState.expanded || mqState.isDetached()) {
            if (mqState.isDetached()) {
                mqState.resetHead()
            } else if (!dualPane && mqState.expanded) {
                mqState.toggleExpand()
            }
        }

        Column(
            modifier = modifier
                .fillMaxWidth(),
        ) {
            QueueInfo(
                mqState = mqState,
                mqEnabled = mqEnabled,
                dualPane = dualPane,
                onDismiss = queueSheetState::collapseSoft,
            )

            AnimatedVisibility(
                visible = mqState.expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val lazyQueuesListState = rememberLazyListState()
                MqList(
                    mqState = mqState,
                    lazyQueuesListState = lazyQueuesListState,
                    modifier = Modifier
                        .heightIn(Dp.Unspecified, if (!dualPane) 300.dp else Dp.Unspecified),
                )
            }
        }

        Column{
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = FloorCenterVertically,
            ) {
                ActionBar(mqState, scheme, actions, playerState) {
                    AsyncImage(
                        model = playerState.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        error = defaultCover,
                        fallback = defaultCover,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(LIST_ROUND_CORNER_SIZE)),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 18.dp),
                    ) {
                        SingleLineText(
                            playerState.title?.toString().orEmpty(),
                            17.sp,
                            500,
                            scheme.color { onSurface },
                            Modifier.fillMaxWidth(),
                        )
                        SingleLineText(
                            playerState.artist?.toString().orEmpty(), 14.sp, 400,
                            scheme.color { onSurfaceVariant },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { playerState.positionFraction.coerceIn(0f, 1f) },
                drawStopIndicator = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QUEUE_PREVIEW_PROGRESS_HEIGHT),
                color = scheme.color { primary },
                trackColor = scheme.color { secondaryContainer },
            )
            Spacer(Modifier.height(QUEUE_PREVIEW_BOTTOM_PADDING))
        }
    }
}
