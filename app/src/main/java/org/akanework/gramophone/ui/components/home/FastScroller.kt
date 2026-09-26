/*
 *     Copyright (C) 2024 Akane Foundation
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

package org.akanework.gramophone.ui.components.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Size of the drawn thumb. The touch target is larger, see [THUMB_TOUCH_WIDTH]. */
private val THUMB_WIDTH = 4.dp
private val THUMB_HEIGHT = 40.dp

/** Touch target of the thumb, larger than the drawn thumb. */
private val THUMB_TOUCH_WIDTH = 28.dp
private val THUMB_TOUCH_HEIGHT = 64.dp
private val THUMB_MARGIN_END = 4.dp
private val POPUP_SIZE = 88.dp
private const val AUTO_HIDE_DELAY_MS = 1500L

/** Rotation of the popup shape. */
private const val POPUP_BASE_DEGREES = 90f

/** Popup enter/exit animation while dragging. */
private const val POPUP_APPEAR_MS = 180
private const val POPUP_FADE_MS = 150
private const val POPUP_SCALE = 0.7f

/**
 * Thumb distance from the end of the track at which the FAB hides. At that point the popup,
 * centred on the thumb, reaches the FAB's corner.
 */
private val FAB_HIDE_LEAD = 56.dp

/**
 * Whether a list's fast scroller is at the end of the list. The home FAB hides on it, since the
 * thumb and popup overlap the FAB's corner there.
 */
@Stable
class FastScrollerState {
    var atBottom by mutableStateOf(false)
        internal set
}

/**
 * A fast scroller in the MD2 style: a thumb on the trailing edge that appears while the list
 * moves, can be dragged to jump through the list and shows a popup with [hintFor] of the item
 * under the thumb.
 *
 * [itemCount] is the number of scrollable items after [headerCount] header items, which the
 * thumb never points at. [rowHeightPx] is the height of one row of [columns] items.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryFastScroller(
    gridState: LazyGridState,
    itemCount: Int,
    headerCount: Int,
    columns: Int,
    rowHeightPx: Int,
    headerHeightPx: Int,
    hintFor: (Int) -> String,
    modifier: Modifier = Modifier,
    /** Updated with whether the list is scrolled to its end. Null if nothing needs it. */
    state: FastScrollerState? = null,
) {
    if (itemCount == 0) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val popupShape = MaterialShapes.Arrow.toShape()
    var trackHeight by remember { mutableIntStateOf(0) }
    val thumbHeightPx = with(density) { THUMB_HEIGHT.roundToPx() }
    // Scroll position in items, computed from the visible items. The thumb, hint and drag all use
    // this unit so they stay consistent. Deriving it from [rowHeightPx] alone would drift on pages
    // with rows of other heights, and the first drag would make the list jump.
    val scrolledItems by remember {
        derivedStateOf {
            val first = (gridState.firstVisibleItemIndex - headerCount).coerceAtLeast(0)
            val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull()
            val within = if (info != null && info.size.height > 0) {
                (gridState.firstVisibleItemScrollOffset.toFloat() / info.size.height)
                    .coerceIn(0f, 1f)
            } else 0f
            first + within
        }
    }
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val scrolling = gridState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) visible = true
        else if (visible) {
            delay(AUTO_HIDE_DELAY_MS)
            visible = false
        }
    }
    val progress = if (dragging) dragProgress
    else if (itemCount > 0) (scrolledItems / itemCount).coerceIn(0f, 1f) else 0f
    val thumbTop = ((trackHeight - thumbHeightPx) * progress).roundToInt()
    val hintIndex = (progress * itemCount).toInt().coerceIn(0, itemCount - 1)
    val canScroll = gridState.canScrollForward || gridState.canScrollBackward
    // Checks the last item is fully visible instead of using the estimated progress, which falls
    // slightly short of the end and would leave the FAB shown at the last row.
    val atEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null && last.index == info.totalItemsCount - 1 &&
                last.offset.y + last.size.height <= info.viewportEndOffset
        }
    }
    // Hide the FAB [FAB_HIDE_LEAD] before the thumb reaches the end, so it is gone before the
    // popup overlaps it. [atEnd] still covers the case where the estimate falls short.
    val thumbTravel = (trackHeight - thumbHeightPx).coerceAtLeast(1)
    val nearEnd = progress >= 1f - with(density) { FAB_HIDE_LEAD.toPx() } / thumbTravel
    if (state != null) SideEffect { state.atBottom = canScroll && (atEnd || nearEnd) }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { trackHeight = it.height },
    ) {
        AnimatedVisibility(
            visible = visible && canScroll,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = dragging,
                    enter = fadeIn(tween(POPUP_APPEAR_MS)) +
                            scaleIn(tween(POPUP_APPEAR_MS), initialScale = POPUP_SCALE),
                    exit = fadeOut(tween(POPUP_FADE_MS)) +
                            scaleOut(tween(POPUP_FADE_MS), targetScale = POPUP_SCALE),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, (thumbTop + thumbHeightPx / 2 - with(density) { POPUP_SIZE.roundToPx() } / 2).coerceAtLeast(0)) },
                ) {
                    Box(
                        Modifier
                            .padding(end = 24.dp)
                            .size(POPUP_SIZE),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Only the background shape is rotated, the hint text stays upright.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .rotate(POPUP_BASE_DEGREES)
                                .clip(popupShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                        )
                        SingleLineText(
                            hintFor(hintIndex), 32.sp, 600,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                val touchOverhang = with(density) {
                    ((THUMB_TOUCH_HEIGHT - THUMB_HEIGHT) / 2).roundToPx()
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbTop - touchOverhang) }
                        .padding(end = THUMB_MARGIN_END)
                        .width(THUMB_TOUCH_WIDTH)
                        .height(THUMB_TOUCH_HEIGHT)
                        .pointerInput(trackHeight, itemCount) {
                            detectDragGestures(
                                onDragStart = { dragging = true; dragProgress = progress },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, drag ->
                                change.consume()
                                val range = (trackHeight - thumbHeightPx).coerceAtLeast(1)
                                dragProgress = (dragProgress + drag.y / range).coerceIn(0f, 1f)
                                // Target item and offset into it, in the same unit as the thumb
                                // position.
                                val target = dragProgress * itemCount
                                val index = target.toInt().coerceIn(0, itemCount - 1)
                                val into = ((target - index) * rowHeightPx).roundToInt()
                                scope.launch {
                                    gridState.scrollToItem(headerCount + index, into)
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .width(THUMB_WIDTH)
                            .height(THUMB_HEIGHT)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}
