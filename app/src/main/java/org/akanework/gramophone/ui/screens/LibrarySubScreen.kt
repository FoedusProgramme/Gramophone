/*
 *     Copyright (C) 2026 Akane Foundation
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

import android.content.Context
import android.content.SharedPreferences
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.logic.utils.flows.provideReplayCacheInvalidationManager
import org.akanework.gramophone.ui.LibraryAdapterTypes
import org.akanework.gramophone.ui.actions.AppActionEnv
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.actions.rememberAppActionEnv
import org.akanework.gramophone.ui.components.compose.IconTileButton
import org.akanework.gramophone.ui.components.compose.rememberDefaultPreferences
import org.akanework.gramophone.ui.components.home.DECOR_HEIGHT
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.GRID_CARD_SIDE_PADDING
import org.akanework.gramophone.ui.components.home.GlassTitleBar
import org.akanework.gramophone.ui.components.home.LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LargeTitle
import org.akanework.gramophone.ui.components.home.LargeTitleState
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.LibraryHeader
import org.akanework.gramophone.ui.components.home.ListRowLeading
import org.akanework.gramophone.ui.components.home.NowPlayingState
import org.akanework.gramophone.ui.components.home.SingleLineText
import org.akanework.gramophone.ui.components.home.SortMenuChooser
import org.akanework.gramophone.ui.components.home.largeTitleScroll
import org.akanework.gramophone.ui.components.home.rememberLargeTitleState
import org.akanework.gramophone.ui.components.home.rememberNowPlayingState
import org.akanework.gramophone.ui.components.home.textViewStyle
import org.akanework.gramophone.ui.components.player.LocalHarmonizeCovers
import org.akanework.gramophone.ui.components.player.rememberArtworkColorScheme
import org.akanework.gramophone.ui.library.Sorter
import org.akanework.gramophone.ui.library.isGrid
import org.akanework.gramophone.ui.nav.AlbumKey
import org.akanework.gramophone.ui.nav.ArtistKey
import org.akanework.gramophone.ui.nav.DateKey
import org.akanework.gramophone.ui.nav.GenreKey
import org.akanework.gramophone.ui.nav.LibrarySubKey
import org.akanework.gramophone.ui.nav.NavViewModel
import org.akanework.gramophone.ui.nav.PlaylistEditKey
import org.akanework.gramophone.ui.nav.PlaylistKey
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.state.LibraryTabState
import org.akanework.gramophone.ui.theme.LocalCardSurface
import org.akanework.gramophone.ui.theme.THEME_ANIMATION_MS
import org.akanework.gramophone.ui.theme.cardSurface
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.reader.FlowReader
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The grid starts with the carousel, the large title and the top of the sheet the lists sit on,
 * at these indices. The sheet then holds an artist's albums under their header and the songs
 * header, and the songs.
 */
private const val CAROUSEL_INDEX = 0
private const val TITLE_INDEX = CAROUSEL_INDEX + 1
private const val SHEET_TOP_INDEX = TITLE_INDEX + 1
private const val SHEET_TOP_KEY = "sheet-top"

/** Index of an artist page's albums header, the first item on the sheet. */
private const val ALBUMS_HEADER_INDEX = SHEET_TOP_INDEX + 1

/** The rounded top of the sheet, above its first row. */
private val SHEET_TOP_HEIGHT = 16.dp

/** Gap between the title and the carousel above and the sheet below. */
private val TITLE_GAP = 24.dp

/** The play and shuffle buttons after the title. */
private val TITLE_BUTTON_SIZE = 56.dp
private val TITLE_BUTTON_ICON_SIZE = 26.dp

