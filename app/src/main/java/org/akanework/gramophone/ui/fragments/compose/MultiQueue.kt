package org.akanework.gramophone.ui.fragments.compose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.MultiQueueList
import org.akanework.gramophone.logic.MultiQueueObject
import org.akanework.gramophone.logic.age
import org.akanework.gramophone.logic.deleteQueue
import org.akanework.gramophone.logic.getInactiveQueues
import org.akanework.gramophone.logic.getInactiveQueue
import org.akanework.gramophone.logic.getQueueForUi
import org.akanework.gramophone.logic.loadQueue
import org.akanework.gramophone.logic.pinQueue
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.renameQueue
import org.akanework.gramophone.logic.supportsWideScreen
import org.akanework.gramophone.logic.unpinQueue
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.Chronometer
import org.akanework.gramophone.ui.components.PlaylistQueueSheet
import org.akanework.gramophone.ui.components.compose.QueueDropdownMenu
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
    val pinId = if (isActiveQueue) {
        -1L
    } else {
        mq.id
    }

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
                            (mqState.inactiveQueues + mqState.activeQueue)
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
                            horizontalArrangement = if (!isPinned) Arrangement.SpaceBetween else Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            if (!isPinned) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // TODO: why need div by 10 here
                                    val remainingTimeMs =
                                        (expiry!! - System.currentTimeMillis()) / 10
                                    Icon(
                                        painter = painterResource(if (!isActiveQueue && remainingTimeMs < 1800000) R.drawable.ic_warning else R.drawable.ic_keep),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable(onClick = {
                                                mqState.togglePin(pinId)
                                            }),
                                    )
                                    Text(
                                        text = if (isActiveQueue) "∞" else makeTimeString(
                                            remainingTimeMs
                                        ),
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
                        imageVector = Icons.Rounded.DragHandle,
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

    val currentMediaItemIndex by mqState.currentMediaItemIndex.collectAsState()
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
                    mq = it,
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
                    imageVector = if (mqState.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
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
                text = "${currentMediaItemIndex + 1} / $mediaItemCount",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = makeTimeString(durationMs),
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
    val isPlaying by mqState.isPlaying.collectAsState()
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
                    tint = LocalContentColor.current.copy(if (repeatMode == REPEAT_MODE_OFF) 0.5f else 1f)
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
                    painter = painterResource(if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow),
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
            val index = -1
            val mq = mqState.activeQueue
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

@Composable
fun BottomSheetActions(
    mqState: MqState,
    durationView: Chronometer,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onRecyclerScrollTo: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // TODO: no longer needed?
            Button(
                colors = ButtonDefaults.textButtonColors(),
                onClick = {
                    onDismiss?.invoke()
                    mqState.removeQueue()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_delete_sweep_24),
                    contentDescription = null,
                )
                Text(
                    stringResource(R.string.clear_queue)
                )
            }

            Button(
                colors = ButtonDefaults.textButtonColors(),
                onClick = {
                    onRecyclerScrollTo?.invoke()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_unfold_double),
                    contentDescription = null,
                )
                Text(
                    stringResource(R.string.scroll_to_playing)
                )
            }
        }

        AndroidView(
            factory = {
                durationView
            },
        )
    }
}

