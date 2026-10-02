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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.actions.PlaylistDialogs
import org.akanework.gramophone.ui.actions.rememberAppActionEnv
import org.akanework.gramophone.ui.components.home.DECOR_HEIGHT
import org.akanework.gramophone.ui.components.home.IosOverscrollState
import org.akanework.gramophone.ui.components.home.LIBRARY_GROUP_CORNER
import org.akanework.gramophone.ui.components.home.LIBRARY_ITEM_GAP
import org.akanework.gramophone.ui.components.home.LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.LibraryHeader
import org.akanework.gramophone.ui.components.home.NowPlayingState
import org.akanework.gramophone.ui.components.home.iosOverscroll
import org.akanework.gramophone.ui.components.home.libraryCellShape
import org.akanework.gramophone.ui.components.home.libraryItemCard
import org.akanework.gramophone.ui.components.home.libraryItemShape
import org.akanework.gramophone.ui.components.home.rememberIosFlingBehavior
import org.akanework.gramophone.ui.library.isGrid
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.state.LibraryTabState
import uk.akane.libphonograph.items.Album

/**
 * One of the simple library tabs: the header with the item count, the play, shuffle and sort
 * buttons, then the list / grid of items, scrolling with iOS physics.
 */
@Composable
fun <T : Any> LibraryTabScreen(
    state: LibraryTabState<T>,
    nowPlaying: NowPlayingState,
    reselectTick: Int,
    overscroll: IosOverscrollState,
    modifier: Modifier = Modifier,
) {
    val env = rememberAppActionEnv()
    val scope = rememberCoroutineScope()
    // Owned by the composition, not the view model: a LazyGridState holds on to its layout
    // node and through it the activity, which a view model would keep across recreation.
    val gridState = rememberLazyGridState()
    CollectLibraryItems(state)
    ReportFullyDrawnWhen(state.loaded)
    val spec = state.spec
    val items = state.items
    val layoutType = state.layoutType
    val isGrid = layoutType.isGrid
    val columns = libraryColumns(layoutType)
    val density = LocalDensity.current
    val rowHeightPx = with(density) {
        LIST_HEIGHT.roundToPx()
    }
    val queueTitle = state.queueTitleOverride ?: stringResource(spec.queueTitle)
    val goToPlayingSong: (() -> Unit)? = if (spec === LibraryTabSpec.Songs) {
        {
            val id = nowPlaying.currentMediaId
            val index = if (id != null) items.indexOfFirst { (it as MediaItem).mediaId == id } else -1
            if (index >= 0) {
                scope.launch {
                    // Land half a row below the top, the way the View list used to.
                    gridState.animateScrollToItem(index + HEADER_ITEMS, -rowHeightPx / 2)
                }
            }
        }
    } else null
    LaunchedEffect(reselectTick) { if (reselectTick > 0) goToPlayingSong?.invoke() }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val gapPx = with(density) { LIBRARY_ITEM_GAP.roundToPx() }
    Box(modifier.fillMaxSize()) {
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
        // The header opens the group of cards, so the items under it don't.
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            val count = items.size
            @Suppress("UNCHECKED_CAST")
            LibraryHeader(
                modifier = Modifier.libraryItemCard(
                    libraryItemShape(
                        topStart = true, topEnd = true,
                        bottomStart = items.isEmpty(), bottomEnd = items.isEmpty(),
                    )
                ),
                counterText = pluralStringResource(spec.pluralStr, count, count),
                onCounterClick = goToPlayingSong,
                onCreatePlaylist = if (spec === LibraryTabSpec.Playlists) {
                    { PlaylistDialogs.create(env) }
                } else null,
                onPlayAll = if (spec.hasPlayButtons) {
                    {
                        if (spec === LibraryTabSpec.Albums)
                            LibraryActions.playAllAlbums(env, items as List<Album>, queueTitle)
                        else
                            LibraryActions.playAll(env, items as List<MediaItem>, queueTitle)
                    }
                } else null,
                onShuffleAll = if (spec.hasPlayButtons) {
                    {
                        if (spec === LibraryTabSpec.Albums)
                            LibraryActions.shuffleAllAlbums(env, items as List<Album>, queueTitle)
                        else
                            LibraryActions.shuffleAll(env, items as List<MediaItem>, queueTitle)
                    }
                } else null,
                onSort = { sortMenuOpen = true },
                sortMenu = { LibrarySortMenu(state, sortMenuOpen, onDismiss = { sortMenuOpen = false }) },
            )
        }
        itemsIndexed(items, key = { _, it -> spec.helper.getId(it) }) { index, item ->
            LibraryItem(
                state, item, nowPlaying, env, layoutType,
                Modifier.animateItem(),
                cardShape = { libraryCellShape(index, items.size, columns, it) },
            )
        }
    }
    LibraryFastScroller(
        gridState = gridState,
        itemCount = items.size,
        headerCount = HEADER_ITEMS,
        columns = columns,
        rowHeightPx = libraryRowHeightPx(layoutType, columns) + gapPx,
        headerHeightPx = with(density) { DECOR_HEIGHT.roundToPx() } + gapPx,
        hintFor = { i -> items.getOrNull(i)?.let { state.fastScrollHintFor(it, i) } ?: "-" },
        modifier = Modifier.padding(vertical = LIBRARY_GROUP_CORNER),
    )
    }
}

/** Items before the first library item: the header. */
private const val HEADER_ITEMS = 1
