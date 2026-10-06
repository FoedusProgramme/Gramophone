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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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

/** The drawn thumb's height. Its touch target is larger, see [THUMB_TOUCH_HEIGHT]. */
private val THUMB_HEIGHT = 40.dp

/** The height of the thumb's touch target, larger than the drawn thumb. */
private val THUMB_TOUCH_HEIGHT = 64.dp
private val POPUP_SIZE = 88.dp
private const val AUTO_HIDE_DELAY_MS = 1500L

/** Fade of the whole scroller as it shows and hides. */
private const val SCROLLER_FADE_MS = 150

/** Rotation of the popup shape. */
private const val POPUP_BASE_DEGREES = 90f

/** Popup enter/exit animation while dragging. */
private const val POPUP_APPEAR_MS = 180
private const val POPUP_FADE_MS = 150
private const val POPUP_SCALE = 0.7f

/** The lazy grid or list a fast scroller moves, as much of it as the scroller needs. */
private interface FastScrollable {
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int
    val canScrollForward: Boolean
    val canScrollBackward: Boolean
    val isScrollInProgress: Boolean

    /** The layout info, which changes with every layout. */
    val layoutInfo: Any
    val beforeContentPadding: Int
    val afterContentPadding: Int
    val viewportHeight: Int

    /** Calls [block] with each laid out item's index, row, and pitch (height plus spacing). */
    fun forEachVisibleItem(block: (index: Int, row: Int, pitch: Int) -> Unit)

    /** The row of item [index], if it's laid out. */
    fun rowOf(index: Int): Int?

    suspend fun scrollToItem(index: Int, offset: Int)
}

private class GridScrollable(private val state: LazyGridState) : FastScrollable {
    override val firstVisibleItemIndex get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset get() = state.firstVisibleItemScrollOffset
    override val canScrollForward get() = state.canScrollForward
    override val canScrollBackward get() = state.canScrollBackward
    override val isScrollInProgress get() = state.isScrollInProgress
    override val layoutInfo: Any get() = state.layoutInfo
    override val beforeContentPadding get() = state.layoutInfo.beforeContentPadding
    override val afterContentPadding get() = state.layoutInfo.afterContentPadding
    override val viewportHeight get() = state.layoutInfo.viewportSize.height

    override fun forEachVisibleItem(block: (index: Int, row: Int, pitch: Int) -> Unit) {
        val info = state.layoutInfo
        for (item in info.visibleItemsInfo) {
            block(item.index, item.row, item.size.height + info.mainAxisItemSpacing)
        }
    }

    override fun rowOf(index: Int) =
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.row

    override suspend fun scrollToItem(index: Int, offset: Int) = state.scrollToItem(index, offset)
}

/** A list is a grid of one column: its rows are its items. */
private class ListScrollable(private val state: LazyListState) : FastScrollable {
    override val firstVisibleItemIndex get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset get() = state.firstVisibleItemScrollOffset
    override val canScrollForward get() = state.canScrollForward
    override val canScrollBackward get() = state.canScrollBackward
    override val isScrollInProgress get() = state.isScrollInProgress
    override val layoutInfo: Any get() = state.layoutInfo
    override val beforeContentPadding get() = state.layoutInfo.beforeContentPadding
    override val afterContentPadding get() = state.layoutInfo.afterContentPadding
    override val viewportHeight get() = state.layoutInfo.viewportSize.height

    override fun forEachVisibleItem(block: (index: Int, row: Int, pitch: Int) -> Unit) {
        val info = state.layoutInfo
        for (item in info.visibleItemsInfo) {
            block(item.index, item.index, item.size + info.mainAxisItemSpacing)
        }
    }

    override fun rowOf(index: Int) = index

    override suspend fun scrollToItem(index: Int, offset: Int) = state.scrollToItem(index, offset)
}

