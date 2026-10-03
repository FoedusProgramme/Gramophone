/*
 *     Copyright (C) 2024 The Gramophone authors
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

package uk.akane.libphonograph.utils

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Log
import uk.akane.libphonograph.ALLOWED_EXT
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.items.artistNames
import uk.akane.libphonograph.items.rawArtist
import uk.akane.libphonograph.utils.TagSplitter.TagSplitConfig
import java.io.File
import java.util.Locale

object MiscUtils {
    internal class FileNodeImpl(
        override val folderName: String
    ) : FileNode {
        override val folderList = hashMapOf<String, FileNode>()
        override val songList = mutableListOf<MediaItem>()
        override var albumId: Long? = null
        fun addSong(item: MediaItem, id: Long?) {
            if (albumId != null && id != albumId) {
                albumId = null
            } else if (albumId == null && songList.isEmpty()) {
                albumId = id
            }
            songList.add(item)
        }
    }

    internal fun handleMediaFolder(path: String, rootNode: FileNode): FileNode {
        val splitPath = path.substring(
            1, if (path.endsWith('/'))
                path.length - 1 else path.length
        ).split('/')
        var node: FileNode = rootNode
        for (fld in splitPath) {
            var newNode = node.folderList[fld]
            if (newNode == null) {
                newNode = FileNodeImpl(fld)
                (node.folderList as HashMap)[newNode.folderName] = newNode
            }
            node = newNode
        }
        return node
    }

    internal fun handleShallowMediaItem(
        mediaItem: MediaItem,
        albumId: Long?,
        folderName: String,
        shallowFolder: FileNode
    ) {
        var folder = (shallowFolder.folderList as HashMap)[folderName]
        if (folder == null) {
            folder = FileNodeImpl(folderName)
            (shallowFolder.folderList as HashMap)[folder.folderName] = folder
        }
        (folder as FileNodeImpl).addSong(mediaItem, albumId)
    }

    fun findBestCover(songFolder: File): File? {
        var bestScore = 0
        var bestFile: File? = null
        try {
            val files = songFolder.listFiles() ?: return null
            for (file in files) {
                if (file.extension !in ALLOWED_EXT)
                    continue
                var score = 1
                when (file.extension) {
                    "jpg" -> score += 3
                    "png" -> score += 2
                    "jpeg" -> score += 1
                }
                if (file.nameWithoutExtension.contentEquals("albumart", true)) score += 24
                else if (file.nameWithoutExtension.contentEquals("cover", true)) score += 20
                else if (file.nameWithoutExtension.startsWith("albumart", true)) score += 16
                else if (file.nameWithoutExtension.startsWith("cover", true)) score += 12
                else if (file.nameWithoutExtension.contains("albumart", true)) score += 8
                else if (file.nameWithoutExtension.contains("cover", true)) score += 4
                if (bestScore < score) {
                    bestScore = score
                    bestFile = file
                }
            }
        } catch (e: Exception) {
            Log.e("libPhonograph", Log.getThrowableString(e)!!)
        }
        // allow .jpg or .png files with any name, but only permit more exotic
        // formats if name contains either cover or albumart
        if (bestScore >= 3) {
            return bestFile
        }
        return null
    }

    data class ResolvedAlbumArtist(
        val albumArtist: String,
        val primaryAlbumArtists: List<String>
    )

    fun resolveAlbumArtist(
        songs: List<MediaItem>,
        tagSplitConfig: TagSplitConfig
    ): ResolvedAlbumArtist? {
        if (songs.isEmpty()) return null

        val foundAlbumArtists = songs.groupBy {
            it.mediaMetadata.albumArtist?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() }
        }.mapValues { it.value.size }

        val nonNullAlbumArtists = foundAlbumArtists.mapNotNull { (k, v) ->
            if (k != null) k to v else null
        }.toMap()
        val theAlbumArtist = if (nonNullAlbumArtists.size == 1) {
            val candidate = nonNullAlbumArtists.keys.first()
            val countWithNull = foundAlbumArtists[null] ?: 0
            val countWithAlbumArtist = nonNullAlbumArtists[candidate]!!
            if (countWithAlbumArtist >= countWithNull) candidate else null
        } else if (nonNullAlbumArtists.size > 1) {
            val best = nonNullAlbumArtists.maxByOrNull { it.value }
            if (best != null && best.value * 10 >= songs.size * 6) {
                best.key
            } else {
                Log.w(
                    "libPhonograph", "Album artists: $foundAlbumArtists for one album exceed 1 with no clear majority, " +
                            "falling back to track artist inference"
                )
                null
            }
        } else {
            null
        }

        if (theAlbumArtist != null) {
            val primaryArtists = if (tagSplitConfig.isMultiArtistEnabled) {
                TagSplitter.splitArtists(theAlbumArtist, tagSplitConfig).ifEmpty { listOf(theAlbumArtist) }
            } else {
                listOf(theAlbumArtist)
            }
            return ResolvedAlbumArtist(theAlbumArtist, primaryArtists)
        } else {
            // Infer based on track artist tag (exact beta >= 60% threshold, grouping by raw artist tag)
            val bestMatch = songs.groupBy {
                (it.mediaMetadata.rawArtist ?: it.mediaMetadata.artist?.toString())?.trim()?.takeIf { s -> s.isNotEmpty() }
            }.maxByOrNull { it.value.size }

            if (bestMatch?.key == null) return null
            if (bestMatch.value.size * 10 < songs.size * 6) return null

            val rawTag = bestMatch.key!!
            val primaryArtists = if (tagSplitConfig.isMultiArtistEnabled) {
                TagSplitter.splitArtists(rawTag, tagSplitConfig).ifEmpty { listOf(rawTag) }
            } else {
                listOf(rawTag)
            }
            return ResolvedAlbumArtist(rawTag, primaryArtists)
        }
    }

    private const val SYNTHETIC_ARTIST_PREFIX = "nonMediaStoreArtist:"

    /**
     * Produces a canonical lowercase trimmed lookup key for an artist name.
     * Serves as the single source of truth for cache insertion, resolution, and synthetic ID generation.
     *
     * @author SteveZMTstudios
     */
    fun canonicalArtistKey(name: String?): String? {
        return name?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
    }

    /**
     * Generates a deterministic, strictly negative synthetic artist ID for non-MediaStore artists.
     * Uses [canonicalArtistKey] to ensure consistent IDs regardless of casing or surrounding whitespace.
     *
     * @author SteveZMTstudios
     */
    fun syntheticArtistId(name: String?): Long {
        val canonicalKey = canonicalArtistKey(name)
        val hash = "$SYNTHETIC_ARTIST_PREFIX$canonicalKey".hashCode().toLong()
        val pos = if (hash == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(hash)
        return -(if (pos == 0L) 1L else pos)
    }

    /**
     * Resolves an artist name to an ID by querying [artistCacheMap] with [canonicalArtistKey].
     * Falls back to [fallbackId] if provided and cache misses, and finally to [syntheticArtistId].
     *
     * @author SteveZMTstudios
     */
    fun resolveArtistId(
        artistCacheMap: Map<String, Long>?,
        name: String?,
        fallbackId: Long? = null
    ): Long {
        val key = canonicalArtistKey(name)
        return (if (key != null) artistCacheMap?.get(key) else null)
            ?: fallbackId
            ?: syntheticArtistId(name)
    }

    /**
     * Builds a canonical artist cache map from raw name-to-ID mappings.
     * Normalizes all keys using [canonicalArtistKey].
     *
     * @author SteveZMTstudios
     */
    fun buildArtistCache(rawMappings: Map<String, Long>): MutableMap<String, Long> {
        val cache = mutableMapOf<String, Long>()
        for ((name, id) in rawMappings) {
            val canonicalKey = canonicalArtistKey(name)
            if (canonicalKey != null) {
                cache.putIfAbsent(canonicalKey, id)
            }
        }
        return cache
    }

    /**
     * Builds an artist cache map from existing songs, mapping canonical artist names to MediaStore artist IDs.
     *
     * @author SteveZMTstudios
     */
    fun buildArtistCacheMap(songs: List<MediaItem>): MutableMap<String, Long> {
        val cache = mutableMapOf<String, Long>()
        for (song in songs) {
            val raw = song.mediaMetadata.rawArtist ?: song.mediaMetadata.artist?.toString()
            val canonicalKey = canonicalArtistKey(raw)
            val artistId = try { song.mediaMetadata.artistId } catch (_: Throwable) { null }
            if (canonicalKey != null && artistId != null) {
                cache.putIfAbsent(canonicalKey, artistId)
            }
        }
        return cache
    }

    /**
     * Aggregates songs into an artist map using a two-pass resolution against [artistCacheMap].
     * Pure function free of Android framework dependencies.
     *
     * @author SteveZMTstudios
     */
    fun aggregateArtistsInto(
        targetMap: MutableMap<Long?, Artist>,
        songs: List<MediaItem>,
        artistCacheMap: Map<String, Long>?,
        tagSplitConfig: TagSplitConfig
    ): MutableMap<Long?, Artist> {
        for (song in songs) {
            val artistNames = if (tagSplitConfig.isMultiArtistEnabled) {
                val extrasList = song.mediaMetadata.artistNames
                if (extrasList.size > 1) {
                    extrasList
                } else {
                    val raw = song.mediaMetadata.rawArtist ?: song.mediaMetadata.artist?.toString()
                    TagSplitter.splitArtists(raw, tagSplitConfig).ifEmpty { extrasList }
                }
            } else {
                emptyList()
            }

            if (tagSplitConfig.isMultiArtistEnabled && artistNames.isNotEmpty()) {
                for (singleArtist in artistNames) {
                    val singleArtistId = resolveArtistId(artistCacheMap, singleArtist)
                    val artistObj = targetMap.getOrPut(singleArtistId) {
                        Artist(singleArtistId, singleArtist, mutableListOf(), mutableListOf())
                    }
                    (artistObj.songList as MutableList).add(song)
                }
            } else {
                val rawArtist = song.mediaMetadata.rawArtist?.trim()
                val singleArtistId = resolveArtistId(
                    artistCacheMap,
                    rawArtist,
                    fallbackId = song.mediaMetadata.artistId
                )
                val artistObj = targetMap.getOrPut(singleArtistId) {
                    Artist(singleArtistId, rawArtist, mutableListOf(), mutableListOf())
                }
                (artistObj.songList as MutableList).add(song)
            }
        }
        return targetMap
    }

    /**
     * Pure function variant returning a new artist map.
     *
     * @author SteveZMTstudios
     */
    fun aggregateArtists(
        songs: List<MediaItem>,
        artistCacheMap: Map<String, Long>?,
        tagSplitConfig: TagSplitConfig
    ): MutableMap<Long?, Artist> {
        return aggregateArtistsInto(mutableMapOf(), songs, artistCacheMap, tagSplitConfig)
    }

    internal fun findBestAlbumArtist(songs: List<MediaItem>): Pair<String, Long?>? {
        val foundAlbumArtists = songs.groupBy { it.mediaMetadata.albumArtist?.toString() }
            .mapValues { it.value.size }
        if (foundAlbumArtists.size > 2
            || (foundAlbumArtists.size == 2 && !foundAlbumArtists.containsKey(null))
        ) {
            Log.w(
                "libPhonograph", "Odd, album artists: $foundAlbumArtists for one album exceed 1, " +
                        "MediaStore usually doesn't do that"
            )
            return null
        }
        val theAlbumArtist = foundAlbumArtists.keys.find { it != null }
        if (theAlbumArtist != null) {
            // We got at least one album artist tag.
            val countWithNull = foundAlbumArtists[null] ?: 0
            val countWithAlbumArtist = foundAlbumArtists[theAlbumArtist]!!
            if (countWithAlbumArtist < countWithNull) return null
            return Pair(
                theAlbumArtist,
                // If the album artist made some song on the album, using the ID from the song will be
                // more accurate than just using any ID which matches the name. Well, it's a best guess.
                songs.firstOrNull { it.mediaMetadata.artist == theAlbumArtist }?.mediaMetadata?.artistId
            )
        } else {
            // Meh, let's guess based on artist tag.
            val bestMatch =
                songs.groupBy { it.mediaMetadata.artist?.toString() }.maxByOrNull { it.value.size }
            if (bestMatch?.key == null) return null
            // If less than 60% of songs have said artist, we can't reasonably assume the best match
            // is the actual album artist.
            if ((bestMatch.value.size.toFloat() / songs.size) < 0.6f) return null
            // Let's go with this.
            return Pair(bestMatch.key!!, bestMatch.value.first().mediaMetadata.artistId)
        }
    }

    internal data class AlbumImpl(
        override val id: Long?,
        override val title: String?,
        override var albumArtist: String?,
        override var albumArtistId: Long?,
        override var cover: Uri?,
        override var albumYear: Int?,
        override var albumAddDate: Long?,
        override var albumModifiedDate: Long?,
        override val songList: MutableList<MediaItem>
    ) : Album
}