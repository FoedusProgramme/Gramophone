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
import org.akanework.gramophone.ui.actions.AppActionEnv
import org.akanework.gramophone.ui.actions.LibraryActions
import org.akanework.gramophone.ui.actions.PlaylistDialogs
import org.akanework.gramophone.ui.components.compose.booleanFlow
import org.akanework.gramophone.ui.library.LayoutType
import org.akanework.gramophone.ui.library.MediaItemHelper
import org.akanework.gramophone.ui.library.Sorter
import org.akanework.gramophone.ui.library.StoreAlbumHelper
import org.akanework.gramophone.ui.library.StoreArtistHelper
import org.akanework.gramophone.ui.library.StoreDateHelper
import org.akanework.gramophone.ui.library.StoreGenreHelper
import org.akanework.gramophone.ui.library.StorePlaylistHelper
import org.akanework.gramophone.ui.state.LibraryTabSpec.Playlists.defaultCoverOf
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.Genre
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.reader.FlowReader
import uk.akane.libphonograph.toUriCompat

/**
 * Entries of the per-item menu, in the order of `more_menu.xml`. [Play] is not listed with
 * [LibraryTabSpec.menuActions]'s other entries: it is the sheet header's play button.
 */
enum class LibraryMenuAction(val title: Int) {
    Play(R.string.play),
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

    /** Placeholder of an item without a cover; the list may pick a different one per item. */
    @DrawableRes
    open fun defaultCoverOf(item: T): Int = defaultCover
    abstract fun menuActions(item: T): List<LibraryMenuAction>
    abstract fun onClick(env: AppActionEnv, state: LibraryTabState<T>, item: T, position: Int)
    abstract fun onMenuAction(
        env: AppActionEnv,
        state: LibraryTabState<T>,
        item: T,
        position: Int,
        action: LibraryMenuAction,
    )

    /** Title of a queue created from this list, named the way [onClick] names it. */
    fun queueTitleOf(env: AppActionEnv, state: LibraryTabState<T>): String =
        state.queueTitleOverride ?: env.getString(queueTitle)

    data object Songs : LibraryTabSpec<MediaItem>(
        tab = HomeTab.Songs,
        adapterType = LibraryAdapterTypes.SONG,
        helper = MediaItemHelper,
        initialSortType = Sorter.Type.ByTitleAscending,
        defaultLayoutType = LayoutType.LIST,
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
            env: AppActionEnv, state: LibraryTabState<MediaItem>, item: MediaItem, position: Int
        ) {
            LibraryActions.playSong(env, state.items, position, env.getString(queueTitle))
        }

