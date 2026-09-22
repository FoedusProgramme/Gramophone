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

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.flows.LifecyclePauseManager
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.actions.PlaylistDialogs
import org.akanework.gramophone.ui.actions.findMainActivity
import org.akanework.gramophone.ui.adapters.BaseAdapter.LayoutType
import org.akanework.gramophone.ui.components.CustomGridLayoutManager
import org.akanework.gramophone.ui.components.compose.rememberPreference
import org.akanework.gramophone.ui.components.home.DECOR_HEIGHT
import org.akanework.gramophone.ui.components.home.GRID_CARD_LABEL_HEIGHT
import org.akanework.gramophone.ui.components.home.GRID_CARD_MARGIN_LABEL
import org.akanework.gramophone.ui.components.home.GRID_CARD_MARGIN_TOP
import org.akanework.gramophone.ui.components.home.GRID_CARD_PADDING_BOTTOM
import org.akanework.gramophone.ui.components.home.GRID_CARD_SIDE_PADDING
import org.akanework.gramophone.ui.components.home.LARGER_LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.LibraryGridCard
import org.akanework.gramophone.ui.components.home.LibraryHeader
import org.akanework.gramophone.ui.components.home.LibraryItemMenu
import org.akanework.gramophone.ui.components.home.LibraryListRow
import org.akanework.gramophone.ui.components.home.NowPlayingIndicator
import org.akanework.gramophone.ui.components.home.NowPlayingState
import org.akanework.gramophone.ui.components.home.SortMenu
import org.akanework.gramophone.ui.nav.LocalPlayerBottomPadding
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.state.LibraryTabState
import org.akanework.gramophone.ui.state.SortPrefState
import uk.akane.libphonograph.items.Album
import kotlin.math.max
import kotlin.math.roundToInt

/** Column count of a list / grid, from `BaseAdapter.getSpanSize` + `CustomGridLayoutManager`. */
@Composable
fun libraryColumns(layoutType: LayoutType?): Int {
    val config = LocalConfiguration.current
    val isList = layoutType != LayoutType.GRID && layoutType != LayoutType.COMPACT_GRID
    val lowWidth = config.orientation == Configuration.ORIENTATION_PORTRAIT ||
            config.screenWidthDp < 600
    val spanSize = when {
        isList && lowWidth -> CustomGridLayoutManager.LIST_PORTRAIT_SPAN_SIZE
        isList -> CustomGridLayoutManager.LIST_LANDSCAPE_SPAN_SIZE
        layoutType == LayoutType.GRID && lowWidth -> CustomGridLayoutManager.GRID_PORTRAIT_SPAN_SIZE
        layoutType == LayoutType.GRID -> CustomGridLayoutManager.GRID_LANDSCAPE_SPAN_SIZE
        layoutType == LayoutType.COMPACT_GRID && lowWidth -> CustomGridLayoutManager.COMPACT_GRID_PORTRAIT_SPAN_SIZE
        else -> CustomGridLayoutManager.COMPACT_GRID_LANDSCAPE_SPAN_SIZE
    }
    return CustomGridLayoutManager.FULL_SPAN_COUNT / spanSize
}

/**
 * Content padding of a list: horizontal system bar / cutout insets plus the grid gutter, and
 * at the bottom whichever is larger of the navigation bar and the mini player.
 */
@Composable
fun libraryContentPadding(isGrid: Boolean): PaddingValues {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val direction = LocalLayoutDirection.current
    val playerPadding = with(LocalDensity.current) { LocalPlayerBottomPadding.current.toDp() }
    val gutter = if (isGrid) GRID_CARD_SIDE_PADDING else 0.dp
    return PaddingValues(
        start = insets.calculateStartPadding(direction) + gutter,
        end = insets.calculateEndPadding(direction) + gutter,
        bottom = max(insets.calculateBottomPadding().value, playerPadding.value).dp,
    )
}

/**
 * Collects the sorted items into the state, pausing (like `repeatPausingWithLifecycle`) while
 * the lifecycle is below RESUMED, except during the first two seconds.
 */
@Composable
fun <T : Any> CollectLibraryItems(state: LibraryTabState<T>) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state, lifecycleOwner) {
        val bypass = flow {
            emit(true)
            delay(2000)
            emit(false)
        }
        withContext(
            LifecyclePauseManager(this, lifecycleOwner, Lifecycle.State.RESUMED, bypass)
        ) {
            state.sortedFlow.collect {
                state.items = it
                state.loaded = true
            }
        }
    }
}

