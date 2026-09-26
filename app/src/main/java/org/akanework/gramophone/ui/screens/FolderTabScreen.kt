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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.actions.rememberAppActionEnv
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
import org.akanework.gramophone.ui.state.FolderPage
import org.akanework.gramophone.ui.state.FolderTabState
import org.akanework.gramophone.ui.state.SortPrefState

/** Folder page transition: the slide, and the outgoing page's fade-out before the incoming fade-in. */
private const val FOLDER_SLIDE_MS = 200
private const val FOLDER_FADE_OUT_MS = 70
private const val FOLDER_FADE_IN_MS = 130

/** The Folders / Filesystem tab, laid out like [LibraryTabScreen]: folders first, then songs. */
@Composable
fun FolderTabScreen(
    state: FolderTabState,
    nowPlaying: NowPlayingState,
    reselectTick: Int,
    overscroll: IosOverscrollState,
    modifier: Modifier = Modifier,
) {
    val env = rememberAppActionEnv()
    val scope = rememberCoroutineScope()
    val songs = state.songs
    ReportFullyDrawnWhen(songs.loaded)
    LaunchedEffect(state) {
        state.pageFlow.collect {
            state.page = it
            songs.items = it.songs
            songs.loaded = true
            songs.queueTitleOverride = it.path.lastOrNull() ?: "/"
        }
    }
    val page = state.page
    val layoutType = songs.layoutType
    val isGrid = layoutType == LayoutType.GRID || layoutType == LayoutType.COMPACT_GRID
    val columns = libraryColumns(layoutType)
    val density = LocalDensity.current
    val rowHeightPx = with(density) {
        LIST_HEIGHT.roundToPx()
    }
    // One scroll state per folder, so the outgoing and incoming pages never share a grid state.
    // The current folder's ancestors keep theirs, so going up returns to where the parent was.
    val gridStates = remember(state) { HashMap<List<String>, LazyGridState>() }
    fun gridStateFor(path: List<String>) = gridStates.getOrPut(path) { LazyGridState() }
    LaunchedEffect(page?.path) {
        val path = page?.path ?: return@LaunchedEffect
        gridStates.keys.removeAll { it.size > path.size || it != path.subList(0, it.size) }
    }
    var folderSortOpen by remember { mutableStateOf(false) }
    var songSortOpen by remember { mutableStateOf(false) }

    // The folders header, the parent folder row if any, the folders, then the songs header.
    fun songsHeaderIndex(page: FolderPage) =
        1 + (if (page.path.isNotEmpty()) 1 else 0) + page.folders.size

    fun scrollTo(gridState: LazyGridState, index: Int) {
        scope.launch { gridState.animateScrollToItem(index, -rowHeightPx / 2) }
    }

    fun goToPlayingSong(page: FolderPage) {
        val id = nowPlaying.currentMediaId
        val index = if (id != null) page.songs.indexOfFirst { it.mediaId == id } else -1
        if (index >= 0) scrollTo(gridStateFor(page.path), songsHeaderIndex(page) + 1 + index)
    }
    LaunchedEffect(reselectTick) { if (reselectTick > 0) state.page?.let { goToPlayingSong(it) } }

    AnimatedContent(
        targetState = page,
        modifier = modifier.fillMaxSize(),
        // A new page only for another folder. A resort or rescan of the same folder updates it
        contentKey = { it?.path },
        transitionSpec = {
            val from = initialState?.path
            val to = targetState?.path
            if (from == null || to == null) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                // Shared axis: a quarter-width slide, the outgoing page fading out before the
                // incoming one fades in, so the two never show on top of each other.
                val up = to.size < from.size
                (slideInHorizontally(tween(FOLDER_SLIDE_MS, easing = FastOutSlowInEasing)) {
                    if (up) -it / 4 else it / 4
                } + fadeIn(tween(FOLDER_FADE_IN_MS, delayMillis = FOLDER_FADE_OUT_MS)))
                    .togetherWith(
                        slideOutHorizontally(tween(FOLDER_SLIDE_MS, easing = FastOutSlowInEasing)) {
                            if (up) it / 4 else -it / 4
                        } + fadeOut(tween(FOLDER_FADE_OUT_MS))
                    )
            }
        },
        label = "folder",
    ) { animatedPage ->
        if (animatedPage == null) return@AnimatedContent
        // Everything below comes from this page's own folder, so the outgoing page keeps showing
        // its folder (and parent row) while the incoming one shows the new folder
        val gridState = remember { gridStateFor(animatedPage.path) }
        val showParent = animatedPage.path.isNotEmpty()
        val folders = animatedPage.folders
        val items = animatedPage.songs
        val songsHeaderIndex = songsHeaderIndex(animatedPage)
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
                val count = folders.size
                LibraryHeader(
                    modifier = Modifier.libraryItemCard(libraryItemShape(topStart = true, topEnd = true)),
                    counterText = pluralStringResource(R.plurals.folders_plural, count, count),
                    onSort = { folderSortOpen = true },
                    onJumpDown = { scrollTo(gridState, songsHeaderIndex) },
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
            items(folders, key = { "folder:" + it.folderName }, span = { GridItemSpan(maxLineSpan) }) { node ->
                val n = node.folderList.size + node.songList.size
                LibraryFolderRow(
                    title = node.folderName,
                    subtitle = pluralStringResource(R.plurals.items, n, n),
                    onClick = { state.enter(node.folderName) },
                    modifier = Modifier.animateItem().libraryItemCard(),
                )
            }
            item(key = "songs-header", span = { GridItemSpan(maxLineSpan) }) {
                val count = items.size
                val queueTitle = animatedPage.path.lastOrNull() ?: "/"
                LibraryHeader(
                    modifier = Modifier.libraryItemCard(
                        libraryItemShape(bottomStart = items.isEmpty(), bottomEnd = items.isEmpty())
                    ),
                    counterText = pluralStringResource(R.plurals.songs, count, count),
                    onCounterClick = { goToPlayingSong(animatedPage) },
                    onPlayAll = { LibraryActions.playAll(env, items, queueTitle) },
                    onShuffleAll = { LibraryActions.shuffleAll(env, items, queueTitle) },
                    onSort = { songSortOpen = true },
                    onJumpUp = { scrollTo(gridState, 0) },
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
            itemsIndexed(items, key = { _, it -> "song:" + it.mediaId }) { index, item: MediaItem ->
                LibraryItem(
                    songs, item, nowPlaying, env, layoutType,
                    Modifier.animateItem(),
                    cardShape = { libraryCellShape(index, items.size, columns, it) },
                )
            }
        }
        val gapPx = with(LocalDensity.current) { LIBRARY_ITEM_GAP.roundToPx() }
        val folderRowPx = with(LocalDensity.current) { FOLDER_CARD_HEIGHT.roundToPx() } + gapPx
        val decorPx = with(LocalDensity.current) { DECOR_HEIGHT.roundToPx() } + gapPx
        LibraryFastScroller(
            gridState = gridState,
            itemCount = items.size,
            headerCount = songsHeaderIndex + 1,
            columns = columns,
            rowHeightPx = (if (isGrid) libraryGridRowHeightPx(true, columns) else rowHeightPx) + gapPx,
            headerHeightPx = decorPx * 2 + folderRowPx * (songsHeaderIndex - 1),
            hintFor = { i -> items.getOrNull(i)?.let { songs.fastScrollHintFor(it, i) } ?: "-" },
            modifier = Modifier.padding(vertical = LIBRARY_GROUP_CORNER),
        )
        }
    }
}

