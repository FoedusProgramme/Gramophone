package uk.akane.libphonograph.reader

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.util.Log
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.hasImagePermission
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import uk.akane.libphonograph.Constants
import uk.akane.libphonograph.getColumnIndexOrNull
import uk.akane.libphonograph.getIntOrNullIfThrow
import uk.akane.libphonograph.getLongOrNullIfThrow
import uk.akane.libphonograph.getStringOrNullIfThrow
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.EXTRA_ADD_DATE
import uk.akane.libphonograph.items.EXTRA_ALBUM_ID
import uk.akane.libphonograph.items.EXTRA_ALBUM_YEAR
import uk.akane.libphonograph.items.EXTRA_ARTIST_ID
import uk.akane.libphonograph.items.EXTRA_AUTHOR
import uk.akane.libphonograph.items.EXTRA_CD_TRACK_NUMBER
import uk.akane.libphonograph.items.EXTRA_MODIFIED_DATE
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.items.Genre
import uk.akane.libphonograph.items.RawPlaylist
import uk.akane.libphonograph.items.addDate
import uk.akane.libphonograph.items.modifiedDate
import uk.akane.libphonograph.manipulator.PlaylistSerializer
import uk.akane.libphonograph.putIfAbsentSupport
import uk.akane.libphonograph.toUriCompat
import uk.akane.libphonograph.utils.MiscUtils
import uk.akane.libphonograph.utils.MiscUtils.findBestAlbumArtist
import uk.akane.libphonograph.utils.MiscUtils.findBestCover
import uk.akane.libphonograph.utils.MiscUtils.handleMediaFolder
import uk.akane.libphonograph.utils.MiscUtils.handleShallowMediaItem
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min