/**
 * Estimates the list's scroll position and range in pixels, so the thumb spans the whole track:
 * top at the very start, bottom exactly at the end, and the header and footer rows (carousel,
 * title, albums, folders, song count) take their real share of the travel.
 *
 * The [itemCount] scrollable items after [headerCount] header items are rows of [columns] items
 * [rowHeightPx] apart. The other rows' pitches are [record]ed as they are laid out, with
 * [headerHeightPx] standing in for the header until all of it has been seen. What was recorded is
 * snapshot state, so estimates read in a derived state are worked out again once a row is seen.
 */
private class ScrollModel(
    private val headerCount: Int,
    private val itemCount: Int,
    private val columns: Int,
    rowHeightPx: Int,
    private val headerHeightPx: Int,
) {
    private val rowHeightPx = rowHeightPx.coerceAtLeast(1)

    /** Pitch (height plus spacing) of the header and footer rows seen so far, by grid row. */
    private val otherRows = mutableStateMapOf<Int, Int>()
    /** Grid row of the first scrollable item, once it has been laid out. */
    private var firstItemRow by mutableIntStateOf(-1)
    /** Grid row of the last scrollable item, once it has been laid out. */
    private var lastItemRow by mutableIntStateOf(-1)

    private val itemRows get() = (itemCount + columns - 1) / columns

    /** Remembers the rows laid out in [target]. Only writes what it learns anything new from. */
    fun record(target: FastScrollable) {
        val end = headerCount + itemCount
        target.forEachVisibleItem { index, row, pitch ->
            when {
                index == headerCount -> firstItemRow = row
                index == end - 1 -> lastItemRow = row
            }
            if (index < headerCount || index >= end) {
                if (pitch > (otherRows[row] ?: 0)) otherRows[row] = pitch
            }
        }
    }

    /** Height of the header rows, measured if all of them have been seen. */
    private fun headerPx(): Int {
        if (headerCount == 0) return 0
        if (firstItemRow < 0) return headerHeightPx
        var sum = 0
        for (row in 0 until firstItemRow) sum += otherRows[row] ?: return headerHeightPx
        return sum
    }

    /** Height of the footer rows seen so far. */
    private fun footerPx(): Int {
        if (lastItemRow < 0) return 0
        return otherRows.entries.sumOf { (row, pitch) -> if (row > lastItemRow) pitch else 0 }
    }

    fun scrollPx(target: FastScrollable): Int {
        val index = target.firstVisibleItemIndex
        val offset = target.firstVisibleItemScrollOffset
        if (index >= headerCount) {
            val row = ((index - headerCount) / columns).coerceAtMost(itemRows)
            return headerPx() + row * rowHeightPx + offset
        }
        val row = target.rowOf(index) ?: 0
        var sum = 0
        for (r in 0 until row) sum += otherRows[r] ?: 0
        return sum + offset
    }

    fun maxScrollPx(target: FastScrollable): Int =
        (headerPx() + itemRows * rowHeightPx + footerPx() +
                target.beforeContentPadding + target.afterContentPadding - target.viewportHeight)
            .coerceAtLeast(1)

    /** Scrolls [target] to [px] pixels from the top, as measured by [scrollPx]. */
    suspend fun scrollTo(target: FastScrollable, px: Int) {
        val header = headerPx()
        if (px < header || itemCount == 0) {
            // The grid skips whole lines the offset scrolls past.
            target.scrollToItem(0, px.coerceAtLeast(0))
        } else {
            val row = ((px - header) / rowHeightPx).coerceAtMost(itemRows - 1)
            val into = px - header - row * rowHeightPx
            target.scrollToItem(headerCount + row * columns, into)
        }
    }
}

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
) = FastScroller(
    remember(gridState) { GridScrollable(gridState) },
    itemCount, headerCount, columns, rowHeightPx, headerHeightPx, hintFor, modifier,
)