@Composable
fun QueueRoot(
    mqState: MqState,
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    durationView: Chronometer,
    mqEnabled: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onRecyclerScrollTo: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val landscapeMode = false && context.supportsWideScreen()
//        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    @Composable
    fun BoxScope.pager(
        modifier: Modifier = Modifier,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            beyondViewportPageCount = 1,
            userScrollEnabled = Flags.MQ_PREVIEW && !mqState.expanded
        ) { page ->
            when (page) {
                0 -> {
                    if (!Flags.MQ_PREVIEW) return@HorizontalPager
                    if (Flags.MQ_PREVIEW && landscapeMode) {
                        QueueInfo(
                            mqState = mqState,
                            mqEnabled = mqEnabled,
                            landscape = landscapeMode,
                            onDismiss = onDismiss,
                        )
                    } else {
                        MqContent(
                            mqState = mqState,
                            mqEnabled = mqEnabled,
                            landscape = false,
                            onDismiss = onDismiss,
                        )
                    }
                }

                1 -> {
                    BottomSheetActions(
                        mqState = mqState,
                        durationView = durationView,
                        onDismiss = onDismiss,
                        onRecyclerScrollTo = onRecyclerScrollTo,
                    )
                }
            }
        }

        if (!Flags.MQ_PREVIEW) return
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .align(Alignment.BottomCenter)
                .alpha(if (!mqState.expanded) 1f else 0.3f)
                .animateContentSize()
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
                            enabled = !mqState.expanded,
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

    if (landscapeMode) {
        Row(
            modifier = modifier
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(0.5f)
            ) {
                Box {
                    pager()
                }

                ActionBar(
                    mqState = mqState,
                )

                val lazyQueuesListState = rememberLazyListState()
                MqList(
                    mqState = mqState,
                    lazyQueuesListState = lazyQueuesListState,
                    modifier = Modifier
                        .heightIn(Dp.Unspecified, Dp.Unspecified)
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
        ) {
            Box {
                pager()
            }
        }
    }
}


// TODO: clean up later
fun makeTimeString(duration: Long?): String {
    if (duration == null || duration < 0) return ""
    var sec = duration / 1000
    val day = sec / 86400
    sec %= 86400
    val hour = sec / 3600
    sec %= 3600
    val minute = sec / 60
    sec %= 60
    return when {
        day > 0 -> "%d:%02d:%02d:%02d".format(day, hour, minute, sec)
        hour > 0 -> "%d:%02d:%02d".format(hour, minute, sec)
        else -> "%d:%02d".format(minute, sec)
    }
}

@Composable
fun EmptyPlaceholder(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Image(
            icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.size(64.dp)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// end clean up later

/**
 * State object for Multiqueue.
 */
class MqState(
    private val coroutineScope: CoroutineScope,
    private val activity: MainActivity,
    private val playlistQueueSheet: PlaylistQueueSheet?,
) {

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

    private val instance = activity.getPlayer()!!
    val isPlaying = MutableStateFlow(instance.isPlaying)

    // shuffle and repeat modes do not need to be manually set for queue loads, they will be set automatically
    val shuffleModeEnabled = MutableStateFlow(instance.shuffleModeEnabled)
    val repeatMode = MutableStateFlow(instance.repeatMode)

    val mediaItemCount = MutableStateFlow(instance.mediaItemCount)
    val currentMediaItemIndex = MutableStateFlow(getShuffledIndex())
    val durationMs = MutableStateFlow(getDurationMs())

    var expanded by mutableStateOf(false)
        private set

    private val detachedQueueState = mutableStateOf<MultiQueueObject?>(null)
    var detachedQueue: MultiQueueObject?
        get() = detachedQueueState.value
        private set(value) {
            detachedQueueState.value = value
            playlistQueueSheet?.lockQueue(value != null)
        }

    var activeQueue: MultiQueueObject? by mutableStateOf(null)
        private set

    var inactiveQueues = mutableStateListOf<MultiQueueObject>()
        private set

    var isEditAllowed by mutableStateOf(false)


    val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@MqState.isPlaying.value = isPlaying
        }

        override fun onRepeatModeChanged(repeatMode: @Player.RepeatMode Int) {
            this@MqState.repeatMode.value = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            this@MqState.shuffleModeEnabled.value = shuffleModeEnabled
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: @Player.MediaItemTransitionReason Int
        ) {
            if (isDetached()) return
            this@MqState.mediaItemCount.value = instance.mediaItemCount
            this@MqState.currentMediaItemIndex.value = getShuffledIndex()
        }
    }

    init {
        activity.controllerViewModel.addRecreationalPlayerListener(
            playlistQueueSheet!!.lifecycle,
            playerListener
        ) {
        }

        activity.controllerViewModel.customCommandListeners.addCallback(playlistQueueSheet.lifecycle) { _, command, _ ->
            when (command.customAction) {
                CLIENT_QB_REFRESH_ALL, CLIENT_QB_REFRESH_QUEUES, CLIENT_QB_REFRESH_ITEM, CLIENT_QB_REFRESH_LIST, CLIENT_QB_REFRESH_CLEAR -> {
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
                    return@addCallback Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }
            }
            return@addCallback Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        init()
    }

    /**
     * (Re)initialize multiqueue data.
     */
    private fun init(
        onFinish: (() -> Unit)? = null,
    ) {
        coroutineScope.launch {
            activeQueue = null
            detachedQueue = null
            inactiveQueues.clear()

            instance.getInactiveQueue()?.let {
                activeQueue = it
            }
            instance.getInactiveQueues().toMutableList().let {
                inactiveQueues.addAll(it)
            }
            onFinish?.invoke()
        }
    }


    /**
     * =================
     * UI Interface Functions
     * =================
     */


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
            playlistQueueSheet?.forceUpdate()
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
            playlistQueueSheet?.forceUpdate()
            return
        }
        detachedQueue = mq
        this.repeatMode.value = mq.repeatMode
        this.shuffleModeEnabled.value = mq.shuffleModeEnabled
        this.mediaItemCount.value = mq.getSize()
        this.currentMediaItemIndex.value = getShuffledIndex(mq)
        this.durationMs.value = getDurationMs(mq)
        playlistQueueSheet?.forceUpdate(mq.id)
    }

    /**
     * Exit the detached state. The active queue's metadata and content is restored.
     */
    fun resetHead(updateSongList: Boolean = true) {
        detachedQueue = null
        this.repeatMode.value = instance.repeatMode
        this.shuffleModeEnabled.value = instance.shuffleModeEnabled
        this.mediaItemCount.value = instance.mediaItemCount
        this.currentMediaItemIndex.value = getShuffledIndex()
        this.durationMs.value = getDurationMs()
        if (updateSongList) {
            playlistQueueSheet?.forceUpdate(-1)
        }
    }

    fun toggleExpand() {
        if (!expanded) {
            expand()
        } else {
            collapse()
        }
    }

    fun removeQueue(queueId: Long = -1) {
        if (!Flags.MQ_PREVIEW) {
            instance.clearMediaItems()
            return
        }

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

        // do not use full resetHead(false) to avoid restoring the stats of old active queue right before the new one is loaded
        detachedQueue = null
        playlistQueueSheet?.lockQueue(detachedQueue != null)
        playlistQueueSheet?.forceRefresh()

        init()
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
        instance.shuffleModeEnabled = !instance.shuffleModeEnabled
        playlistQueueSheet?.forceUpdate()
    }

    fun playNext(queueId: Long) {
        instance.getQueueForUi(queueId)?.let { mq ->
            instance.addMediaItems(
                instance.currentMediaItemIndex + 1,
                mq.first.zip(mq.second.queue).sortedBy { it.first }.map { it.second },
            )
        }
        if (!isDetached()) {
            playlistQueueSheet?.forceUpdate()
        }
    }

    fun addToQueue(queueId: Long) {
        instance.getQueueForUi(queueId)?.let { mq ->
            instance.addMediaItems(
                mq.first.zip(mq.second.queue).sortedBy { it.first }.map { it.second },
            )
        }
        if (!isDetached()) {
            playlistQueueSheet?.forceUpdate()
        }
    }

    fun addToPlaylist(queueId: Int) {
//        activity.addToPlaylistDialog(item)
    }

    fun renameQueue(queueId: Long, title: String, dryRun: Boolean): Boolean {
        val ret = instance.renameQueue(queueId, title, dryRun)
        if (!dryRun && ret) {
            init() // can be more efficient
        }
        return ret
    }

    fun togglePin(queueId: Long? = -1) {
        if (queueId == null) return
        val queue =
            (if (queueId == -1L) activeQueue else inactiveQueues.find { it.id == queueId })!!

        if (queue.expiry != null) {
            instance.pinQueue(queueId)
        } else {
            instance.unpinQueue(queueId)
        }
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
        when (level) {
            RefreshLevel.ALL -> {
                this.activeQueue = activeQueue?.second
                detachedQueue = null
                this.inactiveQueues.clear()
                this.inactiveQueues.addAll(inactiveQueues)

                playlistQueueSheet?.forceUpdate(activeQueue)
            }
            // TODO(mq): it should be possible to refresh single items
            RefreshLevel.QUEUES, RefreshLevel.ITEM -> {
                this.activeQueue = activeQueue?.second
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
                    playlistQueueSheet?.forceUpdate(queues.find { it.id == queueId }!!.id)
                } else {
                    // update active queue with one provided by the service. Avoid dumpPlaylist() race condition
                    playlistQueueSheet?.forceUpdate(activeQueue)
                }
            }

            RefreshLevel.CLEAR -> {
                playlistQueueSheet?.dismiss()
            }
        }
    }

    fun age() {
        instance.age()
    }
}

@Composable
fun rememberMqState(
    coroutineScope: CoroutineScope,
    instance: MainActivity,
    playlistQueueSheet: PlaylistQueueSheet?,
): MqState {
    return remember {
        MqState(coroutineScope, instance, playlistQueueSheet)
    } // TODO: rememberSaveable
}
