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

package uk.akane.libphonograph.reader

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.akanework.gramophone.logic.emitOrDie
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.utils.flows.Invalidation
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.logic.utils.flows.conflateAndBlockWhenPaused
import org.akanework.gramophone.logic.utils.flows.provideReplayCacheInvalidationManager
import org.akanework.gramophone.logic.utils.flows.repeatUntilDoneWhenUnpaused
import org.akanework.gramophone.logic.utils.flows.requireReplayCacheInvalidationManager
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.ContentObserverCompat
import uk.akane.libphonograph.contentObserverVersioningFlow
import uk.akane.libphonograph.dynamicitem.RecentlyAdded
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.items.Genre
import uk.akane.libphonograph.versioningCallbackFlow
import uk.akane.libphonograph.utils.TagSplitter
import kotlin.time.Duration.Companion.milliseconds

/**
 * SimpleReader reimplementation using flows with focus on efficiency.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowReader(
    context: Context,
    minSongLengthSecondsFlow: SharedFlow<Long>,
    blackListSetFlow: SharedFlow<Set<String>>,
    whiteListSetFlow: SharedFlow<Set<String>>,
    shouldUseEnhancedCoverReadingFlow: SharedFlow<Boolean?>, // null means load if permission is granted
    recentlyAddedFilterSecondFlow: SharedFlow<Long?>, // null means don't generate recently added
    tagSplitConfigFlow: SharedFlow<TagSplitter.TagSplitConfig>,
) {
    // IMPORTANT: Do not use distinctUntilChanged() or StateFlow here because equals() on thousands
    // of MediaItems is very, very expensive!
    private var awaitingRefresh = false
    var hadFirstRefresh = false
        private set
    private val scope = CoroutineScope(Dispatchers.IO + CoroutineName("FlowReader"))
    private val finishRefreshTrigger = MutableSharedFlow<Unit>(replay = 0)
    private val manualRefreshTrigger = MutableSharedFlow<Unit>(replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        manualRefreshTrigger.emitOrDie(Unit)
    }

    // Start observing as soon as class gets instantiated. ContentObservers are cheap, and more
    // importantly, this allows us to skip the expensive Reader call if nothing changed while we
    // were inactive - that's the most common case!
    private val rawPlaylistVersionFlow = (if (Build.VERSION.SDK_INT != Build.VERSION_CODES.R ||
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) >= 2)
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
                    context.contentResolver.query(MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Files.FileColumns._ID),
                        "${MediaStore.Files.FileColumns.MEDIA_TYPE} = " +
                                "${MediaStoreCompat.MEDIA_TYPE_PLAYLIST}",
                        null, null).use { cursor ->
                        if (cursor == null)
                            return null
                        val tmp = mutableSetOf<Long>()
                        if (cursor.moveToFirst()) {
                            do {
                                tmp.add(cursor.getLong(cursor.getColumnIndexOrThrow(
                                    MediaStore.Files.FileColumns._ID)))
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
        context.contentResolver.registerContentObserver(MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
            true, listener)
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

    private suspend fun maybeDoRead(
        context: Context,
        minSongLengthSeconds: Long,
        blackListSet: Set<String>,
        whiteListSet: Set<String>,
        shouldUseEnhancedCoverReading: Boolean?,
        tagSplitConfig: TagSplitter.TagSplitConfig
    ) =
        // TODO repeatUntilDoneWhenUnpaused makes no sense with non-cancelable
        //  function, make it cancelable
        try {
            if (context.hasAudioPermission())
                Reader.readFromMediaStore(
                    context,
                    minSongLengthSeconds,
                    blackListSet,
                    whiteListSet,
                    shouldUseEnhancedCoverReading,
                    tagSplitConfig = tagSplitConfig
                )
            else ReaderResult.emptyReaderResult()
        } catch (e: IllegalArgumentException) {
            // MediaStore.getExternalVolumeNames().contains("external_primary") can return
            // true but this exception might still be thrown. There's no API returning the
            // exact state that MediaProvider uses, so any checking is prone to races. This
            // case is one where try-catch works the best.
            if (e.message == "Volume external_primary not found")
                ReaderResult.emptyReaderResult()
            else
                throw e
        }

    // These expensive Reader calls are only done if we have someone (UI) observing the result AND
    // something changed. The PauseableFlows mechanism allows us to skip any unnecessary work.
    private val rawPlaylistFlow = rawPlaylistVersionFlow
        .onEach { requireReplayCacheInvalidationManager().invalidate() }
        .conflateAndBlockWhenPaused()
        .flatMapLatest {
            manualRefreshTrigger.mapLatest { _ ->
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
                }
            }
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)
    private val readerFlow: Flow<ReaderResult> =
        tagSplitConfigFlow.distinctUntilChanged()
            .debounceSubsequent(300.milliseconds)
            .flatMapLatest { tagSplitConfig ->
                shouldUseEnhancedCoverReadingFlow.distinctUntilChanged()
                    .flatMapLatest { shouldUseEnhancedCoverReading ->
                        minSongLengthSecondsFlow.distinctUntilChanged()
                            .flatMapLatest { minSongLengthSeconds ->
                                blackListSetFlow.distinctUntilChanged()
                                    .flatMapLatest { blackListSet ->
                                        whiteListSetFlow.distinctUntilChanged()
                                            .flatMapLatest { whiteListSet ->
                                                mediaVersionFlow
                                                    .onEach { requireReplayCacheInvalidationManager().invalidate() }
                                                    .conflateAndBlockWhenPaused()
                                                    .flatMapLatest {
                                                        // manual refresh may for whatever reason
                                                        // run in background, but all others
                                                        // shouldn't trigger background runs
                                                        manualRefreshTrigger.mapLatest { _ ->
                                                            repeatUntilDoneWhenUnpaused {
                                                                maybeDoRead(
                                                                    context,
                                                                    minSongLengthSeconds,
                                                                    blackListSet,
                                                                    whiteListSet,
                                                                    shouldUseEnhancedCoverReading,
                                                                    tagSplitConfig
                                                                )
                                                            }
                                                        }
                                                    }
                                            }
                                    }
                            }
                    }
            }
            .onEach {
                finishRefreshTrigger.emit(Unit)
                awaitingRefresh = true
            }
            .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
            .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)
    val idMapFlow: Flow<Map<Long, MediaItem>> = readerFlow.map { it.idMap!! }
    val pathMapFlow = readerFlow.map { it.pathMap!! }
    val songListFlow: Flow<List<MediaItem>> = readerFlow.map { it.songList }
    private val recentlyAddedFlow = recentlyAddedFilterSecondFlow.distinctUntilChanged()
        .onEach { requireReplayCacheInvalidationManager().invalidate() }
        .combine(songListFlow) { recentlyAddedFilterSecond, songList ->
            if (recentlyAddedFilterSecond != null)
                RecentlyAdded(
                    (System.currentTimeMillis() / 1000L) - recentlyAddedFilterSecond,
                    songList
                )
            else
                null
        }
        .provideReplayCacheInvalidationManager(copyDownstream = Invalidation.Optional)
        .sharePauseableIn(scope, WhileSubscribed(20000), WhileSubscribed(2000), replay = 1)
    private val mappedPlaylistsFlow =
        pathMapFlow.combine(rawPlaylistFlow) { pathMap, rawPlaylists ->
            rawPlaylists.mapNotNull { it.toPlaylist(pathMap) }
        }
    val albumListFlow: Flow<List<Album>> = readerFlow.map { it.albumList!! }
    val albumArtistListFlow: Flow<List<Artist>> = readerFlow.map { it.albumArtistList!! }
    val artistListFlow: Flow<List<Artist>> = readerFlow.map { it.artistList!! }
    val genreListFlow: Flow<List<Genre>> = readerFlow.map { it.genreList!! }
    val dateListFlow: Flow<List<Date>> = readerFlow.map { it.dateList!! }
    val playlistListFlow = combine(mappedPlaylistsFlow, recentlyAddedFlow)
    { mappedPlaylists, recentlyAdded ->
        if (recentlyAdded != null) mappedPlaylists + recentlyAdded else mappedPlaylists
    }
    val folderStructureFlow: Flow<FileNode> = readerFlow.map { it.folderStructure!! }
    val shallowFolderFlow: Flow<FileNode> = readerFlow.map { it.shallowFolder!! }
    val foldersFlow: Flow<Set<String>> = readerFlow.map { it.folders!! }
    val foldersForWhitelistFlow: Flow<Set<String>> = readerFlow.map { it.foldersForWhitelist!! }

    /**
     * If the library hasn't been loaded yet, forces a load of the library. Otherwise forces a
     * manual refresh of the library. Suspends until new data is available.
     */
    suspend fun refresh() {
        hadFirstRefresh = true
        coroutineScope {
            if (!awaitingRefresh) {
                // The playlist flow uses pull principle, and causes readerFlow to refresh, so
                // getting a value here means all data is up to date
                playlistListFlow.first()
                return@coroutineScope
            }
            val waiter = launch {
                finishRefreshTrigger.first()
            }
            manualRefreshTrigger.emit(Unit)
            waiter.join()
        }
    }
}

/**
 * Emits the first element immediately without delay, but debounces any subsequent emissions.
 * Prevents cold-boot startup penalties while smoothly debouncing rapid preference changes.
 *
 * @author SteveZMTstudios
 */
@OptIn(FlowPreview::class)
private fun <T> Flow<T>.debounceSubsequent(timeout: kotlin.time.Duration): Flow<T> = flow {
    var isInitial = true
    emitAll(this@debounceSubsequent.debounce {
        if (isInitial) {
            isInitial = false
            0L
        } else {
            timeout.inWholeMilliseconds
        }
    })
}