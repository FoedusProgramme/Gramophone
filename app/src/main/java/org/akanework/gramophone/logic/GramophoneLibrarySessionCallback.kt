package org.akanework.gramophone.logic

import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Rating
import androidx.media3.common.util.Log
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaConstants
import androidx.preference.PreferenceManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.comparators.SupportComparator
import org.akanework.gramophone.ui.adapters.AlbumAdapter
import org.akanework.gramophone.ui.adapters.ArtistAdapter
import org.akanework.gramophone.ui.adapters.DateAdapter
import org.akanework.gramophone.ui.adapters.GenreAdapter
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.adapters.SongAdapter
import org.akanework.gramophone.ui.adapters.Sorter
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter
import uk.akane.libphonograph.items.*

/**
 * Handles the media library browsing logic for [GramophonePlaybackService].
 * Extracted from the service to improve maintainability and separate UI/tree-building logic.
 */
class GramophoneLibrarySessionCallback(
    private val context: Context,
    private val app: GramophoneApplication,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val convertItem: (MediaItem) -> MediaItem,
    private val delegate: SessionDelegate,
) : MediaLibrarySession.Callback {

    private val tag = "GramophoneLibSession"

    interface SessionDelegate {
        fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult
        fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo)
        fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo)
        fun onSetRating(session: MediaSession, controller: MediaSession.ControllerInfo, mediaId: String, rating: Rating): ListenableFuture<SessionResult>
        fun onSetRating(session: MediaSession, controller: MediaSession.ControllerInfo, rating: Rating): ListenableFuture<SessionResult>
        fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult>
        fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean): ListenableFuture<MediaItemsWithStartPosition>
    }

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo) = delegate.onConnect(session, controller)
    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) = delegate.onPostConnect(session, controller)
    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) = delegate.onDisconnected(session, controller)
    override fun onSetRating(session: MediaSession, controller: MediaSession.ControllerInfo, mediaId: String, rating: Rating) = delegate.onSetRating(session, controller, mediaId, rating)
    override fun onSetRating(session: MediaSession, controller: MediaSession.ControllerInfo, rating: Rating) = delegate.onSetRating(session, controller, rating)
    override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle) = delegate.onCustomCommand(session, controller, customCommand, args)
    override fun onPlaybackResumption(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean) = delegate.onPlaybackResumption(mediaSession, controller, isForPlayback)

    // --- Helpers ---

    private fun getEnabledTabs(): List<ViewPager2Adapter.Companion.Tab> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val tabs = ViewPager2Adapter.mapSettingToTabList(prefs.getString("tabs", "")!!)
        return tabs.takeWhile { it != null }
            .filterNotNull()
            .filter { it != ViewPager2Adapter.Companion.Tab.FileSystem }
    }

    private fun getCategoryItem(id: String): MediaItem? {
        val gridExtras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        }
        return when (id) {
            "root" -> createFolderItem("root", "", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            "more" -> createFolderItem("more", context.getString(R.string.more), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            "songs" -> createFolderItem("songs", context.getString(R.string.category_songs), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            "albums" -> createFolderItem("albums", context.getString(R.string.category_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, extras = gridExtras)
            "artists" -> createFolderItem("artists", context.getString(R.string.category_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS, extras = gridExtras)
            "genres" -> createFolderItem("genres", context.getString(R.string.category_genres), MediaMetadata.MEDIA_TYPE_FOLDER_GENRES)
            "dates" -> createFolderItem("dates", context.getString(R.string.category_dates), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            "folders" -> createFolderItem("folders", context.getString(R.string.folders), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            "playlists" -> createFolderItem("playlists", context.getString(R.string.category_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
            else -> null
        }
    }

    private fun mapDomainItemToMediaItem(item: Any): MediaItem? {
        return when (item) {
            is Album -> createFolderItem(
                "album_${item.id}", item.title ?: "", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                subtitle = item.albumArtist ?: item.songList.firstOrNull()?.mediaMetadata?.artist?.toString(),
                artworkUri = item.cover, isPlayable = true, isBrowsable = false
            )
            is Artist -> createFolderItem(
                "artist_${item.title}", item.title ?: "", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                subtitle = context.resources.getQuantityString(R.plurals.songs, item.songList.size, item.songList.size),
                artworkUri = item.albumList.firstOrNull()?.cover, isPlayable = true, isBrowsable = false
            )
            is Playlist -> {
                val title = when (item) {
                    is uk.akane.libphonograph.dynamicitem.RecentlyAdded -> context.getString(R.string.recently_added)
                    is uk.akane.libphonograph.dynamicitem.Favorite -> context.getString(R.string.playlist_favourite)
                    else -> item.title ?: ""
                }
                val icon = when (item) {
                    is uk.akane.libphonograph.dynamicitem.RecentlyAdded -> "android.resource://${context.packageName}/${R.drawable.ic_default_cover_playlist_recently}".toUri()
                    is uk.akane.libphonograph.dynamicitem.Favorite -> "android.resource://${context.packageName}/${R.drawable.ic_default_cover_playlist_favorite}".toUri()
                    else -> null
                }
                val id = when (item) {
                    is uk.akane.libphonograph.dynamicitem.RecentlyAdded -> "playlist_recently_added"
                    is uk.akane.libphonograph.dynamicitem.Favorite -> "playlist_favorite"
                    else -> "playlist_${item.id}"
                }
                createFolderItem(id, title, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, artworkUri = icon, isPlayable = true, isBrowsable = false)
            }
            is Genre -> createFolderItem(
                "genre_${item.id}", item.title ?: context.getString(R.string.unknown_genre), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                subtitle = context.resources.getQuantityString(R.plurals.songs, item.songList.size, item.songList.size),
                isPlayable = true, isBrowsable = false, artworkUri = null
            )
            is Date -> createFolderItem(
                "date_${item.id}", item.title ?: context.getString(R.string.unknown_year), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                subtitle = context.resources.getQuantityString(R.plurals.songs, item.songList.size, item.songList.size),
                isPlayable = true, isBrowsable = false, artworkUri = null
            )
            is FileNode -> createFolderItem(
                "folder_${item.folderName}", item.folderName, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                subtitle = context.resources.getQuantityString(R.plurals.items, item.folderList.size + item.songList.size, item.folderList.size + item.songList.size),
                isPlayable = true, isBrowsable = false
            )
            else -> null
        }
    }

    private suspend fun getSongsInParent(parentId: String): List<MediaItem> {
        val (songs, adapterType, naturalOrder) = when {
            parentId.startsWith("album_") -> {
                val id = parentId.removePrefix("album_").toLongOrNull()
                Triple(app.reader.songListFlow.first().filter { it.mediaMetadata.albumId == id }, 12, false)
            }
            parentId.startsWith("artist_") -> {
                val name = parentId.removePrefix("artist_")
                Triple(app.reader.songListFlow.first().filter { it.mediaMetadata.artist?.toString() == name }, 11, false)
            }
            parentId.startsWith("genre_") -> {
                val id = parentId.removePrefix("genre_").toLongOrNull()
                Triple(app.reader.genreListFlow.first().find { it.id == id }?.songList ?: emptyList(), 9, false)
            }
            parentId.startsWith("date_") -> {
                val id = parentId.removePrefix("date_").toLongOrNull()
                Triple(app.reader.dateListFlow.first().find { it.id == id }?.songList ?: emptyList(), 10, false)
            }
            parentId.startsWith("folder_") -> {
                val name = parentId.removePrefix("folder_")
                Triple(app.reader.shallowFolderFlow.first().folderList[name]?.songList ?: emptyList(), 6, false)
            }
            parentId.startsWith("playlist_") -> {
                val idStr = parentId.removePrefix("playlist_")
                val playlist = app.reader.playlistListFlow.first().find {
                    when (idStr) {
                        "recently_added" -> it is uk.akane.libphonograph.dynamicitem.RecentlyAdded
                        "favorite" -> it is uk.akane.libphonograph.dynamicitem.Favorite
                        else -> it.id?.toString() == idStr
                    }
                }
                Triple(playlist?.songList ?: emptyList(), 8, true)
            }
            else -> Triple(emptyList(), -1, false)
        }
        if (adapterType == -1) return emptyList()
        val sorter = Sorter(SongAdapter.MediaItemHelper, null, if (naturalOrder) Sorter.Type.NaturalOrder else null)
        return sortList(songs, adapterType, sorter)
    }

    private fun <T> sortList(list: List<T>, adapterType: Int, sorter: Sorter<T>): List<T> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val sortTypeStr = prefs.getString("S$adapterType", null)
        val sortType = sortTypeStr?.let {
            try { Sorter.Type.valueOf(it) } catch (_: Exception) { null }
        } ?: Sorter.Type.None
        
        val (cmp, reverseFirst) = sorter.getComparator(if (sortType == Sorter.Type.None) Sorter.Type.ByTitleAscending else sortType)
        return ArrayList(list).apply {
            if (reverseFirst) reverse()
            if (cmp != null) sortWith(cmp)
        }
    }

    // --- MediaLibrarySession.Callback Implementation ---

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val outParams = LibraryParams.Builder().setOffline(true).setSuggested(false).setRecent(false).build()
        val item = getCategoryItem("root")!!
        return Futures.immediateFuture(LibraryResult.ofItem(item, outParams))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val completion = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val list: List<MediaItem> = when (parentId) {
                    "root" -> {
                        val tabs = getEnabledTabs()
                        if (tabs.size <= 4) tabs.map { getCategoryItem(mapTabToMediaId(it))!! }
                        else tabs.take(3).map { getCategoryItem(mapTabToMediaId(it))!! } + getCategoryItem("more")!!
                    }
                    "more" -> getEnabledTabs().drop(3).map { getCategoryItem(mapTabToMediaId(it))!! }
                    "albums" -> sortList(app.reader.albumListFlow.first(), 0, Sorter(AlbumAdapter.StoreAlbumHelper, null)).map { mapDomainItemToMediaItem(it)!! }
                    "artists" -> sortList(app.reader.artistListFlow.first(), 1, Sorter(ArtistAdapter.StoreArtistHelper, null)).map { mapDomainItemToMediaItem(it)!! }
                    "songs" -> sortList(app.reader.songListFlow.first(), 5, Sorter(SongAdapter.MediaItemHelper, null))
                    "playlists" -> sortList(app.reader.playlistListFlow.first(), 4, Sorter(PlaylistAdapter.StorePlaylistHelper, null)).map { mapDomainItemToMediaItem(it)!! }
                    "genres" -> sortList(app.reader.genreListFlow.first(), 3, Sorter(GenreAdapter.StoreGenreHelper, null)).map { mapDomainItemToMediaItem(it)!! }
                    "dates" -> sortList(app.reader.dateListFlow.first(), 2, Sorter(DateAdapter.StoreDateHelper, null)).map { mapDomainItemToMediaItem(it)!! }
                    "folders" -> {
                        val folders = app.reader.shallowFolderFlow.first().folderList.values.toList()
                        folders.sortedWith(SupportComparator.createAlphanumericComparator(cnv = { it.folderName })).map { mapDomainItemToMediaItem(it)!! }
                    }
                    else -> getSongsInParent(parentId)
                }

                val finalPageSize = pageSize.coerceAtMost(200)
                val pagedList = list.asSequence().drop(page * finalPageSize).take(finalPageSize).map { convertItem(it) }.toList()
                completion.set(LibraryResult.ofItemList(ImmutableList.copyOf(pagedList), params))
            } catch (e: Exception) {
                Log.w(tag, "onGetChildren failed for $parentId", e)
                completion.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return completion
    }

    private fun mapTabToMediaId(tab: ViewPager2Adapter.Companion.Tab) = when (tab) {
        ViewPager2Adapter.Companion.Tab.Songs -> "songs"
        ViewPager2Adapter.Companion.Tab.Albums -> "albums"
        ViewPager2Adapter.Companion.Tab.Artists -> "artists"
        ViewPager2Adapter.Companion.Tab.Genres -> "genres"
        ViewPager2Adapter.Companion.Tab.Dates -> "dates"
        ViewPager2Adapter.Companion.Tab.Folders -> "folders"
        ViewPager2Adapter.Companion.Tab.Playlist -> "playlists"
        ViewPager2Adapter.Companion.Tab.FileSystem -> "detailed_folders"
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val completion = SettableFuture.create<LibraryResult<MediaItem>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val item = getCategoryItem(mediaId) ?: when {
                    mediaId.startsWith("album_") -> {
                        val id = mediaId.removePrefix("album_").toLongOrNull()
                        app.reader.albumListFlow.first().find { it.id == id }?.let { mapDomainItemToMediaItem(it) }
                    }
                    mediaId.startsWith("artist_") -> {
                        val name = mediaId.removePrefix("artist_")
                        app.reader.artistListFlow.first().find { it.title == name }?.let { mapDomainItemToMediaItem(it) }
                    }
                    mediaId.startsWith("genre_") -> {
                        val id = mediaId.removePrefix("genre_").toLongOrNull()
                        app.reader.genreListFlow.first().find { it.id == id }?.let { mapDomainItemToMediaItem(it) }
                    }
                    mediaId.startsWith("date_") -> {
                        val id = mediaId.removePrefix("date_").toLongOrNull()
                        app.reader.dateListFlow.first().find { it.id == id }?.let { mapDomainItemToMediaItem(it) }
                    }
                    mediaId.startsWith("folder_") -> {
                        val name = mediaId.removePrefix("folder_")
                        app.reader.shallowFolderFlow.first().folderList[name]?.let { mapDomainItemToMediaItem(it) }
                    }
                    mediaId.startsWith("playlist_") -> {
                        val idStr = mediaId.removePrefix("playlist_")
                        app.reader.playlistListFlow.first().find {
                            when (idStr) {
                                "recently_added" -> it is uk.akane.libphonograph.dynamicitem.RecentlyAdded
                                "favorite" -> it is uk.akane.libphonograph.dynamicitem.Favorite
                                else -> it.id?.toString() == idStr
                            }
                        }?.let { mapDomainItemToMediaItem(it) }
                    }
                    else -> app.reader.songListFlow.first().find { it.mediaId == mediaId }
                }

                if (item != null) completion.set(LibraryResult.ofItem(convertItem(item), null))
                else completion.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            } catch (e: Exception) {
                Log.w(tag, "onGetItem failed for $mediaId", e)
                completion.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return completion
    }

    private fun createFolderItem(
        id: String, title: String, mediaType: Int, subtitle: String? = null,
        extras: Bundle? = null, artworkUri: android.net.Uri? = null,
        isPlayable: Boolean = false, isBrowsable: Boolean = true
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title).setSubtitle(subtitle).setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable).setMediaType(mediaType)
        if (extras != null) metadataBuilder.setExtras(extras)
        if (artworkUri != null) metadataBuilder.setArtworkUri(artworkUri)
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadataBuilder.build()).build()
    }

    override fun onSearch(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        query: String, params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        val completion = SettableFuture.create<LibraryResult<Void>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                session.notifySearchResultChanged(browser, query, 0, params)
                completion.set(LibraryResult.ofVoid())
            } catch (e: Exception) {
                Log.w(tag, "onSearch failed for $query", e)
                completion.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return completion
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
        query: String, page: Int, pageSize: Int, params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val completion = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val list = searchForMediaItemSync(query)
                val finalPageSize = pageSize.coerceAtMost(200)
                val pagedList = list.asSequence().drop(page * finalPageSize).take(finalPageSize).map { convertItem(it) }.toList()
                completion.set(LibraryResult.ofItemList(ImmutableList.copyOf(pagedList), params))
            } catch (e: Exception) {
                Log.w(tag, "onGetSearchResult failed for $query", e)
                completion.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return completion
    }

    private suspend fun searchForMediaItemSync(query: String): List<MediaItem> {
        val text = query.trim()
        val list = app.reader.songListFlow.first()
        val sortedList = sortList(list, 7, Sorter(SongAdapter.MediaItemHelper, null))
        return if (text == "") sortedList else sortedList.filter {
            it.mediaMetadata.title?.contains(text, true) == true ||
            it.mediaMetadata.albumTitle?.contains(text, true) == true ||
            it.mediaMetadata.artist?.contains(text, true) == true
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        if (mediaItems.all { it.localConfiguration != null })
            return Futures.immediateFuture(mediaItems.map { convertItem(it) })
        val completion = SettableFuture.create<List<MediaItem>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                var startingIndex: Int? = null
                val resultList = mutableListOf<MediaItem>()

                for (item in mediaItems) {
                    if (item.localConfiguration != null) {
                        resultList.add(item)
                        continue
                    }

                    val expanded = getSongsInParent(item.mediaId)
                    if (expanded.isNotEmpty()) {
                        resultList.addAll(expanded)
                    } else if (item.mediaId != MediaItem.DEFAULT_MEDIA_ID) {
                        val fullSongList = app.reader.songListFlow.first()
                        val sortedFull = sortList(fullSongList, 5, Sorter(SongAdapter.MediaItemHelper, null))
                        val idx = sortedFull.indexOfFirst { it.mediaId == item.mediaId }
                        if (idx >= 0 && startingIndex == null) {
                            startingIndex = resultList.size + idx
                        }
                        resultList.addAll(sortedFull)
                    } else if (item.requestMetadata.searchQuery != null) {
                        resultList.addAll(searchForMediaItem(item))
                    } else {
                        throw UnsupportedOperationException("can't do anything with $item")
                    }
                }

                val convertedResult = resultList.map { convertItem(it) }
                completion.set(convertedResult)

                val startIdx = startingIndex
                if (startIdx != null && convertedResult.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        mediaSession.player.setMediaItems(convertedResult, startIdx, androidx.media3.common.C.TIME_UNSET)
                    }
                }
            } catch (e: Exception) { completion.setException(e) }
        }
        return completion
    }

    private suspend fun searchForMediaItem(item: MediaItem): List<MediaItem> {
        val text = item.requestMetadata.searchQuery?.trim() ?: ""
        val list = app.reader.songListFlow.first()
        val sortedList = sortList(list, 7, Sorter(SongAdapter.MediaItemHelper, null))
        return if (text == "") sortedList else sortedList.filter {
            it.mediaMetadata.title?.contains(text, true) == true ||
            it.mediaMetadata.albumTitle?.contains(text, true) == true ||
            it.mediaMetadata.artist?.contains(text, true) == true
        }
    }
}
