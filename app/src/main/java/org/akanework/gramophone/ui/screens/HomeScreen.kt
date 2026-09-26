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

package org.akanework.gramophone.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.akanework.gramophone.ui.HomeTab
import org.akanework.gramophone.ui.actions.HomeActions
import org.akanework.gramophone.ui.actions.findMainActivity
import org.akanework.gramophone.ui.components.compose.rememberPreference
import org.akanework.gramophone.ui.components.home.ACTION_BUTTON_HEIGHT
import org.akanework.gramophone.ui.components.home.FastScrollerState
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.HomeAppBar
import org.akanework.gramophone.ui.components.home.HomeTabRow
import org.akanework.gramophone.ui.components.home.IosOverscrollState
import org.akanework.gramophone.ui.components.home.LIBRARY_FAB_MARGIN
import org.akanework.gramophone.ui.components.home.LIBRARY_GROUP_CORNER
import org.akanework.gramophone.ui.components.home.LIBRARY_SIDE_MARGIN
import org.akanework.gramophone.ui.components.home.LibraryFab
import org.akanework.gramophone.ui.components.home.TAB_INDICATOR_INSET
import org.akanework.gramophone.ui.components.home.rememberNowPlayingState
import org.akanework.gramophone.ui.nav.LocalAppBarTopPadding
import org.akanework.gramophone.ui.nav.LocalListBottomPadding
import org.akanework.gramophone.ui.nav.LocalPlayerBottomPadding
import org.akanework.gramophone.ui.nav.NAV_TRANSITION_MS
import org.akanework.gramophone.ui.nav.NavAxisEasing
import org.akanework.gramophone.ui.state.HomeViewModel
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.visibleHomeTabs

/*
 * The home as two containers: the bar and the tab row sit still on the surface-container-low
 * ground, and the library pages scroll inside a rounded sheet under them, which ends above the
 * mini player. The items are surface-bright blocks with the sheet's colour between them.
 */

/*
 * The tab row sits [HEADER_GAP] under the bar's buttons and the sheet [HEADER_GAP] under the
 * tab row's chips. The buttons end above the bar's bottom edge and the chips sit inside the tab
 * row, so both gaps are measured from those, not from the layout boxes.
 */
private val HEADER_GAP = 20.dp

/** How far above the bar's bottom edge its buttons end. */
private val BUTTON_TO_BAR_BOTTOM = (GLASS_BAR_HEIGHT - ACTION_BUTTON_HEIGHT) / 2
private val TABS_OVERLAP_BAR = BUTTON_TO_BAR_BOTTOM + TAB_INDICATOR_INSET - HEADER_GAP
private val TABS_TO_SHEET_GAP = HEADER_GAP - TAB_INDICATOR_INSET
private val BAR_TO_SHEET_GAP = 16.dp

