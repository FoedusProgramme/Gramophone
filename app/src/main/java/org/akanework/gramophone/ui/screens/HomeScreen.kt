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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.HomeTab
import org.akanework.gramophone.ui.actions.HomeActions
import org.akanework.gramophone.ui.actions.findMainActivity
import org.akanework.gramophone.ui.components.compose.rememberPreference
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.HomeAppBar
import org.akanework.gramophone.ui.components.home.IosOverscrollState
import org.akanework.gramophone.ui.components.home.largeTitleScroll
import org.akanework.gramophone.ui.components.home.rememberLargeTitleState
import org.akanework.gramophone.ui.components.home.rememberNowPlayingState
import org.akanework.gramophone.ui.nav.LocalAppBarTopPadding
import org.akanework.gramophone.ui.nav.LocalPlayerBottomPadding
import org.akanework.gramophone.ui.state.HomeViewModel
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.visibleHomeTabs
import kotlin.math.abs

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
    val hazeState = remember { HazeState() }
    val titleState = rememberLargeTitleState()
    val overscrolls = remember { HashMap<HomeTab, IosOverscrollState>() }
    fun overscrollOf(tab: HomeTab) = overscrolls.getOrPut(tab) { IosOverscrollState() }
    fun gridOf(tab: HomeTab): LazyGridState {
        val spec = LibraryTabSpec.forTab(tab)
        return if (spec != null) viewModel.tabState(spec).gridState
        else viewModel.folderState(isDetailed = tab == HomeTab.FileSystem).songs.gridState
    }
    fun scrollOf(tab: HomeTab) = with(density) { largeTitleScroll(gridOf(tab), overscrollOf(tab), titleState) }
    // The bar follows the page on show, blended with its neighbour while a swipe is in flight.
    val scrolled = {
        val current = pagerState.currentPage
        if (current > tabs.lastIndex) 0f else {
            val fraction = pagerState.currentPageOffsetFraction
            val next = (if (fraction >= 0f) current + 1 else current - 1).coerceIn(0, tabs.lastIndex)
            val from = scrollOf(tabs[current])
            from + (scrollOf(tabs[next]) - from) * abs(fraction)
        }
    }
    val topInset = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    Box(modifier.background(background)) {
        // The pages sit behind the bar as its blur source, padded clear of the toolbar at the top.
        // The background is painted inside the source, or the recorded layer is transparent
        // between the items and the sharp text underneath shows through the frost.
        CompositionLocalProvider(LocalAppBarTopPadding provides topInset + GLASS_BAR_HEIGHT) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState).background(background),
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
                        title = stringResource(R.string.app_name),
                        titleState = titleState,
                        overscroll = overscrollOf(tab),
                        hasTabRow = showTabs,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    FolderTabScreen(
                        state = viewModel.folderState(isDetailed = tab == HomeTab.FileSystem),
                        nowPlaying = nowPlaying,
                        reselectTick = reselectTicks[tab] ?: 0,
                        title = stringResource(R.string.app_name),
                        titleState = titleState,
                        overscroll = overscrollOf(tab),
                        hasTabRow = showTabs,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        HomeAppBar(
            hazeState = hazeState,
            titleState = titleState,
            scrolled = scrolled,
            tabs = tabs,
            selectedTab = pagerState.currentPage,
            tabOffsetFraction = pagerState.currentPageOffsetFraction,
            onTabClick = { index ->
                if (index == pagerState.currentPage) {
                    reselectTicks[tabs[index]] = (reselectTicks[tabs[index]] ?: 0) + 1
                } else {
                    scope.launch { pagerState.animateScrollToPage(index) }
                }
            },
            onSearch = { HomeActions.search(activity) },
            onMenuAction = { HomeActions.run(activity, it) },
        )
        // Behind the floating mini player: fade the content scrolling under it into the surface
        // colour, from the screen bottom up to the collapsed player's top.
        if (playerBottomPadding > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { playerBottomPadding.toDp() })
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerLow),
                        ),
                    ),
            )
        }
    }
}
