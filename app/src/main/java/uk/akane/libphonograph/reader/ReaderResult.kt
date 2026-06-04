package uk.akane.libphonograph.reader

import androidx.media3.common.MediaItem
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Artist
import uk.akane.libphonograph.items.Date
import uk.akane.libphonograph.items.EmptyFileNode
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.items.Genre
import uk.akane.libphonograph.items.Playlist

data class ReaderResult(
    val songList: List<MediaItem>,
    val albumList: List<Album>?,
    val albumArtistList: List<Artist>?,
    val artistList: List<Artist>?,
    val genreList: List<Genre>?,
    val dateList: List<Date>?,
    val idMap: Map<Long, MediaItem>?,
    val pathMap: Map<String, MediaItem>?,
    val folderStructure: FileNode?,
    val shallowFolder: FileNode?,
    val folders: Set<String>?,
    val foldersForWhitelist: Set<String>?
) {
    companion object {
        fun emptyReaderResult() = ReaderResult(
            listOf(), listOf(), listOf(), listOf(), listOf(), listOf(),
            mapOf(), mapOf(),
            EmptyFileNode, EmptyFileNode,
            setOf(), setOf()
        )
    }
}

data class SimpleReaderResult(
    val songList: List<MediaItem>,
    val albumList: List<Album>,
    val albumArtistList: List<Artist>,
    val artistList: List<Artist>,
    val genreList: List<Genre>,
    val dateList: List<Date>,
    val playlistList: List<Playlist>,
    val folderStructure: FileNode,
    val shallowFolder: FileNode,
    val folders: Set<String>,
    val foldersForWhitelist: Set<String>
)