internal object Reader {
    private val trackNumberRegex = Regex("^([0-9]+)\\..*$")
    private val projection =
        arrayListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            // There is no way to get an ID for the album artist.
            // The MediaStore.Audio.Albums.ARTIST_ID in fact is an arbitrary _song_ artist ID
            // from a random song from the album. Which random song? Decided by SQLite - it's not
            // specified in the view query creation.
            // https://cs.android.com/android/platform/superproject/main/+/main:packages/providers/MediaProvider/src/com/android/providers/media/DatabaseHelper.java;drc=907c4754ff300c413dd0a178245f859b82393d4d;l=1624
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        ).apply {
            if (hasImprovedMediaStore()) {
                add(MediaStore.Audio.Media.GENRE)
                add(MediaStore.Audio.Media.GENRE_ID)
                add(MediaStore.Audio.Media.CD_TRACK_NUMBER)
                add(MediaStore.Audio.Media.COMPILATION)
                add(MediaStore.Audio.Media.COMPOSER)
                add(MediaStore.Audio.Media.DATE_TAKEN)
                add(MediaStore.Audio.Media.WRITER)
                add(MediaStore.Audio.Media.DISC_NUMBER)
                add(MediaStore.Audio.Media.AUTHOR)
                add(MediaStore.Audio.Media.IS_FAVORITE)
            }
        }.toTypedArray()

    /*
     * This takes the approach of reading all song columns and inferring collections such as:
     * - albums
     * - genres
     * - years
     * - artists
     * from the music metadata. MediaStore has built-in support for these collections, however,
     * in modern MediaStore versions, these are just SQL views of the audio table we access anyway.
     * Using the built-in collections would allow us to lazy-load songs only when displaying them,
     * instead of keeping potentially thousands of songs' metadata in RAM. However, we would be
     * forced to take collection metadata at face value and could not do some informed decisions on
     * how to interpret it, which is bad for albums:
     * - The ARTIST/ARTIST_ID columns on an Album are actually populated using a random song's data.
     * - Album covers are populated using a random song's cover
     * This approach allows us to:
     * - Choose album artist based on math (at least 80% of songs with artist) or album artist tag
     * - Choose album cover from folder if only one album is inside that folder
     * - Back-reference albums from the correct _album_ artist, not a song artist
     * without much hassle.
     */
    suspend fun readFromMediaStore(
        context: Context,
        minSongLengthSeconds: Long = 0,
        blackListSet: Set<String> = setOf(),
        shouldUseEnhancedCoverReading: Boolean? = false, // null means load if permission is granted
        shouldIncludeExtraFormat: Boolean = true,
        shouldLoadAlbums: Boolean = true, // implies album artists too
        shouldLoadArtists: Boolean = true,
        shouldLoadGenres: Boolean = true,
        shouldLoadDates: Boolean = true,
        shouldLoadFolders: Boolean = true,
        shouldLoadFilesystem: Boolean = true,
        shouldLoadIdMap: Boolean = true,
        coverStubUri: String? = null
    ): ReaderResult {
        if (!shouldLoadFilesystem && shouldUseEnhancedCoverReading != false) {
            throw IllegalArgumentException("Enhanced cover loading requires loading filesystem")
        }
        if (!context.hasAudioPermission()) {
            throw SecurityException("Audio permission is not granted")
        }
        val useEnhancedCoverReading = if (hasScopedStorageWithMediaTypes() && !context.hasImagePermission()) {
            if (shouldUseEnhancedCoverReading == true)
                throw SecurityException("Requested enhanced cover reading but permission isn't granted")
            false
        } else shouldUseEnhancedCoverReading != false

        var selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        if (shouldIncludeExtraFormat) {
            selection += listOf(
                "audio/x-wav",
                "audio/ogg",
                "audio/aac",
                "audio/midi"
            ).joinToString("") {
                " or ${MediaStore.Audio.Media.MIME_TYPE} = '$it'"
            }
        }

        // Initialize list and maps.
        val coverCache = if (shouldLoadAlbums && useEnhancedCoverReading)
            hashMapOf<Long, Pair<File, FileNode>>() else null
        val folders = if (shouldLoadFolders) hashSetOf<String>() else null
        val root = if (shouldLoadFilesystem) MiscUtils.FileNodeImpl("storage") else null
        val shallowRoot = if (shouldLoadFolders) MiscUtils.FileNodeImpl("shallow") else null
        val songs = mutableListOf<MediaItem>()
        val albumMap = if (shouldLoadAlbums) hashMapOf<Long?, MiscUtils.AlbumImpl>() else null
        val artistMap = if (shouldLoadArtists) hashMapOf<Long?, Artist>() else null
        val artistCacheMap = if (shouldLoadAlbums) hashMapOf<String?, Long?>() else null
        val albumArtistMap = if (shouldLoadAlbums) hashMapOf<Long?, Artist>() else null
        // Note: it has been observed on a user's Pixel(!) that MediaStore assigned 3 different IDs
        // for "Unknown genre" (null genre tag), and genres are simple - like dates - same name
        // means it's the same genre. That's why we completely ignore genre IDs.
        val genreMap = if (shouldLoadGenres) hashMapOf<String?, Genre>() else null
        val dateMap = if (shouldLoadDates) hashMapOf<Int?, Date>() else null
        val idMap = if (shouldLoadIdMap) hashMapOf<Long, MediaItem>() else null
        val pathMap = if (shouldLoadIdMap) hashMapOf<String, MediaItem>() else null
        val hasVolume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
	        MediaStore.getExternalVolumeNames(context).contains(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else true
        val cursor = if (hasVolume) context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE UNICODE ASC",
        ) else null
        val defaultZone by lazy { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ZoneId.systemDefault()
        } else null }

        cursor?.use {
            // Get columns from mediaStore.
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumArtistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val yearColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val albumIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val artistIdColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val discNumberColumn = it.getColumnIndexOrNull(MediaStore.Audio.Media.DISC_NUMBER)
            val trackNumberColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val genreColumn = if (hasImprovedMediaStore())
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE) else null
            val cdTrackNumberColumn = if (hasImprovedMediaStore())
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.CD_TRACK_NUMBER) else null
            val compilationColumn = if (hasImprovedMediaStore())
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPILATION) else null
            val dateTakenColumn = if (hasImprovedMediaStore())
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_TAKEN) else null
            val composerColumn = if (hasImprovedMediaStore())
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER) else null
            val writerColumn = if (hasImprovedMediaStore())
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.WRITER) else null
            val authorColumn = if (hasImprovedMediaStore())
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.AUTHOR) else null
            val favoriteColumn = if (hasImprovedMediaStore())
                it.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_FAVORITE) else null
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addDateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedDateColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (it.moveToNext()) {
                val path = it.getStringOrNullIfThrow(pathColumn)
                val duration = it.getLongOrNullIfThrow(durationColumn)?.let { if (it >= 0) it else null }
                val pathFile = path?.let { it1 -> File(it1) }
                val parent = pathFile?.parentFile
                val fldPath = parent?.absolutePath
                var isBlacklisted = false
                if (blackListSet.isNotEmpty()) {
                    var f = pathFile
                    while (f != null) {
                        if (blackListSet.contains(f.absolutePath)) {
                            isBlacklisted = true
                            break
                        }
                        f = f.parentFile
                    }
                }
                val skip = (duration != null && duration != 0L &&
                        duration < minSongLengthSeconds * 1000) || (fldPath == null
                        || isBlacklisted)
                // We need to add blacklisted songs to idMap as they can be referenced by playlist
                if (skip && idMap == null && pathMap == null) continue
                val id = it.getLongOrNullIfThrow(idColumn)!!
                val title = it.getStringOrNullIfThrow(titleColumn)!!
                val artist: String?
                val hasNoMetadata: Boolean
                it.getStringOrNullIfThrow(artistColumn).let {
                    hasNoMetadata = it == MediaStore.UNKNOWN_STRING || it == null
                    artist = if (hasNoMetadata) null else it
                }
                val album = it.getStringOrNullIfThrow(albumColumn)
                val albumArtist = it.getStringOrNullIfThrow(albumArtistColumn)
                val year = it.getIntOrNullIfThrow(yearColumn).let { v -> if (v == 0) null else v }
                val albumId = it.getLongOrNullIfThrow(albumIdColumn)
                val artistId = it.getLongOrNullIfThrow(artistIdColumn)
                val mimeType = it.getStringOrNullIfThrow(mimeTypeColumn)!!
                var discNumber = discNumberColumn?.let { col -> it.getIntOrNullIfThrow(col) }
                var trackNumber = it.getIntOrNullIfThrow(trackNumberColumn)
                var cdTrackNumber = cdTrackNumberColumn?.let { col -> it.getStringOrNullIfThrow(col) }
                val compilation = compilationColumn?.let { col -> it.getStringOrNullIfThrow(col) }
                val dateTaken = dateTakenColumn?.let { col -> it.getStringOrNullIfThrow(col) }
                val composer = composerColumn?.let { col -> it.getStringOrNullIfThrow(col) }
                val writer = writerColumn?.let { col -> it.getStringOrNullIfThrow(col) }
                val author = authorColumn?.let { col -> it.getStringOrNullIfThrow(col) }
                val genre = genreColumn?.let { col -> it.getStringOrNullIfThrow(col) }
                val favorite = favoriteColumn?.let { col -> it.getIntOrNullIfThrow(col) }.let {
                    when (it) { 1 -> true; 0 -> false; else -> null }
                }
                val addDate = it.getLongOrNullIfThrow(addDateColumn)
                val modifiedDate = it.getLongOrNullIfThrow(modifiedDateColumn)
                val dateTakenParsed = if (hasImprovedMediaStore()) {
                    // the column exists since R, so we can always use these APIs
                    dateTaken?.toLongOrNull()?.let { it1 -> Instant.ofEpochMilli(it1) }
                        ?.atZone(defaultZone)
                } else null
                val dateTakenYear = if (hasImprovedMediaStore()) {
                    dateTakenParsed?.year
                } else null
                val dateTakenMonth = if (hasImprovedMediaStore()) {
                    dateTakenParsed?.monthValue
                } else null
                val dateTakenDay = if (hasImprovedMediaStore()) {
                    dateTakenParsed?.dayOfMonth
                } else null
                val imgUri = Uri.Builder().scheme("gramophoneSongCover")
                    .authority(id.toString()).path(path).build()
                if (cdTrackNumber != null && trackNumber == null) {
                    cdTrackNumber.toIntOrNull()?.let {
                        trackNumber = it
                        cdTrackNumber = null
                    }
                }
                // If there is no track number metadata set by now, and the file name is something
                // along the lines of "04.Englishman in New York.mp3" or "04. La isla bonita.flac",
                // AND either:
                // - there is no valid track metadata (this means the title is the filename without
                //   extension)
                // - there is valid track metadata, and the title doesn't begin with that number
                // we can assume this is referring to the track number.
                if (trackNumber == null && pathFile != null) {
                    val match = trackNumberRegex.matchEntire(pathFile.name)
                    if (match != null && match.groups.size > 1
                        && (hasNoMetadata || !title.startsWith(match.groups[1]!!.value))) {
                        trackNumber = match.groups[1]!!.value.toIntOrNull()
                    }
                }
                // Process track numbers that have disc number added on.
                // e.g. 1001 - Disc 01, Track 01
                // MediaStore encodes info this way even if the file does not
                if (trackNumber != null &&
                    (discNumber == null || discNumber == 0 || discNumber == trackNumber / 1000) && 
                    trackNumber >= 1000) {
                    discNumber = trackNumber / 1000
                    trackNumber %= 1000
                }

                // Build our mediaItem.
                val song = MediaItem.Builder()
                    .setUri(pathFile?.toUriCompat())
                    .setMediaId("MediaStore:$id")
                    .setMimeType(mimeType)
                    .setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                            .setDurationMs(duration)
                            .setTitle(title)
                            .setWriter(writer)
                            .setCompilation(compilation)
                            .setComposer(composer)
                            .setArtist(artist)
                            .setAlbumTitle(album)
                            .setAlbumArtist(albumArtist)
                            .setArtworkUri(imgUri)
                            .setTrackNumber(trackNumber)
                            .setDiscNumber(discNumber)
                            .setGenre(genre)
                            .setRecordingDay(dateTakenDay)
                            .setRecordingMonth(dateTakenMonth)
                            .setRecordingYear(dateTakenYear)
                            .setReleaseYear(year)
                            .setUserRating(if (favorite != null) HeartRating(favorite) else HeartRating())
                            .setExtras(Bundle().apply {
                                if (artistId != null) {
                                    putLong(EXTRA_ARTIST_ID, artistId)
                                }
                                if (albumId != null) {
                                    putLong(EXTRA_ALBUM_ID, albumId)
                                }
                                // EXTRA_ALBUM_YEAR assigned below
                                // TODO: is albumYear truly a good property of a song? it creates
                                //  a cyclic dependency between albums derived from songs, and song
                                //  metadata derived from album. maybe users of this should just
                                //  query the album list instead and fetch album year from there.
                                putString(EXTRA_AUTHOR, author)
                                if (addDate != null) {
                                    putLong(EXTRA_ADD_DATE, addDate)
                                }
                                if (modifiedDate != null) {
                                    putLong(EXTRA_MODIFIED_DATE, modifiedDate)
                                }
                                putString(EXTRA_CD_TRACK_NUMBER, cdTrackNumber)
                            })
                            .build(),
                    ).build()
                // Build our metadata maps/lists.
                idMap?.put(id, song)
                if (path != null)
                    pathMap?.put(path, song)
                // Now that the song can be found by playlists, do NOT register other metadata.
                if (skip) continue
                songs.add(song)
                (artistMap?.getOrPut(artistId) {
                    Artist(artistId, artist, mutableListOf(), mutableListOf())
                }?.songList as MutableList?)?.add(song)
                artistCacheMap?.putIfAbsentSupport(artist, artistId)
                albumMap?.getOrPut(albumId) {
                    // in enhanced cover loading case, cover uri is created later using coverCache
                    val cover = if (coverCache != null || albumId == null) null else
                            ContentUris.withAppendedId(Constants.baseAlbumCoverUri, albumId)
                    MiscUtils.AlbumImpl(
                        albumId,
                        album,
                        null,
                        null,
                        cover,
                        null,
                        null,
                        null,
                        mutableListOf()
                    )
                }?.songList?.add(song)
                (genreMap?.getOrPut(genre) { Genre(genre.hashCode().toLong(), genre, mutableListOf()) }?.songList
                        as MutableList?)?.add(song)
                (dateMap?.getOrPut(year) {
                    Date(
                        year?.toLong() ?: 0,
                        year?.toString(),
                        mutableListOf()
                    )
                }?.songList as MutableList?)?.add(song)
                if (shouldLoadFilesystem) {
                    val fn = handleMediaFolder(fldPath, root!!)
                    (fn as MiscUtils.FileNodeImpl).addSong(song, albumId)
                    if (albumId != null) {
                        coverCache?.putIfAbsentSupport(albumId, Pair(parent, fn))
                    }
                }
                if (shouldLoadFolders) {
                    handleShallowMediaItem(song, albumId, parent.name, shallowRoot!!)
                    var tmpPath = parent
                    while (tmpPath != null) {
                        folders!!.add(tmpPath.absolutePath)
                        tmpPath = tmpPath.parentFile
                        if (tmpPath.absolutePath == "/storage/emulated" || tmpPath.absolutePath == "/storage")
                            tmpPath = null // lets not allow to blacklist more than entire volumes
                    }
                }
            }
        }

        // Parse all the lists.
        val albumList = albumMap?.values?.onEach {
            if (it.albumArtistId != null || it.albumArtist != null)
                throw IllegalStateException("code bug? failed: it.albumArtistId != null || it.albumArtist != null")
            val artistFound = findBestAlbumArtist(it.songList)
            it.albumArtist = artistFound?.first
            it.albumArtistId = artistFound?.second ?: artistCacheMap?.get(it.albumArtist)
                    ?: "nonMediaStoreArtist:${it.albumArtist}".hashCode().toLong()
            it.albumYear = it.songList.mapNotNull { it.mediaMetadata.releaseYear }.maxOrNull()
            if (it.albumYear != null)
                it.songList.forEach { item -> item.mediaMetadata.extras!!.putLong(EXTRA_ALBUM_YEAR, it.albumYear!!.toLong()) }
            it.albumAddDate = it.songList.mapNotNull { it.mediaMetadata.addDate }.minOrNull()
            it.albumModifiedDate = it.songList.mapNotNull { it.mediaMetadata.modifiedDate }.maxOrNull()
            val albumArtist = albumArtistMap?.getOrPut(it.albumArtistId) {
                Artist(it.albumArtistId, it.albumArtist, mutableListOf(), mutableListOf())
            }
            (albumArtist?.albumList as MutableList?)?.add(it)
            (albumArtist?.songList as MutableList?)?.addAll(it.songList)
            (artistMap?.get(it.albumArtistId)?.albumList as MutableList?)?.add(it)
            // coverCache == null if !useEnhancedCoverReading
            coverCache?.get(it.id)?.let { p ->
                // if this is false, folder contains >1 albums
                if (p.second.albumId == it.id) {
                    if (coverStubUri != null)
                        it.cover = Uri.Builder().scheme(coverStubUri)
                            .authority(it.id.toString()).path(p.first.absolutePath).build()
                    else
                        findBestCover(p.first)?.let { f -> it.cover = f.toUriCompat() }
                }
            }
        }?.toList<Album>()
        if (!albumList.isNullOrEmpty()) {
            supervisorScope {
                for (i in 0..(albumList.size / 100)) {
                    launch {
                        for (j in (i * 100)..<min(i * 100 + 100, albumList.size)) {
                            (albumList[j].songList as MutableList).sortBy {
                                (it.mediaMetadata.discNumber ?: 0) * 1000 +
                                        (it.mediaMetadata.trackNumber ?: 0)
                            }
                        }
                    }
                }
            }
        }
        val artistList = artistMap?.values?.toList()
        val albumArtistList = albumArtistMap?.values?.toList()
        val genreList = genreMap?.values?.toList()
        val dateList = dateMap?.values?.toList()

        if (folders != null) {
            for (blacklistedFolder in blackListSet) {
                var tmpPath: File? = File(blacklistedFolder)
                while (tmpPath != null) {
                    folders.add(tmpPath.absolutePath)
                    tmpPath = tmpPath.parentFile
                    if (tmpPath.absolutePath == "/storage/emulated" || tmpPath.absolutePath == "/storage")
                        tmpPath = null // lets not allow to blacklist more than entire volumes
                }
            }
        }

        return ReaderResult(
            songs,
            albumList,
            albumArtistList,
            artistList,
            genreList,
            dateList,
            idMap,
            pathMap,
            root,
            shallowRoot,
            folders
        )
    }

    fun fetchPlaylists(context: Context): Pair<List<RawPlaylist>, Boolean> {
        var foundPlaylistContent = false
        val playlists = mutableListOf<RawPlaylist>()
        context.contentResolver.query(
            @Suppress("DEPRECATION")
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, arrayOf(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists._ID,
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.NAME,
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.DATA,
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.DATE_ADDED,
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.DATE_MODIFIED
            ), null, null, null
        )?.use {
            val playlistIdColumn = it.getColumnIndexOrThrow(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists._ID
            )
            val playlistPathColumn = it.getColumnIndexOrThrow(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.DATA
            )
            val playlistNameColumn = it.getColumnIndexOrThrow(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.NAME
            )
            val playlistDateAddedColumn = it.getColumnIndexOrThrow(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.DATE_ADDED
            )
            val playlistDateModifiedColumn = it.getColumnIndexOrThrow(
                @Suppress("DEPRECATION") MediaStore.Audio.Playlists.DATE_MODIFIED
            )
            while (it.moveToNext()) {
                val playlistId = it.getLong(playlistIdColumn)
                val playlistName = it.getString(playlistNameColumn)?.ifEmpty { null }
                val playlistPath = it.getString(playlistPathColumn)?.ifEmpty { null }?.let { p -> File(p) }
                val playlistDateAdded = it.getLongOrNullIfThrow(playlistDateAddedColumn)
                val playlistDateModified = it.getLongOrNullIfThrow(playlistDateModifiedColumn)
                val content = mutableListOf<Long?>()
                context.contentResolver.query(
                    @Suppress("DEPRECATION") MediaStore.Audio
                        .Playlists.Members.getContentUri("external", playlistId), arrayOf(
                        @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.AUDIO_ID,
                        @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                    ), null, null, @Suppress("DEPRECATION")
                    MediaStore.Audio.Playlists.Members.PLAY_ORDER + " ASC"
                )?.use { cursor ->
                    val column = cursor.getColumnIndexOrThrow(
                        @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.AUDIO_ID
                    )
                    val column2 = cursor.getColumnIndexOrThrow(
                        @Suppress("DEPRECATION") MediaStore.Audio.Playlists.Members.PLAY_ORDER
                    )
                    var first: Long? = null
                    while (cursor.moveToNext()) {
                        val last = first
                        first = cursor.getLong(column2)
                        while (last != null && first + 1 < last) {
                            content.add(null)
                            first++
                        }
                        foundPlaylistContent = true
                        content.add(cursor.getLong(column))
                    }
                }
                val paths = try {
                    playlistPath?.let { p -> PlaylistSerializer.read(p) }
                } catch (_: PlaylistSerializer.UnsupportedPlaylistFormatException) {
                    null
                } catch (e: IOException) {
                    Log.w("Reader", "failed to read playlist $playlistPath", e)
                    null
                }
                if (paths != null && content.size > paths.size) {
                    throw IllegalStateException("playlist $playlistName failed to parse: $content, $paths")
                }
                playlists.add(RawPlaylist(playlistId, playlistName, playlistPath,
                    playlistDateAdded, playlistDateModified, content, paths))
            }
        }
        return Pair(playlists, foundPlaylistContent)
    }
}
