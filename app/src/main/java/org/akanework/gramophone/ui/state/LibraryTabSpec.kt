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

package org.akanework.gramophone.ui.state

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.media3.common.MediaItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.ui.HomeTab
import org.akanework.gramophone.ui.LibraryAdapterTypes
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.library.LayoutType
import org.akanework.gramophone.ui.library.MediaItemHelper
import org.akanework.gramophone.ui.library.Sorter
import org.akanework.gramophone.ui.library.StorePlaylistHelper
import org.akanework.gramophone.ui.library.StoreAlbumHelper
import org.akanework.gramophone.ui.library.StoreArtistHelper
import org.akanework.gramophone.ui.library.StoreDateHelper
import org.akanework.gramophone.ui.library.StoreGenreHelper
import org.akanework.gramophone.ui.components.compose.booleanFlow
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.Genre
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.reader.FlowReader
import uk.akane.libphonograph.toUriCompat

/** Entries of the per-item overflow / long-press menu, in the order of `more_menu.xml`. */
enum class LibraryMenuAction(val title: Int) {
    PlayNext(R.string.play_next),
    AddToQueue(R.string.add_to_queue),
    GoToAlbum(R.string.go_to_album),
    GoToArtist(R.string.go_to_artist),
    Rename(R.string.rename),
    AddToPlaylist(R.string.add_to_playlist),
    Details(R.string.details),
    Delete(R.string.delete),
    Share(R.string.share),
}

/**
 * Everything that differs between the library lists: data source, sorting helper, defaults,
 * menus and click targets. Rendering and state handling are shared.
 */
