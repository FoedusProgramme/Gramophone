package org.akanework.gramophone.ui.fragments.compose

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_GET_INACTIVE_LIST
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.SERVICE_QB_GET_QUEUE_FOR_UI
import org.akanework.gramophone.logic.MultiQueueList
import org.akanework.gramophone.logic.MultiQueueObject
import org.akanework.gramophone.logic.age
import org.akanework.gramophone.logic.deleteQueue
import org.akanework.gramophone.logic.getInactiveQueues
import org.akanework.gramophone.logic.getQueueForUi
import org.akanework.gramophone.logic.loadQueue
import org.akanework.gramophone.logic.pinQueue
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.renameQueue
import org.akanework.gramophone.logic.replaceAllSupport
import org.akanework.gramophone.logic.showsPause
import org.akanework.gramophone.logic.supportsWideScreen
import org.akanework.gramophone.logic.unpinQueue
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.compose.QueueDropdownMenu
import org.akanework.gramophone.ui.components.compose.TintedIcon
import java.util.LinkedList

@Composable
fun MqListItem(
    mqState: MqState,
//    queueListState: ReorderableLazyListState, // sh.calvin.reorderable.ReorderableLazyListState
    index: Int,
    mq: MultiQueueObject,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 4.dp,
    isActiveQueue: Boolean = false,
    isHighlightedQueue: Boolean = false,
    isEditAllowed: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val expiry = mq.expiry
    val isPinned = mq.expiry == null
    val isOriginal = mq.isOriginal
    val pinId = mq.id

    Row( // wrapper
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isHighlightedQueue) {
                    MaterialTheme.colorScheme.tertiary.copy(0.1f)
                } else {
                    Color.Transparent
                }
            )
            .combinedClickable(
//                    enabled = !inSelectMode,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row( // row contents (wrapper is needed for margin)
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, false)
            ) {
                if (isEditAllowed) {
                    if (isPinned) {
                        IconButton(
                            onClick = {
                                mqState.togglePin(pinId)
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_keep_off),
                                contentDescription = null
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                mqState.removeQueue(pinId)
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = null
                            )
                        }
                    }
                }
                Column(

                ) {
                    val titleText = if (isActiveQueue) {
                        mq.title
                    } else {
                        "${index + 1}. ${mq.title}"
                    }
                    val showId = Flags.MQ_ALWAYS_SHOW_QUEUE_ID ||
                            (mqState.inactiveQueues + mqState.activeQueue?.second)
                                .filterNotNull()
                                .any { it.id != mq.id && it.title == mq.title }

                    // title line
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                        if (showId) {
                            Text(
                                text = "(${mq.id})",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    // extras line
                    if (!isPinned || !isOriginal) {
                        Row(
                            horizontalArrangement =
                                if (!isPinned) Arrangement.SpaceBetween else Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            if (!isPinned) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val remainingTimeMs = (expiry!! - System.currentTimeMillis())
                                    Icon(
                                        painter = painterResource(
                                            if (!isActiveQueue && remainingTimeMs < 1800000) {
                                                R.drawable.ic_warning
                                            } else {
                                                R.drawable.ic_keep
                                            }
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable(onClick = {
                                                mqState.togglePin(pinId)
                                            }),
                                    )
                                    Text(
                                        text = if (isActiveQueue) {
                                            "∞"
                                        } else {
                                            convertDurationToTimeStamp(remainingTimeMs)
                                        },
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.MiddleEllipsis,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .clickable(onClick = {
                                                mqState.togglePin(pinId)
                                            }),
                                    )
                                }
                            }

                            if (!isOriginal) {
                                Text(
                                    text = "(+)",
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEditAllowed || !isActiveQueue) {
                    QueueDropdownMenu(
                        mqState = mqState,
                        mq = mq,
                        isPinned = isPinned,
                    )
                }

                if (isEditAllowed && !isActiveQueue) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
//                            .draggableHandle()
                    )
                }
            }
        }
    }
}