/** The data behind a detail page: a title, its songs and, for artists, its albums. */
private class LibrarySubPage(
    val title: Flow<String>,
    val songs: LibraryTabState<MediaItem>,
    val albums: LibraryTabState<Album>? = null,
    /** Id of the playlist the edit button opens, when this is an editable playlist. */
    val editablePlaylistId: Long? = null,
    /**
     * Whether the page takes its colours from the shown entry's cover, as album and artist pages
     * do. Other pages keep the app theme.
     */
    val wantsCoverScheme: Boolean = false,
    /**
     * Whether song rows show their cover next to the number, for songs of different albums. Only
     * an album page leaves it off, and shows covers only when its songs span several albums.
     */
    val numberedCovers: Boolean = true,
    /** Whether song rows are numbered by their track number rather than their position. */
    val trackNumbers: Boolean = false,
) {
    /** Whether the songs and, on an artist page, the albums have loaded. */
    val loaded: Boolean get() = songs.loaded && albums?.loaded != false

    /** Index of the first song: after the sheet top and, on an artist page, the albums. */
    val firstSongIndex: Int
        get() = SHEET_TOP_INDEX + 1 + (if (albums != null) albums.items.size + 2 else 0)

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
                        wantsCoverScheme = true,
                        numberedCovers = false,
                        trackNumbers = true,
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
                        wantsCoverScheme = true,
                    )
                }
            }
        }
    }
}

/**
 * Whether [songs] come from more than one album, going by the album ids in their extras. Songs
 * without an id are skipped, so a list without ids counts as one album.
 */
