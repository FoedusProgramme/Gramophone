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

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akanework.gramophone.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.akanework.gramophone.ui.home.HomeMenuAction
import org.akanework.gramophone.ui.home.HomeTab

// M3 medium collapsing toolbar (fragment_viewpager.xml)
val APP_BAR_EXPANDED_HEIGHT = 112.dp
val APP_BAR_LARGE_EXPANDED_HEIGHT = 152.dp
val APP_BAR_COLLAPSED_HEIGHT = 64.dp
val TAB_ROW_HEIGHT = 48.dp
private val EXPANDED_TITLE_MARGIN_START = 24.dp
private val EXPANDED_TITLE_MARGIN_BOTTOM = 16.dp
private val EXPANDED_TITLE_MARGIN_END = 16.dp
private val TOOLBAR_PADDING_START = 24.dp
private val TOOLBAR_PADDING_END = 8.dp
private val EXPANDED_TITLE_SIZE = 32.sp // textAppearanceHeadlineLarge
private val COLLAPSED_TITLE_SIZE = 22.sp // textAppearanceTitleLarge
private val TAB_CONTENT_PADDING = 24.dp // tab_layout_content_padding
private val TAB_PADDING = 12.dp
private val TAB_INDICATOR_INSET = 6.dp
private val TAB_INDICATOR_RADIUS = 10.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberHomeAppBarScrollBehavior(): TopAppBarScrollBehavior {
    val state = rememberTopAppBarState()
    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state)
}

/** Decelerate interpolator, as the collapsing toolbar uses for the title size. */
private fun decelerate(f: Float) = 1f - (1f - f) * (1f - f)

/**
 * A collapsing top bar in the style of Material's medium / large collapsing toolbar: the title
 * scales from headline-large at the bottom of the expanded area into the pinned 64dp toolbar
 * row, which holds [navigationIcon] at the start and [actions] at the end. [below] is laid out
 * under the collapsing area (the home puts its tab row there).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingTitleBar(
    scrollBehavior: TopAppBarScrollBehavior,
    title: String,
    expandedHeight: Dp,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 1,
    toolbarPaddingStart: Dp = TOOLBAR_PADDING_START,
    collapsedTitleStart: Dp = TOOLBAR_PADDING_START,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val collapseRangePx = with(density) { (expandedHeight - APP_BAR_COLLAPSED_HEIGHT).toPx() }
    LaunchedEffect(collapseRangePx) {
        scrollBehavior.state.heightOffsetLimit = -collapseRangePx
    }
    val offset = scrollBehavior.state.heightOffset
    val fraction = scrollBehavior.state.collapsedFraction
    val collapsingHeight = with(density) { expandedHeight.toPx() + offset }
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { collapsingHeight.toDp() })
                .clipToBounds(),
        ) {
            CollapsingTitle(
                title = title,
                fraction = fraction,
                offset = offset,
                expandedHeight = expandedHeight,
                collapsedStart = collapsedTitleStart,
                maxLines = titleMaxLines,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(APP_BAR_COLLAPSED_HEIGHT)
                    .padding(start = toolbarPaddingStart, end = TOOLBAR_PADDING_END)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navigationIcon?.invoke()
                Spacer(Modifier.weight(1f))
                actions()
            }
        }
        below()
    }
}

/** The home app bar: the collapsing title bar with search / overflow, then the tab row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAppBar(
    scrollBehavior: TopAppBarScrollBehavior,
    tabs: List<HomeTab>,
    selectedTab: Int,
    tabOffsetFraction: Float,
    onTabClick: (Int) -> Unit,
    onSearch: () -> Unit,
    onMenuAction: (HomeMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    CollapsingTitleBar(
        scrollBehavior = scrollBehavior,
        title = stringResource(R.string.app_name),
        expandedHeight = APP_BAR_EXPANDED_HEIGHT,
        modifier = modifier,
        actions = {
            LibraryIconButton(
                icon = R.drawable.ic_search,
                iconSize = 24.dp,
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onSearch,
            )
            HomeOverflowMenu(onMenuAction)
        },
    ) {
        if (tabs.size >= 2) {
            HomeTabRow(
                tabs = tabs,
                selectedTab = selectedTab,
                offsetFraction = tabOffsetFraction,
                onTabClick = onTabClick,
            )
        }
    }
}

/**
 * The title, scaling from 32sp at the bottom-left of the expanded area to 22sp centred in the
 * pinned 64dp toolbar row, like `titleCollapseMode="scale"`.
 */