@Composable
fun MqContent(
    mqState: MqState,
    modifier: Modifier = Modifier,
    mqEnabled: Boolean,
    landscape: Boolean,
    onDismiss: (() -> Unit)? = null,
) {
    BackHandler(mqState.expanded || mqState.isDetached()) {
        if (mqState.isDetached()) {
            mqState.resetHead()
        } else if (mqState.expanded) {
            mqState.toggleExpand()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        QueueInfo(
            mqState = mqState,
            mqEnabled = mqEnabled,
            landscape = landscape,
            onDismiss = onDismiss,
        )

        val lazyQueuesListState = rememberLazyListState()
        AnimatedVisibility(
            visible = mqState.expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            MqList(
                mqState = mqState,
                lazyQueuesListState = lazyQueuesListState,
                modifier = Modifier
                    .heightIn(Dp.Unspecified, if (!landscape) 300.dp else Dp.Unspecified)
            )
        }

        ActionBar(
            mqState = mqState,
        )
    }
}

@Composable
fun QueueInfo(
    mqState: MqState,
    mqEnabled: Boolean,
    landscape: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current

    val mediaItemCount by mqState.mediaItemCount.collectAsState()
    val durationMs by mqState.durationMs.collectAsState()

    // clean up later
    val MediumCornerRadius = 12.dp
    // clean up later

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(16.dp, 4.dp)
            .clickable(onClick = {
                onDismiss?.invoke()
            })
    ) {
        // queue title and show multiqueue button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.secondary,
                    RoundedCornerShape(MediumCornerRadius)
                )
                .padding(2.dp)
                .weight(1f)
                .height(IntrinsicSize.Min)
                .clickable(enabled = mqEnabled && !landscape) {
                    mqState.toggleExpand()
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
        ) {
            mqState.activeQueue?.let {
                MqListItem(
                    mqState = mqState,
                    index = -1,
                    mq = it.second,
                    horizontalPadding = 0.dp,
                    verticalPadding = 0.dp,
                    isActiveQueue = true,
                    isEditAllowed = mqState.isEditAllowed,
                    isHighlightedQueue = mqState.expanded && !mqState.isDetached(),
                    onClick = {
                        if (mqState.isDetached()) {
                            mqState.resetHead()
                        }
                    },
                    onLongClick = {
                        mqState.isEditAllowed = !mqState.isEditAllowed
                    },
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }
            IconButton(
                enabled = mqEnabled && !mqState.inactiveQueues.isEmpty() && !landscape,
                onClick = {
                    mqState.toggleExpand()
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                },
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (mqState.expanded) {
                        Icons.Outlined.ExpandLess
                    } else {
                        Icons.Outlined.ExpandMore
                    },
                    contentDescription = null,
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = "${mqState.currentMediaItemIndex + 1} / $mediaItemCount",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = convertDurationToTimeStamp(durationMs),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun MqList(
    mqState: MqState,
    lazyQueuesListState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = lazyQueuesListState,
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(rememberNestedScrollInteropConnection())
    ) {
        itemsIndexed(
            items = mqState.inactiveQueues,
            key = { _, item -> item.id },
        ) { index, mq ->
            MqListItem(
                mqState = mqState,
                index = index,
                mq = mq,
                isActiveQueue = false,
                isHighlightedQueue = mq == mqState.detachedQueue,
                isEditAllowed = mqState.isEditAllowed,
                onClick = {
                    if (mqState.detachedQueue != mq) {
                        mqState.detach(mq)
                        // TODO: scroll to when click
                    }
                },
                onLongClick = {
                    mqState.isEditAllowed = !mqState.isEditAllowed
                },
                modifier = Modifier
                    .animateItem()
            )
        }
    }
}

@Composable
fun ActionBar(
    mqState: MqState,
    modifier: Modifier = Modifier
) {
    val showPause by mqState.showPause.collectAsState()
    val repeatMode by mqState.repeatMode.collectAsState()
    val shuffleModeEnabled by mqState.shuffleModeEnabled.collectAsState()

    BackHandler(mqState.isEditAllowed) {
        mqState.isEditAllowed = false
    }

    FlowRow(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // left options
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
        ) {
            IconButton(
                onClick = {
                    mqState.toggleRepeatMode()
                },
                enabled = !mqState.isDetached(),
            ) {
                Icon(
                    painter = painterResource(
                        when (repeatMode) {
                            REPEAT_MODE_OFF, REPEAT_MODE_ALL -> R.drawable.ic_repeat
                            else -> R.drawable.ic_repeat_one
                        }
                    ),
                    contentDescription = null,
                    tint = LocalContentColor.current
                        .copy(if (repeatMode == REPEAT_MODE_OFF) 0.5f else 1f)
                )
            }
            IconButton(
                onClick = {
                    mqState.toggleShuffleMode()
                },
                enabled = !mqState.isDetached(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(if (shuffleModeEnabled) 1f else 0.5f)
                )
            }
        }

        // center options
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 8.dp)
        ) {
            IconButton(
                onClick = {
                    mqState.seekPrev()
                },
                enabled = !mqState.isDetached(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = null,
                )
            }
            IconButton(
                onClick = {
                    mqState.togglePlayPause()
                },
                enabled = !mqState.isDetached(),
            ) {
                Icon(
                    painter = painterResource(
                        if (showPause) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow
                    ),
                    contentDescription = null,
                )
            }
            IconButton(
                onClick = {
                    mqState.seekNext()
                },
                enabled = !mqState.isDetached(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = null,
                )
            }
        }

        // right options
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
        ) {
            val mq = mqState.activeQueue?.second
            if (mq != null) {
                QueueDropdownMenu(
                    mqState = mqState,
                    mq = mq,
                    isPinned = mq.expiry == null,
                    enabled = !mqState.isDetached() && !mqState.isEditAllowed,
                )
            }
            AnimatedVisibility(mqState.isDetached()) {
                IconButton(
                    onClick = {
                        mqState.loadDetached()
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_arrow),
                        contentDescription = null,
                    )
                }
            }
        }
    }

}


