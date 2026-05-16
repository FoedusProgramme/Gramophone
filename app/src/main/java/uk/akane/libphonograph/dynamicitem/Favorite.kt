package uk.akane.libphonograph.dynamicitem

import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import uk.akane.libphonograph.items.Playlist

class Favorite(songList: List<MediaItem>) : Playlist(null, null, null, null, null) {
    override val songList: List<MediaItem> = songList
        .filter { (it.mediaMetadata.userRating as? HeartRating)?.isHeart == true }
}