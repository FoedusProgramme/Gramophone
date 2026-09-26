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

import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.flows.LifecyclePauseManager
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.actions.PlaylistDialogs
import org.akanework.gramophone.ui.actions.findMainActivity
import org.akanework.gramophone.ui.components.compose.rememberPreference
import org.akanework.gramophone.ui.components.home.FastScrollerState
import org.akanework.gramophone.ui.components.home.GRID_CARD_LABEL_HEIGHT
import org.akanework.gramophone.ui.components.home.GRID_CARD_MARGIN_LABEL
import org.akanework.gramophone.ui.components.home.GRID_CARD_MARGIN_TOP
import org.akanework.gramophone.ui.components.home.GRID_CARD_PADDING_BOTTOM
import org.akanework.gramophone.ui.components.home.GRID_CARD_SIDE_PADDING
import org.akanework.gramophone.ui.components.home.IosOverscrollState
import org.akanework.gramophone.ui.components.home.LIBRARY_GROUP_CORNER
import org.akanework.gramophone.ui.components.home.LIBRARY_ITEM_GAP
import org.akanework.gramophone.ui.components.home.LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryFabAction
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.LibraryGridCard
import org.akanework.gramophone.ui.components.home.LibraryItemSheet
import org.akanework.gramophone.ui.components.home.LibraryListRow
import org.akanework.gramophone.ui.components.home.NowPlayingState
import org.akanework.gramophone.ui.components.home.SortMenu
import org.akanework.gramophone.ui.components.home.iosOverscroll
import org.akanework.gramophone.ui.components.home.libraryCellShape
import org.akanework.gramophone.ui.components.home.libraryItemCard
import org.akanework.gramophone.ui.components.home.libraryItemShape
import org.akanework.gramophone.ui.components.home.nowPlayingRowColors
import org.akanework.gramophone.ui.components.home.rememberIosFlingBehavior
import org.akanework.gramophone.ui.library.LayoutType
import org.akanework.gramophone.ui.nav.LocalAppBarTopPadding
import org.akanework.gramophone.ui.nav.LocalListBottomPadding
import org.akanework.gramophone.ui.nav.LocalPlayerBottomPadding
import org.akanework.gramophone.ui.state.LibraryMenuAction
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.state.LibraryTabState
import org.akanework.gramophone.ui.state.SortPrefState
import uk.akane.libphonograph.items.Album
import kotlin.math.max
import kotlin.math.roundToInt

/* The grid is twelve spans wide, and each layout takes a share of them per item. */
private const val FULL_SPAN_COUNT = 12
private const val LIST_PORTRAIT_SPAN_SIZE = 12
private const val LIST_LANDSCAPE_SPAN_SIZE = 6
private const val GRID_PORTRAIT_SPAN_SIZE = 6
private const val GRID_LANDSCAPE_SPAN_SIZE = 3
private const val COMPACT_GRID_PORTRAIT_SPAN_SIZE = 4
private const val COMPACT_GRID_LANDSCAPE_SPAN_SIZE = 2

/** Column count of a list / grid. */
@Composable
fun libraryColumns(layoutType: LayoutType?): Int {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val windowWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isList = layoutType != LayoutType.GRID && layoutType != LayoutType.COMPACT_GRID
    val lowWidth = config.orientation == Configuration.ORIENTATION_PORTRAIT ||
            windowWidthDp < 600.dp
    val spanSize = when {
        isList && lowWidth -> LIST_PORTRAIT_SPAN_SIZE
        isList -> LIST_LANDSCAPE_SPAN_SIZE
        layoutType == LayoutType.GRID && lowWidth -> GRID_PORTRAIT_SPAN_SIZE
        layoutType == LayoutType.GRID -> GRID_LANDSCAPE_SPAN_SIZE
        layoutType == LayoutType.COMPACT_GRID && lowWidth -> COMPACT_GRID_PORTRAIT_SPAN_SIZE
        else -> COMPACT_GRID_LANDSCAPE_SPAN_SIZE
    }
    return FULL_SPAN_COUNT / spanSize
}

/**
 * Content padding of a list: horizontal system bar / cutout insets, and at the bottom whichever
 * is larger of the navigation bar and the mini player.
 */
