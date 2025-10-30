package org.akanework.gramophone.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R

class TrackInfoAdapter(private val context: Context) :
    RecyclerView.Adapter<TrackInfoAdapter.TrackViewHolder>() {
    private var mediaItems = listOf<MediaItem>()
    private var shuffleIndices: List<Int>? = null
    private var currentPlayingPosition = 0

    fun updatePlaylist(controller: MediaController?) {
        val newItems = if (controller != null && controller.mediaItemCount > 0) {
            val timeline = controller.currentTimeline
            val shuffleEnabled = controller.shuffleModeEnabled
            val items = mutableListOf<MediaItem>()
            val indices = if (shuffleEnabled) mutableListOf<Int>() else null

            var index = timeline.getFirstWindowIndex(shuffleEnabled)
            var position = 0

            while (index != C.INDEX_UNSET) {
                items.add(controller.getMediaItemAt(index))
                indices?.add(index)
                if (index == controller.currentMediaItemIndex) {
                    currentPlayingPosition = position
                }
                index = timeline.getNextWindowIndex(
                    index,
                    Player.REPEAT_MODE_OFF,
                    shuffleEnabled)
                position++
            }

            shuffleIndices = indices
            items
        } else {
            currentPlayingPosition = 0
            shuffleIndices = null
            emptyList()
        }

        val diffCallback = TrackDiffCallback(mediaItems, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        mediaItems = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    fun getCurrentPosition(): Int = currentPlayingPosition

    fun getCurrentIndex(pos: Int) = shuffleIndices?.getOrNull(pos) ?: pos

    override fun getItemCount() = mediaItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_track_info, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        holder.bind(mediaItems[position])
    }

    inner class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.track_title)
        private val artistView: TextView = view.findViewById(R.id.track_artist)

        fun bind(mediaItem: MediaItem) {
            titleView.text = mediaItem.mediaMetadata.title
            artistView.text = mediaItem.mediaMetadata.artist
                ?: context.getString(R.string.unknown_artist)
        }
    }

    private class TrackDiffCallback(
        private val oldList: List<MediaItem>,
        private val newList: List<MediaItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].mediaId == newList[newItemPosition].mediaId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem.mediaMetadata.title == newItem.mediaMetadata.title &&
                    oldItem.mediaMetadata.artist == newItem.mediaMetadata.artist
        }
    }
}