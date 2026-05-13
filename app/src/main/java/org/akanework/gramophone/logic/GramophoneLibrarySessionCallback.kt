package org.akanework.gramophone.logic

import android.content.Context
import android.os.Bundle
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
import org.akanework.gramophone.ui.adapters.ViewPager2Adapter
import uk.akane.libphonograph.items.albumId

/**
 * Handles the media library browsing logic for [GramophonePlaybackService].
 * Extracted from the service to improve maintainability and separate UI/tree-building logic.
 */
class GramophoneLibrarySessionCallback(
    private val context: Context,
    private val app: GramophoneApplication,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val convertItem: (MediaItem) -> MediaItem,
    private val delegate: SessionDelegate
) : MediaLibrarySession.Callback {

    private val TAG = "GramophoneLibSesClbk"

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

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val outParams = LibraryParams.Builder()
            .setOffline(true)
            .setSuggested(false)
            .setRecent(false)
            .build()
        val item = MediaItem.Builder()
            .setMediaId("root")
            .setMediaMetadata(MediaMetadata.Builder()
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build())
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(item, outParams))
    }

    private fun getEnabledTabs(): List<ViewPager2Adapter.Companion.Tab> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val tabs = ViewPager2Adapter.mapSettingToTabList(prefs.getString("tabs", "")!!)
        return tabs.takeWhile { it != null }.filterNotNull()
    }

    private fun tabToMediaItem(tab: ViewPager2Adapter.Companion.Tab): MediaItem {
        val gridExtras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
        }
        return when (tab) {
            ViewPager2Adapter.Companion.Tab.Songs -> createFolderItem("songs", context.getString(R.string.category_songs), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            ViewPager2Adapter.Companion.Tab.Albums -> createFolderItem("albums", context.getString(R.string.category_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, extras = gridExtras)
            ViewPager2Adapter.Companion.Tab.Artists -> createFolderItem("artists", context.getString(R.string.category_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS, extras = gridExtras)
            ViewPager2Adapter.Companion.Tab.Genres -> createFolderItem("genres", context.getString(R.string.category_genres), MediaMetadata.MEDIA_TYPE_FOLDER_GENRES, extras = gridExtras)
            ViewPager2Adapter.Companion.Tab.Dates -> createFolderItem("dates", context.getString(R.string.category_dates), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, extras = gridExtras)
            ViewPager2Adapter.Companion.Tab.Folders -> createFolderItem("folders", context.getString(R.string.folders), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            ViewPager2Adapter.Companion.Tab.Playlist -> createFolderItem("playlists", context.getString(R.string.category_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
            ViewPager2Adapter.Companion.Tab.FileSystem -> createFolderItem("detailed_folders", context.getString(R.string.filesystem), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        }
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
                val list = when (parentId) {
                    "root" -> {
                        val enabledTabs = getEnabledTabs()
                        if (enabledTabs.size <= 4) {
                            enabledTabs.map { tabToMediaItem(it) }
                        } else {
                            val topThree = enabledTabs.take(3).map { tabToMediaItem(it) }
                            val moreItem = createFolderItem("more", context.getString(R.string.more), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            topThree + moreItem
                        }
                    }
                    "more" -> {
                        val enabledTabs = getEnabledTabs()
                        if (enabledTabs.size > 4) {
                            enabledTabs.drop(3).map { tabToMediaItem(it) }
                        } else emptyList()
                    }
                    "albums" -> app.reader.albumListFlow.first().map { createFolderItem("album_${it.id}", it.title ?: "", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = it.albumArtist ?: it.songList.firstOrNull()?.mediaMetadata?.artist?.toString(), artworkUri = it.cover, isPlayable = true, isBrowsable = false) }
                    "artists" -> app.reader.artistListFlow.first().map { createFolderItem("artist_${it.title}", it.title ?: "", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = context.resources.getQuantityString(R.plurals.songs, it.songList.size, it.songList.size), artworkUri = it.albumList.firstOrNull()?.cover, isPlayable = true, isBrowsable = false) }
                    "songs" -> app.reader.songListFlow.first()
                    "playlists" -> app.reader.playlistListFlow.first().map {
                        val title = when (it) {
                            is uk.akane.libphonograph.dynamicitem.RecentlyAdded -> context.getString(R.string.recently_added)
                            is uk.akane.libphonograph.dynamicitem.Favorite -> context.getString(R.string.playlist_favourite)
                            else -> it.title ?: ""
                        }
                        val icon = when (it) {
                            is uk.akane.libphonograph.dynamicitem.RecentlyAdded -> android.net.Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_default_cover_playlist_recently}")
                            is uk.akane.libphonograph.dynamicitem.Favorite -> android.net.Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_default_cover_playlist_favorite}")
                            else -> null
                        }
                        val id = when (it) {
                            is uk.akane.libphonograph.dynamicitem.RecentlyAdded -> "playlist_recently_added"
                            is uk.akane.libphonograph.dynamicitem.Favorite -> "playlist_favorite"
                            else -> "playlist_${it.id}"
                        }
                        createFolderItem(id, title, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, artworkUri = icon, isPlayable = true, isBrowsable = false)
                    }
                    "genres" -> app.reader.genreListFlow.first().map { createFolderItem("genre_${it.id}", it.title ?: context.getString(R.string.unknown_genre), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = context.resources.getQuantityString(R.plurals.songs, it.songList.size, it.songList.size), isPlayable = true, isBrowsable = false) }
                    "dates" -> app.reader.dateListFlow.first().map { createFolderItem("date_${it.id}", it.title ?: context.getString(R.string.unknown_year), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = context.resources.getQuantityString(R.plurals.songs, it.songList.size, it.songList.size), isPlayable = true, isBrowsable = false) }
                    "folders" -> app.reader.shallowFolderFlow.first().folderList.values.map { createFolderItem("folder_${it.folderName}", it.folderName, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = context.resources.getQuantityString(R.plurals.items, it.folderList.size + it.songList.size, it.folderList.size + it.songList.size), isPlayable = true, isBrowsable = false) }
                    else -> {
                        if (parentId.startsWith("album_")) {
                            val albumId = parentId.removePrefix("album_").toLongOrNull()
                            app.reader.songListFlow.first().filter { it.mediaMetadata.albumId == albumId }
                        } else if (parentId.startsWith("artist_")) {
                            val artistName = parentId.removePrefix("artist_")
                            app.reader.songListFlow.first().filter { it.mediaMetadata.artist == artistName }
                        } else if (parentId.startsWith("genre_")) {
                            val genreId = parentId.removePrefix("genre_").toLongOrNull()
                            app.reader.genreListFlow.first().find { it.id == genreId }?.songList ?: emptyList()
                        } else if (parentId.startsWith("date_")) {
                            val dateId = parentId.removePrefix("date_").toLongOrNull()
                            app.reader.dateListFlow.first().find { it.id == dateId }?.songList ?: emptyList()
                        } else if (parentId.startsWith("folder_")) {
                            val folderName = parentId.removePrefix("folder_")
                            app.reader.shallowFolderFlow.first().folderList[folderName]?.songList ?: emptyList()
                        } else if (parentId.startsWith("playlist_")) {
                            val playlistIdStr = parentId.removePrefix("playlist_")
                            val playlist = app.reader.playlistListFlow.first().find {
                                when (playlistIdStr) {
                                    "recently_added" -> it is uk.akane.libphonograph.dynamicitem.RecentlyAdded
                                    "favorite" -> it is uk.akane.libphonograph.dynamicitem.Favorite
                                    else -> it.id?.toString() == playlistIdStr
                                }
                            }
                            playlist?.songList ?: emptyList()
                        } else emptyList()
                    }
                }

                val finalPageSize = pageSize.coerceAtMost(200)
                val pagedList = list.drop(page * finalPageSize).take(finalPageSize).map { convertItem(it) }

                completion.set(LibraryResult.ofItemList(ImmutableList.copyOf(pagedList), params))
            } catch (e: Exception) {
                Log.w(TAG, "onGetChildren failed for $parentId", e)
                completion.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return completion
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val completion = SettableFuture.create<LibraryResult<MediaItem>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val item = if (mediaId == "root") {
                    MediaItem.Builder()
                        .setMediaId("root")
                        .setMediaMetadata(MediaMetadata.Builder()
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build())
                        .build()
                } else if (mediaId == "more") {
                    createFolderItem("more", context.getString(R.string.more), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                } else if (mediaId == "songs") {
                    createFolderItem("songs", context.getString(R.string.category_songs), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                } else if (mediaId == "albums") {
                    val gridExtras = Bundle().apply {
                        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                    }
                    createFolderItem("albums", context.getString(R.string.category_albums), MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS, extras = gridExtras)
                } else if (mediaId == "artists") {
                    val gridExtras = Bundle().apply {
                        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
                    }
                    createFolderItem("artists", context.getString(R.string.category_artists), MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS, extras = gridExtras)
                } else if (mediaId == "genres") {
                    createFolderItem("genres", context.getString(R.string.category_genres), MediaMetadata.MEDIA_TYPE_FOLDER_GENRES)
                } else if (mediaId == "dates") {
                    createFolderItem("dates", context.getString(R.string.category_dates), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                } else if (mediaId == "folders") {
                    createFolderItem("folders", context.getString(R.string.folders), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                } else if (mediaId == "playlists") {
                    createFolderItem("playlists", context.getString(R.string.category_playlists), MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                } else if (mediaId.startsWith("album_")) {
                    val albumId = mediaId.removePrefix("album_").toLongOrNull()
                    val album = app.reader.albumListFlow.first().find { it.id == albumId }
                    if (album != null) createFolderItem("album_${album.id}", album.title ?: "", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = album.albumArtist ?: album.songList.firstOrNull()?.mediaMetadata?.artist?.toString(), artworkUri = album.cover, isPlayable = true, isBrowsable = false) else null
                } else if (mediaId.startsWith("artist_")) {
                    val artistName = mediaId.removePrefix("artist_")
                    val artist = app.reader.artistListFlow.first().find { it.title == artistName }
                    if (artist != null) createFolderItem("artist_${artist.title}", artist.title ?: "", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = context.resources.getQuantityString(R.plurals.songs, artist.songList.size, artist.songList.size), artworkUri = artist.albumList.firstOrNull()?.cover, isPlayable = true, isBrowsable = false) else null
                } else if (mediaId.startsWith("genre_")) {
                    val genreId = mediaId.removePrefix("genre_").toLongOrNull()
                    val genre = app.reader.genreListFlow.first().find { it.id == genreId }
                    if (genre != null) createFolderItem("genre_${genre.id}", genre.title ?: context.getString(R.string.unknown_genre), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = context.resources.getQuantityString(R.plurals.songs, genre.songList.size, genre.songList.size), isPlayable = true, isBrowsable = false) else null
                } else if (mediaId.startsWith("date_")) {
                    val dateId = mediaId.removePrefix("date_").toLongOrNull()
                    val date = app.reader.dateListFlow.first().find { it.id == dateId }
                    if (date != null) createFolderItem("date_${date.id}", date.title ?: context.getString(R.string.unknown_year), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = context.resources.getQuantityString(R.plurals.songs, date.songList.size, date.songList.size), isPlayable = true, isBrowsable = false) else null
                } else if (mediaId.startsWith("folder_")) {
                    val folderName = mediaId.removePrefix("folder_")
                    val folder = app.reader.shallowFolderFlow.first().folderList[folderName]
                    if (folder != null) createFolderItem("folder_${folder.folderName}", folder.folderName, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, subtitle = context.resources.getQuantityString(R.plurals.items, folder.folderList.size + folder.songList.size, folder.folderList.size + folder.songList.size), isPlayable = true, isBrowsable = false) else null
                } else if (mediaId.startsWith("playlist_")) {
                    val playlistIdStr = mediaId.removePrefix("playlist_")
                    val playlist = app.reader.playlistListFlow.first().find {
                        when (playlistIdStr) {
                            "recently_added" -> it is uk.akane.libphonograph.dynamicitem.RecentlyAdded
                            "favorite" -> it is uk.akane.libphonograph.dynamicitem.Favorite
                            else -> it.id?.toString() == playlistIdStr
                        }
                    }
                    if (playlist != null) {
                        val title = when (playlist) {
                            is uk.akane.libphonograph.dynamicitem.RecentlyAdded -> context.getString(R.string.recently_added)
                            is uk.akane.libphonograph.dynamicitem.Favorite -> context.getString(R.string.playlist_favourite)
                            else -> playlist.title ?: ""
                        }
                        val icon = when (playlist) {
                            is uk.akane.libphonograph.dynamicitem.RecentlyAdded -> android.net.Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_default_cover_playlist_recently}")
                            is uk.akane.libphonograph.dynamicitem.Favorite -> android.net.Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_default_cover_playlist_favorite}")
                            else -> null
                        }
                        createFolderItem(mediaId, title, MediaMetadata.MEDIA_TYPE_FOLDER_MIXED, artworkUri = icon)
                    } else null
                } else {
                    app.reader.songListFlow.first().find { it.mediaId == mediaId }
                }

                if (item != null) {
                    completion.set(LibraryResult.ofItem(convertItem(item), null))
                } else {
                    completion.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                }
            } catch (e: Exception) {
                Log.w(TAG, "onGetItem failed for $mediaId", e)
                completion.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return completion
    }

    private fun createFolderItem(
        id: String,
        title: String,
        mediaType: @MediaMetadata.MediaType Int,
        subtitle: String? = null,
        extras: Bundle? = null,
        artworkUri: android.net.Uri? = null,
        isPlayable: Boolean = false,
        isBrowsable: Boolean = true
    ): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .setMediaType(mediaType)
        if (extras != null) metadataBuilder.setExtras(extras)
        if (artworkUri != null) metadataBuilder.setArtworkUri(artworkUri)
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        val completion = SettableFuture.create<LibraryResult<Void>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                session.notifySearchResultChanged(browser, query, 0, params)
                completion.set(LibraryResult.ofVoid())
            } catch (e: Exception) {
                Log.w(TAG, "onSearch failed for $query", e)
                completion.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return completion
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val completion = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val list = searchForMediaItemSync(query)
                val finalPageSize = pageSize.coerceAtMost(200)
                val pagedList = list.drop(page * finalPageSize).take(finalPageSize).map { convertItem(it) }
                completion.set(LibraryResult.ofItemList(ImmutableList.copyOf(pagedList), params))
            } catch (e: Exception) {
                Log.w(TAG, "onGetSearchResult failed for $query", e)
                completion.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return completion
    }

    private suspend fun searchForMediaItemSync(query: String): List<MediaItem> {
        val text = query.trim()
        val list = app.reader.songListFlow.first()
        return if (text == "") list else list.filter {
            val isMatchingTitle = it.mediaMetadata.title?.contains(text, true) == true
            val isMatchingAlbum = it.mediaMetadata.albumTitle?.contains(text, true) == true
            val isMatchingArtist = it.mediaMetadata.artist?.contains(text, true) == true
            isMatchingTitle || isMatchingAlbum || isMatchingArtist
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        if (mediaItems.find { it.localConfiguration == null } == null)
            return Futures.immediateFuture(mediaItems.map { convertItem(it) })
        val completion = SettableFuture.create<List<MediaItem>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Track which item was clicked (used to set starting index)
                var clickedItemIndex = 0

                val result = mediaItems.flatMap {
                    if (it.localConfiguration != null)
                        listOf(it)
                    else if (it.mediaId.startsWith("album_")) {
                        val albumId = it.mediaId.removePrefix("album_").toLongOrNull()
                        app.reader.albumListFlow.first().find { a -> a.id == albumId }?.songList ?: emptyList()
                    } else if (it.mediaId.startsWith("artist_")) {
                        val artistName = it.mediaId.removePrefix("artist_")
                        app.reader.artistListFlow.first().find { a -> a.title == artistName }?.songList ?: emptyList()
                    } else if (it.mediaId.startsWith("genre_")) {
                        val genreId = it.mediaId.removePrefix("genre_").toLongOrNull()
                        app.reader.genreListFlow.first().find { it.id == genreId }?.songList ?: emptyList()
                    } else if (it.mediaId.startsWith("date_")) {
                        val dateId = it.mediaId.removePrefix("date_").toLongOrNull()
                        app.reader.dateListFlow.first().find { it.id == dateId }?.songList ?: emptyList()
                    } else if (it.mediaId.startsWith("folder_")) {
                        val folderName = it.mediaId.removePrefix("folder_")
                        app.reader.shallowFolderFlow.first().folderList[folderName]?.songList ?: emptyList()
                    } else if (it.mediaId.startsWith("playlist_")) {
                        val playlistIdStr = it.mediaId.removePrefix("playlist_")
                        app.reader.playlistListFlow.first().find { p ->
                            when (playlistIdStr) {
                                "recently_added" -> p is uk.akane.libphonograph.dynamicitem.RecentlyAdded
                                "favorite" -> p is uk.akane.libphonograph.dynamicitem.Favorite
                                else -> p.id?.toString() == playlistIdStr
                            }
                        }?.songList ?: emptyList()
                    } else if (it.mediaId != MediaItem.DEFAULT_MEDIA_ID) {
                        // Load entire song list, track which item was clicked
                        val fullSongList = app.reader.songListFlow.first()
                        clickedItemIndex = fullSongList.indexOfFirst { m -> m.mediaId == it.mediaId }
                        fullSongList
                    }
                    else if (it.requestMetadata.searchQuery != null)
                        searchForMediaItem(it)
                    else
                        throw UnsupportedOperationException("can't do anything with $it")
                }
                val convertedResult = result.map { convertItem(it) }
                completion.set(convertedResult)

                // Set the clicked item as the starting point (must be done on main thread)
                if (clickedItemIndex > 0 && convertedResult.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        mediaSession.player.setMediaItems(convertedResult, clickedItemIndex, androidx.media3.common.C.TIME_UNSET)
                    }
                }
            } catch (e: UnsupportedOperationException) {
                completion.setException(e)
            } catch (e: Exception) {
                completion.setException(e)
            }
        }
        return completion
    }

    private suspend fun searchForMediaItem(item: MediaItem): List<MediaItem> {
        val text = item.requestMetadata.searchQuery?.trim() ?: ""
        val list = app.reader.songListFlow.first()
        // TODO support focus and sub queries (see MainActivity)
        return if (text == "") list else list.filter {
            // TODO sort results by match quality? (using raw=natural order)
            // TODO this is copied directly from SearchFragment, which should probably call into
            //  here for its search needs instead in the future
            val isMatchingTitle =
                it.mediaMetadata.title?.contains(text, true) == true
            val isMatchingAlbum =
                it.mediaMetadata.albumTitle?.contains(text, true) == true
            val isMatchingArtist =
                it.mediaMetadata.artist?.contains(text, true) == true
            isMatchingTitle || isMatchingAlbum || isMatchingArtist
        }
    }
}