/**
 * State object for Multiqueue.
 */
class MqState(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    /** Where the queue's player and service listeners are registered. */
    private val controller: MediaControllerViewModel,
    /** The controller connected as the queue's made, the first [instance]. */
    connection: MediaBrowser,
    private val onDismissRequest: () -> Unit,
) : LifecycleOwner {
    /**
     * The connected controller, see [rememberMqState], which everything else goes through. The
     * activity releases it when it stops: until it starts again and the next one takes its place,
     * the queue keeps what it shows, and a controller no longer connected ignores commands.
     */
    private var instance: MediaBrowser = connection

    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    val listState = LazyListState()

    // -1 for none. Goes through the whole queue, worked out once it's loaded, see syncWithPlayer
    var currentMediaItemIndex: Int by mutableStateOf(-1)
        private set
    var locked by mutableStateOf(false)
        private set
    var listVersion by mutableIntStateOf(0)
        private set
    var pendingScroll by mutableStateOf<Pair<Int, Int>?>(null)
    var pendingSmoothScroll by mutableStateOf<Int?>(null)

    /** The time left in the queue, null until it's loaded, see [updateTimer]. */
    var timer by mutableStateOf<QueueTimer?>(null)
        private set

    /**
     * Whether any of the queue shows. Down, it isn't kept up to date, and catches up as it comes
     * up, see [onReveal].
     */
    var shown = false
        set(value) {
            if (field == value) return
            field = value
            if (value) onReveal()
        }

    fun onShow() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun onHide() {
        registry.currentState = Lifecycle.State.DESTROYED
    }

    private fun dismiss() = onDismissRequest()

    private fun scrollToPositionWithOffset(position: Int, offsetPx: Int) {
        pendingScroll = position to offsetPx
    }

    private fun smoothScrollTo(position: Int) {
        pendingSmoothScroll = position
    }

    private fun notifyListChanged() {
        listVersion++
    }

    companion object {
        const val CLIENT_QB_REFRESH_ALL = "qb_refresh_all"
        const val CLIENT_QB_REFRESH_ITEM = "qb_refresh_queue"
        const val CLIENT_QB_REFRESH_QUEUES = "qb_refresh_queues"
        const val CLIENT_QB_REFRESH_LIST = "qb_refresh_songs"
        const val CLIENT_QB_REFRESH_CLEAR = "qb_refresh_clear"

        /**
         * Representation of the depth of ui refresh required.
         */
        enum class RefreshLevel {
            // Everything
            ALL,

            // Refresh all queues, media item list will not be refreshed.
            QUEUES,

            // Refresh single queue, media item list will not be refreshed.
            ITEM,

            // Refresh media item list only
            SONGS,

            // Signal to clear the queue, no refresh is done
            CLEAR
        }
    }

    val showPause = MutableStateFlow(instance.showsPause)

    // shuffle and repeat modes do not need to be manually set for queue loads, they will be set
    // automatically
    val shuffleModeEnabled = MutableStateFlow(instance.shuffleModeEnabled)
    val repeatMode = MutableStateFlow(instance.repeatMode)

    val mediaItemCount = MutableStateFlow(instance.mediaItemCount)

    // Goes through the whole queue, worked out once it's loaded, see syncWithPlayer
    val durationMs = MutableStateFlow(0L)

    var expanded by mutableStateOf(false)
        private set

    private val detachedQueueState = mutableStateOf<MultiQueueObject?>(null)
    var detachedQueue: MultiQueueObject?
        get() = detachedQueueState.value
        private set(value) {
            detachedQueueState.value = value
        }

    var activeQueue: Pair<MutableList<Int>, MultiQueueObject>? by mutableStateOf(null)
        private set

    /** The songs shown, the queue's [Pair.second] in the order of [Pair.first]. */
    var playlist: Pair<MutableList<Int>, MutableList<MediaItem>> = Pair(ArrayList(), ArrayList())
        private set

    // A key for each song of the playlist, at the song's index and moved and removed with it, so
    // a row's state (a swipe, say) never passes to another row, a copy of the same song in
    // particular. See setPlaylist
    private var keys: MutableList<Long> = ArrayList()
    private var nextKey = 0L

    var inactiveQueues = mutableStateListOf<MultiQueueObject>()
        private set

    var isEditAllowed by mutableStateOf(false)

    // Loaded when the queue first comes up, see onReveal, empty until then
    private var loaded = false
    private var loading: Job? = null

    // The player's queue changed, or was shuffled or unshuffled, while the queue wasn't shown or
    // a row was dragged: it catches up once it shows and the row's dropped, see catchUp
    private var changed = false
    private var reordered = false

    // Shuffled here, the player has its new order from the service only after the toggle: the
    // next change of the playlist is then looked at for its order too, see onTimelineChanged
    private var shuffleOrderDue = false

    /** Whether a row's being dragged, see [catchUp]. */
    var dragging = false
        set(value) {
            field = value
            if (!value) catchUp(scroll = false)
        }


    val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            showPause.value = instance.showsPause
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            showPause.value = instance.showsPause
        }

        override fun onRepeatModeChanged(repeatMode: @Player.RepeatMode Int) {
            this@MqState.repeatMode.value = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            this@MqState.shuffleModeEnabled.value = shuffleModeEnabled
            shuffleOrderDue = true
            queueChanged(reordered = true)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: @Player.TimelineChangeReason Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                // The same songs in a new order: only after a shuffle, see matchesPlayer
                val reordered = shuffleOrderDue && loaded && playlist.first != playerOrder()
                shuffleOrderDue = false
                queueChanged(reordered = reordered)
            }
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: @Player.MediaItemTransitionReason Int
        ) {
            if (isDetached()) return
            this@MqState.mediaItemCount.value = instance.mediaItemCount
            this@MqState.currentMediaItemIndex = currentRow() ?: -1
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
        ) {
            if (isDetached()) return
            updateTimer()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateTimer()
        }
    }

    init {
        controller.addRecreationalPlayerListener(
            lifecycle,
            playerListener
        ) {
            // Connected again, the activity having stopped: the queue may have changed meanwhile
            if (it !== instance) {
                instance = it
                syncWithPlayer()
                queueChanged(reordered = false)
                // Paused or skipped meanwhile, which a new controller isn't told about
                updateTimer()
            }
        }

        controller.customCommandListeners.addCallback(lifecycle) { _, command, _ ->
            when (command.customAction) {
                CLIENT_QB_REFRESH_ALL, CLIENT_QB_REFRESH_QUEUES, CLIENT_QB_REFRESH_ITEM,
                CLIENT_QB_REFRESH_LIST, CLIENT_QB_REFRESH_CLEAR -> {
                    SessionResult(SessionResult.RESULT_SUCCESS).also { res ->
                        val level = when (command.customAction) {
                            CLIENT_QB_REFRESH_ALL -> RefreshLevel.ALL
                            CLIENT_QB_REFRESH_QUEUES -> RefreshLevel.QUEUES
                            CLIENT_QB_REFRESH_ITEM -> RefreshLevel.ITEM
                            CLIENT_QB_REFRESH_LIST -> RefreshLevel.SONGS
                            CLIENT_QB_REFRESH_CLEAR -> RefreshLevel.CLEAR
                            else -> throw IllegalArgumentException("Unsupported level")
                        }
                        val queueId = command.customExtras.getLong("queueId").let {
                            if (it == 0L) {
                                null
                            } else {
                                it
                            }
                        }

                        val activeQueue = command.customExtras.getBinder("activeQueue")
                        val inactiveQueues = command.customExtras.getBinder("inactiveQueues")

                        handleRefresh(
                            level = level,
                            activeQueue = MultiQueueList.getList(activeQueue).map { mq ->
                                val indexes: MutableList<Int> = if (mq.shuffleOrder?.data == null) {
                                    (0 until mq.getSize()).toMutableList()
                                } else {
                                    mq.shuffleOrder!!.data!!.toMutableList()
                                }

                                Pair(indexes, mq)
                            }.firstOrNull(),
                            inactiveQueues = MultiQueueList.getList(inactiveQueues),
                            queueId = queueId
                        )
                    }
                }

                else -> {
                    return@addCallback Futures.immediateFuture(
                        SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                    )
                }
            }
            return@addCallback Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * (Re)initialize multiqueue data, asking the service for it without holding up the main thread.
     */
    private suspend fun init() {
        val active = instance.awaitQueueForUi()
        val inactive = instance.awaitInactiveQueues()

        activeQueue = active
        detachedQueue = null
        inactiveQueues.clear()
        inactiveQueues.addAll(inactive)
        if (active != null) {
            setPlaylist(active.first, active.second.queue)
        } else {
            setPlaylist(ArrayList(), ArrayList())
        }
        notifyListChanged()
        syncWithPlayer()
    }

    /** Loads the queue, then shows the song playing. */
    private fun load() {
        if (loading != null) return
        loading = coroutineScope.launch {
            init()
            loaded = true
            // Changed since the service answered, or it had no queue to give
            if (!matchesPlayer()) updateList(scroll = false)
            updateTimer()
            scrollToCurrent()
        }
    }


    /**
     * =================
     * UI Interface Functions
     * =================
     */


    /**
     * The queue's coming up: loaded the first time, brought up to date if the player's queue
     * changed while it was down, and scrolled to the song playing.
     */
    private fun onReveal() {
        if (!loaded) {
            load()
            return
        }
        if (isDetached()) return
        catchUp(scroll = false)
        updateTimer()
        scrollToCurrent()
    }

    /** The key of the song at [index] of the playlist's songs, see [keys]. */
    fun songKey(index: Int): Long = keys[index]

    /** Scrolls smoothly to the song playing, if it's in the queue. */
    fun smoothScrollToCurrent() {
        currentRow()?.let { smoothScrollTo(it) }
    }

    /**
     * Whether the queue is in a detached state.
     */
    fun isDetached(): Boolean = detachedQueue != null

    /**
     * Enter a detached state with the given queue index. The inactive queue's metadata and content
     * is loaded.
     *
     * Use [resetHead] to revert to the active queue.
     */
    fun detach(index: Int) {
        val mq = inactiveQueues.getOrNull(index)
        if (mq == null) {
            updateList()
            return
        }
        detach(mq)
    }

    /**
     * Enter a detached state with the given queue. The inactive queue's metadata and content
     * is loaded.
     */
    fun detach(mq: MultiQueueObject) {
        if (!inactiveQueues.contains(mq)) {
            resetHead()
            return
        }
        detachedQueue = mq
        this.repeatMode.value = mq.repeatMode
        this.shuffleModeEnabled.value = mq.shuffleModeEnabled
        this.mediaItemCount.value = mq.getSize()
        this.currentMediaItemIndex = getShuffledIndex(mq)
        this.durationMs.value = getDurationMs(mq)
        updateList(instance.getQueueForUi(mq.id))
    }

    /**
     * Exit the detached state. The active queue's metadata and content is restored.
     */
    fun resetHead(updateSongList: Boolean = true) {
        detachedQueue = null
        this.repeatMode.value = instance.repeatMode
        this.shuffleModeEnabled.value = instance.shuffleModeEnabled
        this.mediaItemCount.value = instance.mediaItemCount
        this.currentMediaItemIndex = getShuffledIndex()
        this.durationMs.value = getDurationMs()
        if (updateSongList) {
            updateList(mq = activeQueue)
        }
    }

    fun toggleExpand() {
        if (!expanded) {
            expand()
        } else {
            collapse()
        }
    }

    fun removeQueue(queueId: Long? = activeQueue?.second?.id) {
        if (queueId == null) return

        instance.deleteQueue(queueId)

        detachedQueue?.repeatMode?.let {
            playerListener.onRepeatModeChanged(it)
        }
        detachedQueue?.shuffleModeEnabled?.let {
            playerListener.onShuffleModeEnabledChanged(it)
        }
    }

    fun loadDetached(startIndex: Int = C.INDEX_UNSET) {
        if (detachedQueue == null) return
        instance.loadQueue(detachedQueue!!.id, startIndex)

        // do not use full resetHead(false) to avoid restoring the stats of old active queue right
        // before the new one is loaded
        detachedQueue = null

        coroutineScope.launch {
            init()
        }
    }


    fun togglePlayPause() = instance.playOrPause()
    fun seekPrev() = instance.seekToPrevious()
    fun seekNext() = instance.seekToNext()

    fun toggleRepeatMode() {
        instance.repeatMode = when (instance.repeatMode) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_OFF
            else -> REPEAT_MODE_OFF
        }
    }

    fun toggleShuffleMode() {
        // The songs are shown in the new order as the player tells, see playerListener
        instance.shuffleModeEnabled = !instance.shuffleModeEnabled
    }

    fun playNext(queueId: Long) {
        instance.getQueueForUi(queueId)?.let { mq ->
            instance.addMediaItems(
                instance.currentMediaItemIndex + 1,
                mq.first.zip(mq.second.queue).sortedBy { it.first }.map { it.second },
            )
        }
        if (!isDetached()) {
            updateList()
            activeQueue = instance.getQueueForUi()
            this.mediaItemCount.value = instance.mediaItemCount
            this.durationMs.value = getDurationMs(activeQueue!!.second)
        }
    }

    fun addToQueue(queueId: Long) {
        instance.getQueueForUi(queueId)?.let { mq ->
            instance.addMediaItems(
                mq.first.zip(mq.second.queue).sortedBy { it.first }.map { it.second },
            )
        }
        if (!isDetached()) {
            updateList()
        }
    }

    fun addToPlaylist(queueId: Int) {
//        activity.addToPlaylistDialog(item)
    }

    fun renameQueue(queueId: Long, title: String, dryRun: Boolean): Boolean {
        val ret = instance.renameQueue(queueId, title, dryRun)
        return ret
    }

    fun togglePin(queueId: Long? = activeQueue?.second?.id) {
        if (queueId == null) return
        val queue = (
            if (queueId == activeQueue?.second?.id) activeQueue?.second
            else inactiveQueues.find { it.id == queueId }
        )!!

        if (queue.expiry != null) {
            instance.pinQueue(queueId)
        } else {
            instance.unpinQueue(queueId)
        }
    }

    /**
     * Trigger a chromometer update
     *
     * @param currentMediaItemIndex Override for
     *   [androidx.media3.session.MediaBrowser.currentMediaItemIndex]
     * @param currentPosition Override for [androidx.media3.session.MediaBrowser.getCurrentPosition]
     *
     * TODO: this recomputes the whole timer even when a caller only changed a single row.
     */
    fun updateTimer(currentMediaItemIndex: Int? = null, currentPosition: Long? = null) {
        // Only seen with the queue up, see onReveal
        if (!loaded || !shown) return
        if (currentMediaItemIndex == -1) return
        val current = (currentMediaItemIndex ?: instance.currentMediaItemIndex).let {
            playlist.first.indexOf(it).takeIf { it != -1 }
        } ?: 0
        if (current < 0) return
        // A queue to start from the start of its song has no position
        val elapsedCurrentMs =
            currentPosition?.let { if (it == C.TIME_UNSET) 0L else it } ?: instance.currentPosition
        timer = QueueTimer(
            totalMs = playlist.second.sumOf { it.mediaMetadata.durationMs ?: 0L },
            remainingMs = playlist.first.subList(current, playlist.first.size)
                .sumOf { playlist.second[it].mediaMetadata.durationMs ?: 0L } - elapsedCurrentMs,
            atRealtime = SystemClock.elapsedRealtime(),
            running = instance.isPlaying,
        )
    }

    /** A tap on row [pos] of the queue: play it, or in a detached queue load that queue there. */
    fun clickRow(pos: Int) {
        if (isDetached()) {
            detachedQueue?.let { loadDetached(playlist.first[pos]) }
        } else {
            instance.seekToDefaultPosition(playlist.first[pos])
        }
    }

    /** Row [from] of the queue dragged to [to]. */
    fun moveRow(from: Int, to: Int) {
        if (from == to) return
        // The player's order is a stand-in until the service's comes, see matchesPlayer
        shuffleOrderDue = false
        val from1 = playlist.first.removeAt(from)
        playlist.first.replaceAllSupport { if (it > from1) it - 1 else it }
        val movedItem = playlist.second.removeAt(from1)
        val movedKey = keys.removeAt(from1)
        val to1 = if (to > 0) playlist.first[to - 1] + 1 else 0
        playlist.first.replaceAllSupport { if (it >= to1) it + 1 else it }
        playlist.first.add(to, to1)
        playlist.second.add(to1, movedItem)
        keys.add(to1, movedKey)
        // Changed elsewhere since the drag started, the songs aren't where the rows say: the
        // move's only shown, and undone as the queue catches up when the row's dropped
        if (!changed) instance.moveMediaItem(from1, to1)
        notifyListChanged()
        val currentIndex = currentMediaItemIndex
        if (currentIndex == from)
            currentMediaItemIndex = to
        else if (from < to && from < currentIndex && currentIndex <= to)
            currentMediaItemIndex = currentIndex - 1
        else if (from > to && to <= currentIndex && currentIndex < from)
            currentMediaItemIndex = currentIndex + 1
        updateTimer()
    }

    /** Row [pos] of the queue removed. The last row goes with its whole queue. */
    fun removeRow(pos: Int) {
        // Changed elsewhere, the songs aren't where the rows say, as in moveRow
        if (changed) return
        shuffleOrderDue = false
        if (playlist.first.size <= 1) {
            removeQueue()
            return
        }
        val idx = playlist.first.removeAt(pos)
        playlist.first.replaceAllSupport { if (it > idx) it - 1 else it }
        playlist.second.removeAt(idx)
        keys.removeAt(idx)
        // Told after the songs shown changed, so the player's change matches them, see queueChanged
        instance.removeMediaItem(idx)
        notifyListChanged()
        if (pos < currentMediaItemIndex) {
            currentMediaItemIndex--
        }
        updateTimer()
    }

    /** The row of the song of key [key], see [songKey], removed, if it's still there. */
    fun removeEntry(key: Long) {
        val pos = playlist.first.indexOfFirst { keys[it] == key }
        if (pos != -1) removeRow(pos)
    }


    /**
     * =================
     * Helper Functions
     * =================
     */


    private fun expand() {
        expanded = true
    }

    private fun collapse() {
        expanded = false
        resetHead()
    }

    private fun getShuffledIndex(mq: MultiQueueObject? = null): Int {
        if (mq != null) {
            if (mq.shuffleOrder == null) {
                return mq.startIndex
            }

            val indexes = mq.shuffleOrder!!.data!!.toMutableList()
            return indexes.indexOf(mq.startIndex)
        }

        val indexes = LinkedList<Int>()
        val s = instance.shuffleModeEnabled
        var i = instance.currentTimeline.getFirstWindowIndex(s)
        while (i != C.INDEX_UNSET) {
            indexes.add(i)
            i = instance.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, s)
        }

        return indexes.indexOf(instance.currentMediaItemIndex)
    }

    private fun getDurationMs(mq: MultiQueueObject? = null): Long {
        if (mq != null) {
            return mq.getDuration()
        }
        var duration = 0L
        for (i in 0 until instance.mediaItemCount) {
            duration += instance.getMediaItemAt(i).mediaMetadata.durationMs ?: 0L
        }
        return duration

    }

    /** The song playing, as a row of the queue, or null if it isn't in it. */
    private fun currentRow(): Int? =
        playlist.first.indexOf(instance.currentMediaItemIndex).takeIf { it != -1 }

    /** Scrolls to the song playing, at once, as the queue comes up. */
    private fun scrollToCurrent() {
        if (isDetached()) return
        val scrollPos = currentRow() ?: return
        scrollToPositionWithOffset(
            scrollPos,
            // quick UX hack to show there's more songs above (well, if there is).
            if (scrollPos >= playlist.first.size - 2) 0 else (context
                .resources.getDimensionPixelOffset(R.dimen.list_height) * 0.5f).toInt()
        )
    }

    /** The player's state, as the queue head shows it, and the song playing. */
    private fun syncWithPlayer() {
        showPause.value = instance.showsPause
        if (isDetached()) return
        shuffleModeEnabled.value = instance.shuffleModeEnabled
        repeatMode.value = instance.repeatMode
        mediaItemCount.value = instance.mediaItemCount
        durationMs.value = getDurationMs()
        currentMediaItemIndex = currentRow() ?: -1
    }

    /**
     * The player's queue may have changed, or been [reordered] (shuffled or unshuffled): unless
     * it's what the queue shows already, its own change say, the songs are shown again as the
     * player has them, now, or once the queue shows and no row's dragged.
     */
    private fun queueChanged(reordered: Boolean) {
        if (!loaded || isDetached()) return
        if (reordered) {
            this.reordered = true
        } else if (!changed && shown && matchesPlayer()) {
            // Down, the queue doesn't go through its songs for every change, see catchUp
            return
        }
        changed = true
        catchUp(scroll = reordered)
    }

    /** Shows the songs again as the player has them, if they changed, see [queueChanged]. */
    private fun catchUp(scroll: Boolean) {
        if (!changed || !shown || dragging || isDetached()) return
        val reload = reordered || !matchesPlayer()
        changed = false
        reordered = false
        if (reload) updateList(scroll = scroll)
    }

    /**
     * Whether the songs shown are the player's. Not in their order: in a shuffled queue, the
     * order the player has right after the queue moved or removed a song is a stand-in, until
     * the service's arrives. The order only changes elsewhere with the songs, or with a shuffle.
     */
    private fun matchesPlayer(): Boolean {
        val timeline = instance.currentTimeline
        val items = playlist.second
        if (timeline.windowCount != items.size) return false
        val window = Timeline.Window()
        return items.indices.all { timeline.getWindow(it, window).mediaItem.mediaId == items[it].mediaId }
    }

    /**
     * Shows [items] in [order], each song that was shown before keeping its key: copies of a
     * song take its old keys in turn, any more get new ones.
     */
    private fun setPlaylist(order: MutableList<Int>, items: MutableList<MediaItem>) {
        val oldKeys = HashMap<String, ArrayDeque<Long>>()
        playlist.second.forEachIndexed { i, item ->
            oldKeys.getOrPut(item.mediaId) { ArrayDeque() }.addLast(keys[i])
        }
        keys = items.mapTo(ArrayList<Long>(items.size)) {
            oldKeys[it.mediaId]?.removeFirstOrNull() ?: nextKey++
        }
        playlist = Pair(order, items)
    }

    /**
     * Refresh the ui based on what the service tells us to do.
     *
     * We send action requests from the UI (delete, rename, etc.), however it is the service's
     * responsibility to tell us what to refresh.
     */
    private fun handleRefresh(
        level: RefreshLevel,
        activeQueue: Pair<MutableList<Int>, MultiQueueObject>?,
        inactiveQueues: List<MultiQueueObject>,
        queueId: Long? = null,
    ) {
        // Not loaded yet, it will be with all this as the queue comes up
        if (!loaded && level != RefreshLevel.CLEAR) return
        when (level) {
            RefreshLevel.ALL -> {
                this.activeQueue = activeQueue
                detachedQueue = null
                this.inactiveQueues.clear()
                this.inactiveQueues.addAll(inactiveQueues)

                updateList(mq = activeQueue)
            }
            // TODO(mq): it should be possible to refresh single items
            RefreshLevel.QUEUES, RefreshLevel.ITEM -> {
                this.activeQueue = activeQueue
                detachedQueue = null
                this.inactiveQueues.clear()
                this.inactiveQueues.addAll(inactiveQueues)
            }

            RefreshLevel.SONGS -> {
                if (queueId != null) {
                    // update detached queue
                    val queues = inactiveQueues.toMutableList()
                    activeQueue?.second?.let {
                        queues.add(it)
                    }
                    updateList(instance.getQueueForUi(queues.find { it.id == queueId }!!.id))
                } else {
                    this.activeQueue = activeQueue
                    // update active queue with one provided by the service. Avoid dumpPlaylist()
                    // race condition
                    updateList(mq = activeQueue)
                }
            }

            RefreshLevel.CLEAR -> {
                dismiss()
            }
        }
    }

    /**
     * Update playlist and timer
     * 
     * @param mq Optionally specify [MultiQueueObject] to be used instead of the player's queue
     * @param scroll Whether to scroll to the song playing
     */
    private fun updateList(
        mq: Pair<MutableList<Int>, MultiQueueObject>? = null,
        scroll: Boolean = true,
    ) {
        val pl: Pair<MutableList<Int>, MutableList<MediaItem>> = if (mq != null) {
            Pair(mq.first, mq.second.queue)
        } else {
            dumpPlaylist()
        }
        setPlaylist(pl.first, pl.second)
        notifyListChanged()

        // update playing indicator, scroll to
        val i = (mq?.second?.startIndex ?: instance.currentMediaItemIndex).let {
            if (it == -1) 0 else it
        }
        currentMediaItemIndex = playlist.first.indexOf(i)
        if (scroll) smoothScrollTo(playlist.first.indexOf(i))

        updateTimer(mq?.second?.startIndex, mq?.second?.startPositionMs)
    }

    /** The queue as the player has it: empty once the controller is no longer connected. */
    private fun dumpPlaylist(): Pair<MutableList<Int>, MutableList<MediaItem>> {
        val items = ArrayList<MediaItem>(instance.mediaItemCount)
        for (i in 0 until instance.mediaItemCount) {
            items.add(instance.getMediaItemAt(i))
        }
        return Pair(playerOrder(), items)
    }

    /** The order the player plays its queue in. */
    private fun playerOrder(): MutableList<Int> {
        val indexes = ArrayList<Int>(instance.mediaItemCount)
        val s = instance.shuffleModeEnabled
        var i = instance.currentTimeline.getFirstWindowIndex(s)
        while (i != C.INDEX_UNSET) {
            indexes.add(i)
            i = instance.currentTimeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, s)
        }
        return indexes
    }

    fun age() {
        instance.age()
    }
}