sealed class LibraryTabSpec<T : Any>(
    val tab: HomeTab,
    /** [LibraryAdapterTypes] constant, the number in the "L<n>" and "S<n>" preference keys. */
    val adapterType: Int,
    val helper: Sorter.Helper<T>,
    val initialSortType: Sorter.Type,
    val defaultLayoutType: LayoutType,
    @PluralsRes val pluralStr: Int,
    @DrawableRes val defaultCover: Int,
    val rawOrderExposed: Sorter.Type? = null,
    /** Header shows play-all / shuffle-all buttons. */
    val hasPlayButtons: Boolean = false,
) {
    /** Title of the playback queue created from this tab. */
    val queueTitle: Int get() = tab.label

    abstract fun flow(reader: FlowReader, prefs: SharedPreferences): Flow<List<T>>
    open fun titleOf(prefs: SharedPreferences, item: T): String? =
        if (helper.canGetTitle()) helper.getTitle(item) else "null"
    abstract fun virtualTitleOf(context: Context, item: T): String
    open fun getPinnedOrder(item: T): Int = 0
    open fun coverOf(context: Context, item: T): Uri? = helper.getCover(item)
    abstract fun menuActions(item: T): List<LibraryMenuAction>
    abstract fun onClick(activity: MainActivity, state: LibraryTabState<T>, item: T, position: Int)
    abstract fun onMenuAction(activity: MainActivity, item: T, action: LibraryMenuAction)

    data object Songs : LibraryTabSpec<MediaItem>(
        tab = HomeTab.Songs,
        adapterType = LibraryAdapterTypes.SONG,
        helper = MediaItemHelper,
        initialSortType = Sorter.Type.ByTitleAscending,
        defaultLayoutType = LayoutType.COMPACT_LIST,
        pluralStr = R.plurals.songs,
        defaultCover = R.drawable.ic_default_cover,
        rawOrderExposed = Sorter.Type.ByTitleAscending,
        hasPlayButtons = true,
    ) {
        override fun flow(reader: FlowReader, prefs: SharedPreferences) = reader.songListFlow
        override fun virtualTitleOf(context: Context, item: MediaItem) = "null"
        override fun menuActions(item: MediaItem) = listOf(
            LibraryMenuAction.PlayNext, LibraryMenuAction.AddToQueue,
            LibraryMenuAction.GoToAlbum, LibraryMenuAction.GoToArtist,
            LibraryMenuAction.AddToPlaylist, LibraryMenuAction.Details,
            LibraryMenuAction.Delete, LibraryMenuAction.Share,
        )

        override fun onClick(
            activity: MainActivity, state: LibraryTabState<MediaItem>, item: MediaItem, position: Int
        ) {
            LibraryActions.playSong(activity, state.items, position, activity.getString(queueTitle))
        }

        override fun onMenuAction(activity: MainActivity, item: MediaItem, action: LibraryMenuAction) {
            when (action) {
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(activity, listOf(item))
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(activity, listOf(item))
                LibraryMenuAction.GoToAlbum -> LibraryActions.goToAlbum(activity, item)
                LibraryMenuAction.GoToArtist -> LibraryActions.goToArtist(activity, item)
                LibraryMenuAction.Details -> LibraryActions.showDetails(activity, item)
                LibraryMenuAction.Delete -> LibraryActions.deleteSongs(
                    activity, listOf(item), R.string.delete_really, item.mediaMetadata.title
                )
                LibraryMenuAction.Share -> LibraryActions.share(activity, item)
                LibraryMenuAction.AddToPlaylist -> activity.addToPlaylistDialog(item)
                LibraryMenuAction.Rename -> {}
            }
        }
    }

    /** The song section of the Folders and Filesystem tabs. */
    data object FolderSongs : LibraryTabSpec<MediaItem>(
        tab = HomeTab.Folders,
        adapterType = LibraryAdapterTypes.FOLDER,
        helper = MediaItemHelper,
        initialSortType = Sorter.Type.ByFilePathAscending,
        defaultLayoutType = LayoutType.COMPACT_LIST,
        pluralStr = R.plurals.songs,
        defaultCover = R.drawable.ic_default_cover,
        hasPlayButtons = true,
    ) {
        const val SHOW_FILE_NAMES_PREF = "show_file_names"

        override fun flow(reader: FlowReader, prefs: SharedPreferences): Flow<List<MediaItem>> =
            throw UnsupportedOperationException("provided by FolderTabState")

        override fun titleOf(prefs: SharedPreferences, item: MediaItem): String? =
            if (prefs.getBoolean(SHOW_FILE_NAMES_PREF, true)) item.getFile()?.name
            else super.titleOf(prefs, item)

        override fun virtualTitleOf(context: Context, item: MediaItem) = "null"
        override fun menuActions(item: MediaItem) = Songs.menuActions(item)
        override fun onClick(
            activity: MainActivity, state: LibraryTabState<MediaItem>, item: MediaItem, position: Int
        ) {
            LibraryActions.playSong(
                activity, state.items, position, state.queueTitleOverride ?: "/"
            )
        }

        override fun onMenuAction(activity: MainActivity, item: MediaItem, action: LibraryMenuAction) =
            Songs.onMenuAction(activity, item, action)
    }

    data object Albums : LibraryTabSpec<Album>(
        tab = HomeTab.Albums,
        adapterType = LibraryAdapterTypes.ALBUM,
        helper = StoreAlbumHelper,
        initialSortType = Sorter.Type.ByTitleAscending,
        defaultLayoutType = LayoutType.GRID,
        pluralStr = R.plurals.albums,
        defaultCover = R.drawable.ic_default_cover,
        hasPlayButtons = true,
    ) {
        override fun flow(reader: FlowReader, prefs: SharedPreferences) = reader.albumListFlow
        override fun virtualTitleOf(context: Context, item: Album) =
            context.getString(R.string.unknown_album)

        override fun menuActions(item: Album) = listOf(
            LibraryMenuAction.PlayNext, LibraryMenuAction.AddToQueue, LibraryMenuAction.Delete,
        )

        override fun onClick(activity: MainActivity, state: LibraryTabState<Album>, item: Album, position: Int) {
            LibraryActions.openAlbum(activity, item.id)
        }

        override fun onMenuAction(activity: MainActivity, item: Album, action: LibraryMenuAction) {
            when (action) {
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(activity, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(activity, item.songList)
                LibraryMenuAction.Delete -> LibraryActions.deleteSongs(
                    activity, item.songList, R.string.delete_really, item.title
                )
                else -> {}
            }
        }
    }

    data object Artists : LibraryTabSpec<Artist>(
        tab = HomeTab.Artists,
        adapterType = LibraryAdapterTypes.ARTIST,
        helper = StoreArtistHelper,
        initialSortType = Sorter.Type.ByTitleAscending,
        defaultLayoutType = LayoutType.LIST,
        pluralStr = R.plurals.artists,
        defaultCover = R.drawable.ic_default_cover_artist,
    ) {
        const val ALBUM_ARTIST_PREF = "isDisplayingAlbumArtist"

        @OptIn(ExperimentalCoroutinesApi::class)
        override fun flow(reader: FlowReader, prefs: SharedPreferences) =
            prefs.booleanFlow(ALBUM_ARTIST_PREF, false).flatMapLatest {
                if (it) reader.albumArtistListFlow else reader.artistListFlow
            }

        override fun virtualTitleOf(context: Context, item: Artist) =
            context.getString(R.string.unknown_artist)

        override fun menuActions(item: Artist) = listOf(
            LibraryMenuAction.PlayNext, LibraryMenuAction.AddToQueue, LibraryMenuAction.Delete,
        )

        override fun onClick(activity: MainActivity, state: LibraryTabState<Artist>, item: Artist, position: Int) {
            val isAlbumArtist = state.prefs.getBoolean(ALBUM_ARTIST_PREF, false)
            LibraryActions.openArtist(activity, item.id, isAlbumArtist)
        }

        override fun onMenuAction(activity: MainActivity, item: Artist, action: LibraryMenuAction) {
            when (action) {
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(activity, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(activity, item.songList)
                LibraryMenuAction.Delete -> LibraryActions.deleteSongs(
                    activity, item.songList, R.string.delete_really_artist, item.title
                )
                else -> {}
            }
        }
    }

    data object Genres : LibraryTabSpec<Genre>(
        tab = HomeTab.Genres,
        adapterType = LibraryAdapterTypes.GENRE,
        helper = StoreGenreHelper,
        initialSortType = Sorter.Type.ByTitleAscending,
        defaultLayoutType = LayoutType.LIST,
        pluralStr = R.plurals.items,
        defaultCover = R.drawable.ic_default_cover_genre,
    ) {
        override fun flow(reader: FlowReader, prefs: SharedPreferences) = reader.genreListFlow
        override fun virtualTitleOf(context: Context, item: Genre) =
            context.getString(R.string.unknown_genre)

        override fun menuActions(item: Genre) =
            listOf(LibraryMenuAction.PlayNext, LibraryMenuAction.AddToQueue)

        override fun onClick(activity: MainActivity, state: LibraryTabState<Genre>, item: Genre, position: Int) {
            LibraryActions.openGenre(activity, item.id)
        }

        override fun onMenuAction(activity: MainActivity, item: Genre, action: LibraryMenuAction) {
            when (action) {
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(activity, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(activity, item.songList)
                else -> {}
            }
        }
    }

    data object Dates : LibraryTabSpec<Date>(
        tab = HomeTab.Dates,
        adapterType = LibraryAdapterTypes.DATE,
        helper = StoreDateHelper,
        initialSortType = Sorter.Type.ByTitleAscending,
        defaultLayoutType = LayoutType.LIST,
        pluralStr = R.plurals.items,
        defaultCover = R.drawable.ic_default_cover_date,
    ) {
        override fun flow(reader: FlowReader, prefs: SharedPreferences) = reader.dateListFlow
        override fun virtualTitleOf(context: Context, item: Date) =
            context.getString(R.string.unknown_year)

        override fun menuActions(item: Date) =
            listOf(LibraryMenuAction.PlayNext, LibraryMenuAction.AddToQueue)

        override fun onClick(activity: MainActivity, state: LibraryTabState<Date>, item: Date, position: Int) {
            LibraryActions.openDate(activity, item.id)
        }

        override fun onMenuAction(activity: MainActivity, item: Date, action: LibraryMenuAction) {
            when (action) {
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(activity, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(activity, item.songList)
                else -> {}
            }
        }
    }

    data object Playlists : LibraryTabSpec<Playlist>(
        tab = HomeTab.Playlist,
        adapterType = LibraryAdapterTypes.PLAYLIST,
        helper = StorePlaylistHelper,
        initialSortType = Sorter.Type.ByTitleAscending,
        defaultLayoutType = LayoutType.LIST,
        pluralStr = R.plurals.items,
        defaultCover = R.drawable.ic_default_cover_playlist,
    ) {
        override fun flow(reader: FlowReader, prefs: SharedPreferences) = reader.playlistListFlow
        override fun virtualTitleOf(context: Context, item: Playlist) = when (item) {
            is RecentlyAdded -> context.getString(R.string.recently_added)
            is Favorite -> context.getString(R.string.playlist_favourite)
            else -> context.getString(R.string.unknown_playlist) + " (${item.id} - ${item.path})"
        }

        override fun getPinnedOrder(item: Playlist) = when (item) {
            is RecentlyAdded -> 1
            is Favorite -> 4
            else -> 999
        }

        override fun coverOf(context: Context, item: Playlist): Uri? {
            return if (item.title != null) {
                item.cover?.toUriCompat() ?: super.coverOf(context, item)
            } else
                Uri.Builder()
                    .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                    .authority(context.packageName)
                    .path(
                        when (item) {
                            is RecentlyAdded -> R.drawable.ic_default_cover_playlist_recently
                            is Favorite -> R.drawable.ic_default_cover_playlist_favorite
                            else -> R.drawable.ic_default_cover_playlist
                        }.toString()
                    ).build()
        }

        override fun menuActions(item: Playlist) = buildList {
            add(LibraryMenuAction.PlayNext)
            add(LibraryMenuAction.AddToQueue)
            if (item.title != null) add(LibraryMenuAction.Rename)
            if (item.id != null) add(LibraryMenuAction.Delete)
        }

        override fun onClick(activity: MainActivity, state: LibraryTabState<Playlist>, item: Playlist, position: Int) {
            LibraryActions.openPlaylist(activity, item)
        }

        override fun onMenuAction(activity: MainActivity, item: Playlist, action: LibraryMenuAction) {
            when (action) {
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(activity, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(activity, item.songList)
                LibraryMenuAction.Delete -> LibraryActions.deletePlaylist(activity, item)
                LibraryMenuAction.Rename -> LibraryActions.renamePlaylist(activity, item)
                else -> {}
            }
        }
    }

    /** The song list of a detail page. Its items come from the page, not from [flow]. */
    class SubSongs(
        adapterType: Int,
        rawOrderExposed: Sorter.Type? = null,
    ) : LibraryTabSpec<MediaItem>(
        tab = HomeTab.Songs,
        adapterType = adapterType,
        helper = MediaItemHelper,
        initialSortType = rawOrderExposed ?: Sorter.Type.ByTitleAscending,
        defaultLayoutType = LayoutType.COMPACT_LIST,
        pluralStr = R.plurals.songs,
        defaultCover = R.drawable.ic_default_cover,
        rawOrderExposed = rawOrderExposed,
        hasPlayButtons = true,
    ) {
        override fun flow(reader: FlowReader, prefs: SharedPreferences): Flow<List<MediaItem>> =
            throw UnsupportedOperationException("provided by the detail page")

        override fun virtualTitleOf(context: Context, item: MediaItem) = "null"
        override fun menuActions(item: MediaItem) = Songs.menuActions(item)
        override fun onClick(
            activity: MainActivity, state: LibraryTabState<MediaItem>, item: MediaItem, position: Int
        ) {
            LibraryActions.playSong(activity, state.items, position, state.queueTitleOverride ?: "")
        }

        override fun onMenuAction(activity: MainActivity, item: MediaItem, action: LibraryMenuAction) =
            Songs.onMenuAction(activity, item, action)
    }

    /** The album grid of an artist page. */
    data object ArtistAlbums : LibraryTabSpec<Album>(
        tab = HomeTab.Albums,
        adapterType = LibraryAdapterTypes.ARTIST_ALBUMS,
        helper = StoreAlbumHelper,
        initialSortType = Sorter.Type.ByTitleAscending,
        defaultLayoutType = LayoutType.GRID,
        pluralStr = R.plurals.albums,
        defaultCover = R.drawable.ic_default_cover,
        hasPlayButtons = true,
    ) {
        override fun flow(reader: FlowReader, prefs: SharedPreferences): Flow<List<Album>> =
            throw UnsupportedOperationException("provided by the artist page")

        override fun virtualTitleOf(context: Context, item: Album) =
            context.getString(R.string.unknown_album)

        override fun menuActions(item: Album) = Albums.menuActions(item)
        override fun onClick(activity: MainActivity, state: LibraryTabState<Album>, item: Album, position: Int) =
            Albums.onClick(activity, state, item, position)

        override fun onMenuAction(activity: MainActivity, item: Album, action: LibraryMenuAction) =
            Albums.onMenuAction(activity, item, action)
    }

    companion object {
        fun forTab(tab: HomeTab): LibraryTabSpec<*>? = when (tab) {
            HomeTab.Songs -> Songs
            HomeTab.Albums -> Albums
            HomeTab.Artists -> Artists
            HomeTab.Genres -> Genres
            HomeTab.Dates -> Dates
            HomeTab.Playlist -> Playlists
            HomeTab.Folders, HomeTab.FileSystem -> null
        }
    }
}