@Composable
private fun CollapsingTitle(
    title: String,
    fraction: Float,
    offset: Float,
    expandedHeight: Dp,
    collapsedStart: Dp,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var titleSize by remember { mutableStateOf(IntSize.Zero) }
    val scale = 1f + (COLLAPSED_TITLE_SIZE.value / EXPANDED_TITLE_SIZE.value - 1f) * decelerate(fraction)
    val collapsedScale = COLLAPSED_TITLE_SIZE.value / EXPANDED_TITLE_SIZE.value
    val expandedX = with(density) { EXPANDED_TITLE_MARGIN_START.toPx() }
    val collapsedX = with(density) { collapsedStart.toPx() }
    val expandedY = with(density) {
        expandedHeight.toPx() - EXPANDED_TITLE_MARGIN_BOTTOM.toPx() - titleSize.height
    }
    // Collapsed position in expanded-area coordinates, before the area is translated up.
    val collapsedY = with(density) {
        (expandedHeight - APP_BAR_COLLAPSED_HEIGHT).toPx() +
                (APP_BAR_COLLAPSED_HEIGHT.toPx() - titleSize.height * collapsedScale) / 2f
    }
    BasicText(
        text = title,
        style = textViewStyle(EXPANDED_TITLE_SIZE, 400, MaterialTheme.colorScheme.onSurface)
            .copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .padding(end = EXPANDED_TITLE_MARGIN_END)
            .onSizeChanged { titleSize = it }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
                translationX = expandedX + (collapsedX - expandedX) * fraction
                translationY = expandedY + (collapsedY - expandedY) * fraction + offset
            },
    )
}

/** The overflow button with the `home_menu` entries. */
@Composable
private fun HomeOverflowMenu(onMenuAction: (HomeMenuAction) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        LibraryIconButton(
            icon = R.drawable.ic_more_vert_alt_topappbar,
            iconSize = 32.dp,
            tint = MaterialTheme.colorScheme.onSurface,
            onClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            HomeMenuAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(action.title),
                            fontSize = 16.sp, fontWeight = FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        Icon(rememberDrawablePainter(action.icon), contentDescription = null)
                    },
                    onClick = { menuOpen = false; onMenuAction(action) },
                )
            }
        }
    }
}

/**
 * Scrollable tab row with the chip-shaped indicator of `selected_chip_background`, following
 * the pager (selected tab + offset fraction) and keeping the selected tab centred.
 */
@Composable
private fun HomeTabRow(
    tabs: List<HomeTab>,
    selectedTab: Int,
    offsetFraction: Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val tabBounds = remember(tabs) {
        mutableStateListOf<Pair<Float, Float>>().apply { repeat(tabs.size) { add(0f to 0f) } }
    }
    var rowWidth by remember { mutableStateOf(0) }
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val selectedColor = MaterialTheme.colorScheme.onSecondaryContainer
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val inset = with(density) { TAB_INDICATOR_INSET.toPx().toInt().toFloat() }
    val radius = with(density) { TAB_INDICATOR_RADIUS.toPx() }

    // Keep the selected tab centred, like TabLayout.calculateScrollXForTab.
    LaunchedEffect(selectedTab, rowWidth) {
        val (left, width) = tabBounds.getOrNull(selectedTab) ?: return@LaunchedEffect
        if (width == 0f || rowWidth == 0) return@LaunchedEffect
        val target = (left + width / 2f - rowWidth / 2f).roundToInt()
            .coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target, tween(300))
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(TAB_ROW_HEIGHT)
            .onSizeChanged { rowWidth = it.width }
            .horizontalScroll(scrollState)
            .drawBehind {
                val current = tabBounds.getOrNull(selectedTab) ?: return@drawBehind
                val nextIndex = if (offsetFraction >= 0f) selectedTab + 1 else selectedTab - 1
                val next = tabBounds.getOrNull(nextIndex) ?: current
                val f = abs(offsetFraction)
                val (l0, w0) = current
                val (l1, w1) = next
                // Elastic indicator: the leading edge accelerates, the trailing edge decelerates.
                val acc = (1.0 - cos(f * PI / 2.0)).toFloat()
                val dec = sin(f * PI / 2.0).toFloat()
                val movingRight = l1 > l0
                val left = l0 + (l1 - l0) * (if (movingRight) acc else dec)
                val right = (l0 + w0) + ((l1 + w1) - (l0 + w0)) * (if (movingRight) dec else acc)
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset(left, inset),
                    size = Size(right - left, size.height - inset * 2),
                    cornerRadius = CornerRadius(radius, radius),
                )
            },
    ) {
        tabs.forEachIndexed { index, tab ->
            Box(
                Modifier
                    .padding(
                        start = if (index == 0) TAB_CONTENT_PADDING else 0.dp,
                        end = if (index == tabs.lastIndex) TAB_CONTENT_PADDING else 0.dp,
                    )
                    .height(TAB_ROW_HEIGHT)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTabClick(index) },
                    )
                    .onGloballyPositioned { coords ->
                        tabBounds[index] = coords.positionInParent().x to coords.size.width.toFloat()
                    }
                    .padding(horizontal = TAB_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                SingleLineText(
                    stringResource(tab.label), 15.sp, 500,
                    if (index == selectedTab) selectedColor else unselectedColor,
                )
            }
        }
    }
}