private fun songsSpanAlbums(songs: List<MediaItem>): Boolean {
    var first: Long? = null
    for (song in songs) {
        val id = song.mediaMetadata.albumId ?: continue
        if (first == null) first = id
        else if (id != first) return true
    }
    return false
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
private fun lcm(a: Int, b: Int): Int = a / gcd(a, b) * b

/**
 * Where rememberLazyGridState put [gridState] back to (rotation, coming back to this page). Its
 * first layout clamps that to the few fixed items shown before the lists load, so the position is
 * put back once they have, unless the page moved to another entry meanwhile. Made right after
 * [gridState], before it has been laid out.
 */
@Stable
private class ScrollRestore(private val gridState: LazyGridState) {
    private val index = gridState.firstVisibleItemIndex
    private val offset = gridState.firstVisibleItemScrollOffset
    private var pending by mutableStateOf(index != 0 || offset != 0)

    /** Drops the position, for a page that moved to another entry. */
    fun cancel() {
        pending = false
    }

    /** Puts the position back once [page] has loaded, unless it was dropped. */
    suspend fun restoreWhenLoaded(page: LibrarySubPage) {
        if (!pending) return
        snapshotFlow { page.loaded }.first { it }
        pending = false
        gridState.scrollToItem(index, offset)
    }
}

/** The entry a detail page shows, and the carousel that picks it. */
private class ShownEntry(
    val key: LibrarySubKey,
    /** Its card, or -1 until the list has loaded or when it is not in the list. */
    val index: Int,
    val carouselState: CarouselState,
)

/**
 * The entry shown by the page opened by [key], saved so it survives recreation. Only settling the
 * carousel on another card of [siblings] changes it, which shows that entry from the top of
 * [gridState] and drops [restore]. When the list changes, the carousel follows the entry instead,
 * and an entry that is gone keeps its (then empty) page.
 */
@Composable
private fun rememberShownEntry(
    key: LibrarySubKey,
    siblings: SiblingEntries<*>,
    gridState: LazyGridState,
    restore: ScrollRestore,
): ShownEntry {
    var shownKey by rememberSaveable(stateSaver = LibrarySubKeySaver) { mutableStateOf(key) }
    val shownIndex = siblings.indexOf(shownKey)
    // Made again once the list has loaded, so it starts on the shown entry's card.
    val carouselState = remember(siblings, siblings.loaded) {
        CarouselState(shownIndex.coerceAtLeast(0)) { siblings.itemCount }
    }
    LaunchedEffect(carouselState, shownIndex) {
        if (shownIndex >= 0 && carouselState.currentItem != shownIndex) {
            carouselState.scrollToItem(shownIndex)
        }
    }
    // A scroll that settles on another card than it started from shows that card's entry, from
    // the top. The first value is the carousel at rest, not a settle.
    LaunchedEffect(siblings, carouselState) {
        var from = -1
        snapshotFlow { carouselState.isScrollInProgress }.collect { scrolling ->
            val at = carouselState.currentItem
            if (scrolling) {
                from = at
            } else if (from >= 0 && at != from) {
                val settled = siblings.keyAt(at) ?: return@collect
                if (!sameEntry(settled, shownKey)) {
                    shownKey = settled
                    restore.cancel()
                    gridState.scrollToItem(0)
                }
            }
        }
    }
    return ShownEntry(shownKey, shownIndex, carouselState)
}

/**
 * The title of [page], which also names the queues played from it. Keeps the previous title until
 * the new one loads, so the large title never becomes empty.
 */
@Composable
private fun rememberPageTitle(page: LibrarySubPage): State<String> {
    val title = remember { mutableStateOf("") }
    LaunchedEffect(page) { page.title.collect { title.value = it } }
    LaunchedEffect(page, title.value) {
        page.songs.queueTitleOverride = title.value
        page.albums?.queueTitleOverride = title.value
    }
    return title
}

/** The colours of a detail page, see [rememberCoverPageColors]. */
private class CoverPageColors(
    val scheme: ColorScheme,
    /** Whether [scheme] really came from the cover. Without a usable one it is the app's. */
    val usesCoverScheme: Boolean,
    /** The page's ground and the sheet's surface, animated. Read in the draw phase. */
    val background: State<Color>,
    val sheet: State<Color>,
    /** The surface of the page's cards, or null to keep the app's. */
    val cardSurface: Color?,
)

/**
 * The colours of the page opened by [key]: a scheme seeded from [cover] when the page
 * [wantsCoverScheme], else the app theme's. The page's scheme is handed to [navViewModel], so the
 * dialogs shown from the top page take it, and the mini player and the playing row stop leaning
 * towards the app's hue on it. The mini player follows the top page, so it reverts when this page
 * is popped.
 */
@Composable
private fun rememberCoverPageColors(
    key: LibrarySubKey,
    cover: Uri?,
    wantsCoverScheme: Boolean,
    navViewModel: NavViewModel,
): CoverPageColors {
    val scheme = rememberArtworkColorScheme(if (wantsCoverScheme) cover else null)
    // The scheme is not animated because it is a static local, and animating it would recompose
    // the whole page every frame. Only the large background colors are animated, read in the
    // draw phase. The title already fades when the entry changes.
    val background = animateColorAsState(
        scheme.surfaceContainerLow, tween(THEME_ANIMATION_MS), label = "background",
    )
    val sheet = animateColorAsState(scheme.surface, tween(THEME_ANIMATION_MS), label = "sheet")
    // Only a page that really took the cover's colours counts from here on: without a usable
    // cover it keeps the app theme, and everything is harmonized as usual.
    val usesCoverScheme = wantsCoverScheme && scheme !== MaterialTheme.colorScheme
    SideEffect { navViewModel.setPageScheme(key, if (usesCoverScheme) scheme else null) }
    DisposableEffect(key) { onDispose { navViewModel.setPageScheme(key, null) } }
    // The cards (the item sheet's actions, the artist's album cards) sit on the page's surfaces.
    val cardSurface = remember(scheme) {
        if (usesCoverScheme) cardSurface(scheme, scheme.surface.luminance() < 0.5f) else null
    }
    return CoverPageColors(scheme, usesCoverScheme, background, sheet, cardSurface)
}

/**
 * Album, genre, date, playlist and artist pages. Shows a carousel of all entries of the same
 * kind, then the large title and the songs (for an artist, the album grid comes before the songs,
 * each under a header with its count and jump button).
 */
@Composable
fun LibrarySubScreen(key: LibrarySubKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val env = rememberAppActionEnv()
    val reader = koinInject<FlowReader>()
    val prefs = rememberDefaultPreferences()
    val scope = rememberCoroutineScope()
    val navViewModel = koinActivityViewModel<NavViewModel>()
    // Entries of this page's kind, shown in the carousel. Keyed on the initial key only, so
    // switching entries does not rebuild the list and reset the carousel position.
    val siblings = remember(key) { siblingEntries(key, reader, prefs, scope) }
    siblings.Collect()
    val gridState = rememberLazyGridState()
    val restore = remember { ScrollRestore(gridState) }
    val shown = rememberShownEntry(key, siblings, gridState, restore)
    // So opening the entry it shows (from the player, say) doesn't stack the same page again.
    SideEffect { navViewModel.setShownDestination(key, shown.key) }
    val shownToken = entryToken(shown.key)
    val page = remember(shownToken) {
        LibrarySubPage.create(shown.key, context, reader, prefs, scope)
    }
    LaunchedEffect(page) { restore.restoreWhenLoaded(page) }
    CollectLibraryItems(page.songs)
    page.albums?.let { CollectLibraryItems(it) }
    val title = rememberPageTitle(page)
    // Subtitle under the title, e.g. an album's artist or the song count.
    val subtitle = siblings.subtitleAt(shown.index)
    val colors = rememberCoverPageColors(
        key, siblings.coverAt(shown.index), page.wantsCoverScheme, navViewModel,
    )
    val nowPlaying = rememberNowPlayingState(harmonize = !colors.usesCoverScheme)
    val density = LocalDensity.current
    val topInset = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .asPaddingValues().calculateTopPadding()
    // The carousel starts just below the status bar, not below the toolbar.
    val contentTop = topInset + CAROUSEL_TOP_GAP
    val contentTopPx = with(density) { contentTop.toPx() }
    val carouselHeightPx = with(density) { subCarouselHeight().toPx() }
    val titleState = rememberLargeTitleState()
    // The carousel is the item before the title, so the title only starts to scroll under the
    // toolbar after the carousel has.
    val scrolled = {
        largeTitleScroll(
            gridState, titleState, contentTopPx,
            titleIndex = TITLE_INDEX, leadingPx = carouselHeightPx,
        )
    }
    // Scroll offset of the carousel from rest. Drives the carousel buttons
    // and the toolbar blur.
    val pageScroll = { (scrolled() + carouselHeightPx) }
    // How far the page scrolls while the toolbar's blur fades in
    val frostSpanPx = with(density) { 24.dp.toPx() }
    val rowHeightPx = with(density) { LIST_HEIGHT.roundToPx() }
    // The content scrolls under the toolbar, so jumps land below it.
    val toolbarPx = with(density) { (GLASS_BAR_HEIGHT - CAROUSEL_TOP_GAP).roundToPx() }

    fun scrollTo(index: Int, belowToolbarPx: Int = 0) {
        scope.launch { gridState.animateScrollToItem(index, -toolbarPx - belowToolbarPx) }
    }

    val goToPlayingSong = {
        val id = nowPlaying.currentMediaId
        val songs = page.songs.items
        val index = if (id != null) songs.indexOfFirst { it.mediaId == id } else -1
        // Half a row below the toolbar, like the home's lists.
        if (index >= 0) scrollTo(page.firstSongIndex + index, rowHeightPx / 2)
    }

    val hazeState = remember { HazeState() }
    MaterialTheme(colorScheme = colors.scheme) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalCardSurface provides (colors.cardSurface ?: LocalCardSurface.current),
            LocalHarmonizeCovers provides !colors.usesCoverScheme,
        ) {
            Box(modifier.drawBehind { drawRect(colors.background.value) }) {
                LibrarySubList(
                    page = page,
                    gridState = gridState,
                    nowPlaying = nowPlaying,
                    env = env,
                    queueTitle = title.value,
                    contentTop = contentTop,
                    carouselHeightPx = carouselHeightPx,
                    titleState = titleState,
                    sheet = colors.sheet,
                    footerShowsCount = !libraryItemSubtitleIsCount(siblings.state),
                    onScrollTo = { scrollTo(it) },
                    onGoToPlayingSong = goToPlayingSong,
                    // The list sits behind the frosted bar as its blur source and scrolls under
                    // it. The background is painted inside the source so the recorded layer is
                    // opaque.
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .drawBehind { drawRect(colors.background.value) },
                    carousel = {
                        // Tapping the shown entry's card again goes to the playing song, like
                        // tapping the current tab on the home.
                        siblings.Carousel(shown.carouselState) {
                            if (shown.carouselState.currentItem == shown.index) goToPlayingSong()
                        }
                    },
                    title = {
                        SubPageTitle(
                            title = title.value,
                            // Reserve the subtitle line until the siblings load, so the list
                            // below doesn't jump down when it arrives a few frames later.
                            subtitle = subtitle ?: "",
                            state = titleState,
                            scrolled = scrolled,
                            contentKey = shownToken,
                            onPlay = {
                                LibraryActions.playAll(env, page.songs.items, title.value)
                            },
                            onShuffle = {
                                LibraryActions.shuffleAll(env, page.songs.items, title.value)
                            },
                        )
                    },
                )
                GlassTitleBar(
                    hazeState = hazeState,
                    title = title.value,
                    scrolled = scrolled,
                    toolbarPaddingStart = TOOLBAR_BUTTON_PADDING_START,
                    toolbarPaddingEnd = TOOLBAR_BUTTON_PADDING_END,
                    // Leaves room for the buttons docked in the toolbar.
                    titlePaddingStart = TOOLBAR_TITLE_PADDING,
                    // Sort, and edit for an editable playlist.
                    titlePaddingEnd = toolbarTitlePaddingEnd(
                        if (page.editablePlaylistId != null) 2 else 1
                    ),
                    // No blur at rest, since no content is under the bar yet.
                    frost = { (pageScroll() / frostSpanPx).coerceIn(0f, 1f) },
                )
                // The buttons move from the carousel card into the toolbar, so the bar itself has
                // no buttons.
                CarouselButtons(
                    scrolled = pageScroll,
                    topInset = topInset,
                    atLastEntry = siblings.itemCount > 1 &&
                            shown.carouselState.currentItem == siblings.itemCount - 1,
                    onBack = onBack,
                    onEdit = page.editablePlaylistId?.let { id ->
                        { navViewModel.navigateTo(PlaylistEditKey(id)) }
                    },
                    sortMenu = { expanded, onDismiss ->
                        val albums = page.albums
                        if (albums != null) ArtistSortMenu(albums, page.songs, expanded, onDismiss)
                        else LibrarySortMenu(page.songs, expanded, onDismiss)
                    },
                )
            }
        }
    }
}