@Composable
fun libraryContentPadding(
    // The list scrolls under the frosted top bar, so it keeps the bar's height clear at the top.
    top: Dp = LocalAppBarTopPadding.current,
): PaddingValues {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val direction = LocalLayoutDirection.current
    val playerPadding = with(LocalDensity.current) { LocalPlayerBottomPadding.current.toDp() }
    return PaddingValues(
        top = top,
        start = insets.calculateStartPadding(direction),
        end = insets.calculateEndPadding(direction),
        bottom = LocalListBottomPadding.current
            ?: max(insets.calculateBottomPadding().value, playerPadding.value).dp,
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

/**
 * One of the simple library tabs: the header, then the list / grid of items, scrolling with
 * iOS physics.
 */
@Composable
fun <T : Any> LibraryTabScreen(
    state: LibraryTabState<T>,
    nowPlaying: NowPlayingState,
    reselectTick: Int,
    overscroll: IosOverscrollState,
    modifier: Modifier = Modifier,
    /** Updated when the list reaches its end, see [FastScrollerState]. */
    fastScroller: FastScrollerState? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findMainActivity() }
    val scope = rememberCoroutineScope()
    // Owned by the composition, not the view model: a LazyGridState holds on to its layout
    // node and through it the activity, which a view model would keep across recreation.
    val gridState = rememberLazyGridState()
    CollectLibraryItems(state)
    ReportFullyDrawnWhen(state.loaded)
    val spec = state.spec
    val items = state.items
    val layoutType = state.layoutType
    val isGrid = layoutType == LayoutType.GRID || layoutType == LayoutType.COMPACT_GRID
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
                    gridState.animateScrollToItem(index, -rowHeightPx / 2)
                }
            }
        }
    } else null
    LaunchedEffect(reselectTick) { if (reselectTick > 0) goToPlayingSong?.invoke() }
    val gridRowHeightPx = libraryGridRowHeightPx(isGrid, columns)
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
        itemsIndexed(items, key = { _, it -> spec.helper.getId(it) }) { index, item ->
            LibraryItem(
                state, item, nowPlaying, activity, layoutType,
                Modifier.animateItem(),
                cardShape = { libraryCellShape(index, items.size, columns, it, opensGroup = true) },
            )
        }
    }
    LibraryFastScroller(
        gridState = gridState,
        itemCount = items.size,
        headerCount = 0,
        columns = columns,
        rowHeightPx = (if (isGrid) gridRowHeightPx else rowHeightPx) + gapPx,
        headerHeightPx = 0,
        hintFor = { i -> items.getOrNull(i)?.let { state.fastScrollHintFor(it, i) } ?: "-" },
        modifier = Modifier.padding(vertical = LIBRARY_GROUP_CORNER),
        state = fastScroller,
    )
    }
}

/**
 * The home's FABs for a tab: a new playlist, or play and shuffle all. Empty when it has none.
 */
fun <T : Any> libraryFabActions(state: LibraryTabState<T>, activity: MainActivity): List<LibraryFabAction> {
    val spec = state.spec
    val queueTitle = state.queueTitleOverride ?: activity.getString(spec.queueTitle)
    return buildList {
        if (spec === LibraryTabSpec.Playlists) {
            add(LibraryFabAction(Icons.Outlined.Add) { PlaylistDialogs.create(activity) })
        }
        if (spec.hasPlayButtons) {
            @Suppress("UNCHECKED_CAST")
            add(LibraryFabAction(Icons.Outlined.PlayArrow, iconOffsetX = 2.dp) {
                if (spec === LibraryTabSpec.Albums)
                    LibraryActions.playAllAlbums(activity, state.items as List<Album>, queueTitle)
                else
                    LibraryActions.playAll(activity, state.items as List<MediaItem>, queueTitle)
            })
            @Suppress("UNCHECKED_CAST")
            add(LibraryFabAction(Icons.Outlined.Shuffle, 22.dp, iconOffsetX = (-4).dp) {
                if (spec === LibraryTabSpec.Albums)
                    LibraryActions.shuffleAllAlbums(activity, state.items as List<Album>, queueTitle)
                else
                    LibraryActions.shuffleAll(activity, state.items as List<MediaItem>, queueTitle)
            })
        }
    }
}

/** The sort and layout menu of a home tab, opened from the home bar's sort button. */
@Composable
fun <T : Any> LibrarySortMenu(state: LibraryTabState<T>, expanded: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val extraCheckbox = if (state.spec === LibraryTabSpec.Artists) {
        val albumArtist by rememberPreference(LibraryTabSpec.Artists.ALBUM_ARTIST_PREF) {
            it.getBoolean(LibraryTabSpec.Artists.ALBUM_ARTIST_PREF, false)
        }
        stringResource(R.string.album_artist) to albumArtist
    } else null
    SortMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        sortTypes = state.sortTypes,
        activeSort = state.sort.activeSortBase(SortPrefState.SORT_MENU_ORDER),
        isReversed = state.sort.isReversed,
        canReverse = state.sort.canReverse,
        onSelectSort = { state.sort.selectSort(it) },
        onToggleReverse = { state.sort.toggleReverse() },
        layoutType = state.layoutType,
        onSelectLayout = { state.selectLayout(it) },
        extraCheckbox = extraCheckbox,
        onExtraCheckbox = {
            val key = LibraryTabSpec.Artists.ALBUM_ARTIST_PREF
            state.prefs.edit { putBoolean(key, !state.prefs.getBoolean(key, false)) }
        },
    )
}

/**
 * Height of one grid row: the cell width minus the side paddings gives the square cover, plus
 * the label block.
 */
