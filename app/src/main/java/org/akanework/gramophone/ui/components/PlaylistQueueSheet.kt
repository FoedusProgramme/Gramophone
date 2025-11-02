package org.akanework.gramophone.ui.components

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.replaceAllSupport
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.MainActivity
import java.util.LinkedList

// TODO: support listening to externally caused changes to playlist (ie MCT).
class PlaylistQueueSheet(
    context: Context, private val activity: MainActivity
) : BottomSheetDialog(context), Player.Listener {
    private val instance: MediaBrowser?
        get() = activity.getPlayer()
    private val playlistAdapter: PlaylistCardAdapter
    private val touchHelper: ItemTouchHelper
    private val durationView: TextView

    init {
        setContentView(R.layout.playlist_bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        durationView = findViewById(R.id.duration)!!
        val recyclerView = findViewById<MyRecyclerView>(R.id.recyclerview)!!
        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, ic ->
            val i = ic.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            val i2 = ic.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(i.left, 0, i.right, i.bottom)
            return@setOnApplyWindowInsetsListener WindowInsetsCompat.Builder(ic)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(),
                    Insets.of(0, i.top, 0, 0)
                )
                .setInsetsIgnoringVisibility(
                    WindowInsetsCompat.Type.systemBars()
                            or WindowInsetsCompat.Type.displayCutout(),
                    Insets.of(0, i2.top, 0, 0)
                )
                .build()
        }
        playlistAdapter = PlaylistCardAdapter()
        val callback = playlistAdapter.PlaylistCardMoveCallback()
        touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = playlistAdapter
        recyclerView.scrollToPositionWithOffsetCompat(playlistAdapter.playlist.first.indexOfFirst { i ->
            i == (instance?.currentMediaItemIndex ?: 0)
        }, // quick UX hack to show there's more songs above (well, if there is).
            (context.resources.getDimensionPixelOffset(R.dimen.list_height) * 0.5f).toInt())
        recyclerView.fastScroll(null, null)
        findViewById<Button>(R.id.scrollToPlaying)!!.setOnClickListener {
            recyclerView.smoothScrollToPosition(playlistAdapter.playlist.first.indexOfFirst { i ->
                i == (instance?.currentMediaItemIndex ?: 0)
            })
        }
        activity.controllerViewModel.addRecreationalPlayerListener(lifecycle, this) {
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            onIsPlayingChanged(instance?.isPlaying ?: false)
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: @Player.MediaItemTransitionReason Int
    ) {
        playlistAdapter.currentMediaItem = mediaItem?.mediaId
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playlistAdapter.currentIsPlaying = isPlaying
    }

    private inner class PlaylistCardAdapter : EditSongAdapter(activity) {
        var playlist: Pair<MutableList<Int>, MutableList<MediaItem>> = dumpPlaylist()
        private var idToPosMap: HashMap<String, Int>? = null

        init {
            updateList()
        }
        var currentMediaItem: String? = null
            set(value) {
                if (field != value) {
                    val oldValue = field
                    field = value
                    if (idToPosMap != null) {
                        val oldPos = idToPosMap!![oldValue]
                        val newPos = idToPosMap!![value]
                        if (oldPos != null) {
                            notifyItemChanged(oldPos, true)
                        }
                        if (newPos != null) {
                            notifyItemChanged(newPos, true)
                        }
                    }
                }
            }
        var currentIsPlaying: Boolean? = null
            set(value) {
                if (field != value) {
                    field = value
                    if (value != null && currentMediaItem != null) {
                        idToPosMap?.get(currentMediaItem)?.let {
                            notifyItemChanged(it, false)
                        }
                    }
                }
            }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any?>) {
            if (payloads.isNotEmpty()) {
                if (payloads.none { it is Boolean && it }) {
                    holder.nowPlaying.drawable?.level = if (currentIsPlaying == true) 1 else 0
                    return
                }
                if (currentMediaItem == null || playlist.second[playlist.first[position]].mediaId != currentMediaItem) {
                    (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = Runnable {
                        holder.nowPlaying.visibility = View.GONE
                        holder.nowPlaying.setImageDrawable(null)
                    }
                    holder.nowPlaying.drawable?.level = 2
                    return
                }
            } else {
                super.onBindViewHolder(holder, position, payloads)
                if (currentMediaItem == null || playlist.second[playlist.first[position]].mediaId != currentMediaItem)
                    return
            }
            holder.nowPlaying.setImageDrawable(
                NowPlayingDrawable(holder.itemView.context)
                    .also { it.level = if (currentIsPlaying == true) 1 else 0 })
            holder.nowPlaying.visibility = View.VISIBLE
        }

        override fun onViewRecycled(holder: ViewHolder) {
            (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = null
            holder.nowPlaying.setImageDrawable(null)
            holder.nowPlaying.visibility = View.GONE
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = if (playlist.first.size != playlist.second.size)
            throw IllegalStateException()
        else playlist.first.size

        override fun onClick(pos: Int) {
            instance?.seekToDefaultPosition(playlist.first[pos])
        }

        override fun onRowMoved(from: Int, to: Int) {
            val mediaController = activity.getPlayer()
            val from1 = playlist.first.removeAt(from)
            playlist.first.replaceAllSupport { if (it > from1) it - 1 else it }
            val movedItem = playlist.second.removeAt(from1)
            val to1 = if (to > 0) playlist.first[to - 1] + 1 else 0
            playlist.first.replaceAllSupport { if (it >= to1) it + 1 else it }
            playlist.first.add(to, to1)
            playlist.second.add(to1, movedItem)
            mediaController?.moveMediaItem(from1, to1)
            notifyItemMoved(from, to)
        }

        override fun removeItem(pos: Int) {
            val instance = activity.getPlayer()
            val idx = playlist.first.removeAt(pos)
            playlist.first.replaceAllSupport { if (it > idx) it - 1 else it }
            instance?.removeMediaItem(idx)
            playlist.second.removeAt(idx)
            notifyItemRemoved(pos)
            updateList()
        }

        override fun getItem(pos: Int) = playlist.second[playlist.first[pos]]
        override fun startDrag(holder: ViewHolder) {
            touchHelper.startDrag(holder)
        }

        private fun dumpPlaylist(): Pair<MutableList<Int>, MutableList<MediaItem>> {
            val items = LinkedList<MediaItem>()
            val instance = activity.getPlayer()!!
            for (i in 0 until instance.mediaItemCount) {
                items.add(instance.getMediaItemAt(i))
            }
            val indexes = LinkedList<Int>()
            val s = instance.shuffleModeEnabled
            var i = instance.currentTimeline.getFirstWindowIndex(s)
            while (i != C.INDEX_UNSET) {
                indexes.add(i)
                i = instance.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, s)
            }
            return Pair(indexes, items)
        }

        private fun updateList() {
            idToPosMap = hashMapOf()
            playlist.second.forEachIndexed { i, item -> idToPosMap!![item.mediaId] = playlist.first.indexOf(i) }
            durationView.text = context.getString(R.string.duration_queue,
                (playlist.second.sumOf { it.mediaMetadata.durationMs ?: 0L } / 60)
                .convertDurationToTimeStamp())
        }
    }
}