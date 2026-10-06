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
package org.akanework.gramophone.ui.components.compose

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.collectLatest

/*
 * Drag to reorder in a LazyColumn. A row's handle starts the drag. The row follows the finger
 * and swaps with the neighbour whose slot its centre crosses, so the list underneath is always
 * in its final order and the finger's row is drawn off its slot, under the finger. Held near the
 * top or bottom, the list scrolls, the rows passing under it swapping with it. Rows need stable
 * keys so that a swapped or removed row keeps its own node and gesture instead of inheriting one.
 */

/** How fast the list scrolls, per second, with the dragged row held at its edge. */
private val AUTO_SCROLL_MAX_SPEED = 1200.dp

@Stable
class ReorderableListState(
    val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingIndex: Int? by mutableStateOf(null)
        private set

    // Where the dragged row's slot was as the drag started, and how far the finger has gone
    // since: together where the row's drawn, however the list moves under it
    private var startOffset = 0
    private var draggedBy by mutableFloatStateOf(0f)

    // A drag moved the row this frame, and the list isn't laid out in its new order yet
    private var movedByFinger = false

    /** How far the dragged row is drawn from its slot, to stay under the finger. */
    val dragOffset: Float
        get() = draggedItem()?.let { startOffset + draggedBy - it.offset } ?: 0f

    fun start(index: Int) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggingIndex = index
        startOffset = item.offset
        draggedBy = 0f
    }

    fun drag(delta: Float) {
        if (draggingIndex == null) return
        draggedBy += delta
        if (moveUnderFinger()) movedByFinger = true
    }

    fun end() {
        draggingIndex = null
        draggedBy = 0f
        movedByFinger = false
    }

    private fun draggedItem(): LazyListItemInfo? {
        val index = draggingIndex ?: return null
        return listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    }

    /** Swaps the dragged row with the one its centre is over, if it's over another. */
    private fun moveUnderFinger(): Boolean {
        val dragged = draggedItem() ?: return false
        val visible = listState.layoutInfo.visibleItemsInfo
        // Held past the first or last row on screen, it's over that row: the list scrolling there
        // has to keep swapping it along, or its slot would scroll away from under the finger
        val top = visible.first().offset.toFloat()
        val bottom = visible.last().let { it.offset + it.size - 1 }.toFloat().coerceAtLeast(top)
        val centre = (startOffset + draggedBy + dragged.size / 2f).coerceIn(top, bottom)
        val target = visible.firstOrNull {
            it.index != dragged.index && centre >= it.offset && centre < it.offset + it.size
        } ?: return false
        // The list keeps its first visible row in place by key, so moving it, or another row
        // onto it, would move the list along with it, and the row away from the finger. Kept in
        // place by position instead for the move, the two rows just swap slots.
        val first = listState.firstVisibleItemIndex
        if (dragged.index == first || target.index == first) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        onMove(dragged.index, target.index)
        draggingIndex = target.index
        return true
    }

    /** Scrolls the list every frame the dragged row's held near its top or bottom. */
    internal suspend fun autoScroll(maxSpeed: Float) {
        snapshotFlow { autoScrollSpeed(maxSpeed) != 0f }.collectLatest { scrolling ->
            if (!scrolling) return@collectLatest
            var lastFrame = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                val distance = autoScrollSpeed(maxSpeed) * (frame - lastFrame) / 1e9f
                lastFrame = frame
                // Scrolled now, the list would be laid out again in its old order, and lose
                // where it's kept for the move, see moveUnderFinger: next frame
                if (movedByFinger) {
                    movedByFinger = false
                    continue
                }
                if (distance != 0f) {
                    listState.scrollBy(distance)
                    moveUnderFinger()
                }
            }
        }
    }

    /**
     * How fast (px a second, negative up) the list scrolls for the dragged row: from nothing with
     * its centre a row's height from the edge it's been dragged towards, to [maxSpeed] at it.
     */
    private fun autoScrollSpeed(maxSpeed: Float): Float {
        val dragged = draggedItem() ?: return 0f
        val zone = dragged.size.toFloat()
        if (zone <= 0f) return 0f
        val centre = startOffset + draggedBy + dragged.size / 2f
        // Rows start at 0, below any content padding over the list (a bar over it, say), and the
        // list ends at the bottom of the viewport, where any padding under them is just room
        val bottom = listState.layoutInfo.viewportEndOffset
        return when {
            draggedBy < 0f && centre < zone && listState.canScrollBackward ->
                -maxSpeed * ((zone - centre) / zone).coerceAtMost(1f)
            draggedBy > 0f && centre > bottom - zone && listState.canScrollForward ->
                maxSpeed * ((centre - (bottom - zone)) / zone).coerceAtMost(1f)
            else -> 0f
        }
    }
}

@Composable
fun rememberReorderableListState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableListState {
    val currentOnMove by rememberUpdatedState(onMove)
    val state = remember(listState) {
        ReorderableListState(listState) { from, to -> currentOnMove(from, to) }
    }
    val maxSpeed = with(LocalDensity.current) { AUTO_SCROLL_MAX_SPEED.toPx() }
    LaunchedEffect(state, maxSpeed) { state.autoScroll(maxSpeed) }
    return state
}

/**
 * Applied to the row at [index]: follows the pointer while dragged, otherwise animates into place.
 * [scope] is the lazy list item scope, needed for `animateItem`.
 */
fun Modifier.reorderableRow(
    scope: LazyItemScope,
    state: ReorderableListState,
    index: Int,
): Modifier = if (state.draggingIndex == index) {
    zIndex(1f).graphicsLayer { translationY = state.dragOffset }
} else {
    with(scope) { animateItem() }
}

/** On the row's handle: a drag here moves the row at [index]. */
@Composable
fun Modifier.reorderHandle(state: ReorderableListState, index: Int): Modifier {
    val currentIndex by rememberUpdatedState(index)
    return pointerInput(state) {
        // detectDragGestures reports neither end nor cancel when the row leaves the list mid-drag
        var dragging = false
        try {
            detectDragGestures(
                onDragStart = {
                    dragging = true
                    state.start(currentIndex)
                },
                onDragEnd = {
                    dragging = false
                    state.end()
                },
                onDragCancel = {
                    dragging = false
                    state.end()
                },
            ) { change, dragAmount ->
                change.consume()
                state.drag(dragAmount.y)
            }
        } finally {
            if (dragging) state.end()
        }
    }
}
