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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val THUMB_WIDTH = 8.dp
private val THUMB_HEIGHT = 48.dp
private val THUMB_MARGIN_END = 4.dp
private val POPUP_SIZE = 88.dp
private const val AUTO_HIDE_DELAY_MS = 1500L

/**
 * A fast scroller in the MD2 style: a thumb on the trailing edge that appears while the list
 * moves, can be dragged to jump through the list and shows a popup with [hintFor] of the item
 * under the thumb.
 *
 * [itemCount] is the number of scrollable items after [headerCount] header items, which the
 * thumb never points at. [rowHeightPx] is the height of one row of [columns] items.
 */
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
) {
    if (itemCount == 0) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var trackHeight by remember { mutableStateOf(0) }
    val thumbHeightPx = with(density) { THUMB_HEIGHT.roundToPx() }
    val rows = (itemCount + columns - 1) / columns
    val contentHeight = headerHeightPx + rows * rowHeightPx
    val scrollRange by remember(contentHeight, trackHeight) {
        derivedStateOf { (contentHeight - trackHeight).coerceAtLeast(1) }
    }
    val scrollOffset by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val first = gridState.firstVisibleItemIndex
            val itemsBefore = (first - headerCount).coerceAtLeast(0)
            val headerBefore = if (first >= headerCount) headerHeightPx else 0
            headerBefore + (itemsBefore / columns) * rowHeightPx + gridState.firstVisibleItemScrollOffset
        }
    }
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }
    val scrolling = gridState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) visible = true
        else if (visible) {
            delay(AUTO_HIDE_DELAY_MS)
            visible = false
        }
    }
    val progress = if (dragging) dragProgress
    else (scrollOffset.toFloat() / scrollRange).coerceIn(0f, 1f)
    val thumbTop = ((trackHeight - thumbHeightPx) * progress).roundToInt()
    val hintIndex = ((rows * progress).toInt().coerceIn(0, rows - 1)) * columns
    val canScroll = contentHeight > trackHeight

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
                if (dragging) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(0, (thumbTop + thumbHeightPx / 2 - with(density) { POPUP_SIZE.roundToPx() } / 2).coerceAtLeast(0)) }
                            .padding(end = 24.dp)
                            .size(POPUP_SIZE)
                            .clip(RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp, bottomStart = 44.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        SingleLineText(hintFor(hintIndex), 36.sp, 500, MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbTop) }
                        .padding(end = THUMB_MARGIN_END)
                        .width(THUMB_WIDTH)
                        .height(THUMB_HEIGHT)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .pointerInput(trackHeight, contentHeight) {
                            detectDragGestures(
                                onDragStart = { dragging = true; dragProgress = progress },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, drag ->
                                change.consume()
                                val range = (trackHeight - thumbHeightPx).coerceAtLeast(1)
                                dragProgress = (dragProgress + drag.y / range).coerceIn(0f, 1f)
                                val target = (scrollRange * dragProgress).roundToInt()
                                val (index, offset) = if (target < headerHeightPx) 0 to target else {
                                    val rest = target - headerHeightPx
                                    headerCount + (rest / rowHeightPx) * columns to rest % rowHeightPx
                                }
                                scope.launch { gridState.scrollToItem(index, offset) }
                            }
                        },
                )
            }
        }
    }
}
