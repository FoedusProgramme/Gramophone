package uk.akane.libphonograph.items

import androidx.media3.common.MediaItem
import java.io.File
import kotlin.math.max

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
    val idList: List<Long?>?,
    val pathList: List<File?>?,
) {
    // idMap may be null if and only if all playlists are empty
    fun toPlaylist(idMap: Map<Long, MediaItem>?, pathMap: Map<String, MediaItem>?): Playlist {
        val result = if (pathList != null) {
            val tmp = arrayOfNulls<MediaItem?>(pathList.size)
            for (i in 0..<tmp.size) {
                // if we have a path and it's not in the map, something's weird. but it's not a
                // crash-worthy offense
                tmp[i] = pathList.getOrNull(i)?.let { pathMap!![it.absolutePath] }
            }
            if (tmp.contains(null)) tmp.filterNotNull() else
                (@Suppress("UNCHECKED_CAST") (tmp as Array<MediaItem>)).toList()
        } else {
            val tmp = arrayOfNulls<MediaItem?>(idList!!.size)
            for (i in 0..<tmp.size) {
                // if we have an id and it's not in the map, something's weird. but it's not a
                // crash-worthy offense
                tmp[i] = if (idList.getOrNull(i) != null) idMap!![idList[i]!!] else null
            }
            if (tmp.contains(null)) tmp.filterNotNull() else
                (@Suppress("UNCHECKED_CAST") (tmp as Array<MediaItem>)).toList()
        }
        return Playlist(id, title, path, dateAdded, dateModified, result)
    }
}