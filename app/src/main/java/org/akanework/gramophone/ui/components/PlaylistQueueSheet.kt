package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getQueueForUi
import org.akanework.gramophone.logic.replaceAllSupport
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.utils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.GramophoneTheme
import org.akanework.gramophone.ui.MainActivity
import java.util.LinkedList

// TODO: support listening to externally caused changes to playlist (ie MCT).
class PlaylistQueueSheet(
    context: Context, private val activity: MainActivity
) : BottomSheetDialog(context), Player.Listener {
    private var prefs: SharedPreferences
    private val instance: MediaBrowser?
        get() = activity.getPlayer()
    private val playlistAdapter: PlaylistCardAdapter
    private val touchHelper: ItemTouchHelper
    private val queueHead: ComposeView

    private val durationState = mutableLongStateOf(-1)
    private val mqEnabled: Boolean
    private var detachedHead: Boolean = false

    init {
        prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        mqEnabled = Flags.MQ_PREVIEW && prefs.getBooleanStrict("mq_preview", false)

        setContentView(R.layout.playlist_bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
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
        recyclerView.scrollToPositionWithOffsetCompat(
            playlistAdapter.playlist.first.indexOfFirst { i ->
                i == (instance?.currentMediaItemIndex ?: 0)
            }, // quick UX hack to show there's more songs above (well, if there is).
            (context.resources.getDimensionPixelOffset(R.dimen.list_height) * 0.5f).toInt()
        )
        recyclerView.fastScroll(null, null)
        recyclerView.isNestedScrollingEnabled = false // required for queueHead list scrolling
        activity.controllerViewModel.addRecreationalPlayerListener(lifecycle, this) {
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            onIsPlayingChanged(instance?.isPlaying ?: false)
        }

        queueHead = findViewById(R.id.queue_head)!!
        queueHead.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                @Composable
                fun HeadActions(
                    modifier: Modifier = Modifier
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { context ->
                                val layout = LayoutInflater.from(context)
                                    .inflate(R.layout.playlist_bottom_sheet_actions, null)
                                val durationView: Chronometer = layout.findViewById(R.id.duration)!!
                                durationView.isCountDown = true

                                layout.findViewById<Button>(R.id.clearQueue)!!
                                    .setOnClickListener {
                                        dismiss()
                                        instance?.clearMediaItems()
                                    }
                                layout.findViewById<Button>(R.id.scrollToPlaying)!!
                                    .setOnClickListener {
                                        recyclerView.smoothScrollToPosition(
                                            playlistAdapter.playlist.first.indexOfFirst { i ->
                                                i == (instance?.currentMediaItemIndex ?: 0)
                                            })
                                    }

                                playlistAdapter.updateList()
                                durationView.start()
                                setOnDismissListener {
                                    durationView.stop()
                                }

                                layout
                            },
                            update = { view ->
                                val durationBase = durationState.longValue
                                if (detachedHead) return@AndroidView // chromometer is never shown for inactive queues
                                val durationView: Chronometer = view.findViewById(R.id.duration)
                                val pl = playlistAdapter.playlist


                                durationView.format = context.getString(
                                    R.string.duration_queue,
                                    "%s",
                                    pl.second.sumOf { it.mediaMetadata.durationMs ?: 0L }
                                        .convertDurationToTimeStamp(true))
                                if (instance?.isPlaying == true) {
                                    durationView.start()
                                } else {
                                    durationView.stop()
                                }
                                durationView.base = durationBase
                            }
                        )
                    }
                }

                val coroutineScope = rememberCoroutineScope()
                val haptic = LocalHapticFeedback.current

                GramophoneTheme(
                    pureDark = false, // TODO: I don't want to do this rn. Does not respect light/dark mode
                ) {
                    val mqState =
                        rememberMqState(coroutineScope, instance, this@PlaylistQueueSheet)
                    val pagerState = rememberPagerState(pageCount = { 2 })

                    LaunchedEffect(mqState) {
                        detachedHead = mqState.isDetached()
                    }

                    LaunchedEffect(mqState.detachedQueue) {
                        coroutineScope.launch {
                            if (mqState.isDetached()) {
                                playlistAdapter.currentMediaItemIndex =
                                    mqState.detachedQueue?.startIndex
                                recyclerView.smoothScrollToPosition(
                                    playlistAdapter.playlist.first.indexOfFirst { i ->
                                        i == (mqState.detachedQueue?.startIndex ?: 0)
                                    })
                            } else {
                                playlistAdapter.currentMediaItemIndex =
                                    instance?.currentMediaItemIndex.let {
                                        playlistAdapter.playlist.first.indexOf(
                                            it
                                        )
                                    }
                                recyclerView.smoothScrollToPosition(
                                    playlistAdapter.playlist.first.indexOfFirst { i ->
                                        i == (instance?.currentMediaItemIndex ?: 0)
                                    })
                            }
                        }
                    }

                    Box {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp),
                            beyondViewportPageCount = 1,
                            userScrollEnabled = !mqState.expanded
                        ) { page ->
                            when (page) {
                                0 -> {
                                    MqContent(
                                        mqState = mqState,
                                        mqEnabled = mqEnabled
                                    )
                                }

                                1 -> {
                                    HeadActions()
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .wrapContentHeight()
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .alpha(if (!mqState.expanded) 1f else 0f)
                        ) {
                            repeat(pagerState.pageCount) { iteration ->
                                val color = if (pagerState.currentPage == iteration) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .size(8.dp)
                                        .clickable(
                                            onClick = {
                                                coroutineScope.launch {
                                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                    pagerState.animateScrollToPage(iteration)
                                                }
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: @Player.MediaItemTransitionReason Int
    ) {
        val i = instance?.currentMediaItemIndex
        playlistAdapter.currentMediaItemIndex = i?.let { playlistAdapter.playlist.first.indexOf(i) }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: @Player.DiscontinuityReason Int
    ) {
        playlistAdapter.updateTimer()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playlistAdapter.currentIsPlaying = isPlaying
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: @Player.TimelineChangeReason Int
    ) {
        playlistAdapter.updateList()
    }

    /**
     * Force a full update of playlist and timer
     *
     * @param mq Inactive queue index. Set to -1 to load the active queue
     */
    fun forceUpdate(mq: Int = -1) {
        playlistAdapter.updateList(mq)
    }

    private inner class PlaylistCardAdapter : EditSongAdapter(activity) {
        var playlist: Pair<MutableList<Int>, MutableList<MediaItem>> = dumpPlaylist()
        var currentMediaItemIndex: Int? = null
            set(value) {
                if (field != value) {
                    val oldValue = field
                    field = value

                    if (oldValue != null) {
                        notifyItemChanged(oldValue, true)
                    }
                    if (value != null) {
                        notifyItemChanged(value, true)
                    }
                }
            }
        var currentIsPlaying: Boolean? = null
            set(value) {
                if (field != value) {
                    field = value
                    updateTimer()
                    if (value != null && currentMediaItemIndex != null) {
                        currentMediaItemIndex?.let {
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
                if (currentMediaItemIndex == null || position != currentMediaItemIndex) {
                    (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = Runnable {
                        holder.nowPlaying.visibility = View.GONE
                        holder.nowPlaying.setImageDrawable(null)
                    }
                    holder.nowPlaying.drawable?.level = 2
                    return
                }
            } else {
                super.onBindViewHolder(holder, position, payloads)
                if (currentMediaItemIndex == null || position != currentMediaItemIndex)
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
            updateList() // TODO: this could be more efficient
        }

        override fun removeItem(pos: Int) {
            val instance = activity.getPlayer()
            val idx = playlist.first.removeAt(pos)
            playlist.first.replaceAllSupport { if (it > idx) it - 1 else it }
            instance?.removeMediaItem(idx)
            playlist.second.removeAt(idx)
            notifyItemRemoved(pos)
            updateList() // TODO: this could be more efficient
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

        /**
         * Update playlist and timer
         */
        fun updateList(mqIndex: Int? = null) {
            val mq = mqIndex?.let { instance?.getQueueForUi(mqIndex) }
            val pl = if (mq != null) Pair(mq.first, mq.second.queue) else dumpPlaylist()
            playlist = pl
            notifyDataSetChanged()
            updateTimer(mq?.second?.startIndex, mq?.second?.startPositionMs)
        }

        fun updateTimer(currentMediaItemIndex: Int? = null, currentPosition: Long? = null) {
            val current = (currentMediaItemIndex ?: instance?.currentMediaItemIndex ?: 0)
            val elapsedCurrentMs = (currentPosition ?: instance?.currentPosition ?: 0)
            durationState.longValue = SystemClock.elapsedRealtime() +
                    playlist.second.subList(current, playlist.second.size)
                        .sumOf {
                            it.mediaMetadata.durationMs ?: 0L
                        } - elapsedCurrentMs + 1000
        }
    }
}