/**
 * The scrolling content of a detail page: [carousel] and [title], then the sheet with an artist's
 * albums and the songs, and the fast scroller over them. It starts [contentTop] down, just below
 * the status bar. [onScrollTo] scrolls an item into view below the toolbar.
 */
@Composable
private fun LibrarySubList(
    page: LibrarySubPage,
    gridState: LazyGridState,
    nowPlaying: NowPlayingState,
    env: AppActionEnv,
    /** Title of the queues played from the headers. */
    queueTitle: String,
    contentTop: Dp,
    carouselHeightPx: Float,
    titleState: LargeTitleState,
    /** The sheet's colour, read when drawing. */
    sheet: State<Color>,
    /** Whether the footer shows the song count too, when the title's subtitle does not. */
    footerShowsCount: Boolean,
    onScrollTo: (index: Int) -> Unit,
    onGoToPlayingSong: () -> Unit,
    modifier: Modifier = Modifier,
    carousel: @Composable () -> Unit,
    title: @Composable () -> Unit,
) {
    val songs = page.songs
    val albums = page.albums
    val songLayout = songs.layoutType
    val songColumns = libraryColumns(songLayout)
    val albumColumns = if (albums != null) libraryColumns(albums.layoutType) else 1
    val albumIsGrid = albums?.layoutType.isGrid
    val columns = lcm(songColumns, albumColumns)
    // An album's rows only need covers when its songs come from different albums, e.g. an
    // album title shared by several albums. Read from the songs' extras, so no IO.
    val numberedCovers = page.numberedCovers ||
        remember(songs.items) { songsSpanAlbums(songs.items) }
    // A playlist may list a song more than once. The keys go with this very list.
    val songItems = songs.items
    val songKeys = remember(songItems) { uniqueKeys(songItems) { "song:" + it.mediaId } }
    val firstSongIndex = page.firstSongIndex
    val overscroll = rememberOverscrollEffect()
    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                // Drawn around the sheet too, so it stretches with the songs on it
                .overscroll(overscroll)
                .drawBehind { drawListSheet(gridState, sheet.value, 28.dp.toPx()) },
            contentPadding = libraryContentPadding(top = contentTop),
            overscrollEffect = overscroll?.withoutVisualEffect(),
        ) {
            // At CAROUSEL_INDEX, TITLE_INDEX and SHEET_TOP_INDEX.
            item(key = "carousel", span = { GridItemSpan(maxLineSpan) }) { carousel() }
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) { title() }
            // The sheet is drawn behind the grid. This spacer only makes room for its rounded
            // top above the first row.
            item(key = SHEET_TOP_KEY, span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(SHEET_TOP_HEIGHT))
            }
            if (albums != null) {
                item(key = "albums-header", span = { GridItemSpan(maxLineSpan) }) {
                    val count = albums.items.size
                    LibraryHeader(
                        counterText = pluralStringResource(R.plurals.albums, count, count),
                        onPlayAll = {
                            LibraryActions.playAllAlbums(env, albums.items, queueTitle)
                        },
                        onShuffleAll = {
                            LibraryActions.shuffleAllAlbums(env, albums.items, queueTitle)
                        },
                        onJumpDown = { onScrollTo(firstSongIndex - 1) },
                    )
                }
                itemsIndexed(
                    albums.items,
                    key = { _, it -> "album:" + LibraryTabSpec.ArtistAlbums.helper.getId(it) },
                    span = { _, _ -> GridItemSpan(columns / albumColumns) },
                ) { index, item ->
                    // Outer grid gutter, matching the Albums tab's content padding.
                    val column = index % albumColumns
                    val first = column == 0
                    val last = column == albumColumns - 1
                    Box(
                        Modifier.animateItem().padding(
                            start = if (albumIsGrid && first) GRID_CARD_SIDE_PADDING else 0.dp,
                            end = if (albumIsGrid && last) GRID_CARD_SIDE_PADDING else 0.dp,
                        )
                    ) {
                        LibraryItem(albums, item, index, nowPlaying, env, albums.layoutType)
                    }
                }
                item(key = "songs-header", span = { GridItemSpan(maxLineSpan) }) {
                    val count = songs.items.size
                    LibraryHeader(
                        counterText = pluralStringResource(R.plurals.songs, count, count),
                        onCounterClick = onGoToPlayingSong,
                        onJumpUp = { onScrollTo(ALBUMS_HEADER_INDEX) },
                    )
                }
            }
            itemsIndexed(
                songItems,
                key = { index, _ -> songKeys[index] },
                span = { _, _ -> GridItemSpan(columns / songColumns) },
            ) { index, item ->
                // An album's songs go by their track numbers, other lists by their position.
                val number = (if (page.trackNumbers) item.mediaMetadata.trackNumber
                    ?.takeIf { it > 0 } else null) ?: (index + 1)
                LibraryItem(
                    songs, item, index, nowPlaying, env, songLayout, Modifier.animateItem(),
                    leading = ListRowLeading.Number(number, withCover = numberedCovers),
                    // Each song's length.
                    trailing = item.mediaMetadata.durationMs
                        ?.let { convertDurationToTimeStamp(it) },
                    // The playing song's container keeps clear of the sheet's edges.
                    containerInset = 8.dp,
                )
            }
            if (songs.items.isNotEmpty()) {
                item(key = "songs-footer", span = { GridItemSpan(maxLineSpan) }) {
                    SongsFooter(songs.items, showCount = footerShowsCount)
                }
            }
        }
        LibraryFastScroller(
            gridState = gridState,
            itemCount = songs.items.size,
            headerCount = firstSongIndex,
            columns = songColumns,
            rowHeightPx = libraryRowHeightPx(songLayout, songColumns),
            headerHeightPx = subPageHeaderHeightPx(
                carouselHeightPx, titleState, albums, albumColumns,
            ),
            hintFor = { i ->
                songs.items.getOrNull(i)?.let { songs.fastScrollHintFor(it, i) } ?: "-"
            },
            modifier = Modifier.padding(top = contentTop),
        )
    }
}