/**
 * The queue's state, made once the controller first connects, null until then. It outlives that
 * controller: when the activity stops and starts again, it carries on with the next one.
 */
@Composable
fun rememberMqState(
    context: Context,
    coroutineScope: CoroutineScope,
    controller: MediaControllerViewModel,
    onDismiss: (() -> Unit) = {}
): MqState? {
    val connection by controller.connection.collectAsState()
    val holder = remember { MqStateHolder() }
    return holder.state ?: connection?.let { instance ->
        MqState(context, coroutineScope, controller, instance, onDismiss).also { holder.state = it }
    } // TODO: rememberSaveable
}

private class MqStateHolder {
    var state: MqState? = null
}

/** The time left in the queue: [remainingMs] at [atRealtime], counting down if [running]. */
@Immutable
class QueueTimer(
    val totalMs: Long,
    val remainingMs: Long,
    val atRealtime: Long,
    val running: Boolean,
)

/**
 * [getQueueForUi] without holding up the main thread: the service's answer is awaited, and
 * unpacked on another thread. Null without a queue, or a player.
 */
private suspend fun MediaController.awaitQueueForUi(
    queueId: Long = -1L,
): Pair<MutableList<Int>, MultiQueueObject>? {
    val result = sendCustomCommand(
        SessionCommand(SERVICE_QB_GET_QUEUE_FOR_UI, Bundle.EMPTY).apply {
            customExtras.putLong("queueId", queueId)
        }, Bundle.EMPTY
    ).await()
    val binder = result.extras.getBinder("allQueues") ?: return null
    return withContext(Dispatchers.Default) {
        MultiQueueList.getList(binder).firstOrNull()?.let { mq ->
            val indexes: MutableList<Int> =
                mq.shuffleOrder?.data?.toMutableList() ?: (0 until mq.getSize()).toMutableList()
            Pair(indexes, mq)
        }
    }
}

/** [getInactiveQueues], answered like [awaitQueueForUi]. */
private suspend fun MediaController.awaitInactiveQueues(): List<MultiQueueObject> {
    val result = sendCustomCommand(
        SessionCommand(SERVICE_QB_GET_INACTIVE_LIST, Bundle.EMPTY),
        Bundle.EMPTY
    ).await()
    val binder = result.extras.getBinder("allQueues") ?: return emptyList()
    return withContext(Dispatchers.Default) { MultiQueueList.getList(binder) }
}