@Composable
fun libraryGridRowHeightPx(isGrid: Boolean, columns: Int): Int {
    if (!isGrid) return 0
    val density = LocalDensity.current
    val padding = libraryContentPadding()
    val direction = LocalLayoutDirection.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    return with(density) {
        val width = windowWidthPx -
                padding.calculateStartPadding(direction).toPx() -
                padding.calculateEndPadding(direction).toPx() -
                LIBRARY_ITEM_GAP.toPx() * (columns - 1)
        val cover = width / columns - GRID_CARD_SIDE_PADDING.toPx() * 2
        (cover + GRID_CARD_MARGIN_TOP.toPx() + GRID_CARD_LABEL_HEIGHT.toPx() +
                GRID_CARD_MARGIN_LABEL.toPx() * 2 + GRID_CARD_PADDING_BOTTOM.toPx()).roundToInt()
    }
}

/**
 * Subtitle of a library item: its artist if the kind has one, otherwise its song count. Used by
 * the tabs, their sheets and the detail page carousel.
 */
@Composable
internal fun <T : Any> libraryItemSubtitle(state: LibraryTabState<T>, item: T): String {
    val helper = state.spec.helper
    return if (helper.canGetArtist())
        helper.getArtist(item) ?: stringResource(R.string.unknown_artist)
    else if (helper.canGetSize()) {
        val s = helper.getSize(item)
        pluralStringResource(R.plurals.songs, s, s)
    } else "null"
}

@Composable
internal fun <T : Any> LibraryItem(
    state: LibraryTabState<T>,
    item: T,
    nowPlaying: NowPlayingState,
    activity: org.akanework.gramophone.ui.MainActivity,
    layoutType: LayoutType,
    modifier: Modifier = Modifier,
    cardShape: ((emphasis: Float) -> Shape)? = null,
    /** Shown in place of the cover on a list row. */
    number: Int? = null,
) {
    val context = LocalContext.current
    val spec = state.spec
    val helper = spec.helper
    val title = state.titleOf(item) ?: spec.virtualTitleOf(context, item)
    val subtitle = libraryItemSubtitle(state, item)
    val cover = spec.coverOf(context, item)
    val defaultCover = spec.defaultCoverOf(item)
    val actions = spec.menuActions(item)
    var menuOpen by remember { mutableStateOf(false) }
    val menu: @Composable () -> Unit = {
        // The sheet's play button is its header, not one of the listed actions.
        val onMenuAction = { action: LibraryMenuAction ->
            spec.onMenuAction(activity, state, item, state.items.indexOf(item), action)
        }
        LibraryItemSheet(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            title = title,
            subtitle = subtitle,
            category = stringResource(spec.tab.label),
            cover = cover,
            defaultCover = defaultCover,
            actions = actions,
            onPlay = { onMenuAction(LibraryMenuAction.Play) },
            onAction = onMenuAction,
        )
    }
    val colors = nowPlayingRowColors(
        isCurrent = item is MediaItem && item.mediaId == nowPlaying.currentMediaId,
        colors = nowPlaying.colors,
        containerShape = if (cardShape == null) libraryItemShape(emphasis = 1f) else RectangleShape,
    )
    // The playing song's card takes its own corners, see libraryItemShape.
    val rowModifier = if (cardShape == null) modifier
        else modifier.libraryItemCard(cardShape(colors.emphasis))
    val onClick = { spec.onClick(activity, state, item, state.items.indexOf(item)) }
    if (layoutType == LayoutType.GRID || layoutType == LayoutType.COMPACT_GRID) {
        val trackCount = if (helper.canGetSize()) {
            if (helper.canGetArtist()) {
                val s = helper.getSize(item)
                pluralStringResource(R.plurals.songs, s, s)
            } else if (helper.canGetAlbumSize()) {
                val s = helper.getAlbumSize(item)
                pluralStringResource(R.plurals.albums, s, s)
            } else ""
        } else if (helper.canGetAlbumTitle()) {
            helper.getAlbumTitle(item) ?: "null"
        } else "null"
        LibraryGridCard(
            title = title,
            subtitle = subtitle,
            trackCount = trackCount,
            cover = cover,
            defaultCover = defaultCover,
            hasMenu = actions.isNotEmpty(),
            onClick = onClick,
            onMenu = { menuOpen = true },
            modifier = rowModifier,
            colors = colors,
            menu = menu,
        )
    } else {
        LibraryListRow(
            title = title,
            subtitle = subtitle,
            cover = cover,
            defaultCover = defaultCover,
            hasMenu = actions.isNotEmpty(),
            onClick = onClick,
            onMenu = { menuOpen = true },
            modifier = rowModifier,
            colors = colors,
            menu = menu,
            number = number,
            // Numbered lists are album or artist song lists, which show each song's duration.
            trailing = if (number != null && item is MediaItem)
                item.mediaMetadata.durationMs?.let { convertDurationToTimeStamp(it) } else null,
        )
    }
}

