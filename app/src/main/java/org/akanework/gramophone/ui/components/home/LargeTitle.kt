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

package org.akanework.gramophone.ui.components.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The large title a page's content starts with, under the glass toolbar. It scrolls with the
 * content and fades out as it passes under the toolbar, whose own small title takes over.
 * Adapted from FundamentalApps/Weather (CollapsingLargeTitle / CollapsingTitle), except that the
 * handover is measured from the list's scroll position rather than from layout callbacks, so it
 * also follows the rubber band.
 */

private val LARGE_TITLE_TOP_GAP = 8.dp
private val LARGE_TITLE_BOTTOM_GAP = 8.dp
private val LARGE_TITLE_MARGIN_START = 24.dp
private val LARGE_TITLE_MARGIN_END = 16.dp
private val LARGE_TITLE_SIZE = 32.sp // textAppearanceHeadlineLarge

/** How far the large title travels under the toolbar before the toolbar's own is fully in. */
private val TITLE_FADE_SPAN = 48.dp

/** Height of the [LargeTitle] item once laid out, starting from an estimate for the first frame. */
@Stable
class LargeTitleState(initialHeightPx: Float) {
    var itemHeight by mutableFloatStateOf(initialHeightPx)
        internal set
}

@Composable
fun rememberLargeTitleState(): LargeTitleState {
    val density = LocalDensity.current
    return remember(density) {
        LargeTitleState(
            with(density) {
                LARGE_TITLE_TOP_GAP.toPx() + LARGE_TITLE_BOTTOM_GAP.toPx() + LARGE_TITLE_SIZE.toPx() * 1.2f
            }
        )
    }
}

/** 0 while the large title is clear of the toolbar, 1 once its top is [TITLE_FADE_SPAN] under it. */
fun Density.barTitleAlpha(scrolled: Float): Float =
    ((scrolled - LARGE_TITLE_TOP_GAP.toPx()) / TITLE_FADE_SPAN.toPx()).coerceIn(0f, 1f)

/**
 * How far a grid whose first item is the [LargeTitle] has moved from rest, in px: positive once
 * scrolled up, negative while the rubber band holds it pulled down. The grid only reports the
 * offset while the title item is in the viewport, which with [contentTopPx] of padding above it
 * is until it has travelled its own height plus that padding. The value is capped there, so it
 * stays continuous when the grid moves on to the next item.
 */
fun largeTitleScroll(
    grid: LazyGridState,
    overscroll: IosOverscrollState,
    state: LargeTitleState,
    contentTopPx: Float,
): Float {
    val limit = state.itemHeight + contentTopPx
    val scrolled = if (grid.firstVisibleItemIndex == 0) grid.firstVisibleItemScrollOffset.toFloat() else limit
    return (scrolled - overscroll.offset).coerceAtMost(limit)
}

/**
 * The large title, as the first (full span) item of a page's grid. [gutter] is the grid's own
 * side padding, taken off the margin so the title stays on the 24dp line in grid layouts.
 * [bottomSpacer] leaves room below the title for a row drawn over the content, such as the
 * home's tab row, which then scrolls as if it were part of this item.
 */
@Composable
fun LargeTitle(
    title: String,
    state: LargeTitleState,
    scrolled: () -> Float,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    gutter: Dp = 0.dp,
    bottomSpacer: Dp = 0.dp,
) {
    BasicText(
        text = title,
        style = textViewStyle(LARGE_TITLE_SIZE, 400, MaterialTheme.colorScheme.onSurface)
            .copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { state.itemHeight = it.height.toFloat() }
            .padding(
                start = LARGE_TITLE_MARGIN_START - gutter,
                end = LARGE_TITLE_MARGIN_END - gutter,
                top = LARGE_TITLE_TOP_GAP,
                bottom = LARGE_TITLE_BOTTOM_GAP + bottomSpacer,
            )
            .graphicsLayer { alpha = 1f - barTitleAlpha(scrolled()) },
    )
}