/**
 * Estimated height of the items before the songs, which the fast scroller goes by until it has
 * seen them all: the carousel, the title, the sheet's top and, on an artist page, the headers and
 * the rows of albums.
 */
@Composable
private fun subPageHeaderHeightPx(
    carouselHeightPx: Float,
    titleState: LargeTitleState,
    albums: LibraryTabState<Album>?,
    albumColumns: Int,
): Int {
    val density = LocalDensity.current
    val sheetTopPx = with(density) { SHEET_TOP_HEIGHT.roundToPx() }
    val leadingPx = carouselHeightPx.roundToInt() + titleState.itemHeight.roundToInt() + sheetTopPx
    if (albums == null) return leadingPx
    val decorPx = with(density) { DECOR_HEIGHT.roundToPx() }
    val albumRows = (albums.items.size + albumColumns - 1) / albumColumns
    return leadingPx + decorPx * 2 +
            albumRows * libraryRowHeightPx(albums.layoutType, albumColumns)
}

/** The page's large title and subtitle, followed by its play and shuffle buttons. */
@Composable
private fun SubPageTitle(
    title: String,
    subtitle: String,
    state: LargeTitleState,
    scrolled: () -> Float,
    contentKey: Any,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    LargeTitle(
        title, state, scrolled,
        subtitle = subtitle, marquee = true,
        style = textViewStyle(
            // Smaller than the library's large title, to fit next to the buttons
            28.sp, 400, MaterialTheme.colorScheme.onSurface, includeFontPadding = false,
        ),
        contentKey = contentKey,
        // Centred between the carousel cards and the sheet. The carousel's bottom padding is part
        // of the top gap.
        topGap = TITLE_GAP - CAROUSEL_ROW_PADDING,
        bottomGap = TITLE_GAP,
        trailing = { TitleButtons(onPlay = onPlay, onShuffle = onShuffle) },
    )
}

