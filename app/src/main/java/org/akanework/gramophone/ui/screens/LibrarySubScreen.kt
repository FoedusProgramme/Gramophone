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
import org.akanework.gramophone.ui.components.home.SingleLineText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.res.pluralStringResource
import org.akanework.gramophone.ui.components.home.textViewStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.akanework.gramophone.ui.THEME_ANIMATION_MS
import org.akanework.gramophone.ui.components.player.rememberArtworkColorScheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.logic.utils.flows.provideReplayCacheInvalidationManager
import org.akanework.gramophone.ui.LibraryAdapterTypes
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.actions.findMainActivity
import org.akanework.gramophone.ui.components.compose.rememberDefaultPreferences
import org.akanework.gramophone.ui.components.home.GRID_CARD_SIDE_PADDING
import org.akanework.gramophone.ui.components.home.LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.GlassTitleBar
import org.akanework.gramophone.ui.components.home.LargeTitle
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.iosOverscroll
import org.akanework.gramophone.ui.components.home.largeTitleScroll
import org.akanework.gramophone.ui.components.home.rememberIosFlingBehavior
import org.akanework.gramophone.ui.components.home.rememberIosOverscrollState
import org.akanework.gramophone.ui.components.home.rememberLargeTitleState
import org.akanework.gramophone.ui.components.home.rememberNowPlayingState
import org.akanework.gramophone.ui.library.LayoutType
import org.akanework.gramophone.ui.library.Sorter
import org.akanework.gramophone.ui.nav.AlbumKey
import org.akanework.gramophone.ui.nav.ArtistKey
import org.akanework.gramophone.ui.nav.DateKey
import org.akanework.gramophone.ui.nav.GenreKey
import org.akanework.gramophone.ui.nav.LibrarySubKey
import org.akanework.gramophone.ui.nav.PlaylistEditKey
import org.akanework.gramophone.ui.nav.PlaylistKey
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.state.LibraryTabState
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.reader.FlowReader
import kotlin.math.roundToInt

