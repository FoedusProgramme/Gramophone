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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.actions.findMainActivity
import org.akanework.gramophone.ui.components.home.DECOR_HEIGHT
import org.akanework.gramophone.ui.components.home.FOLDER_CARD_HEIGHT
import org.akanework.gramophone.ui.components.home.IosOverscrollState
import org.akanework.gramophone.ui.components.home.LIBRARY_GROUP_CORNER
import org.akanework.gramophone.ui.components.home.LIBRARY_ITEM_GAP
import org.akanework.gramophone.ui.components.home.LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.LibraryFolderRow
import org.akanework.gramophone.ui.components.home.LibraryHeader
import org.akanework.gramophone.ui.components.home.NowPlayingState
import org.akanework.gramophone.ui.components.home.SortMenu
import org.akanework.gramophone.ui.components.home.iosOverscroll
import org.akanework.gramophone.ui.components.home.libraryCellShape
import org.akanework.gramophone.ui.components.home.libraryItemCard
import org.akanework.gramophone.ui.components.home.libraryItemShape
import org.akanework.gramophone.ui.components.home.rememberIosFlingBehavior
import org.akanework.gramophone.ui.library.LayoutType
import org.akanework.gramophone.ui.state.FolderTabState
import org.akanework.gramophone.ui.state.SortPrefState