/**
 * The sort menu of an artist page, which has two lists: it first lists them, then opens the
 * picked one's [LibrarySortMenu] beside its entry.
 */
@Composable
private fun ArtistSortMenu(
    albums: LibraryTabState<Album>,
    songs: LibraryTabState<MediaItem>,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    SortMenuChooser(
        expanded = expanded,
        onDismiss = onDismiss,
        titles = listOf(stringResource(R.string.category_albums), stringResource(R.string.category_songs)),
    ) { index, open, dismiss ->
        if (index == 0) LibrarySortMenu(albums, open, dismiss, submenu = true)
        else LibrarySortMenu(songs, open, dismiss, submenu = true)
    }
}

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
        info.visibleItemsInfo.isNotEmpty() &&
                info.visibleItemsInfo.first().index > SHEET_TOP_INDEX -> -corner
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

/**
 * Footer after the last song with the total duration, and the song count unless the title's
 * subtitle already shows it.
 */
@Composable
private fun SongsFooter(songs: List<MediaItem>, showCount: Boolean) {
    val count = songs.size
    val total = remember(songs) { songs.sumOf { it.mediaMetadata.durationMs ?: 0L } }
    val locale = LocalLocale.current
    val duration = remember(total, locale) { formatTotalDuration(total, locale.platformLocale) }
    SingleLineText(
        if (showCount) stringResource(
            R.string.songs_total_duration,
            pluralStringResource(R.plurals.songs, count, count),
            duration,
        ) else stringResource(R.string.songs_total_length, duration),
        14.sp, 400, MaterialTheme.colorScheme.onSurfaceVariant,
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    )
}

