/*
 *     Copyright (C) 2026 The Gramophone authors
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.flows.LifecyclePauseManager
import org.akanework.gramophone.ui.actions.AppActionEnv
import org.akanework.gramophone.ui.components.compose.rememberPreference
import org.akanework.gramophone.ui.components.home.GRID_CARD_LABEL_HEIGHT
import org.akanework.gramophone.ui.components.home.GRID_CARD_MARGIN_LABEL
import org.akanework.gramophone.ui.components.home.GRID_CARD_MARGIN_TOP
import org.akanework.gramophone.ui.components.home.GRID_CARD_PADDING_BOTTOM
import org.akanework.gramophone.ui.components.home.GRID_CARD_SIDE_PADDING
import org.akanework.gramophone.ui.components.home.LIBRARY_ITEM_GAP
import org.akanework.gramophone.ui.components.home.LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryGridCard
import org.akanework.gramophone.ui.components.home.LibraryItemSheet
import org.akanework.gramophone.ui.components.home.LibraryListRow
import org.akanework.gramophone.ui.components.home.ListRowLeading
import org.akanework.gramophone.ui.components.home.NowPlayingState
import org.akanework.gramophone.ui.components.home.SortMenu
import org.akanework.gramophone.ui.components.home.libraryItemCard
import org.akanework.gramophone.ui.components.home.libraryItemShape
import org.akanework.gramophone.ui.components.home.nowPlayingRowColors
import org.akanework.gramophone.ui.library.LayoutType
import org.akanework.gramophone.ui.library.isGrid
import org.akanework.gramophone.ui.nav.LocalAppBarTopPadding
import org.akanework.gramophone.ui.nav.LocalListBottomPadding
import org.akanework.gramophone.ui.nav.LocalListSideInsets
import org.akanework.gramophone.ui.nav.LocalPlayerBottomPadding
import org.akanework.gramophone.ui.nav.LocalReportFullyDrawn
import org.akanework.gramophone.ui.state.FolderTabState
import org.akanework.gramophone.ui.state.LibraryMenuAction
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.state.LibraryTabState
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * What the library's lists share: the home tabs, the folder tabs, the detail pages and the
 * search. Their columns and padding, collecting their items, their sort menu and their items.
 */

/* The grid is twelve spans wide, and each layout takes a share of them per item. */
private const val FULL_SPAN_COUNT = 12
private const val LIST_PORTRAIT_SPAN_SIZE = 12
private const val LIST_LANDSCAPE_SPAN_SIZE = 6
private const val GRID_PORTRAIT_SPAN_SIZE = 6
private const val GRID_LANDSCAPE_SPAN_SIZE = 3
private const val COMPACT_GRID_PORTRAIT_SPAN_SIZE = 4
private const val COMPACT_GRID_LANDSCAPE_SPAN_SIZE = 2

/** How long after a list is first shown it keeps collecting even below RESUMED. */
private const val PAUSE_BYPASS_MS = 2000L