/** Ends the splash / reportFullyDrawn once the first list frame is on screen. */
@Composable
fun ReportFullyDrawnWhen(loaded: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(loaded) {
        if (loaded) {
            withFrameNanos { }
            context.findMainActivity().maybeReportFullyDrawn()
        }
    }
}

/** One of the simple library tabs: header + list / grid of items. */
@Composable
fun <T : Any> LibraryTabScreen(
    state: LibraryTabState<T>,
    nowPlaying: NowPlayingState,
    reselectTick: Int,
    onCollapseAppBar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findMainActivity() }
    val scope = rememberCoroutineScope()
    CollectLibraryItems(state)
    ReportFullyDrawnWhen(state.loaded)
    val spec = state.spec
    val items = state.items
    val layoutType = state.layoutType
    val isGrid = layoutType == LayoutType.GRID || layoutType == LayoutType.COMPACT_GRID
    val columns = libraryColumns(layoutType)
    val rowHeightPx = with(LocalDensity.current) {
        (if (layoutType == LayoutType.LIST) LARGER_LIST_HEIGHT else LIST_HEIGHT).roundToPx()
    }
    val queueTitle = state.queueTitleOverride ?: context.getString(spec.queueTitle)
    val goToPlayingSong: (() -> Unit)? = if (spec === LibraryTabSpec.Songs) {
        {
            val id = nowPlaying.currentMediaId
            val index = if (id != null) items.indexOfFirst { (it as MediaItem).mediaId == id } else -1
            if (index >= 0) {
                onCollapseAppBar()
                scope.launch {
                    // QuickLinearSmoothScroller with SNAP_TO_START lands half a row below the top.
                    state.gridState.animateScrollToItem(index + 1, -rowHeightPx / 2)
                }
            }
        }
    } else null
    LaunchedEffect(reselectTick) { if (reselectTick > 0) goToPlayingSong?.invoke() }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val extraCheckbox = if (spec === LibraryTabSpec.Artists) {
        val albumArtist by rememberPreference(LibraryTabSpec.Artists.ALBUM_ARTIST_PREF) {
            it.getBoolean(LibraryTabSpec.Artists.ALBUM_ARTIST_PREF, false)
        }
        context.getString(R.string.album_artist) to albumArtist
    } else null

    val gridRowHeightPx = libraryGridRowHeightPx(isGrid, columns)
    val headerHeightPx = with(LocalDensity.current) { DECOR_HEIGHT.roundToPx() }
    Box(modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state.gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = libraryContentPadding(isGrid),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(
                counterText = context.resources.getQuantityString(spec.pluralStr, items.size, items.size),
                onCounterClick = goToPlayingSong,
                onCreatePlaylist = if (spec === LibraryTabSpec.Playlists) {
                    { PlaylistDialogs.create(activity) }
                } else null,
                onPlayAll = if (spec.hasPlayButtons) {
                    {
                        @Suppress("UNCHECKED_CAST")
                        if (spec === LibraryTabSpec.Albums)
                            LibraryActions.playAllAlbums(activity, items as List<Album>, queueTitle)
                        else
                            LibraryActions.playAll(activity, items as List<MediaItem>, queueTitle)
                    }
                } else null,
                onShuffleAll = if (spec.hasPlayButtons) {
                    {
                        @Suppress("UNCHECKED_CAST")
                        if (spec === LibraryTabSpec.Albums)
                            LibraryActions.shuffleAllAlbums(activity, items as List<Album>, queueTitle)
                        else
                            LibraryActions.shuffleAll(activity, items as List<MediaItem>, queueTitle)
                    }
                } else null,
                onSort = { sortMenuOpen = true },
                sortMenu = {
                    SortMenu(
                        expanded = sortMenuOpen,
                        onDismiss = { sortMenuOpen = false },
                        sortTypes = state.sortTypes,
                        activeSort = state.sort.activeSortBase(SortPrefState.SORT_MENU_ORDER),
                        isReversed = state.sort.isReversed,
                        canReverse = state.sort.canReverse,
                        onSelectSort = { state.sort.selectSort(it) },
                        onToggleReverse = { state.sort.toggleReverse() },
                        layoutType = layoutType,
                        onSelectLayout = { state.selectLayout(it) },
                        extraCheckbox = extraCheckbox,
                        onExtraCheckbox = {
                            val key = LibraryTabSpec.Artists.ALBUM_ARTIST_PREF
                            state.prefs.edit().putBoolean(key, !state.prefs.getBoolean(key, false)).apply()
                        },
                    )
                },
            )
        }
        items(items, key = { spec.helper.getId(it) }) { item ->
            LibraryItem(state, item, nowPlaying, activity, layoutType)
        }
    }
    LibraryFastScroller(
        gridState = state.gridState,
        itemCount = items.size,
        headerCount = 1,
        columns = columns,
        rowHeightPx = if (isGrid) gridRowHeightPx else rowHeightPx,
        headerHeightPx = headerHeightPx,
        hintFor = { i -> items.getOrNull(i)?.let { state.fastScrollHintFor(it, i) } ?: "-" },
    )
    }
}