/** The data behind a detail page: a title, its songs and, for artists, its albums. */
private class LibrarySubPage(
    val title: Flow<String>,
    val songs: LibraryTabState<MediaItem>,
    val albums: LibraryTabState<Album>? = null,
    /** Id of the playlist the edit button opens, when this is an editable playlist. */
    val editablePlaylistId: Long? = null,
) {
    companion object {
        fun create(
            key: LibrarySubKey,
            context: Context,
            reader: FlowReader,
            prefs: SharedPreferences,
            scope: CoroutineScope,
        ): LibrarySubPage {
            fun songs(spec: LibraryTabSpec<MediaItem>, flow: Flow<List<MediaItem>>) =
                LibraryTabState(spec, prefs, reader, scope, flowOverride = flow)
            return when (key) {
                is AlbumKey -> {
                    val item = reader.albumListFlow.map { l -> l.find { it.id == key.id } }
                    LibrarySubPage(
                        title = item.map { it?.title ?: context.getString(R.string.unknown_album) },
                        songs = songs(
                            LibraryTabSpec.SubSongs(LibraryAdapterTypes.ALBUM_SONGS, Sorter.Type.ByAlbumTitleAscending),
                            item.map { it?.songList ?: emptyList() },
                        ),
                    )
                }
                is GenreKey -> {
                    val item = reader.genreListFlow.map { l -> l.find { it.id == key.id } }
                    LibrarySubPage(
                        title = item.map { it?.title ?: context.getString(R.string.unknown_genre) },
                        songs = songs(
                            LibraryTabSpec.SubSongs(LibraryAdapterTypes.GENRE_SONGS),
                            item.map { it?.songList ?: emptyList() },
                        ),
                    )
                }
                is DateKey -> {
                    val item = reader.dateListFlow.map { l -> l.find { it.id == key.id } }
                    LibrarySubPage(
                        title = item.map { it?.title ?: context.getString(R.string.unknown_year) },
                        songs = songs(
                            LibraryTabSpec.SubSongs(LibraryAdapterTypes.DATE_SONGS),
                            item.map { it?.songList ?: emptyList() },
                        ),
                    )
                }
                is PlaylistKey -> {
                    val item = reader.playlistListFlow.map { l ->
                        l.find { if (key.id != null) it.id == key.id else it.javaClass.name == key.className }
                    }
                        .provideReplayCacheInvalidationManager()
                        .sharePauseableIn(
                            CoroutineScope(scope.coroutineContext + Dispatchers.Default),
                            SharingStarted.WhileSubscribed(), replay = 1
                        )
                    LibrarySubPage(
                        title = item.map {
                            when (it) {
                                is RecentlyAdded -> context.getString(R.string.recently_added)
                                is Favorite -> context.getString(R.string.playlist_favourite)
                                else -> it?.title ?: (context.getString(R.string.unknown_playlist) +
                                        if (it != null) " (${it.id} - ${it.path})" else "")
                            }
                        },
                        songs = songs(
                            LibraryTabSpec.SubSongs(LibraryAdapterTypes.PLAYLIST_DYNAMIC, Sorter.Type.NaturalOrder),
                            item.map { it?.songList ?: emptyList() },
                        ),
                        editablePlaylistId = key.id?.takeIf { key.className == Playlist::class.java.name },
                    )
                }
                is ArtistKey -> {
                    val item = (if (key.albumArtist) reader.albumArtistListFlow else reader.artistListFlow)
                        .map { l -> l.find { it.id == key.id } }
                    LibrarySubPage(
                        title = item.map { it?.title ?: context.getString(R.string.unknown_artist) },
                        songs = songs(
                            LibraryTabSpec.SubSongs(LibraryAdapterTypes.ARTIST_SONGS),
                            item.map { it?.songList ?: emptyList() },
                        ),
                        albums = LibraryTabState(
                            LibraryTabSpec.ArtistAlbums, prefs, reader, scope,
                            flowOverride = item.map { it?.albumList ?: emptyList() },
                        ),
                    )
                }
            }
        }
    }
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
private fun lcm(a: Int, b: Int): Int = a / gcd(a, b) * b

/**
 * Album, genre, date, playlist and artist pages. Shows a carousel of all entries of the same
 * kind, then the large title and the songs (for an artist, the album grid comes before the songs).
 */
@Composable
fun LibrarySubScreen(key: LibrarySubKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findMainActivity() }
    val prefs = rememberDefaultPreferences()
    val scope = rememberCoroutineScope()
    // Entries of this page's kind, shown in the carousel. Keyed on the initial key only, so
    // switching entries does not rebuild the list and reset the carousel position.
    val siblings = remember(key) { siblingEntries(key, activity.reader, prefs, scope) }
    siblings.Collect()
    // Index of the shown entry, or -1 until the list has loaded (then the initial key is shown).
    // Saved so the selected entry survives recreation.
    var currentIndex by rememberSaveable { mutableIntStateOf(-1) }
    val carouselState = remember(siblings) {
        CarouselState(currentIndex.coerceAtLeast(0)) { siblings.itemCount }
    }
    // The entry the carousel settles on becomes the shown entry. The first settle is not a
    // selection: it only scrolls the carousel to the restored or initial entry.
    var placed by remember(siblings) { mutableStateOf(false) }
    var placedAt by remember(siblings) { mutableIntStateOf(-1) }
    LaunchedEffect(siblings, carouselState) {
        snapshotFlow {
            Triple(
                carouselState.currentItem,
                carouselState.isScrollInProgress,
                siblings.itemCount,
            )
        }.collect { (settled, scrolling, count) ->
            if (scrolling || count == 0) return@collect
            if (!placed) {
                placed = true
                val target =
                    if (currentIndex >= 0) currentIndex else siblings.indexOf(key)
                placedAt = if (target >= 0) target else settled
                if (target >= 0) {
                    currentIndex = target
                    if (settled != target) carouselState.scrollToItem(target)
                }
                return@collect
            }
            // If the initial entry is not in the list (e.g. an album without a known id), keep it
            // until the carousel is scrolled away from the card it was placed on.
            if (settled != currentIndex &&
                (currentIndex >= 0 || settled != placedAt)) currentIndex = settled
        }
    }
    val currentKey = (if (currentIndex >= 0) siblings.keyAt(currentIndex) else null) ?: key
    // Subtitle under the title, e.g. an album's artist or the song count.
    val subtitle = siblings.subtitleAt(currentIndex)
    val page = remember(entryToken(currentKey)) {
        LibrarySubPage.create(currentKey, context, activity.reader, prefs, scope)
    }
    // Keeps the previous title until the new one loads, so the large title never becomes empty.
    val title = remember { mutableStateOf("") }
    LaunchedEffect(page) { page.title.collect { title.value = it } }
    LaunchedEffect(page, title.value) {
        page.songs.queueTitleOverride = title.value
        page.albums?.queueTitleOverride = title.value
    }
    // Album and artist pages use a color scheme seeded from the current cover. Other pages keep
    // the app theme.
    val tinted = key is AlbumKey || key is ArtistKey
    val cover = siblings.coverAt(
        if (currentIndex >= 0) currentIndex else siblings.indexOf(key)
    )
    val targetScheme = rememberArtworkColorScheme(if (tinted) cover else null)
    // The scheme is not animated because it is a static local, and animating it would recompose
    // the whole page every frame. Only the large background colors are animated, read in the
    // draw phase. The title already fades when the entry changes.
    val background = animateColorAsState(
        targetScheme.surfaceContainerLow, tween(THEME_ANIMATION_MS), label = "background",
    )
    val sheet = animateColorAsState(targetScheme.surface, tween(THEME_ANIMATION_MS), label = "sheet")
    // The mini player and the playing row are harmonized to the page's accent. The mini player
    // uses the accent of the top page, so it reverts when this page is popped.
    val accent = if (tinted) targetScheme.primary else null
    val pageAccents = activity.navViewModel.pageAccents
    SideEffect { if (accent != null) pageAccents[key] = accent else pageAccents.remove(key) }
    DisposableEffect(key) { onDispose { pageAccents.remove(key) } }
    val nowPlaying = rememberNowPlayingState(
        activity.controllerViewModel, LocalLifecycleOwner.current.lifecycle, accent,
    )
    CollectLibraryItems(page.songs)
    page.albums?.let { CollectLibraryItems(it) }
    val density = LocalDensity.current
    val topInset = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()
    val carouselHeight = subCarouselHeight()
    val titleState = rememberLargeTitleState()
    val overscroll = rememberIosOverscrollState()
    val songs = page.songs
    val albums = page.albums
    val songLayout = songs.layoutType
    val songCols = libraryColumns(songLayout)
    val albumLayout = albums?.layoutType
    val albumCols = if (albums != null) libraryColumns(albumLayout) else 1
    val albumIsGrid = albumLayout == LayoutType.GRID || albumLayout == LayoutType.COMPACT_GRID
    val cols = lcm(songCols, albumCols)
    val gridState = rememberLazyGridState()
    LaunchedEffect(page) { gridState.scrollToItem(0) }
    val rowHeightPx = with(density) {
        LIST_HEIGHT.roundToPx()
    }
    // The carousel starts just below the status bar, not below the toolbar.
    val contentTop = topInset + CAROUSEL_TOP_GAP
    val contentTopPx = with(density) { contentTop.toPx() }
    // The carousel is the item before the title, so the title only starts to scroll under the
    // toolbar after the carousel has.
    val carouselHeightPx = with(density) { carouselHeight.toPx() }
    val scrolled = {
        largeTitleScroll(
            gridState, overscroll, titleState, contentTopPx,
            titleIndex = 1, leadingPx = carouselHeightPx,
        )
    }
    // Scroll offset of the carousel from rest, overscroll included. Drives the carousel buttons
    // and the toolbar blur.
    val pageScroll = { (scrolled() + carouselHeightPx) }
    val frostSpanPx = with(density) { CAROUSEL_FROST_SPAN.toPx() }
    // Items before the songs: carousel, title, sheet top, and for an artist the albums.
    val firstSongIndex = 3 + (albums?.items?.size ?: 0)
    val sheetTopPx = with(density) { SHEET_TOP_HEIGHT.roundToPx() }

    val hazeState = remember { HazeState() }
    val barTopPadding = contentTop
    MaterialTheme(colorScheme = targetScheme) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(modifier.drawBehind { drawRect(background.value) }) {
                // The list sits behind the frosted bar as its blur source, padded clear of it at the top.
                // The background is painted inside the source so the recorded layer is opaque.
                Box(Modifier.fillMaxSize().hazeSource(hazeState).drawBehind { drawRect(background.value) }) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(cols),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .iosOverscroll(overscroll)
                            // After iosOverscroll, so the sheet is offset by the overscroll too.
                            .drawBehind { drawListSheet(gridState, sheet.value, SHEET_CORNER.toPx()) },
                        contentPadding = libraryContentPadding(top = barTopPadding),
                        flingBehavior = rememberIosFlingBehavior(gridState),
                        overscrollEffect = null,
                    ) {
                        item(key = "carousel", span = { GridItemSpan(maxLineSpan) }) {
                            siblings.Carousel(carouselState)
                        }
                        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
                            LargeTitle(
                                title.value, titleState, scrolled,
                                subtitle = subtitle, marquee = true,
                                style = textViewStyle(TITLE_SIZE, 400, MaterialTheme.colorScheme.onSurface)
                                    .copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                contentKey = entryToken(currentKey),
                                // Centred between the carousel cards and the sheet. The carousel's
                                // bottom padding is part of the top gap.
                                topGap = TITLE_GAP - CAROUSEL_ROW_PADDING,
                                bottomGap = TITLE_GAP,
                                trailing = {
                                    TitleButtons(
                                        onPlay = { LibraryActions.playAll(activity, songs.items, title.value) },
                                        onShuffle = { LibraryActions.shuffleAll(activity, songs.items, title.value) },
                                    )
                                },
                            )
                        }
                        // The sheet is drawn behind the grid. This spacer only makes room for
                        // its rounded top above the first row.
                        item(key = SHEET_TOP_KEY, span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(SHEET_TOP_HEIGHT))
                        }
                        if (albums != null) {
                            itemsIndexed(
                                albums.items,
                                key = { _, it -> "album:" + LibraryTabSpec.ArtistAlbums.helper.getId(it) },
                                span = { _, _ -> GridItemSpan(cols / albumCols) },
                            ) { index, item ->
                                // Outer grid gutter, matching the Albums tab's content padding.
                                val column = index % albumCols
                                Box(
                                    Modifier.animateItem().padding(
                                        start = if (albumIsGrid && column == 0) GRID_CARD_SIDE_PADDING else 0.dp,
                                        end = if (albumIsGrid && column == albumCols - 1) GRID_CARD_SIDE_PADDING else 0.dp,
                                    )
                                ) {
                                    LibraryItem(albums, item, nowPlaying, activity, albums.layoutType)
                                }
                            }
                        }
                        itemsIndexed(
                            songs.items,
                            key = { _, it -> "song:" + it.mediaId },
                            span = { _, _ -> GridItemSpan(cols / songCols) },
                        ) { index, item ->
                            LibraryItem(
                                songs, item, nowPlaying, activity, songLayout, Modifier.animateItem(),
                                number = index + 1,
                            )
                        }
                        if (songs.items.isNotEmpty()) {
                            item(key = "songs-footer", span = { GridItemSpan(maxLineSpan) }) {
                                SongsFooter(songs.items)
                            }
                        }
                    }
                    LibraryFastScroller(
                        gridState = gridState,
                        itemCount = songs.items.size,
                        headerCount = firstSongIndex,
                        columns = songCols,
                        rowHeightPx = if (songLayout == LayoutType.GRID || songLayout == LayoutType.COMPACT_GRID)
                            libraryGridRowHeightPx(true, songCols) else rowHeightPx,
                        headerHeightPx = carouselHeightPx.roundToInt() +
                                titleState.itemHeight.roundToInt() + sheetTopPx +
                                (if (albums != null) (albums.items.size + albumCols - 1) / albumCols *
                                        libraryGridRowHeightPx(albumIsGrid, albumCols) else 0),
                        hintFor = { i -> songs.items.getOrNull(i)?.let { songs.fastScrollHintFor(it, i) } ?: "-" },
                        modifier = Modifier.padding(top = barTopPadding),
                    )
                }
                GlassTitleBar(
                    hazeState = hazeState,
                    title = title.value,
                    scrolled = scrolled,
                    toolbarPaddingStart = TOOLBAR_BUTTON_PADDING_START,
                    toolbarPaddingEnd = TOOLBAR_BUTTON_PADDING_END,
                    // Leaves room for the back button docked in the toolbar.
                    titlePaddingStart = TOOLBAR_TITLE_PADDING,
                    // No blur at rest, since no content is under the bar yet.
                    frost = { (pageScroll() / frostSpanPx).coerceIn(0f, 1f) },
                )
                // The buttons move from the carousel card into the toolbar, so the bar itself has
                // no buttons.
                CarouselButtons(
                    scrolled = pageScroll,
                    topInset = topInset,
                    onBack = onBack,
                    onEdit = page.editablePlaylistId?.let { id ->
                        { activity.navigateTo(PlaylistEditKey(id)) }
                    },
                )
            }
        }
    }
}