/** Column count of a list / grid. */
@Composable
fun libraryColumns(layoutType: LayoutType?): Int {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val windowWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isList = !layoutType.isGrid
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
 * Content padding of a list: horizontal system bar / cutout insets (see [LocalListSideInsets]),
 * and at the bottom [libraryBottomPadding].
 */
@Composable
fun libraryContentPadding(
    // The list scrolls under the frosted top bar, so it keeps the bar's height clear at the top.
    top: Dp = LocalAppBarTopPadding.current,
): PaddingValues {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val direction = LocalLayoutDirection.current
    val sides = LocalListSideInsets.current
    return PaddingValues(
        top = top,
        start = if (sides) insets.calculateStartPadding(direction) else 0.dp,
        end = if (sides) insets.calculateEndPadding(direction) else 0.dp,
        bottom = libraryBottomPadding(),
    )
}

/**
 * Bottom padding of a list: [LocalListBottomPadding] if set, else whichever is larger of the
 * navigation bar and the mini player.
 */
@Composable
fun libraryBottomPadding(): Dp {
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val playerPadding = with(LocalDensity.current) { LocalPlayerBottomPadding.current.toDp() }
    return LocalListBottomPadding.current
        ?: max(insets.calculateBottomPadding().value, playerPadding.value).dp
}

/**
 * Keys for the items of a lazy list, from their [id]s. A lazy list throws on a key used twice,
 * and a list may hold the same entry more than once (a playlist can list a song twice), so each
 * repeat gets its copy number added. The first copy keeps the plain id, so a resort still
 * animates it as the same item.
 */
internal fun <T> uniqueKeys(items: List<T>, id: (T) -> String): List<String> {
    val copies = HashMap<String, Int>()
    return items.map {
        val key = id(it)
        val copy = (copies[key] ?: 0) + 1
        copies[key] = copy
        if (copy == 1) key else "$key#$copy"
    }
}

/**
 * Height of one row of a list in [layoutType] with [columns]. For a grid, the cell width minus
 * the side paddings gives the square cover, plus the label block. For a list, a row's height.
 */
@Composable
fun libraryRowHeightPx(layoutType: LayoutType?, columns: Int): Int {
    val density = LocalDensity.current
    if (!layoutType.isGrid) return with(density) { LIST_HEIGHT.roundToPx() }
    val padding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
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
 * Runs [block] while this is composed, pausing (like `repeatPausingWithLifecycle`) the flows it
 * collects while the lifecycle is below RESUMED, except during the first two seconds.
 */
@Composable
private fun PausingCollectEffect(key: Any, block: suspend CoroutineScope.() -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key, lifecycleOwner) {
        val bypass = flow {
            emit(true)
            delay(PAUSE_BYPASS_MS)
            emit(false)
        }
        withContext(
            LifecyclePauseManager(this, lifecycleOwner, Lifecycle.State.RESUMED, bypass),
            block,
        )
    }
}

/** Collects the sorted items into the state, pausing like [PausingCollectEffect]. */
@Composable
fun <T : Any> CollectLibraryItems(state: LibraryTabState<T>) {
    PausingCollectEffect(state) {
        state.sortedFlow.collect {
            state.items = it
            state.loaded = true
        }
    }
}

/** Collects the folder tab's current folder into the state, pausing like [CollectLibraryItems]. */
@Composable
fun CollectFolderPage(state: FolderTabState) {
    PausingCollectEffect(state) { state.pageFlow.collect(state::show) }
}

/** Ends the splash / reportFullyDrawn once the first list frame is on screen. */
@Composable
fun ReportFullyDrawnWhen(loaded: Boolean) {
    val reportFullyDrawn = LocalReportFullyDrawn.current
    LaunchedEffect(loaded) {
        if (loaded) {
            withFrameNanos { }
            reportFullyDrawn()
        }
    }
}

/**
 * The sort and layout menu of a list: a home tab's, opened from the home bar's sort button, a
 * folder tab's songs', opened from their header, or a detail page's, opened from its carousel's
 * sort button (on an artist page, one for each list).
 */
@Composable
fun <T : Any> LibrarySortMenu(
    state: LibraryTabState<T>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    submenu: Boolean = false,
) {
    val extraCheckbox = if (state.spec === LibraryTabSpec.Artists) {
        val albumArtist by rememberPreference(LibraryTabSpec.Artists.ALBUM_ARTIST_PREF) {
            it.getBoolean(LibraryTabSpec.Artists.ALBUM_ARTIST_PREF, false)
        }
        stringResource(R.string.album_artist) to albumArtist
    } else null
    SortMenu(
        sort = state.sort,
        expanded = expanded,
        onDismiss = onDismiss,
        layoutType = state.layoutType,
        onSelectLayout = { state.selectLayout(it) },
        extraCheckbox = extraCheckbox,
        onExtraCheckbox = {
            val key = LibraryTabSpec.Artists.ALBUM_ARTIST_PREF
            state.prefs.edit { putBoolean(key, !state.prefs.getBoolean(key, false)) }
        },
        submenu = submenu,
    )
}

/** Whether [libraryItemSubtitle] shows the song count for items of [state]'s kind. */
internal fun libraryItemSubtitleIsCount(state: LibraryTabState<*>): Boolean =
    !state.spec.helper.canGetArtist() && state.spec.helper.canGetSize()

/**
 * Subtitle of a library item: its artist if the kind has one, otherwise its song count. Used by
 * the tabs, their sheets and the detail page carousel.
 */
@Composable
internal fun <T : Any> libraryItemSubtitle(state: LibraryTabState<T>, item: T): String {
    val helper = state.spec.helper
    return if (helper.canGetArtist())
        helper.getArtist(item) ?: stringResource(R.string.unknown_artist)
    else if (libraryItemSubtitleIsCount(state)) {
        val s = helper.getSize(item)
        pluralStringResource(R.plurals.songs, s, s)
    } else "null"
}

/**
 * One item of a library list, as a grid card or a list row by [layoutType], with its sheet of
 * actions. [leading], [trailing] and [containerInset] only apply to a list row.
 */
@Composable
internal fun <T : Any> LibraryItem(
    state: LibraryTabState<T>,
    item: T,
    /** The item's index in the list shown, which tells copies of one song apart. */
    index: Int,
    nowPlaying: NowPlayingState,
    env: AppActionEnv,
    layoutType: LayoutType,
    modifier: Modifier = Modifier,
    cardShape: ((emphasis: Float) -> Shape)? = null,
    leading: ListRowLeading = ListRowLeading.Cover,
    /** Shown before the menu button, such as a song's length. */
    trailing: String? = null,
    /** Horizontal inset of the playing song's container on a list row without a [cardShape]. */
    containerInset: Dp = 0.dp,
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
    // The item's place in the state's list, which the actions play from. A row still showing a
    // list that was replaced since (a folder sliding out, a search being typed) looks the item up
    // in the new one, and is -1 when it is gone there.
    val position = {
        if (state.items.getOrNull(index) == item) index else state.items.indexOf(item)
    }
    val menu: @Composable () -> Unit = {
        // The sheet's play button is its header, not one of the listed actions.
        val onMenuAction = { action: LibraryMenuAction ->
            spec.onMenuAction(env, state, item, position(), action)
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
    val onClick = { spec.onClick(env, state, item, position()) }
    if (layoutType.isGrid) {
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
            leading = leading,
            containerInset = containerInset,
            trailing = trailing,
        )
    }
}