/** The Folders / Filesystem tab, laid out like [LibraryTabScreen]: folders first, then songs. */
@Composable
fun FolderTabScreen(
    state: FolderTabState,
    nowPlaying: NowPlayingState,
    reselectTick: Int,
    overscroll: IosOverscrollState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findMainActivity() }
    val scope = rememberCoroutineScope()
    CollectLibraryItems(state.songs)
    ReportFullyDrawnWhen(state.songs.loaded)
    LaunchedEffect(state) {
        state.folderFlow.collect { state.folders = it }
    }
    val songs = state.songs
    val path = state.path
    val layoutType = songs.layoutType
    val isGrid = layoutType == LayoutType.GRID || layoutType == LayoutType.COMPACT_GRID
    val columns = libraryColumns(layoutType)
    val density = LocalDensity.current
    val rowHeightPx = with(density) {
        LIST_HEIGHT.roundToPx()
    }
    val gridState = rememberLazyGridState()
    val queueTitle = songs.queueTitleOverride ?: "/"
    var folderSortOpen by remember { mutableStateOf(false) }
    var songSortOpen by remember { mutableStateOf(false) }
    val showPop = !path.isNullOrEmpty()
    // The folders header, the parent folder row if any, the folders, then the songs header.
    val songsHeaderIndex = 1 + (if (showPop) 1 else 0) + state.folders.size

    fun scrollTo(index: Int) {
        scope.launch { gridState.animateScrollToItem(index, -rowHeightPx / 2) }
    }

    val goToPlayingSong = {
        val id = nowPlaying.currentMediaId
        val index = if (id != null) songs.items.indexOfFirst { it.mediaId == id } else -1
        if (index >= 0) scrollTo(songsHeaderIndex + 1 + index)
    }
    LaunchedEffect(reselectTick) { if (reselectTick > 0) goToPlayingSong() }

    AnimatedContent(
        targetState = path,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            // A quarter-width slide with a fade, 150ms, the way the folder pages used to move.
            val up = state.lastNavigationWasUp
            (slideInHorizontally(tween(150)) { if (up) -it / 4 else it / 4 } + fadeIn(tween(150)))
                .togetherWith(
                    slideOutHorizontally(tween(150)) { if (up) it / 4 else -it / 4 } + fadeOut(tween(150))
                )
        },
        label = "folder",
    ) { animatedPath ->
        // Based on the animated path, so the outgoing page keeps its parent row during the
        // transition.
        val showParent = !animatedPath.isNullOrEmpty()
        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize().iosOverscroll(overscroll),
            contentPadding = libraryContentPadding(),
            verticalArrangement = Arrangement.spacedBy(LIBRARY_ITEM_GAP),
            horizontalArrangement = if (isGrid) Arrangement.spacedBy(LIBRARY_ITEM_GAP) else Arrangement.Start,
            flingBehavior = rememberIosFlingBehavior(gridState),
            overscrollEffect = null,
        ) {
            item(key = "folders-header", span = { GridItemSpan(maxLineSpan) }) {
                val count = state.folders.size
                LibraryHeader(
                    modifier = Modifier.libraryItemCard(libraryItemShape(topStart = true, topEnd = true)),
                    counterText = pluralStringResource(R.plurals.folders_plural, count, count),
                    onSort = { folderSortOpen = true },
                    onJumpDown = { scrollTo(songsHeaderIndex) },
                    sortMenu = {
                        SortMenu(
                            expanded = folderSortOpen,
                            onDismiss = { folderSortOpen = false },
                            sortTypes = state.sort.sortTypes,
                            activeSort = state.sort.activeSortBase(SortPrefState.SORT_MENU_ORDER),
                            isReversed = state.sort.isReversed,
                            canReverse = state.sort.canReverse,
                            onSelectSort = { state.sort.selectSort(it) },
                            onToggleReverse = { state.sort.toggleReverse() },
                            layoutType = null,
                            onSelectLayout = null,
                        )
                    },
                )
            }
            if (showParent) {
                item(key = "..", span = { GridItemSpan(maxLineSpan) }) {
                    LibraryFolderRow(
                        title = stringResource(R.string.upper_folder),
                        subtitle = "",
                        onClick = { state.enter(null) },
                        modifier = Modifier.libraryItemCard(),
                    )
                }
            }
            items(state.folders, key = { "folder:" + it.folderName }, span = { GridItemSpan(maxLineSpan) }) { node ->
                val n = node.folderList.size + node.songList.size
                LibraryFolderRow(
                    title = node.folderName,
                    subtitle = pluralStringResource(R.plurals.items, n, n),
                    onClick = { state.enter(node.folderName) },
                    modifier = Modifier.animateItem().libraryItemCard(),
                )
            }
            item(key = "songs-header", span = { GridItemSpan(maxLineSpan) }) {
                val count = songs.items.size
                LibraryHeader(
                    modifier = Modifier.libraryItemCard(
                        libraryItemShape(bottomStart = songs.items.isEmpty(), bottomEnd = songs.items.isEmpty())
                    ),
                    counterText = pluralStringResource(R.plurals.songs, count, count),
                    onCounterClick = goToPlayingSong,
                    onPlayAll = { LibraryActions.playAll(activity, songs.items, queueTitle) },
                    onShuffleAll = { LibraryActions.shuffleAll(activity, songs.items, queueTitle) },
                    onSort = { songSortOpen = true },
                    onJumpUp = { scrollTo(0) },
                    sortMenu = {
                        SortMenu(
                            expanded = songSortOpen,
                            onDismiss = { songSortOpen = false },
                            sortTypes = songs.sortTypes,
                            activeSort = songs.sort.activeSortBase(SortPrefState.SORT_MENU_ORDER),
                            isReversed = songs.sort.isReversed,
                            canReverse = songs.sort.canReverse,
                            onSelectSort = { songs.sort.selectSort(it) },
                            onToggleReverse = { songs.sort.toggleReverse() },
                            layoutType = layoutType,
                            onSelectLayout = { songs.selectLayout(it) },
                        )
                    },
                )
            }
            itemsIndexed(songs.items, key = { _, it -> "song:" + it.mediaId }) { index, item: MediaItem ->
                LibraryItem(
                    songs, item, nowPlaying, activity, layoutType,
                    Modifier.animateItem(),
                    cardShape = { libraryCellShape(index, songs.items.size, columns, it) },
                )
            }
        }
        val gapPx = with(LocalDensity.current) { LIBRARY_ITEM_GAP.roundToPx() }
        val folderRowPx = with(LocalDensity.current) { FOLDER_CARD_HEIGHT.roundToPx() } + gapPx
        val decorPx = with(LocalDensity.current) { DECOR_HEIGHT.roundToPx() } + gapPx
        LibraryFastScroller(
            gridState = gridState,
            itemCount = songs.items.size,
            headerCount = songsHeaderIndex + 1,
            columns = columns,
            rowHeightPx = (if (isGrid) libraryGridRowHeightPx(true, columns) else rowHeightPx) + gapPx,
            headerHeightPx = decorPx * 2 + folderRowPx * (songsHeaderIndex - 1),
            hintFor = { i -> songs.items.getOrNull(i)?.let { songs.fastScrollHintFor(it, i) } ?: "-" },
            modifier = Modifier.padding(vertical = LIBRARY_GROUP_CORNER),
        )
        }
    }
}

