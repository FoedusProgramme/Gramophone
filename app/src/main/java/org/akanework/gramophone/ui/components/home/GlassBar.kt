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

import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/*
 * A transparent bar of
 * fixed height that blurs whatever scrolls under it. The page's large title lives in the content
 * (see LargeTitle) and the bar's own small title fades in as it slides underneath.
 */

/** Height of the toolbar row, below the status bar. */
val GLASS_BAR_HEIGHT = 64.dp
private val TOOLBAR_PADDING_START = 24.dp
private val TOOLBAR_PADDING_END = 8.dp
private val BAR_TITLE_SIZE = 22.sp // textAppearanceTitleLarge

/**
 * Blur only, no flat tint: a tint would read as a solid band laid across the blurred content.
 * The colour over the blur is a gradient instead, see [topEdgeBlur].
 */
@Composable
fun glassHazeStyle(): HazeStyle = HazeStyle(
    backgroundColor = Color.Transparent,
    tints = emptyList(),
    blurRadius = 32.dp,
    noiseFactor = 0f,
    // Below API 31 there is no RenderEffect blur. Degrade to a near-opaque surface so the title
    // stays readable instead of floating over sharp content.
    fallbackTint = HazeTint(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f)),
)

/**
 * Full blur against the top edge of the screen, gone by the bottom of the bar. The easing matters
 * at both ends: haze's default holds the blur and drops it at the very end, which leaves a
 * visible line where the bar meets the content, while decaying early leaves the middle of the
 * bar barely blurred. Flat at both ends and steep in between frosts the bar and still lets it
 * meet the content without a step.
 */
private val TopEdgeProgressive = HazeProgressive.verticalGradient(
    easing = EaseInOut,
    startY = 0f,
    startIntensity = 1f,
    endY = Float.POSITIVE_INFINITY,
    endIntensity = 0f,
    preferPerformance = false,
)

/** How much of the page's colour covers the blur against the top edge of the screen. */
private const val TOP_EDGE_SCRIM_ALPHA = 0.65f

/**
 * The page's colour over the blur, fading out along the same easing as the blur itself, so text
 * and icons on the bar stay readable over busy content without a band where the bar ends.
 */
private val TopEdgeScrimStops: List<Pair<Float, Float>> = (0..8).map { i ->
    val y = i / 8f
    y to TOP_EDGE_SCRIM_ALPHA * (1f - EaseInOut.transform(y))
}

/**
 * Blurs whatever [hazeState] recorded, strongest against the top edge of the screen, and lays
 * [scrim] (the page's background) over it the same way. Haze redraws the whole node from its
 * half resolution copy of the content whatever the intensity, so the node must be exactly the
 * bar and never extend over content that should stay sharp.
 */
fun Modifier.topEdgeBlur(hazeState: HazeState, style: HazeStyle, scrim: Color): Modifier =
    drawWithCache {
        val brush = Brush.verticalGradient(
            *TopEdgeScrimStops.map { (y, a) -> y to scrim.copy(alpha = scrim.alpha * a) }
                .toTypedArray()
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush)
        }
    }.hazeEffect(hazeState, style) {
        inputScale = HazeInputScale.Fixed(0.5f)
        progressive = TopEdgeProgressive
    }

/**
 * The glass toolbar over a page's content: [navigationIcon], the small [title], then [actions].
 * The title fades in based on [scrolled] as the content's [LargeTitle] scrolls under the bar.
 * [frost] controls the blur alpha, for pages whose content starts directly below the bar and
 * should not be blurred at rest.
 */
@Composable
fun GlassTitleBar(
    hazeState: HazeState,
    title: String,
    scrolled: () -> Float,
    modifier: Modifier = Modifier,
    toolbarPaddingStart: Dp = TOOLBAR_PADDING_START,
    toolbarPaddingEnd: Dp = TOOLBAR_PADDING_END,
    titlePaddingStart: Dp = 0.dp,
    /** Room after the title, for buttons laid over the bar rather than passed as [actions]. */
    titlePaddingEnd: Dp = 8.dp,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /** How far below the bar the content's large title starts at rest, see [barTitleAlpha]. */
    titleTopGap: Dp = LARGE_TITLE_TOP_GAP,
    /** Blur alpha: 1 when content is under the bar, 0 when none is. */
    frost: () -> Float = { 1f },
) {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val topInset = insets.asPaddingValues().calculateTopPadding()
    val style = glassHazeStyle()
    Box(modifier.fillMaxWidth()) {
        // The blur is its own box, sized to exactly the frosted area, status bar included.
        Box(
            Modifier
                .fillMaxWidth()
                .height(topInset + GLASS_BAR_HEIGHT)
                // Read in the draw phase to avoid recomposing on every scroll frame. Fades the
                // colour over the blur along with it.
                .graphicsLayer { alpha = frost() }
                .topEdgeBlur(hazeState, style, MaterialTheme.colorScheme.surfaceContainerLow),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .height(GLASS_BAR_HEIGHT)
                .padding(start = toolbarPaddingStart, end = toolbarPaddingEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon?.invoke()
            BasicText(
                text = title,
                style = textViewStyle(BAR_TITLE_SIZE, 400, MaterialTheme.colorScheme.onSurface)
                    .copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = titlePaddingStart, end = titlePaddingEnd)
                    .graphicsLayer { alpha = barTitleAlpha(scrolled(), titleTopGap) },
            )
            actions()
        }
    }
}
