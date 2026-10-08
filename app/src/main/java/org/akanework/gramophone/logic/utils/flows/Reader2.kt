/*
 *     Copyright (C) 2025 nift4
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

package org.akanework.gramophone.logic.utils.flows

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Log
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.akanework.gramophone.logic.GramophoneAlbumArtProvider
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.requireMediaStoreId
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.Constants
import uk.akane.libphonograph.ContentObserverCompat
import uk.akane.libphonograph.contentObserverVersioningFlow
import uk.akane.libphonograph.items.RawPlaylist
import uk.akane.libphonograph.items.albumArtistMusicbrainzId
import uk.akane.libphonograph.items.albumMusicbrainzId
import uk.akane.libphonograph.items.artistMusicbrainzId
import uk.akane.libphonograph.manipulator.PlaylistSerializer
import uk.akane.libphonograph.reader.Reader
import uk.akane.libphonograph.utils.MiscUtils
import uk.akane.libphonograph.versioningCallbackFlow
import java.io.File
import kotlin.uuid.Uuid

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

class Reader2(
    context: Context,
) {
    private var useEnhancedCoverReading = true // TODO
    private val scope = CoroutineScope(Dispatchers.Default)

    // Start observing as soon as class gets instantiated. ContentObservers are cheap, and more
    // importantly, this allows us to skip the expensive Reader call if nothing changed while we
    // were inactive - that's the most common case!
    private val rawPlaylistVersionFlow = (if (Build.VERSION.SDK_INT != Build.VERSION_CODES.R ||
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2
    )
        contentObserverVersioningFlow(
            context, scope,
            @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true
        ) else versioningCallbackFlow { nextVersion ->
        // Android 11 has a bug where Google forgot to add change notifications for playlists, so we
        // must use Files table URIs and manually track stuff.
        val lock = Mutex()
        val listener = object : ContentObserverCompat(null) {
            var playlistIdsCache: MutableSet<Long>? = null
            override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                scope.launch {
                    val playlistIds = maybeLoadPlaylists() ?: return@launch
                    if ((flags and ContentResolver.NOTIFY_INSERT) != 0) {
                        val playlistIdsAdded = uris.mapNotNull {
                            try {
                                val id = ContentUris.parseId(it) // Ensure id exists
                                val isPlaylist = context.contentResolver.query(
                                    it,
                                    arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE),
                                    null, null, null
                                ).use { cursor ->
                                    if (cursor != null && cursor.moveToFirst()) {
                                        cursor.getInt(
                                            cursor.getColumnIndexOrThrow(
                                                MediaStore.Files.FileColumns.MEDIA_TYPE
                                            )
                                        ) ==
                                                MediaStoreCompat.MEDIA_TYPE_PLAYLIST
                                    } else false
                                }
                                if (isPlaylist) id else null
                            } catch (_: NumberFormatException) {
                                // ignore
                                null
                            } catch (e: Exception) {
                                Log.w("FlowReader", "failed to query new", e)
                                null
                            }
                        }
                        if (playlistIdsAdded.isNotEmpty()) {
                            lock.withLock {
                                playlistIds.addAll(playlistIdsAdded)
                            }
                            send(nextVersion())
                        }
                    } else {
                        val idsChanged = uris.mapNotNull {
                            try {
                                ContentUris.parseId(it)
                            } catch (_: NumberFormatException) {
                                null
                            }
                        }
                        if (lock.withLock {
                                playlistIds.find { i -> idsChanged.contains(i) } != null
                            }) {
                            send(nextVersion())
                        }
                    }
                }
            }

            override fun deliverSelfNotifications(): Boolean {
                return true
            }

            suspend fun maybeLoadPlaylists(): MutableSet<Long>? {
                if (playlistIdsCache != null)
                    return playlistIdsCache
                return try {
                    context.contentResolver.query(
                        MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Files.FileColumns._ID),
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE} = " +
                                "${MediaStoreCompat.MEDIA_TYPE_PLAYLIST}",
                        null, null
                    ).use { cursor ->
                        if (cursor == null)
                            return null
                        val tmp = mutableSetOf<Long>()
                        if (cursor.moveToFirst()) {
                            do {
                                tmp.add(
                                    cursor.getLong(
                                        cursor.getColumnIndexOrThrow(
                                            MediaStore.Files.FileColumns._ID
                                        )
                                    )
                                )
                            } while (cursor.moveToNext())
                        }
                        lock.withLock {
                            if (playlistIdsCache != null) // we raced with another thread, no issue tho
                                return@withLock playlistIdsCache
                            playlistIdsCache = tmp
                            return@withLock playlistIdsCache
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    // MediaStore.getExternalVolumeNames().contains("external_primary") can return
                    // true but this exception might still be thrown. There's no API returning the
                    // exact state that MediaProvider uses, so any checking is prone to races. This
                    // case is one where try-catch works the best.
                    if (e.message == "Volume external_primary not found")
                        null
                    else
                        throw e
                }
            }
        }
        // Notifications may get delayed while we are frozen, but they do not get lost. Though, if
        // too many of them pile up, we will get killed for eating too much space with our async
        // binder transactions and we will have to restart in a new process later.
        context.contentResolver.registerContentObserver(
            MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
            true, listener
        )
        if (listener.maybeLoadPlaylists() != null) {
            send(nextVersion())
        }
        awaitClose {
            context.contentResolver.unregisterContentObserver(listener)
        }
    }).shareIn(scope, Eagerly, replay = 1)
    private val mediaVersionFlow = contentObserverVersioningFlow(
        context, scope, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true
    ).shareIn(scope, Eagerly, replay = 1)

    val songFlow: Flow<IncrementalList<MediaItem>> = mediaVersionFlow
        .onEach { requireReplayCacheInvalidationManager().invalidate() }
        .conflateAndBlockWhenPaused()
        .map {
            try {
                if (context.hasAudioPermission())
                    Reader.readSongsFromMediaStore(context)
                else emptyList<MediaItem>().toPersistentList()
            } catch (e: IllegalArgumentException) {
                // MediaStore.getExternalVolumeNames().contains("external_primary") can return
                // true but this exception might still be thrown. There's no API returning the
                // exact state that MediaProvider uses, so any checking is prone to races. This
                // case is one where try-catch works the best.
                if (e.message == "Volume external_primary not found")
                    emptyList<MediaItem>().toPersistentList()
                else
                    throw e
            }
        }
        // TODO: this is pretty much an extremely inefficient hack, entire upstream will need rewrite
        .listFlowToIncrementalList({ a, b -> a.mediaId == b.mediaId },
            { a, b -> a == b })
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawMusicbrainzIdArtistFlow: Flow<IncrementalMap<Uuid, IncrementalList<MediaItem>>> = songFlow
        .flatMapConcatIncremental {
            // TODO(Reader2): replace this placeholder with real multiple artists
            val ids = it.mediaMetadata.artistMusicbrainzId?.let { id -> listOf(id) } ?: emptyList()
            ids.toSet().map { id -> id to it }
        }
        .groupByIncremental { it.first }
        .mapIncremental { _, list -> list.mapNonCached { it.second } }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawMusicbrainzIdToArtistFlow: Flow<IncrementalMap<Uuid, IncrementalSet<String>>> = rawMusicbrainzIdArtistFlow
        .flatMapLatestWithInputAsFlowIncremental { artistId, flow ->
            flow.flatMapConcatIncremental {
                // TODO(Reader2): replace this placeholder with real multiple artists
                //  in multi artist variant here we would check artistId and get all name for it
                it.mediaMetadata.artist?.toString()?.let { id -> listOf(id) } ?: emptyList()
            }.groupByIncremental { it }.withoutValuesIncremental()
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawNameArtistFlow: Flow<IncrementalMap<String, IncrementalList<MediaItem>>> = songFlow
        .flatMapConcatIncremental {
            // TODO(Reader2): replace this placeholder with real multiple artists
            val names = it.mediaMetadata.artist?.toString()?.let { id -> listOf(id) } ?: emptyList()
            names.toSet().map { name -> name to it }
        }
        .groupByIncremental { it.first }
        .mapIncremental { _, list -> list.mapNonCached { it.second } }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawNameArtistToMusicbrainzIdFlow: Flow<IncrementalMap<String, IncrementalSet<Uuid>>> = rawNameArtistFlow
        .flatMapLatestWithInputAsFlowIncremental { artistName, flow ->
            flow.flatMapConcatIncremental {
                // TODO(Reader2): replace this placeholder with real multiple artists
                //  in multi artist variant here we would check artistName and get all mbzid for it
                it.mediaMetadata.artistMusicbrainzId?.let { id -> listOf(id) } ?: emptyList()
            }.groupByIncremental { it }.withoutValuesIncremental()
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawNameArtistWithoutMusicbrainzIdFlow: Flow<IncrementalSet<String>> = rawNameArtistToMusicbrainzIdFlow
        .filterIncremental { _, musicbrainzIds -> musicbrainzIds.after.isEmpty() }
        .withoutValuesIncremental()
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    @Suppress("UNCHECKED_CAST")
    private val rawArtistsFlow: Flow<IncrementalMap<Any, IncrementalList<MediaItem>>> = (rawMusicbrainzIdArtistFlow
        .withoutValuesIncremental() as Flow<IncrementalMap<Any, Unit>>)
        .mergeWithIncremental(rawNameArtistWithoutMusicbrainzIdFlow as Flow<IncrementalMap<Any, Unit>>)
        .flatMapLatestIncremental { key ->
            // if forKey() returns null, this flow will be canceled soon, so use filterNotNull to
            // halt any further processing
            if (key is Uuid) {
                val mainSongList = rawMusicbrainzIdArtistFlow.forKey(key).filterNotNull()
                val aliasSongLists = rawMusicbrainzIdToArtistFlow.forKey(key).filterNotNull()
                    .filterLatestIncremental {
                        // An artist name is only an alias for ID if there's no file suggesting
                        // there could possibly be another ID for this artist.
                        rawNameArtistToMusicbrainzIdFlow.forKey(it).filterNotNull()
                            .map { it.after.isEmpty() ||
                                    it.after.size == 1 && it.after.containsKey(key) }
                    }
                    .flatMapLatestIncremental { alias ->
                        rawNameArtistFlow.forKey(alias).filterNotNull().filterIncremental {
                            // TODO(Reader2): replace this placeholder with real multiple artists
                            //  in multi artist variant here we would check if there's any combo of
                            //  artistName and null mbzid
                            it.mediaMetadata.artistMusicbrainzId == null
                        }
                    }
                    // TODO(Reader2) this compareBy is nonsense isnt it?
                    .toIncrementalList(compareBy { System.identityHashCode(it) })
                    .flattenConcatIncremental()
                mainSongList.concatIncremental(aliasSongLists)
            } else {
                key as String
                rawNameArtistFlow.forKey(key).filterNotNull().filterIncremental {
                    // TODO(Reader2): replace this placeholder with real multiple artists
                    //  in multi artist variant here we would check if there's any combo of
                    //  artistName and null mbzid
                    it.mediaMetadata.artistMusicbrainzId == null
                }
            }
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    //TODO: album id should be albumartist too not just albumtitle D: (and maybe folder like mediastore ?)
    private val rawMusicbrainzIdAlbumArtistFlow: Flow<IncrementalMap<Uuid, IncrementalList<MediaItem>>> = songFlow
        .flatMapConcatIncremental {
            // TODO(Reader2): replace this placeholder with real multiple artists
            val ids = it.mediaMetadata.albumArtistMusicbrainzId?.let { id -> listOf(id) } ?: emptyList()
            ids.toSet().map { id -> id to it }
        }
        .groupByIncremental { it.first }
        .mapIncremental { _, list -> list.mapNonCached { it.second } }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawMusicbrainzIdToAlbumArtistFlow: Flow<IncrementalMap<Uuid, IncrementalSet<String>>> = rawMusicbrainzIdAlbumArtistFlow
        .flatMapLatestWithInputAsFlowIncremental { artistId, flow ->
            flow.flatMapConcatIncremental {
                // TODO(Reader2): replace this placeholder with real multiple artists
                //  in multi artist variant here we would check artistId and get all name for it
                it.mediaMetadata.albumArtist?.toString()?.let { id -> listOf(id) } ?: emptyList()
            }.groupByIncremental { it }.withoutValuesIncremental()
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawNameAlbumArtistFlow: Flow<IncrementalMap<String, IncrementalList<MediaItem>>> = songFlow
        .flatMapConcatIncremental {
            // TODO(Reader2): replace this placeholder with real multiple artists
            val names = it.mediaMetadata.albumArtist?.toString()?.let { id -> listOf(id) } ?: emptyList()
            names.toSet().map { name -> name to it }
        }
        .groupByIncremental { it.first }
        .mapIncremental { _, list -> list.mapNonCached { it.second } }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawNameAlbumArtistToMusicbrainzIdFlow: Flow<IncrementalMap<String, IncrementalSet<Uuid>>> = rawNameAlbumArtistFlow
        .flatMapLatestWithInputAsFlowIncremental { artistName, flow ->
            flow.flatMapConcatIncremental {
                // TODO(Reader2): replace this placeholder with real multiple artists
                //  in multi artist variant here we would check artistName and get all mbzid for it
                it.mediaMetadata.albumArtistMusicbrainzId?.let { id -> listOf(id) } ?: emptyList()
            }.groupByIncremental { it }.withoutValuesIncremental()
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawNameAlbumArtistWithoutMusicbrainzIdFlow: Flow<IncrementalSet<String>> = rawNameAlbumArtistToMusicbrainzIdFlow
        .filterIncremental { _, musicbrainzIds -> musicbrainzIds.after.isEmpty() }
        .withoutValuesIncremental()
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    @Suppress("UNCHECKED_CAST")
    private val rawAlbumArtistsFlow: Flow<IncrementalMap<Any, IncrementalList<MediaItem>>> = (rawMusicbrainzIdAlbumArtistFlow
        .withoutValuesIncremental() as Flow<IncrementalMap<Any, Unit>>)
        .mergeWithIncremental(rawNameAlbumArtistWithoutMusicbrainzIdFlow as Flow<IncrementalMap<Any, Unit>>)
        .flatMapLatestIncremental { key ->
            // if forKey() returns null, this flow will be canceled soon, so use filterNotNull to
            // halt any further processing
            if (key is Uuid) {
                val mainSongList = rawMusicbrainzIdAlbumArtistFlow.forKey(key).filterNotNull()
                val aliasSongLists = rawMusicbrainzIdToAlbumArtistFlow.forKey(key).filterNotNull()
                    .filterLatestIncremental {
                        // An artist name is only an alias for ID if there's no file suggesting
                        // there could possibly be another ID for this artist.
                        rawNameAlbumArtistToMusicbrainzIdFlow.forKey(it).filterNotNull()
                            .map { it.after.isEmpty() ||
                                    it.after.size == 1 && it.after.containsKey(key) }
                    }
                    .flatMapLatestIncremental { alias ->
                        rawNameAlbumArtistFlow.forKey(alias).filterNotNull().filterIncremental {
                            // TODO(Reader2): replace this placeholder with real multiple artists
                            //  in multi artist variant here we would check if there's any combo of
                            //  artistName and null mbzid
                            it.mediaMetadata.albumArtistMusicbrainzId == null
                        }
                    }
                    // TODO(Reader2) this compareBy is nonsense isnt it?
                    .toIncrementalList(compareBy { System.identityHashCode(it) })
                    .flattenConcatIncremental()
                mainSongList.concatIncremental(aliasSongLists)
            } else {
                key as String
                rawNameAlbumArtistFlow.forKey(key).filterNotNull().filterIncremental {
                    // TODO(Reader2): replace this placeholder with real multiple artists
                    //  in multi artist variant here we would check if there's any combo of
                    //  artistName and null mbzid
                    it.mediaMetadata.albumArtistMusicbrainzId == null
                }
            }
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawMusicbrainzIdAlbumFlow: Flow<IncrementalMap<Uuid, IncrementalList<MediaItem>>> = songFlow
        .groupByIncremental { it.mediaMetadata.albumMusicbrainzId }
        .filterKeyNotNullIncremental()
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawMusicbrainzIdToAlbumFlow: Flow<IncrementalMap<Uuid, IncrementalSet<String>>> = rawMusicbrainzIdAlbumFlow
        .flatMapLatestWithInputAsFlowIncremental { _, flow ->
            flow.groupByIncremental { it.mediaMetadata.albumTitle?.toString() }
                .withoutValuesIncremental().filterKeyNotNullIncremental()
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawNameAlbumFlow: Flow<IncrementalMap<String, IncrementalList<MediaItem>>> = songFlow
        .groupByIncremental { it.mediaMetadata.albumTitle?.toString() }
        .filterKeyNotNullIncremental()
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawNameAlbumToMusicbrainzIdFlow: Flow<IncrementalMap<String, IncrementalSet<Uuid>>> = rawNameAlbumFlow
        .flatMapLatestWithInputAsFlowIncremental { _, flow ->
            flow.groupByIncremental { it.mediaMetadata.albumMusicbrainzId }
                .withoutValuesIncremental().filterKeyNotNullIncremental()
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    private val rawNameAlbumWithoutMusicbrainzIdFlow: Flow<IncrementalSet<String>> = rawNameAlbumToMusicbrainzIdFlow
        .filterIncremental { _, musicbrainzIds -> musicbrainzIds.after.isEmpty() }
        .withoutValuesIncremental()
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    @Suppress("UNCHECKED_CAST")
    private val rawAlbumsFlow: Flow<IncrementalMap<Any, IncrementalList<MediaItem>>> = (rawMusicbrainzIdAlbumFlow
        .withoutValuesIncremental() as Flow<IncrementalMap<Any, Unit>>)
        .mergeWithIncremental(rawNameAlbumWithoutMusicbrainzIdFlow as Flow<IncrementalMap<Any, Unit>>)
        .flatMapLatestIncremental { key ->
            // if forKey() returns null, this flow will be canceled soon, so use filterNotNull to
            // halt any further processing
            if (key is Uuid) {
                val mainSongList = rawMusicbrainzIdAlbumFlow.forKey(key).filterNotNull()
                val aliasSongLists = rawMusicbrainzIdToAlbumFlow.forKey(key).filterNotNull()
                    .filterLatestIncremental {
                        // An album name is only an alias for ID if there's no file suggesting
                        // there could possibly be another ID for this album.
                        rawNameAlbumToMusicbrainzIdFlow.forKey(it).filterNotNull()
                            .map { it.after.isEmpty() ||
                                    it.after.size == 1 && it.after.containsKey(key) }
                    }
                    .flatMapLatestIncremental { alias ->
                        rawNameAlbumFlow.forKey(alias).filterNotNull().filterIncremental {
                            it.mediaMetadata.albumMusicbrainzId == null
                        }
                    }
                    // TODO(Reader2) this compareBy is nonsense isnt it?
                    .toIncrementalList(compareBy { System.identityHashCode(it) })
                    .flattenConcatIncremental()
                mainSongList.concatIncremental(aliasSongLists)
            } else {
                key as String
                rawNameAlbumFlow.forKey(key).filterNotNull().filterIncremental {
                    it.mediaMetadata.albumMusicbrainzId == null
                }
            }
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

        private val allowedFoldersForCoversFlow: SharedFlow<IncrementalSet<String>> = songFlow
        .groupByIncremental { it.getFile()?.parent }
        .filterKeyNotNullIncremental()
        .flatMapLatestWithInputAsFlowIncremental { _, flow ->
            flow.flatMapLatestIncremental { song ->
                if (song.mediaMetadata.albumMusicbrainzId != null)
                    flowOf(song.mediaMetadata.albumMusicbrainzId)
                else if (song.mediaMetadata.albumTitle != null)
                    rawNameAlbumToMusicbrainzIdFlow.forKey(song.mediaMetadata.albumTitle.toString())
                        .filterNotNull()
                        .map { if (it.after.size == 1) it.after.keys.first() else song.mediaMetadata.albumTitle }
                else flowOf(null)
            }.groupByIncremental { it }.withoutValuesIncremental()
                .map { if (it.after.keys.size == 1) it.after.keys.first() else null }
        }
        .filterValueNotNullIncremental()
        .withoutValuesIncremental()
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
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
                val eligibleForFolderAlbumArt = firstFolder != null &&
                        songList.find { it.getFile()?.parent != firstFolder } == null
                if (!eligibleForFolderAlbumArt) flowOf(fallbackCover)
                else allowedFoldersForCoversFlow.hasKey(firstFolder).map {
                    if (it) {
                        val anySong = songList.first()
                        GramophoneAlbumArtProvider.buildAlbumUri(
                            anySong.requireMediaStoreId(),
                            anySong.getFile()!!
                        )
                    } else fallbackCover
                }
            } else flowOf(fallbackCover)
            val artistIdFlow =
                if (artist?.second != null) flowOf(artist.second) else flowOf(null)
            albumArtFlow.combine(artistIdFlow) { cover, artistId ->
                Album2(+albumId, title, artist?.first, artistId, year, cover, songCount)
            }
        }
        .toIncrementalList(::compareValues)
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    fun getSongsInAlbum(album: Album2): Flow<IncrementalList<MediaItem>> = rawAlbumsFlow
        .forKey(album.id).defeatNullable()

    private val albumsForArtistFlow: Flow<IncrementalMap<Long?, IncrementalList<Album2>>> =
        albumsFlow
            .groupByIncremental { it.albumArtistId }
            .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
            .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    fun getAlbumsForArtist(artist: Artist2): Flow<IncrementalList<Album2>> =
        albumsForArtistFlow.forKey(artist.id).defeatNullable()

    private val artistsWithoutSongsFlow = albumsForArtistFlow
        .filterLatestIncremental { artistId, albums ->
            rawArtistFlow.hasKey(artistId).map { !it }
        }
        .mapIncremental { artistId, albums ->
            val firstAlbum = albums.after.first() // TODO is this unsorted? non-deterministic?!
            Artist2(
                artistId,
                firstAlbum.albumArtist,
                firstAlbum.cover,
                0,
                albums.after.size
            )
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

    private val rawPlaylistFlow: Flow<IncrementalMap<Long, RawPlaylist>> = rawPlaylistVersionFlow
        .onEach { requireReplayCacheInvalidationManager().invalidate() }
        .conflateAndBlockWhenPaused()
        .map {
            try {
                if (context.hasAudioPermission())
                    Reader.fetchPlaylists(context).first
                else emptyList()
            } catch (e: IllegalArgumentException) {
                // MediaStore.getExternalVolumeNames().contains("external_primary") can return
                // true but this exception might still be thrown. There's no API returning the
                // exact state that MediaProvider uses, so any checking is prone to races. This
                // case is one where try-catch works the best.
                if (e.message == "Volume external_primary not found")
                    emptyList()
                else
                    throw e
            }.toPersistentList()
        }
        // TODO: this is pretty much an extremely inefficient hack, entire upstream will need rewrite
        .listFlowToIncrementalList({ a, b -> a.id == b.id },
            { a, b -> a == b })
        .groupByIncremental { it.id }
        .mapIncremental { _, list ->
            // list.after.size == 1 here because each playlist has a different ID
            list.after.first()
        }
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
            .flatMapLatestWithInputAsFlowIncremental { _, valueFlow ->
                valueFlow
                    .filter { it.entries != null } // TODO?
                    .map { it.entries!!.toPersistentList() } // PList<Entry>
                    // The playlists are read from disk, so there is no incremental info
                    // coming from upstream. Add it here.
                    .listFlowToIncrementalList(
                        PlaylistSerializer.Entry::fuzzyEquals,
                        PlaylistSerializer.Entry::equals
                    ) // IList<Entry>
                    .mapIncremental { it.resolveMediaItem2(pathMapFlow) } // IList<Flow<MediaItem?>?>
                    .filterNotNullIncremental()  // IList<Flow<MediaItem?>>
                    .flattenLastestIncremental() // IList<MediaItem?>
                    .filterNotNullIncremental() // IList<MediaItem>
            } // IMap<Long, IList<MediaItem>>
            .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Required)
            .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

    val playlistFlow: Flow<IncrementalList<Playlist2>> = playlistSongsFlow
        .keySetAsSortedIncrementalList(compareBy { it })
        .flatMapLatestIncremental {
            rawPlaylistFlow.forKey(it)
        }
        .filterNotNullIncremental()
        .flatMapLatestIncremental { playlist ->
            playlistSongsFlow
                .forKey(playlist.id)
                .map {
                    (it?.after?.size ?: 0) to (if (it?.after?.isNotEmpty() == true) it.after.first()
                        .mediaMetadata.artworkUri else null)
                }
                .distinctUntilChanged()
                .map { songData ->
                    Playlist2(
                        playlist.id, playlist.title, playlist.path, playlist.dateAdded,
                        playlist.dateModified, songData.second, songData.first
                    )
                }
        }
        .provideReplayCacheInvalidationManager()
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)

// TODO make proper album artists (songs sorted by album artist) tab again

// TODO dates

// TODO genres

// TODO folder flat tree

// TODO filesystem tree

    val idMapFlow: Flow<IncrementalMap<Long, MediaItem>> = songFlow
        .groupByIncremental { it.requireMediaStoreId() }
        .mapIncremental { _, song ->
            // song.after.size == 1 here because each song has a different ID
            song.after.first()
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(), replay = 1)
}