        override fun onMenuAction(
            env: AppActionEnv,
            state: LibraryTabState<MediaItem>,
            item: MediaItem,
            position: Int,
            action: LibraryMenuAction,
        ) {
            when (action) {
                LibraryMenuAction.Play -> LibraryActions.playSong(
                    env, state.items, position, queueTitleOf(env, state)
                )
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(env, listOf(item))
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(env, listOf(item))
                LibraryMenuAction.GoToAlbum -> LibraryActions.goToAlbum(env, item)
                LibraryMenuAction.GoToArtist -> LibraryActions.goToArtist(env, item)
                LibraryMenuAction.Details -> LibraryActions.showDetails(env, item)
                LibraryMenuAction.Delete -> LibraryActions.deleteSongs(
                    env, listOf(item), R.string.delete_really, item.mediaMetadata.title
                )
                LibraryMenuAction.Share -> LibraryActions.share(env, item)
                LibraryMenuAction.AddToPlaylist -> PlaylistDialogs.addToPlaylist(env, item)
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
        defaultLayoutType = LayoutType.LIST,
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
            env: AppActionEnv, state: LibraryTabState<MediaItem>, item: MediaItem, position: Int
        ) {
            LibraryActions.playSong(
                env, state.items, position, state.queueTitleOverride ?: "/"
            )
        }

        override fun onMenuAction(
            env: AppActionEnv,
            state: LibraryTabState<MediaItem>,
            item: MediaItem,
            position: Int,
            action: LibraryMenuAction,
        ) = Songs.onMenuAction(env, state, item, position, action)
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

        override fun onClick(env: AppActionEnv, state: LibraryTabState<Album>, item: Album, position: Int) {
            LibraryActions.openAlbum(env, item.id)
        }

        override fun onMenuAction(
            env: AppActionEnv,
            state: LibraryTabState<Album>,
            item: Album,
            position: Int,
            action: LibraryMenuAction,
        ) {
            when (action) {
                LibraryMenuAction.Play -> LibraryActions.playAll(
                    env, item.songList, queueTitleOf(env, state)
                )
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(env, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(env, item.songList)
                LibraryMenuAction.Delete -> LibraryActions.deleteSongs(
                    env, item.songList, R.string.delete_really, item.title
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

        override fun onClick(env: AppActionEnv, state: LibraryTabState<Artist>, item: Artist, position: Int) {
            val isAlbumArtist = state.prefs.getBoolean(ALBUM_ARTIST_PREF, false)
            LibraryActions.openArtist(env, item.id, isAlbumArtist)
        }

        override fun onMenuAction(
            env: AppActionEnv,
            state: LibraryTabState<Artist>,
            item: Artist,
            position: Int,
            action: LibraryMenuAction,
        ) {
            when (action) {
                LibraryMenuAction.Play -> LibraryActions.playAll(
                    env, item.songList, queueTitleOf(env, state)
                )
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(env, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(env, item.songList)
                LibraryMenuAction.Delete -> LibraryActions.deleteSongs(
                    env, item.songList, R.string.delete_really_artist, item.title
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

        override fun menuActions(item: Genre) = listOf(
            LibraryMenuAction.PlayNext, LibraryMenuAction.AddToQueue,
        )

        override fun onClick(env: AppActionEnv, state: LibraryTabState<Genre>, item: Genre, position: Int) {
            LibraryActions.openGenre(env, item.id)
        }

        override fun onMenuAction(
            env: AppActionEnv,
            state: LibraryTabState<Genre>,
            item: Genre,
            position: Int,
            action: LibraryMenuAction,
        ) {
            when (action) {
                LibraryMenuAction.Play -> LibraryActions.playAll(
                    env, item.songList, queueTitleOf(env, state)
                )
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(env, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(env, item.songList)
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

        override fun menuActions(item: Date) = listOf(
            LibraryMenuAction.PlayNext, LibraryMenuAction.AddToQueue,
        )

        override fun onClick(env: AppActionEnv, state: LibraryTabState<Date>, item: Date, position: Int) {
            LibraryActions.openDate(env, item.id)
        }

        override fun onMenuAction(
            env: AppActionEnv,
            state: LibraryTabState<Date>,
            item: Date,
            position: Int,
            action: LibraryMenuAction,
        ) {
            when (action) {
                LibraryMenuAction.Play -> LibraryActions.playAll(
                    env, item.songList, queueTitleOf(env, state)
                )
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(env, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(env, item.songList)
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

        /**
         * Only a playlist that has artwork of its own gets a URI. A missing one falls through to
         * [defaultCoverOf]: a resource drawable would be decoded against the fixed XML theme and
         * so would keep the palette the app shipped with, whatever is picked in the settings.
         */
        override fun coverOf(context: Context, item: Playlist): Uri? =
            if (item.title != null) item.cover?.toUriCompat() ?: super.coverOf(context, item)
            else null

        override fun defaultCoverOf(item: Playlist): Int = when (item) {
            is RecentlyAdded -> R.drawable.ic_default_cover_playlist_recently
            is Favorite -> R.drawable.ic_default_cover_playlist_favorite
            else -> defaultCover
        }

        override fun menuActions(item: Playlist) = buildList {
            add(LibraryMenuAction.PlayNext)
            add(LibraryMenuAction.AddToQueue)
            if (item.title != null) add(LibraryMenuAction.Rename)
            if (item.id != null) add(LibraryMenuAction.Delete)
        }

        override fun onClick(env: AppActionEnv, state: LibraryTabState<Playlist>, item: Playlist, position: Int) {
            LibraryActions.openPlaylist(env, item)
        }

        override fun onMenuAction(
            env: AppActionEnv,
            state: LibraryTabState<Playlist>,
            item: Playlist,
            position: Int,
            action: LibraryMenuAction,
        ) {
            when (action) {
                LibraryMenuAction.Play -> LibraryActions.playAll(
                    env, item.songList, queueTitleOf(env, state)
                )
                LibraryMenuAction.PlayNext -> LibraryActions.playNext(env, item.songList)
                LibraryMenuAction.AddToQueue -> LibraryActions.addToQueue(env, item.songList)
                LibraryMenuAction.Delete -> LibraryActions.deletePlaylist(env, item)
                LibraryMenuAction.Rename -> LibraryActions.renamePlaylist(env, item)
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
        defaultLayoutType = LayoutType.LIST,
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
            env: AppActionEnv, state: LibraryTabState<MediaItem>, item: MediaItem, position: Int
        ) {
            LibraryActions.playSong(env, state.items, position, state.queueTitleOverride ?: "")
        }

        override fun onMenuAction(
            env: AppActionEnv,
            state: LibraryTabState<MediaItem>,
            item: MediaItem,
            position: Int,
            action: LibraryMenuAction,
        ) = Songs.onMenuAction(env, state, item, position, action)
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
        override fun onClick(env: AppActionEnv, state: LibraryTabState<Album>, item: Album, position: Int) =
            Albums.onClick(env, state, item, position)

        override fun onMenuAction(
            env: AppActionEnv,
            state: LibraryTabState<Album>,
            item: Album,
            position: Int,
            action: LibraryMenuAction,
        ) = Albums.onMenuAction(env, state, item, position, action)
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
