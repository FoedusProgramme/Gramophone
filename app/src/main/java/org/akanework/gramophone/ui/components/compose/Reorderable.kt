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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/*
 * Drag to reorder in a LazyColumn. A row's handle starts the drag. The row follows the finger
 * and swaps with the neighbour whose slot its centre crosses, so the list underneath is always
 * in its final order and the finger's row just carries its offset over. Rows need stable keys
 * so that a swapped or removed row keeps its own node and gesture instead of inheriting one.
 */

@Stable
class ReorderableListState(
    val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingIndex: Int? by mutableStateOf(null)
        private set
    var dragOffset: Float by mutableFloatStateOf(0f)
        private set

    fun start(index: Int) {
        draggingIndex = index
        dragOffset = 0f
    }

    fun drag(delta: Float) {
        val index = draggingIndex ?: return
        dragOffset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val dragged = items.firstOrNull { it.index == index } ?: return
        val centre = dragged.offset + dragOffset + dragged.size / 2f
        val target = items.firstOrNull {
            it.index != index && centre >= it.offset && centre < it.offset + it.size
        } ?: return
        onMove(index, target.index)
        // The row keeps its place under the finger while the list re-lays it out at the target.
        dragOffset += dragged.offset - target.offset
        draggingIndex = target.index
    }

    fun end() {
        draggingIndex = null
        dragOffset = 0f
    }
}

@Composable
fun rememberReorderableListState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableListState {
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) { ReorderableListState(listState) { from, to -> currentOnMove(from, to) } }
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
        detectDragGestures(
            onDragStart = { state.start(currentIndex) },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        ) { change, dragAmount ->
            change.consume()
            state.drag(dragAmount.y)
        }
    }
}
