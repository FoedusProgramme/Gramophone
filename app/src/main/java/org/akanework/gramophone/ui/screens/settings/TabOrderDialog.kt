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

package org.akanework.gramophone.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.HomeTab
import org.akanework.gramophone.ui.mapSettingToTabList
import org.akanework.gramophone.ui.mapTabListToSetting

/*
 * The tab order dialog: the home's tabs in a list a long press drags around, with a divider
 * below which tabs are hidden. The divider never goes first and the first tab never goes below
 * the divider, so at least one tab always shows.
 */

private val TAB_ORDER_ITEM_HEIGHT = 50.dp
private val TAB_ORDER_ITEM_PADDING = 18.dp
private val DIVIDER_THICKNESS = 3.dp

/** [initial] and the confirmed value are the stored "tabs" string, see [mapSettingToTabList]. */
@Composable
fun TabOrderDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val items = remember(initial) {
        mutableStateListOf<HomeTab?>().apply { addAll(mapSettingToTabList(initial)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_order)) },
        text = { ReorderableTabList(items) },
        confirmButton = {
            TextButton(onClick = { onConfirm(mapTabListToSetting(items)) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun ReorderableTabList(items: SnapshotStateList<HomeTab?>) {
    val itemHeightPx = with(LocalDensity.current) { TAB_ORDER_ITEM_HEIGHT.toPx() }
    val scrollState = rememberScrollState()
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun canMove(from: Int, to: Int): Boolean {
        if (to !in items.indices) return false
        if (items[from] == null && to == 0) return false
        if (from == 0 && to >= items.indexOf(null)) return false
        return true
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            // After verticalScroll, so a drag that has passed the long press is ours, not a
            // scroll, and its positions are in the (scrolled) content's own coordinates.
            .pointerInput(items) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        dragging = (offset.y / itemHeightPx).toInt().coerceIn(items.indices)
                        dragOffset = 0f
                    },
                    onDragEnd = {
                        dragging = -1
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        dragging = -1
                        dragOffset = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragging < 0) return@detectDragGesturesAfterLongPress
                        dragOffset += dragAmount.y
                        // Every row is the same height, so the dragged one swaps with a neighbour
                        // once it has travelled half a row past it.
                        while (dragOffset > itemHeightPx / 2 && canMove(dragging, dragging + 1)) {
                            items.add(dragging + 1, items.removeAt(dragging))
                            dragging += 1
                            dragOffset -= itemHeightPx
                        }
                        while (dragOffset < -itemHeightPx / 2 && canMove(dragging, dragging - 1)) {
                            items.add(dragging - 1, items.removeAt(dragging))
                            dragging -= 1
                            dragOffset += itemHeightPx
                        }
                    },
                )
            },
    ) {
        items.forEachIndexed { index, tab ->
            key(tab ?: "divider") {
                val isDragged = index == dragging
                TabOrderItem(
                    tab = tab,
                    dragged = isDragged,
                    modifier = Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f },
                )
            }
        }
    }
}

@Composable
private fun TabOrderItem(tab: HomeTab?, dragged: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(TAB_ORDER_ITEM_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (dragged) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                else Modifier
            )
            .padding(horizontal = TAB_ORDER_ITEM_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tab != null) {
            Text(
                text = stringResource(tab.label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .height(DIVIDER_THICKNESS)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        Spacer(Modifier.width(TAB_ORDER_ITEM_PADDING))
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
