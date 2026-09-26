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

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The large title a page's content starts with, under the glass toolbar. It scrolls with the
 * content and fades out as it passes under the toolbar, whose own small title takes over.
 */

val LARGE_TITLE_TOP_GAP = 32.dp
private val LARGE_TITLE_BOTTOM_GAP = 8.dp
private val LARGE_TITLE_MARGIN_START = 24.dp
private val LARGE_TITLE_MARGIN_END = 24.dp
private val LARGE_TITLE_SIZE = 32.sp // textAppearanceHeadlineLarge
private val LARGE_TITLE_SUBTITLE_SIZE = 18.sp
private val LARGE_TITLE_SUBTITLE_GAP = 2.dp

/** Fade-through timings when the title changes to another entry. */
private const val TITLE_FADE_OUT_MS = 90
private const val TITLE_FADE_IN_MS = 210

private data class LargeTitleText(val key: Any, val title: String, val subtitle: String?)

/** How far the large title travels under the toolbar before the toolbar's own is fully in. */
private val TITLE_FADE_SPAN = 48.dp

/**
 * The title of the settings pages: displayMedium at 42sp with a 48sp line height, medium weight.
 */
val PageTitleStyle: TextStyle
    @Composable get() = MaterialTheme.typography.displayMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Medium,
    )

/** Where that title's text starts below the bar. */
val PAGE_TITLE_TOP_GAP = 16.dp + 24.dp

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

/**
 * 0 while the large title is clear of the toolbar, 1 once its top is [TITLE_FADE_SPAN] under it.
 * [titleTopGap] is how far below the toolbar the title's text starts at rest.
 */
fun Density.barTitleAlpha(scrolled: Float, titleTopGap: Dp = LARGE_TITLE_TOP_GAP): Float =
    ((scrolled - titleTopGap.toPx()) / TITLE_FADE_SPAN.toPx()).coerceIn(0f, 1f)

/**
 * Scroll offset of the grid's [LargeTitle] item relative to the bottom of the toolbar, in px.
 * Positive once the title is under the toolbar, negative while it is below it or overscrolled.
 * The offset is only known while the title or the item above it is visible. Otherwise the capped
 * value is returned, so the result stays continuous. [titleIndex] is the title's index in the
 * grid and [leadingPx] the height of the content above it.
 */
fun largeTitleScroll(
    grid: LazyGridState,
    overscroll: IosOverscrollState,
    state: LargeTitleState,
    contentTopPx: Float,
    titleIndex: Int = 0,
    leadingPx: Float = 0f,
): Float {
    val limit = state.itemHeight + contentTopPx
    val scrolled = when (grid.firstVisibleItemIndex) {
        titleIndex -> grid.firstVisibleItemScrollOffset.toFloat()
        titleIndex - 1 -> grid.firstVisibleItemScrollOffset.toFloat() - leadingPx
        else -> limit
    }
    return (scrolled - overscroll.offset).coerceAtMost(limit)
}

/**
 * The large title, as the first (full span) item of a page's grid. [gutter] is the grid's own
 * side padding, taken off the margin so the title stays on the 24dp line in grid layouts.
 * [bottomSpacer] leaves room below the title for a row drawn over the content, such as the
 * home's tab row, which then scrolls as if it were part of this item. [subtitle] is an optional
 * smaller second line. [trailing] is placed after the title and scrolls and fades with it.
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
    subtitle: String? = null,
    /** The text style. */
    style: TextStyle = textViewStyle(LARGE_TITLE_SIZE, 400, MaterialTheme.colorScheme.onSurface)
        .copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    topGap: Dp = LARGE_TITLE_TOP_GAP,
    bottomGap: Dp = LARGE_TITLE_BOTTOM_GAP,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    /**
     * Key for the text. A new key cross-fades the text. Changes under the same key, or a null
     * key, are applied without animation.
     */
    contentKey: Any? = null,
    /** Single-line title and subtitle with a marquee when they overflow. */
    marquee: Boolean = false,
) {
    val lineModifier = if (marquee) Modifier.fillMaxWidth().basicMarquee() else Modifier.fillMaxWidth()
    val overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis
    Row(
        modifier
            .fillMaxWidth()
            .onSizeChanged { state.itemHeight = it.height.toFloat() }
            .padding(
                start = LARGE_TITLE_MARGIN_START - gutter,
                end = LARGE_TITLE_MARGIN_END - gutter,
                top = topGap,
                bottom = bottomGap + bottomSpacer,
            )
            .graphicsLayer { alpha = 1f - barTitleAlpha(scrolled(), topGap) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        @Composable
        fun Texts(title: String, subtitle: String?) = Column(Modifier.fillMaxWidth()) {
            BasicText(
                text = title,
                style = style,
                maxLines = if (marquee) 1 else maxLines,
                overflow = overflow,
                softWrap = !marquee,
                modifier = lineModifier,
            )
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    style = textViewStyle(
                        LARGE_TITLE_SUBTITLE_SIZE, 400, MaterialTheme.colorScheme.onSurfaceVariant
                    ).copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    maxLines = 1,
                    overflow = overflow,
                    softWrap = !marquee,
                    modifier = Modifier
                        .padding(top = LARGE_TITLE_SUBTITLE_GAP)
                        .then(lineModifier),
                )
            }
        }
        if (contentKey == null) {
            Box(Modifier.weight(1f)) { Texts(title, subtitle) }
        } else {
            AnimatedContent(
                targetState = LargeTitleText(contentKey, title, subtitle),
                contentKey = { it.key },
                transitionSpec = {
                    (fadeIn(tween(TITLE_FADE_IN_MS, delayMillis = TITLE_FADE_OUT_MS)) togetherWith
                            fadeOut(tween(TITLE_FADE_OUT_MS)))
                        .using(SizeTransform(clip = false))
                },
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f),
                label = "large title",
            ) { Texts(it.title, it.subtitle) }
        }
        trailing?.invoke(this)
    }
}