/** Title size, smaller than the library's large title to fit next to the buttons. */
private val TITLE_SIZE = 28.sp

/** Gap between the title and the carousel above and the sheet below. */
private val TITLE_GAP = 24.dp

private const val SHEET_TOP_KEY = "sheet-top"
private val SHEET_TOP_HEIGHT = 16.dp
private val SHEET_CORNER = 28.dp

/**
 * Draws the sheet behind the lists, from the grid's sheet-top item to past the bottom of the
 * grid, so gaps, bottom padding and the overscroll area share one continuous surface.
 */
private fun DrawScope.drawListSheet(grid: LazyGridState, color: Color, corner: Float) {
    val info = grid.layoutInfo
    val sheetItem = info.visibleItemsInfo.firstOrNull { it.key == SHEET_TOP_KEY }
    val top = when {
        sheetItem != null -> (sheetItem.offset.y - info.viewportStartOffset).toFloat()
        // Scrolled past the sheet top: fill the grid with the corners off screen.
        info.visibleItemsInfo.isNotEmpty() && info.visibleItemsInfo.first().index > 2 -> -corner
        else -> return
    }
    drawPath(
        Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f, top = top, right = size.width, bottom = size.height * 2,
                    topLeftCornerRadius = CornerRadius(corner),
                    topRightCornerRadius = CornerRadius(corner),
                )
            )
        },
        color,
    )
}

