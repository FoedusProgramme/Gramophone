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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToInt

/*
 * The frosted toolbar, adapted from FundamentalApps/Weather (GlassTopAppBar): a transparent bar of
 * fixed height that blurs whatever scrolls under it. The page's large title lives in the content
 * (see LargeTitle) and the bar's own small title fades in as it slides underneath.
 */

/** Height of the toolbar row, below the status bar. */
val GLASS_BAR_HEIGHT = 64.dp
private val TOOLBAR_PADDING_START = 24.dp
private val TOOLBAR_PADDING_END = 8.dp
private val BAR_TITLE_SIZE = 22.sp // textAppearanceTitleLarge

/** The frost thins out over this much at the bottom of the bar. */
private val FROST_FALLOFF = 32.dp

/** Blur only, no tint: a tint would read as a solid band laid across the blurred content. */
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
 * Blurs whatever [hazeState] recorded over the bar's current [height] in px: full from the top
 * edge of the screen down to [falloffPx] above the bottom, then eased out to nothing at the
 * bottom. A hard edge would leave a visible line where the bar meets the content, while easing
 * over the whole bar would leave the pinned tab row barely frosted.
 *
 * Haze redraws the whole area from its half resolution copy of the content whatever the
 * intensity, so the node must never extend over content that should stay sharp. [height] is
 * read under snapshot observation, so a bar that grows extends its frost with it.
 */
fun Modifier.topEdgeBlur(
    hazeState: HazeState,
    style: HazeStyle,
    falloffPx: Float,
    height: () -> Float,
): Modifier = hazeEffect(hazeState, style) {
    inputScale = HazeInputScale.Fixed(0.5f)
    val bottom = height()
    progressive = HazeProgressive.verticalGradient(
        easing = EaseInOut,
        startY = (bottom - falloffPx).coerceAtLeast(0f),
        startIntensity = 1f,
        endY = bottom,
        endIntensity = 0f,
        preferPerformance = false,
    )
}

/**
 * The glass toolbar over a page's content: [navigationIcon], the small [title], then [actions].
 * The title fades in as the content's [LargeTitle] passes underneath, measured by [scrolled]. The
 * frost can extend by up to [blurExtension] below the toolbar, by [blurExtensionFraction] of it,
 * for a row that pins under the bar.
 */
@Composable
fun GlassTitleBar(
    hazeState: HazeState,
    title: String,
    scrolled: () -> Float,
    modifier: Modifier = Modifier,
    blurExtension: Dp = 0.dp,
    blurExtensionFraction: () -> Float = { 0f },
    toolbarPaddingStart: Dp = TOOLBAR_PADDING_START,
    toolbarPaddingEnd: Dp = TOOLBAR_PADDING_END,
    titlePaddingStart: Dp = 0.dp,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val topInset = insets.asPaddingValues().calculateTopPadding()
    val toolbarBottomPx = with(density) { (topInset + GLASS_BAR_HEIGHT).toPx() }
    val blurExtensionPx = with(density) { blurExtension.toPx() }
    val falloffPx = with(density) { FROST_FALLOFF.toPx() }
    val style = glassHazeStyle()
    val frostHeight = {
        toolbarBottomPx + blurExtensionPx * blurExtensionFraction().coerceIn(0f, 1f)
    }
    Box(modifier.fillMaxWidth()) {
        // The blur is its own box, sized to exactly the frosted area, status bar included.
        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val height = frostHeight().roundToInt()
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = height, maxHeight = height)
                    )
                    layout(placeable.width, height) { placeable.place(0, 0) }
                }
                .topEdgeBlur(hazeState, style, falloffPx, frostHeight),
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
                    .padding(start = titlePaddingStart, end = 8.dp)
                    .graphicsLayer { alpha = barTitleAlpha(scrolled()) },
            )
            actions()
        }
    }
}