/**
 * A list's total length in words, as "1 hour, 12 minutes" or "38 minutes". Leftover seconds are
 * dropped, and only shown for lists under a minute. Falls back to "1:12:05" before API 24.
 */
private fun formatTotalDuration(ms: Long, locale: Locale): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return convertDurationToTimeStamp(ms)
    val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE)
    val seconds = ms / 1000
    if (seconds < 60) return format.format(Measure(seconds, MeasureUnit.SECOND))
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours == 0L -> format.format(Measure(minutes, MeasureUnit.MINUTE))
        minutes % 60 == 0L -> format.format(Measure(hours, MeasureUnit.HOUR))
        else -> format.formatMeasures(
            Measure(hours, MeasureUnit.HOUR), Measure(minutes % 60, MeasureUnit.MINUTE),
        )
    }
}

/** Play and shuffle buttons after the page title. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TitleButtons(onPlay: () -> Unit, onShuffle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Eased into the cover's colours with the rest of the page
    @Composable
    fun animated(color: Color) = animateColorAsState(color, tween(THEME_ANIMATION_MS), label = "button")
    val primary by animated(colors.primary)
    val onPrimary by animated(colors.onPrimary)
    val tertiary by animated(colors.tertiary)
    val onTertiary by animated(colors.onTertiary)
    Row(
        Modifier.padding(start = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTileButton(
            Icons.Outlined.PlayArrow, stringResource(R.string.play), { onPrimary }, onPlay,
            Modifier.size(TITLE_BUTTON_SIZE), iconSize = TITLE_BUTTON_ICON_SIZE,
            shape = MaterialShapes.Cookie9Sided.toShape(), container = { primary },
        )
        IconTileButton(
            Icons.Outlined.Shuffle, stringResource(R.string.shuffle), { onTertiary }, onShuffle,
            Modifier.size(TITLE_BUTTON_SIZE), iconSize = TITLE_BUTTON_ICON_SIZE,
            shape = MaterialShapes.Cookie4Sided.toShape(), container = { tertiary },
        )
    }
}
