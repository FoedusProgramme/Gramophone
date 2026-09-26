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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.HomeTab
import org.akanework.gramophone.ui.LocalCardSurface
import org.akanework.gramophone.ui.actions.HomeMenuAction
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val TAB_ROW_HEIGHT = 48.dp
private val TAB_CONTENT_PADDING = 24.dp
private val TAB_PADDING = 12.dp
/** How far the chip indicator sits inside the tab row, top and bottom. */
val TAB_INDICATOR_INSET = 6.dp
private val TAB_INDICATOR_RADIUS = 12.dp

// The connected app-bar actions (search, the optional sort, overflow): filled buttons, rounded
// 24dp on the group's outer edges and 4dp where they face each other, with a 4dp gap between them.
private val ACTION_BUTTON_WIDTH = 42.dp
val ACTION_BUTTON_HEIGHT = 44.dp
private val ACTION_OUTER_CORNER = 24.dp
private val ACTION_INNER_CORNER = 4.dp
private val ACTION_BUTTON_GAP = 4.dp
private val ACTION_ICON_INNER_OFFSET = 2.dp
private val SEARCH_BUTTON_SHAPE = RoundedCornerShape(
    topStart = ACTION_OUTER_CORNER, bottomStart = ACTION_OUTER_CORNER,
    topEnd = ACTION_INNER_CORNER, bottomEnd = ACTION_INNER_CORNER,
)
private val MIDDLE_BUTTON_SHAPE = RoundedCornerShape(ACTION_INNER_CORNER)
private val OVERFLOW_BUTTON_SHAPE = RoundedCornerShape(
    topStart = ACTION_INNER_CORNER, bottomStart = ACTION_INNER_CORNER,
    topEnd = ACTION_OUTER_CORNER, bottomEnd = ACTION_OUTER_CORNER,
)

/**
 * The home's glass toolbar: the app name, fading in as the page's large title slides under it,
 * with the search and overflow actions. [scrolled] is the page's travel from rest, see
 * [largeTitleScroll]. The tab row is not part of it: it scrolls with the content, see
 * [HomeTabRow].
 */
private val BAR_PADDING_START = 24.dp
private val BAR_PADDING_END = 16.dp

/**
 * The home's bar: the app's mark at the start, the search and overflow actions at the end, and
 * between them a sort button while [sortMenu] is non-null, squeezing in and out as it changes.
 */
@Composable
fun HomeAppBar(
    onSearch: () -> Unit,
    onMenuAction: (HomeMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    sortMenu: (@Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit)? = null,
) {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(GLASS_BAR_HEIGHT)
            .padding(start = BAR_PADDING_START, end = BAR_PADDING_END),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = GramophoneLogo,
            contentDescription = stringResource(R.string.app_name),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        HomeActionButton(
            icon = Icons.Outlined.Search,
            iconSize = 24.dp,
            shape = SEARCH_BUTTON_SHAPE,
            iconOffsetX = ACTION_ICON_INNER_OFFSET, // nudge toward the inner edge
            onClick = onSearch,
        )
        Spacer(Modifier.width(ACTION_BUTTON_GAP))
        // Keeps the last menu while the button squeezes out.
        var shownSortMenu by remember { mutableStateOf(sortMenu) }
        if (sortMenu != null) shownSortMenu = sortMenu
        var sortMenuOpen by remember { mutableStateOf(false) }
        if (sortMenu == null) sortMenuOpen = false
        AnimatedVisibility(
            visible = sortMenu != null,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
        ) {
            Row {
                Box {
                    HomeActionButton(
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        iconSize = 24.dp,
                        shape = MIDDLE_BUTTON_SHAPE,
                        iconOffsetX = 0.dp,
                        onClick = { sortMenuOpen = true },
                    )
                    shownSortMenu?.invoke(sortMenuOpen) { sortMenuOpen = false }
                }
                Spacer(Modifier.width(ACTION_BUTTON_GAP))
            }
        }
        HomeOverflowMenu(onMenuAction)
    }
}

/**
 * One of the connected app-bar actions: a filled [ACTION_BUTTON_WIDTH]×[ACTION_BUTTON_HEIGHT]
 * button rounded 24dp on its outer edge and 4dp on the edge facing its neighbour, with the icon
 * nudged [iconOffsetX] toward the inner edge for optical balance.
 */
@Composable
private fun HomeActionButton(
    icon: ImageVector,
    iconSize: Dp,
    shape: Shape,
    iconOffsetX: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(width = ACTION_BUTTON_WIDTH, height = ACTION_BUTTON_HEIGHT)
            .clip(shape)
            .background(LocalCardSurface.current)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.offset(x = iconOffsetX).size(iconSize),
        )
    }
}

/** The overflow button with the `home_menu` entries. */
@Composable
private fun HomeOverflowMenu(onMenuAction: (HomeMenuAction) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        HomeActionButton(
            icon = Icons.Outlined.MoreVert,
            iconSize = 24.dp,
            shape = OVERFLOW_BUTTON_SHAPE,
            iconOffsetX = -ACTION_ICON_INNER_OFFSET, // nudge toward the inner edge
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
                        Icon(action.icon, contentDescription = null)
                    },
                    onClick = { menuOpen = false; onMenuAction(action) },
                )
            }
        }
    }
}

/**
 * Scrollable tab row with the chip-shaped indicator of `selected_chip_background`, following
 * the pager (selected tab + offset fraction) and keeping the selected tab centred. Drawn over
 * the pages below the large title and scrolling with them, so [enabled] is false once it has
 * gone under the toolbar and touches there belong to whatever is underneath.
 */
@Composable
fun HomeTabRow(
    tabs: List<HomeTab>,
    selectedTab: Int,
    offsetFraction: () -> Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LabelTabRow(
        labels = tabs.map { stringResource(it.label) },
        selectedTab = selectedTab,
        offsetFraction = offsetFraction,
        onTabClick = onTabClick,
        modifier = modifier,
        enabled = enabled,
    )
}

/** The same tab row for any set of [labels], such as the folder filter's two lists. */
@Composable
fun LabelTabRow(
    labels: List<String>,
    selectedTab: Int,
    /** Read in the draw phase, since the pager offset changes every frame. */
    offsetFraction: () -> Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val tabBounds = remember(labels) {
        mutableStateListOf<Pair<Float, Float>>().apply { repeat(labels.size) { add(0f to 0f) } }
    }
    var rowWidth by remember { mutableIntStateOf(0) }
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
            .horizontalScroll(scrollState, enabled = enabled)
            .drawBehind {
                val fraction = offsetFraction()
                val current = tabBounds.getOrNull(selectedTab) ?: return@drawBehind
                val nextIndex = if (fraction >= 0f) selectedTab + 1 else selectedTab - 1
                val next = tabBounds.getOrNull(nextIndex) ?: current
                val f = abs(fraction)
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
        labels.forEachIndexed { index, label ->
            Box(
                Modifier
                    .padding(
                        start = if (index == 0) TAB_CONTENT_PADDING else 0.dp,
                        end = if (index == labels.lastIndex) TAB_CONTENT_PADDING else 0.dp,
                    )
                    .height(TAB_ROW_HEIGHT)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = enabled,
                        onClick = { onTabClick(index) },
                    )
                    .onGloballyPositioned { coords ->
                        tabBounds[index] = coords.positionInParent().x to coords.size.width.toFloat()
                    }
                    .padding(horizontal = TAB_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                SingleLineText(
                    label, 15.sp, 500,
                    if (index == selectedTab) selectedColor else unselectedColor,
                )
            }
        }
    }
}
