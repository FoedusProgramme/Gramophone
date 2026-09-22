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

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.logic.utils.flows.provideReplayCacheInvalidationManager
import org.akanework.gramophone.ui.LibraryAdapterTypes
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.actions.findMainActivity
import org.akanework.gramophone.ui.adapters.BaseAdapter.LayoutType
import org.akanework.gramophone.ui.adapters.Sorter
import org.akanework.gramophone.ui.components.compose.rememberDefaultPreferences
import org.akanework.gramophone.ui.components.home.APP_BAR_LARGE_EXPANDED_HEIGHT
import org.akanework.gramophone.ui.components.home.CollapsingTitleBar
import org.akanework.gramophone.ui.components.home.DECOR_HEIGHT
import org.akanework.gramophone.ui.components.home.GRID_CARD_SIDE_PADDING
import org.akanework.gramophone.ui.components.home.LARGER_LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.LibraryHeader
import org.akanework.gramophone.ui.components.home.LibraryIconButton
import org.akanework.gramophone.ui.components.home.SortMenu
import org.akanework.gramophone.ui.components.home.rememberHomeAppBarScrollBehavior
import org.akanework.gramophone.ui.components.home.rememberNowPlayingState
import org.akanework.gramophone.ui.fragments.PlaylistEditFragment
import org.akanework.gramophone.ui.nav.AlbumKey
import org.akanework.gramophone.ui.nav.ArtistKey
import org.akanework.gramophone.ui.nav.DateKey
import org.akanework.gramophone.ui.nav.GenreKey
import org.akanework.gramophone.ui.nav.LibrarySubKey
import org.akanework.gramophone.ui.nav.PlaylistKey
import org.akanework.gramophone.ui.state.LibraryTabSpec
import org.akanework.gramophone.ui.state.LibraryTabState
import org.akanework.gramophone.ui.state.SortPrefState
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.reader.FlowReader

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
 * Album, genre, date, playlist and artist pages: a large collapsing title bar over the songs
 * (and, for an artist, the album grid above them, with jump buttons between the two).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySubScreen(key: LibrarySubKey, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findMainActivity() }
    val prefs = rememberDefaultPreferences()
    val scope = rememberCoroutineScope()
    val page = remember(key) { LibrarySubPage.create(key, context, activity.reader, prefs, scope) }
    val title by page.title.collectAsState("")
    LaunchedEffect(title) {
        page.songs.queueTitleOverride = title
        page.albums?.queueTitleOverride = title
    }
    val nowPlaying = rememberNowPlayingState(
        activity.controllerViewModel, LocalLifecycleOwner.current.lifecycle
    )
    CollectLibraryItems(page.songs)
    page.albums?.let { CollectLibraryItems(it) }
    val scrollBehavior = rememberHomeAppBarScrollBehavior()
    val songs = page.songs
    val albums = page.albums
    val songLayout = songs.layoutType
    val songCols = libraryColumns(songLayout)
    val albumLayout = albums?.layoutType
    val albumCols = if (albums != null) libraryColumns(albumLayout) else 1
    val albumIsGrid = albumLayout == LayoutType.GRID || albumLayout == LayoutType.COMPACT_GRID
    val cols = lcm(songCols, albumCols)
    val gridState = songs.gridState
    val rowHeightPx = with(LocalDensity.current) {
        (if (songLayout == LayoutType.LIST) LARGER_LIST_HEIGHT else LIST_HEIGHT).roundToPx()
    }
    val decorPx = with(LocalDensity.current) { DECOR_HEIGHT.roundToPx() }
    var songSortOpen by remember { mutableStateOf(false) }
    var albumSortOpen by remember { mutableStateOf(false) }
    val songsHeaderIndex = if (albums != null) 1 + albums.items.size else 0

    fun scrollTo(index: Int) {
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
        scope.launch { gridState.animateScrollToItem(index, -rowHeightPx / 2) }
    }

    val goToPlayingSong = {
        val id = nowPlaying.currentMediaId
        val index = if (id != null) songs.items.indexOfFirst { it.mediaId == id } else -1
        if (index >= 0) scrollTo(songsHeaderIndex + 1 + index)
    }

    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        CollapsingTitleBar(
            scrollBehavior = scrollBehavior,
            title = title,
            expandedHeight = APP_BAR_LARGE_EXPANDED_HEIGHT,
            titleMaxLines = 2,
            toolbarPaddingStart = 4.dp,
            collapsedTitleStart = 56.dp,
            navigationIcon = {
                LibraryIconButton(
                    icon = R.drawable.ic_arrow_back,
                    iconSize = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
            },
            actions = {
                if (page.editablePlaylistId != null) {
                    LibraryIconButton(
                        icon = R.drawable.ic_edit,
                        iconSize = 24.dp,
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            activity.startFragment(PlaylistEditFragment()) {
                                putString("Id", page.editablePlaylistId.toString())
                            }
                        },
                    )
                }
            },
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = libraryContentPadding(isGrid = false),
            ) {
                if (albums != null) {
                    item(key = "albums-header", span = { GridItemSpan(maxLineSpan) }) {
                        val count = albums.items.size
                        LibraryHeader(
                            counterText = context.resources.getQuantityString(R.plurals.albums, count, count),
                            onPlayAll = { LibraryActions.playAllAlbums(activity, albums.items, title) },
                            onShuffleAll = { LibraryActions.shuffleAllAlbums(activity, albums.items, title) },
                            onSort = { albumSortOpen = true },
                            onJumpDown = { scrollTo(songsHeaderIndex) },
                            sortMenu = {
                                SortMenu(
                                    expanded = albumSortOpen,
                                    onDismiss = { albumSortOpen = false },
                                    sortTypes = albums.sortTypes,
                                    activeSort = albums.sort.activeSortBase(SortPrefState.SORT_MENU_ORDER),
                                    isReversed = albums.sort.isReversed,
                                    canReverse = albums.sort.canReverse,
                                    onSelectSort = { albums.sort.selectSort(it) },
                                    onToggleReverse = { albums.sort.toggleReverse() },
                                    layoutType = albumLayout,
                                    onSelectLayout = { albums.selectLayout(it) },
                                )
                            },
                        )
                    }
                    itemsIndexed(
                        albums.items,
                        key = { _, it -> "album:" + LibraryTabSpec.ArtistAlbums.helper.getId(it) },
                        span = { _, _ -> GridItemSpan(cols / albumCols) },
                    ) { index, item ->
                        // The outer grid gutter the Albums tab gets from its content padding.
                        val column = index % albumCols
                        Box(
                            Modifier.padding(
                                start = if (albumIsGrid && column == 0) GRID_CARD_SIDE_PADDING else 0.dp,
                                end = if (albumIsGrid && column == albumCols - 1) GRID_CARD_SIDE_PADDING else 0.dp,
                            )
                        ) {
                            LibraryItem(albums, item, nowPlaying, activity, albums.layoutType)
                        }
                    }
                }
                item(key = "songs-header", span = { GridItemSpan(maxLineSpan) }) {
                    val count = songs.items.size
                    LibraryHeader(
                        counterText = context.resources.getQuantityString(R.plurals.songs, count, count),
                        onCounterClick = goToPlayingSong,
                        onPlayAll = { LibraryActions.playAll(activity, songs.items, title) },
                        onShuffleAll = { LibraryActions.shuffleAll(activity, songs.items, title) },
                        onSort = { songSortOpen = true },
                        onJumpUp = if (albums != null) { { scrollTo(0) } } else null,
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
                                layoutType = songLayout,
                                onSelectLayout = { songs.selectLayout(it) },
                            )
                        },
                    )
                }
                itemsIndexed(
                    songs.items,
                    key = { _, it -> "song:" + it.mediaId },
                    span = { _, _ -> GridItemSpan(cols / songCols) },
                ) { _, item ->
                    LibraryItem(songs, item, nowPlaying, activity, songLayout)
                }
            }
            LibraryFastScroller(
                gridState = gridState,
                itemCount = songs.items.size,
                headerCount = songsHeaderIndex + 1,
                columns = songCols,
                rowHeightPx = if (songLayout == LayoutType.GRID || songLayout == LayoutType.COMPACT_GRID)
                    libraryGridRowHeightPx(true, songCols) else rowHeightPx,
                headerHeightPx = decorPx * (if (albums != null) 2 else 1) +
                        (if (albums != null) (albums.items.size + albumCols - 1) / albumCols *
                                libraryGridRowHeightPx(albumIsGrid, albumCols) else 0),
                hintFor = { i -> songs.items.getOrNull(i)?.let { songs.fastScrollHintFor(it, i) } ?: "-" },
            )
        }
    }
}