/** Footer after the last song with the song count and total duration. */
@Composable
private fun SongsFooter(songs: List<MediaItem>) {
    val count = songs.size
    val total = remember(songs) { songs.sumOf { it.mediaMetadata.durationMs ?: 0L } }
    SingleLineText(
        stringResource(
            R.string.songs_total_duration,
            pluralStringResource(R.plurals.songs, count, count),
            convertDurationToTimeStamp(total),
        ),
        14.sp, 400, MaterialTheme.colorScheme.onSurfaceVariant,
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

private val TITLE_BUTTON_SIZE = 56.dp
private val TITLE_BUTTON_GAP = 12.dp
private val TITLE_BUTTON_ICON_SIZE = 26.dp

/** Play and shuffle buttons after the page title. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TitleButtons(onPlay: () -> Unit, onShuffle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.padding(start = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(TITLE_BUTTON_GAP),
    ) {
        TitleButton(
            icon = Icons.Outlined.PlayArrow,
            description = stringResource(R.string.play),
            shape = MaterialShapes.Cookie9Sided.toShape(),
            container = colors.primary,
            content = colors.onPrimary,
            onClick = onPlay,
        )
        TitleButton(
            icon = Icons.Outlined.Shuffle,
            description = stringResource(R.string.shuffle),
            shape = MaterialShapes.Cookie4Sided.toShape(),
            container = colors.tertiary,
            content = colors.onTertiary,
            onClick = onShuffle,
        )
    }
}

@Composable
private fun TitleButton(
    icon: ImageVector,
    description: String,
    shape: Shape,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    val fill by animateColorAsState(container, tween(THEME_ANIMATION_MS), label = "button")
    val tint by animateColorAsState(content, tween(THEME_ANIMATION_MS), label = "button icon")
    Box(
        Modifier
            .size(TITLE_BUTTON_SIZE)
            .clip(shape)
            .drawBehind { drawRect(fill) }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(TITLE_BUTTON_ICON_SIZE),
        )
    }
}
