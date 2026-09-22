/*
 *     Copyright (C) 2025 nift4
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

package org.akanework.gramophone.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.replaceAllSupport
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.GramophoneTheme
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.compose.MqState
import org.akanework.gramophone.ui.fragments.compose.QueueRoot
import org.akanework.gramophone.ui.fragments.compose.rememberMqState

// TODO:
//  queue menu flickers when queue sheet isnt full height
class PlaylistQueueSheet(
    context: Context, private val activity: MainActivity
) : BottomSheetDialog(context), Player.Listener {
    private val instance: MediaBrowser?
        get() = activity.getPlayer()
    val playlistAdapter: PlaylistCardAdapter
    private val touchHelper: ItemTouchHelper
    private val recyclerView: RecyclerView
    val durationView: Chronometer
    private val queueHead: ComposeView
    private val mqEnabled: Boolean
    private var mqState: MqState? = null
    private var lockEdit = MutableStateFlow(false)

    init {
        val prefs = context.defaultPrefs
        mqEnabled = Flags.MQ_PREVIEW && prefs.getBooleanStrict("mq_preview", false)

        setContentView(R.layout.playlist_bottom_sheet)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        if (mqEnabled) {
            behavior.maxWidth = 900.dpToPx(context)
        }

        recyclerView = findViewById<MyRecyclerView>(R.id.recyclerview)!!

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { v, ic ->
            val i = ic.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.navigationBars()
                        or WindowInsetsCompat.Type.displayCutout()
            )
            val i2 = ic.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.systemBars()
                        or WindowInsetsCompat.Type.navigationBars()
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
        recyclerView.fastScroll(null, null)

        durationView = Chronometer(context)
        durationView.isCountDown = true

        queueHead = findViewById(R.id.queue_head)!!
        queueHead.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val coroutineScope = rememberCoroutineScope()

                // TODO: very inelegant.
                val pureDarkFlow by lazy {
                    callbackFlow {
                        val cb = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                            if (key == "pureDark") {
                                trySendBlocking(prefs.getBooleanStrict("pureDark", false))
                            }
                        }
                        prefs.registerOnSharedPreferenceChangeListener(cb)
                        awaitClose {
                            prefs.unregisterOnSharedPreferenceChangeListener(cb)
                        }
                    }.stateIn(
                        lifecycleScope, WhileSubscribed(),
                        prefs.getBooleanStrict("pureDark", false)
                    )
                }
                val pureDark by pureDarkFlow.collectAsState()

                GramophoneTheme(
                    pureDark = pureDark
                ) {
                    val mqState =
                        rememberMqState(
                            coroutineScope, activity, this@PlaylistQueueSheet,
                        )
                    this@PlaylistQueueSheet.mqState = mqState
                    val pagerState = rememberPagerState(
                        initialPage = if (Flags.MQ_PREVIEW) 0 else 1,
                        pageCount = { 2 }
                    )

                    QueueRoot(
                        mqState = mqState,
                        pagerState = pagerState,
                        coroutineScope = coroutineScope,
                        durationView = durationView,
                        mqEnabled = mqEnabled,
                        onDismiss = { dismiss() },
                        onRecyclerScrollTo = {
                            mqState.playlist.first.indexOfFirst { i ->
                                i == (instance?.currentMediaItemIndex ?: 0)
                            }.takeIf { it != -1 }?.let { recyclerView.smoothScrollToPosition(it) }
                        }
                    )
                }
            }
        }

        activity.controllerViewModel.addRecreationalPlayerListener(lifecycle, this) {
            onMediaItemTransition(
                instance?.currentMediaItem,
                Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            )
            onIsPlayingChanged(instance?.isPlaying ?: false)
        }
    }

    override fun show() {
        super.show()
        val view = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        view!!.post {
            BottomSheetBehavior.from(view).state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: @Player.MediaItemTransitionReason Int
    ) {
        if (mqState == null || mqState?.isDetached() == true) return
        val i = instance?.currentMediaItemIndex
        playlistAdapter.currentMediaItemIndex = i?.let { mqState!!.playlist.first.indexOf(i) }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: @Player.DiscontinuityReason Int
    ) {
        if (mqState == null || mqState?.isDetached() == true) return
        mqState!!.updateTimer()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playlistAdapter.currentIsPlaying = isPlaying
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: @Player.TimelineChangeReason Int
    ) {
        // TODO: support listening to externally caused changes to playlist (ie MCT).
        // playlistAdapter.updateList()
    }

    fun lockQueue(lock: Boolean) {
        lockEdit.value = lock
    }

    fun scrollToPositionWithOffsetCompat(position: Int, offset: Int) {
        (recyclerView as MyRecyclerView).scrollToPositionWithOffsetCompat(position, offset)
    }

    fun smoothScrollToCurrentPosition(position: Int) = recyclerView.post {
        position.takeIf { it != -1 }?.let { recyclerView.smoothScrollToPosition(it) }
    }

    fun setCurrentMediaItemIndex(currentMediaItemIndex: Int) {
        recyclerView.post {
            playlistAdapter.currentMediaItemIndex = currentMediaItemIndex
        }
    }

    inner class PlaylistCardAdapter : EditSongAdapter(activity, true, lockEdit) {
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
                    mqState?.updateTimer()
                    if (value != null && currentMediaItemIndex != null) {
                        currentMediaItemIndex?.let {
                            notifyItemChanged(it, false)
                        }
                    }
                }
            }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any?>) {
            holder.dragHandle.visibility = if (mqState!!.isDetached()) View.GONE else View.VISIBLE
            holder.closeButton.visibility = if (mqState!!.isDetached()) View.GONE else View.VISIBLE
            if (payloads.isNotEmpty()) {
                if (payloads.none { it is Boolean && it }) {
                    holder.nowPlaying.drawable?.level =
                        if (!mqState!!.isDetached() && currentIsPlaying == true) 1 else 0
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
            if (holder.nowPlaying.visibility != View.VISIBLE) {
                holder.nowPlaying.setImageDrawable(
                    NowPlayingDrawable(holder.itemView.context)
                        .also {
                            it.level =
                                if (!mqState!!.isDetached() && currentIsPlaying == true) 1 else 0
                        })
                holder.nowPlaying.visibility = View.VISIBLE
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = null
            holder.nowPlaying.setImageDrawable(null)
            holder.nowPlaying.visibility = View.GONE
            holder.dragHandle.visibility = if (mqState!!.isDetached()) View.GONE else View.VISIBLE
            holder.closeButton.visibility = if (mqState!!.isDetached()) View.GONE else View.VISIBLE
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int =
            if (mqState!!.playlist.first.size != mqState!!.playlist.second.size)
                throw IllegalStateException()
            else mqState!!.playlist.first.size

        override fun onClick(pos: Int) {
            if (mqState!!.isDetached()) {
                mqState!!.detachedQueue?.let {
                    mqState!!.loadDetached(mqState!!.playlist.first[pos])
                }
            } else {
                instance?.seekToDefaultPosition(mqState!!.playlist.first[pos])
            }
        }

        override fun onRowMoved(from: Int, to: Int) {
            val mediaController = activity.getPlayer()
            val from1 = mqState!!.playlist.first.removeAt(from)
            mqState!!.playlist.first.replaceAllSupport { if (it > from1) it - 1 else it }
            val movedItem = mqState!!.playlist.second.removeAt(from1)
            val to1 = if (to > 0) mqState!!.playlist.first[to - 1] + 1 else 0
            mqState!!.playlist.first.replaceAllSupport { if (it >= to1) it + 1 else it }
            mqState!!.playlist.first.add(to, to1)
            mqState!!.playlist.second.add(to1, movedItem)
            mediaController?.moveMediaItem(from1, to1)
            notifyItemMoved(from, to)
            val currentIndex = currentMediaItemIndex
            if (currentIndex != null) {
                if (currentIndex == from)
                    currentMediaItemIndex = to
                else if (from < to && from < currentIndex && currentIndex <= to)
                    currentMediaItemIndex = currentIndex - 1
                else if (from > to && to <= currentIndex && currentIndex < from)
                    currentMediaItemIndex = currentIndex + 1
            }
            mqState!!.updateTimer() // TODO: this could be more efficient
        }

        override fun removeItem(pos: Int) {
            val instance = activity.getPlayer()

            // remove queue if this is the last item, dismiss if no queues left
            if (mqState!!.playlist.first.size <= 1) {
                mqState!!.removeQueue()
                return
            }

            val idx = mqState!!.playlist.first.removeAt(pos)
            mqState!!.playlist.first.replaceAllSupport { if (it > idx) it - 1 else it }
            instance?.removeMediaItem(idx)
            mqState!!.playlist.second.removeAt(idx)

            notifyItemRemoved(pos)
            if (pos == currentMediaItemIndex) {
                notifyItemChanged(currentMediaItemIndex!!, true)
            } else if (pos < (currentMediaItemIndex ?: -1)) {
                currentMediaItemIndex = currentMediaItemIndex!! - 1
            }
            mqState!!.updateTimer() // TODO: this could be more efficient
        }

        override fun getItem(pos: Int) = mqState!!.playlist.second[mqState!!.playlist.first[pos]]
        override fun startDrag(holder: ViewHolder) {
            touchHelper.startDrag(holder)
        }
    }
}
