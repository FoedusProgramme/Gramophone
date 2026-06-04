package org.akanework.gramophone.logic.utils.flows

import android.content.ContentUris
import android.net.Uri
import androidx.media3.common.MediaItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import uk.akane.libphonograph.Constants
import uk.akane.libphonograph.items.RawPlaylist
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.toUriCompat
import uk.akane.libphonograph.utils.MiscUtils
import uk.akane.libphonograph.utils.MiscUtils.findBestCover
import java.io.File
import kotlin.comparisons.compareBy

data class Album2(
    val id: Long?,
    val title: String?,
    val albumArtist: String?,
    val albumArtistId: Long?,
    val albumYear: Int?, // Last year
    val cover: Uri?,
    val songCount: Int,
)

data class Artist2(
    val id: Long?,
    val name: String?,
    val cover: Uri?,
    val songCount: Int,
    val albumCount: Int,
)

data class Playlist2(
    val id: Long?,
    val name: String?,
    val path: File?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val cover: Uri?,
    val songCount: Int,
)

// TODO sharePauseableIn should propagate replay cache invalidation to downstream as well
// doesn't it already do that...? this TODO is so old i wonder if its correct

private var useEnhancedCoverReading = true
private var coverStubUri: String? = "gramophoneAlbumCover"//TODO
private val scope = CoroutineScope(Dispatchers.Default)
val songFlow: Flow<IncrementalList<MediaItem>> = TODO()
    .provideReplayCacheInvalidationManager<IncrementalList<MediaItem>>(
        copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

private val canonicalArtistIdMapRawFlow: Flow<IncrementalMap<String, Long?>> = songFlow
    .groupByIncremental { it.mediaMetadata.artist?.toString() }
    .filterKeyNotNullIncremental()
    .mapIncremental { _, list -> list.after.mapNotNull { it.mediaMetadata.artistId }
        .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
private val allowedFoldersForCoversFlow: SharedFlow<Set<String>> = songFlow
    .groupByIncremental { it.getFile()?.parent }
    .filterIncremental { folder, songs ->
        if (folder != null) {
            val firstAlbum = songs.after.first().mediaMetadata.albumId
            songs.after.find { it.mediaMetadata.albumId != firstAlbum } == null
        } else false
    }
    .map { @Suppress("UNCHECKED_CAST") (it.after.keys as Set<String>) }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

private val rawAlbumsFlow: Flow<IncrementalMap<Long?, IncrementalList<MediaItem>>> = songFlow
    .groupByIncremental { it.mediaMetadata.albumId }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
val albumsFlow: SharedFlow<IncrementalList<Album2>> = rawAlbumsFlow
    .flatMapLatestIncremental { albumId, songs ->
        val songList = songs.after
        val title = songList.first().mediaMetadata.albumTitle?.toString()
        val year = songList.mapNotNull { it.mediaMetadata.releaseYear }.maxOrNull()
        val artist = MiscUtils.findBestAlbumArtist(songList)
        val songCount = songList.size
        val fallbackCover = songList.first().mediaMetadata.artworkUri
        val albumArtFlow = if (useEnhancedCoverReading) {
            val firstFolder = songList.first().getFile()?.parent
            val eligibleForFolderAlbumArt = firstFolder != null && albumId != null &&
                    songList.find { it.getFile()?.parent != firstFolder } == null
            if (!eligibleForFolderAlbumArt) flowOf(fallbackCover)
            else allowedFoldersForCoversFlow.map { it.contains(firstFolder) }.distinctUntilChanged()
                .map {
                    if (it) {
                        if (coverStubUri != null)
                            Uri.Builder().scheme(coverStubUri)
                                .authority(albumId.toString()).path(firstFolder).build()
                        else
                            findBestCover(File(firstFolder))?.toUriCompat()
                    } else fallbackCover
                }
        } else flowOf(
            if (albumId != null)
                ContentUris.withAppendedId(Constants.baseAlbumCoverUri, albumId) else fallbackCover
        )
        val artistIdFlow =
            if (artist?.second != null) flowOf(artist.second) else if (artist != null)
                canonicalArtistIdMapRawFlow.forKey(artist.first).distinctUntilChanged()
            else flowOf(null)
        albumArtFlow.combine(artistIdFlow) { cover, artistId ->
            Album2(albumId, title, artist?.first, artistId, year, cover, songCount)
        }
    }
    .toIncrementalList(::compareValues)
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

fun getSongsInAlbum(album: Album2): Flow<IncrementalList<MediaItem>> = rawAlbumsFlow
    .forKey(album.id).defeatNullable()

private val albumsForArtistFlow: Flow<IncrementalMap<Long?, IncrementalList<Album2>>> = albumsFlow
    .groupByIncremental { it.albumArtistId }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

fun getAlbumsForArtist(artist: Artist2): Flow<IncrementalList<Album2>> =
    albumsForArtistFlow.forKey(artist.id).defeatNullable()

private val rawArtistFlow: Flow<IncrementalMap<Long?, IncrementalList<MediaItem>>> = songFlow
    .groupByIncremental { it.mediaMetadata.artistId }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
private val artistsWithoutSongsFlow = albumsForArtistFlow
    .filterLatestIncremental { artistId, albums ->
        rawArtistFlow.forKey(artistId).map { it == null }
    }
    .flatMapLatestIncremental { artistId, albums ->
        val firstAlbum = albums.after.first() // TODO is this unsorted? non-deterministic?!
        flowOf(Artist2(artistId, firstAlbum.albumArtist, firstAlbum.cover, 0, albums.after.size))
    }
val artistFlow: SharedFlow<IncrementalList<Artist2>> = rawArtistFlow
    .flatMapLatestIncremental { artistId, songs ->
        val songList = songs.after
        val title = songList.first().mediaMetadata.artist?.toString()
        val cover = songList.first().mediaMetadata.artworkUri
        val songCount = songList.size
        albumsForArtistFlow
            .forKey(artistId)
            .map { it?.after?.size ?: 0 }
            .distinctUntilChanged()
            .map { albumCount ->
                Artist2(artistId, title, cover, songCount, albumCount)
            }
    }
    .mergeWithIncremental(artistsWithoutSongsFlow)
    .toIncrementalList(::compareValues)
    .provideReplayCacheInvalidationManager()
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

fun getSongsForArtist(artist: Artist2): Flow<IncrementalList<MediaItem>> =
    rawArtistFlow.forKey(artist.id).defeatNullable()

private val rawPlaylistFlow: Flow<IncrementalMap<Long, IncrementalList<RawPlaylist>>> = TODO()
    .groupByIncremental<RawPlaylist, Long> { it.id }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
private val pathMapFlow: Flow<IncrementalMap<String, MediaItem>> = songFlow
    .groupByIncremental { it.getFile()?.path }
    .filterKeyNotNullIncremental()
    .mapIncremental { _, song ->
        // song.after.size == 1 here because each song has a different path
        song.after.first()
    }
    .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
private val playlistSongsFlow: Flow<IncrementalMap<Long, IncrementalList<MediaItem>>> =
    rawPlaylistFlow
        // The point of key set in between is not recreating flow when the playlist content changes
        .keySetAsSortedIncrementalList(compareBy { it })
        .groupByIncremental { it } // IMap<Long, Long>
        .flatMapLatestIncremental { id, _ -> // IMap<Long, Flow<IList<Entry>>>
            flow {
                rawPlaylistFlow
                    .forKey(id)
                    .defeatNullable()
                    .filterIncremental { it.entries != null }
                    .map { it.after.firstOrNull()?.entries } // List<Entry>?
                    .collect {
                        // TODO provide actually incremental list of entries here
                        emit(IncrementalList.Begin(it?.toPersistentList()
                            ?: persistentListOf()))
                    }
            } // IList<Entry>
                .mapIncremental { it.resolveMediaItem2(pathMapFlow) } // IList<Flow<MediaItem?>>
                .flattenLastestIncremental() // IList<MediaItem?>
                .filterNotNullIncremental() // IList<MediaItem>
        } // IMap<Long, IList<MediaItem>>
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

val playlistFlow: Flow<IncrementalList<Playlist2>> = playlistSongsFlow
    .keySetAsSortedIncrementalList(compareBy { it })
    .flatMapLatestIncremental { rawPlaylistFlow.forKey(it).defeatNullable()
        .map { e -> e.after.firstOrNull() } }
    .filterNotNullIncremental()
    .flatMapLatestIncremental { playlist ->
        playlistSongsFlow
            .forKey(playlist.id)
            .map { (it?.after?.size ?: 0) to (if (it?.after?.isNotEmpty() == true) it.after.first()
                    .mediaMetadata.artworkUri else null) }
            .distinctUntilChanged()
            .map { songData ->
                Playlist2(playlist.id, playlist.title, playlist.path, playlist.dateAdded,
                    playlist.dateModified, songData.second, songData.first)
            }
    }
    .provideReplayCacheInvalidationManager()
    .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

// TODO make proper album artists (songs sorted by album artist) tab again

// TODO dates

// TODO genres

// TODO folder flat tree

// TODO filesystem tree

// TODO id map