/** Between the sheet and what is under it: the mini player, or else the navigation bar. */
private val SHEET_BOTTOM_GAP = 16.dp

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findMainActivity() }
    val viewModel: HomeViewModel = viewModel(activity)
    val tabsSetting by rememberPreference("tabs") { it.getString("tabs", "") ?: "" }
    val tabs = remember(tabsSetting) { visibleHomeTabs(tabsSetting) }
    val showTabs = tabs.size >= 2
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val nowPlaying = rememberNowPlayingState(
        activity.controllerViewModel, LocalLifecycleOwner.current.lifecycle
    )
    // Incremented when the current tab is tapped again ("scroll to the playing song").
    val reselectTicks = remember { mutableStateMapOf<HomeTab, Int>() }
    // Height (px) from the screen bottom to the collapsed mini player's top, 0 when it is hidden.
    val playerBottomPadding = LocalPlayerBottomPadding.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val overscrolls = remember { HashMap<HomeTab, IosOverscrollState>() }
    fun overscrollOf(tab: HomeTab) = overscrolls.getOrPut(tab) { IosOverscrollState() }
    val fastScrollers = remember { HashMap<HomeTab, FastScrollerState>() }
    fun fastScrollerOf(tab: HomeTab) = fastScrollers.getOrPut(tab) { FastScrollerState() }
    // The sheet ends above the mini player. The mini player animates with the page transition,
    // so the inset uses the same curve. Otherwise the sheet's bottom edge would jump when
    // navigating away from or back to home.
    val sheetBottomInset by animateDpAsState(
        targetValue = if (playerBottomPadding > 0) {
            with(density) { playerBottomPadding.toDp() }
        } else {
            insets.asPaddingValues().calculateBottomPadding()
        },
        animationSpec = tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
        label = "sheet bottom inset",
    )

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        // The folder tabs sort their two lists from their own headers.
        val currentSpec = tabs.getOrNull(pagerState.currentPage)?.let { LibraryTabSpec.forTab(it) }
        HomeAppBar(
            onSearch = { HomeActions.search(activity) },
            onMenuAction = { HomeActions.run(activity, it) },
            sortMenu = currentSpec?.let { spec ->
                { expanded, onDismiss ->
                    LibrarySortMenu(viewModel.tabState(spec), expanded, onDismiss)
                }
            },
        )
        if (showTabs) {
            HomeTabRow(
                tabs = tabs,
                selectedTab = pagerState.currentPage,
                offsetFraction = { pagerState.currentPageOffsetFraction },
                onTabClick = { index ->
                    if (index == pagerState.currentPage) {
                        reselectTicks[tabs[index]] = (reselectTicks[tabs[index]] ?: 0) + 1
                    } else {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                },
                modifier = Modifier
                    .pulledUp(TABS_OVERLAP_BAR)
                    .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal)),
            )
            Spacer(Modifier.height(TABS_TO_SHEET_GAP))
        } else {
            Spacer(Modifier.height(BAR_TO_SHEET_GAP))
        }
        // The sheet. The pages scroll inside it, clipped by its corners, their items on its
        // surface with its colour showing between them. It keeps nothing clear at its top or
        // bottom itself.
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                    start = LIBRARY_SIDE_MARGIN,
                    end = LIBRARY_SIDE_MARGIN,
                    bottom = sheetBottomInset + SHEET_BOTTOM_GAP,
                )
                .clip(RoundedCornerShape(LIBRARY_GROUP_CORNER))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            CompositionLocalProvider(
                LocalAppBarTopPadding provides 0.dp,
                LocalListBottomPadding provides 0.dp,
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Between two pages in flight, the ground shows as it does beside the sheet.
                    pageSpacing = LIBRARY_SIDE_MARGIN * 2,
                    beyondViewportPageCount = 1,
                    key = { tabs[it].name },
                    userScrollEnabled = showTabs,
                ) { page ->
                    val tab = tabs[page]
                    val spec = LibraryTabSpec.forTab(tab)
                    if (spec != null) {
                        LibraryTabScreen(
                            state = viewModel.tabState(spec),
                            nowPlaying = nowPlaying,
                            reselectTick = reselectTicks[tab] ?: 0,
                            overscroll = overscrollOf(tab),
                            fastScroller = fastScrollerOf(tab),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        FolderTabScreen(
                            state = viewModel.folderState(isDetailed = tab == HomeTab.FileSystem),
                            nowPlaying = nowPlaying,
                            reselectTick = reselectTicks[tab] ?: 0,
                            overscroll = overscrollOf(tab),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            // One FAB shared by all tabs, resized to the current tab's actions. It hides when the
            // list is scrolled to its end, where the fast scroller's thumb and popup overlap it.
            val fabs = currentSpec?.let { libraryFabActions(viewModel.tabState(it), activity) }.orEmpty()
            val fastScrollerAtBottom =
                tabs.getOrNull(pagerState.currentPage)?.let { fastScrollers[it]?.atBottom } == true
            LibraryFab(
                actions = if (fastScrollerAtBottom) emptyList() else fabs,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal))
                    .padding(LIBRARY_FAB_MARGIN),
            )
        }
    }
}

/**
 * Draws the content [by] higher than its slot and gives the slot back that much, so what comes
 * before is overlapped and what follows closes up. A negative [by] leaves a gap instead.
 */
private fun Modifier.pulledUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
        placeable.placeRelative(0, -shift)
    }
}
