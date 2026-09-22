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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.akanework.gramophone.ui.components.home.HomeAppBar
import org.akanework.gramophone.ui.components.home.rememberHomeAppBarScrollBehavior
import org.akanework.gramophone.ui.components.home.rememberNowPlayingState
import org.akanework.gramophone.ui.home.HomeActions
import org.akanework.gramophone.ui.home.HomeTab
import org.akanework.gramophone.ui.home.HomeViewModel
import org.akanework.gramophone.ui.home.LibraryTabSpec
import org.akanework.gramophone.ui.home.findMainActivity
import org.akanework.gramophone.ui.home.rememberPreference
import org.akanework.gramophone.ui.home.visibleHomeTabs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findMainActivity() }
    val viewModel: HomeViewModel = viewModel(activity)
    val tabsSetting by rememberPreference("tabs") { it.getString("tabs", "") ?: "" }
    val tabs = remember(tabsSetting) { visibleHomeTabs(tabsSetting) }
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val nowPlaying = rememberNowPlayingState(
        activity.controllerViewModel, LocalLifecycleOwner.current.lifecycle
    )
    val scrollBehavior = rememberHomeAppBarScrollBehavior()
    // Incremented when the current tab is tapped again ("scroll to the playing song").
    val reselectTicks = remember { mutableStateMapOf<HomeTab, Int>() }
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        HomeAppBar(
            scrollBehavior = scrollBehavior,
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            beyondViewportPageCount = 1,
            key = { tabs[it].name },
            userScrollEnabled = tabs.size >= 2,
        ) { page ->
            val tab = tabs[page]
            val collapse = {
                scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
            }
            val spec = LibraryTabSpec.forTab(tab)
            if (spec != null) {
                LibraryTabScreen(
                    state = viewModel.tabState(spec),
                    nowPlaying = nowPlaying,
                    reselectTick = reselectTicks[tab] ?: 0,
                    onCollapseAppBar = collapse,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FolderTabScreen(
                    state = viewModel.folderState(isDetailed = tab == HomeTab.FileSystem),
                    nowPlaying = nowPlaying,
                    reselectTick = reselectTicks[tab] ?: 0,
                    onCollapseAppBar = collapse,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
