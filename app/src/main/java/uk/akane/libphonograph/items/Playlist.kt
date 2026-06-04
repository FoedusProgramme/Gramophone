package uk.akane.libphonograph.items

import androidx.media3.common.MediaItem
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.PlaylistSerializer
import java.io.File

open class Playlist protected constructor(
    override val id: Long?,
    override val title: String?,
    val path: File?,
    val dateAdded: Long?,
    val dateModified: Long?,
) : Item {
    private var _songList: List<MediaItem>? = null

    constructor(
        id: Long?, title: String?, path: File?, dateAdded: Long?, dateModified: Long?,
        songList: List<MediaItem>
    ) : this(id, title, path, dateAdded, dateModified) {
        _songList = songList
    }

    override val songList: List<MediaItem>
        get() = _songList ?: throw IllegalStateException(
            "code bug: Playlist subclass used " +
                    "protected constructor but did not override songList"
        )

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + songList.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Playlist

        if (id != other.id) return false
        if (title != other.title) return false
        if (songList != other.songList) return false

        return true
    }
}

internal data class RawPlaylist(
    val id: Long,
    val title: String?,
    val path: File?,
    val dateAdded: Long?,
    val dateModified: Long?,
    val entries: List<PlaylistSerializer.Entry>?,
) {
    // pathMap may be null if and only if all playlists are empty
    fun toPlaylist(pathMap: Map<String, MediaItem>?): Playlist? {
        // Unsupported format and MediaStore can't read it either (ex: smpl), don't display at all
        if (entries == null) return null
        if (title == ItemManipulator.FAVORITES)
            return Favorite(id, path, dateAdded, dateModified, entries.mapNotNull {
                it?.resolveMediaItem(pathMap)
            })
        return Playlist(id, title, path, dateAdded, dateModified, entries.mapNotNull {
            it?.resolveMediaItem(pathMap)
        })
    }
}