/** [LibraryFastScroller] for a list of nothing but [itemCount] rows, [rowHeightPx] apart. */
@Composable
fun LibraryFastScroller(
    listState: LazyListState,
    itemCount: Int,
    rowHeightPx: Int,
    hintFor: (Int) -> String,
    modifier: Modifier = Modifier,
) = FastScroller(
    remember(listState) { ListScrollable(listState) },
    itemCount, headerCount = 0, columns = 1, rowHeightPx, headerHeightPx = 0, hintFor, modifier,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FastScroller(
    target: FastScrollable,
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
    val popupShape = MaterialShapes.Arrow.toShape()
    var trackHeight by remember { mutableIntStateOf(0) }
    val thumbHeightPx = with(density) { THUMB_HEIGHT.roundToPx() }
    val model = remember(headerCount, itemCount, columns, rowHeightPx, headerHeightPx) {
        ScrollModel(headerCount, itemCount, columns, rowHeightPx, headerHeightPx)
    }
    // Recorded after each layout rather than while deriving the position below, which must not
    // write state. The position follows as soon as a newly seen row has been recorded.
    LaunchedEffect(target, model) {
        snapshotFlow { target.layoutInfo }.collect { model.record(target) }
    }
    // Position along the track. Pinned to the ends when the list can't scroll further, so the
    // thumb always reaches them even where the estimate is off.
    val scrollProgress by remember(model) {
        derivedStateOf {
            when {
                !target.canScrollBackward -> 0f
                !target.canScrollForward -> 1f
                else -> (model.scrollPx(target).toFloat() /
                        model.maxScrollPx(target)).coerceIn(0f, 1f)
            }
        }
    }
    // The track ends above the list's bottom padding, clear of the mini player.
    val bottomPaddingPx by remember { derivedStateOf { target.afterContentPadding } }
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    // The scroll range when the drag started. Fixed for the drag, since it is pinned to the
    // current position once the list reaches its end.
    var dragMaxPx by remember { mutableIntStateOf(1) }
    val scrolling = target.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) visible = true
        else if (visible) {
            delay(AUTO_HIDE_DELAY_MS)
            visible = false
        }
    }
    // Where the thumb is along the track. Read in the layout phase, and in composition only
    // through the derived values below, so scrolling moves the thumb without recomposing the
    // scroller every frame.
    fun progress() = if (dragging) dragProgress else scrollProgress
    fun thumbTop() = ((trackHeight - thumbHeightPx) * progress()).roundToInt()
    val hintIndex by remember(model) {
        derivedStateOf { (progress() * itemCount).toInt().coerceIn(0, itemCount - 1) }
    }
    val canScroll = target.canScrollForward || target.canScrollBackward

    Box(
        modifier
            .fillMaxSize()
            .padding(bottom = with(density) { bottomPaddingPx.toDp() })
            .onSizeChanged { trackHeight = it.height },
    ) {
        AnimatedVisibility(
            visible = visible && canScroll,
            enter = fadeIn(tween(SCROLLER_FADE_MS)),
            exit = fadeOut(tween(SCROLLER_FADE_MS)),
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
                        .offset {
                            // Centred on the thumb, but never above the track.
                            val top = thumbTop() + thumbHeightPx / 2 - POPUP_SIZE.roundToPx() / 2
                            IntOffset(0, top.coerceAtLeast(0))
                        },
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
                        .offset { IntOffset(0, thumbTop() - touchOverhang) }
                        .padding(end = 4.dp)
                        .width(28.dp)
                        .height(THUMB_TOUCH_HEIGHT)
                        .pointerInput(trackHeight, model) {
                            detectDragGestures(
                                onDragStart = {
                                    // The live position: this handler outlives the composition
                                    // that made it.
                                    dragProgress = scrollProgress
                                    dragging = true
                                    dragMaxPx = if (target.canScrollForward) {
                                        model.maxScrollPx(target)
                                    } else model.scrollPx(target).coerceAtLeast(1)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, drag ->
                                change.consume()
                                val range = (trackHeight - thumbHeightPx).coerceAtLeast(1)
                                dragProgress = (dragProgress + drag.y / range).coerceIn(0f, 1f)
                                val px = (dragProgress * dragMaxPx).roundToInt()
                                scope.launch { model.scrollTo(target, px) }
                            }
                        },
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(THUMB_HEIGHT)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
    }
}