/**
 * Height of one grid row, from `BaseAdapter.calculateGridSizeIfNeeded`: the cell width minus
 * the side paddings gives the square cover, plus the label block.
 */
@Composable
fun libraryGridRowHeightPx(isGrid: Boolean, columns: Int): Int {
    if (!isGrid) return 0
    val density = LocalDensity.current
    val padding = libraryContentPadding(true)
    val direction = LocalLayoutDirection.current
    val config = LocalConfiguration.current
    return with(density) {
        val width = config.screenWidthDp.dp.toPx() -
                padding.calculateStartPadding(direction).toPx() -
                padding.calculateEndPadding(direction).toPx()
        val cover = width / columns - GRID_CARD_SIDE_PADDING.toPx() * 2
        (cover + GRID_CARD_MARGIN_TOP.toPx() + GRID_CARD_LABEL_HEIGHT.toPx() +
                GRID_CARD_MARGIN_LABEL.toPx() * 2 + GRID_CARD_PADDING_BOTTOM.toPx()).roundToInt()
    }
}

@Composable
internal fun <T : Any> LibraryItem(
    state: LibraryTabState<T>,
    item: T,
    nowPlaying: NowPlayingState,
    activity: org.akanework.gramophone.ui.MainActivity,
    layoutType: LayoutType,
) {
    val context = LocalContext.current
    val spec = state.spec
    val helper = spec.helper
    val title = state.titleOf(item) ?: spec.virtualTitleOf(context, item)
    val subtitle = if (helper.canGetArtist())
        helper.getArtist(item) ?: context.getString(R.string.unknown_artist)
    else if (helper.canGetSize()) {
        val s = helper.getSize(item)
        context.resources.getQuantityString(R.plurals.songs, s, s)
    } else "null"
    val cover = spec.coverOf(context, item)
    val actions = spec.menuActions(item)
    var menuOpen by remember { mutableStateOf(false) }
    val menu: @Composable () -> Unit = {
        LibraryItemMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            actions = actions,
            onAction = { spec.onMenuAction(activity, item, it) },
        )
    }
    val nowPlayingSlot: (@Composable () -> Unit)? = if (item is MediaItem) {
        {
            NowPlayingIndicator(
                isCurrent = item.mediaId == nowPlaying.currentMediaId,
                isPlaying = nowPlaying.isPlaying,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else null
    val onClick = { spec.onClick(activity, state, item, state.items.indexOf(item)) }
    if (layoutType == LayoutType.GRID || layoutType == LayoutType.COMPACT_GRID) {
        val trackCount = if (helper.canGetSize()) {
            if (helper.canGetArtist()) {
                val s = helper.getSize(item)
                context.resources.getQuantityString(R.plurals.songs, s, s)
            } else if (helper.canGetAlbumSize()) {
                val s = helper.getAlbumSize(item)
                context.resources.getQuantityString(R.plurals.albums, s, s)
            } else ""
        } else if (helper.canGetAlbumTitle()) {
            helper.getAlbumTitle(item) ?: "null"
        } else "null"
        LibraryGridCard(
            title = title,
            subtitle = subtitle,
            trackCount = trackCount,
            cover = cover,
            defaultCover = spec.defaultCover,
            hasMenu = actions.isNotEmpty(),
            onClick = onClick,
            onMenu = { menuOpen = true },
            nowPlaying = nowPlayingSlot,
            menu = menu,
        )
    } else {
        LibraryListRow(
            layout = layoutType,
            title = title,
            subtitle = subtitle,
            cover = cover,
            defaultCover = spec.defaultCover,
            hasMenu = actions.isNotEmpty(),
            onClick = onClick,
            onMenu = { menuOpen = true },
            nowPlaying = nowPlayingSlot,
            menu = menu,
        )
    }
}

