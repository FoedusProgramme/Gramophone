package uk.akane.libphonograph.dynamicitem

import androidx.media3.common.MediaItem
import uk.akane.libphonograph.items.Playlist
import java.io.File

class Favorite(id: Long, path: File?, dateAdded: Long?, dateModified: Long?,
    songList: List<MediaItem>) : Playlist(id, null, path, dateAdded, dateModified, songList)