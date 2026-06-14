package org.akanework.gramophone.ui.fragments.compose

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.List
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
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.session.MediaBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.MultiQueueObject
import org.akanework.gramophone.logic.age
import org.akanework.gramophone.logic.deleteQueue
import org.akanework.gramophone.logic.getInactiveQueues
import org.akanework.gramophone.logic.getQueue
import org.akanework.gramophone.logic.loadQueue
import org.akanework.gramophone.logic.pinQueue
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.supportsWideScreen
import org.akanework.gramophone.logic.unpinQueue
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.components.Chronometer
import org.akanework.gramophone.ui.components.PlaylistQueueSheet
import org.akanework.gramophone.ui.components.compose.ActionDropdown
import org.akanework.gramophone.ui.components.compose.DropdownItem

@Composable
fun MqListItem(
    mqState: MqState,
//    queueListState: ReorderableLazyListState, // sh.calvin.reorderable.ReorderableLazyListState
    index: Int,
    mq: MultiQueueObject,
    modifier: Modifier = Modifier,
    isActiveQueue: Boolean = false,
    isInactiveActiveQueue: Boolean = false,
    isEditAllowed: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val expiry by mq.expiry.collectAsState(initial = null)
    val isPinned = expiry == null

    Row( // wrapper
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActiveQueue) {
                    MaterialTheme.colorScheme.tertiary.copy(0.3f)
                } else if (isInactiveActiveQueue) {
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
                .padding(8.dp)
                .fillMaxWidth()
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
                                mqState.togglePin(index)
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
                                mqState.removeQueue(index)
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
                    Text(
                        text = "${index + 1}. ${mq.title}",
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
                    )
                    if (!isPinned) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                        ) {
                            // TODO: why need div by 10 here
                            val remainingTimeMs = (expiry!! - System.currentTimeMillis()) / 10
                            Icon(
                                painter = painterResource(if (remainingTimeMs < 1800000) R.drawable.ic_warning else R.drawable.ic_keep), //TODO: represent state of pin, or the action of this button
                                contentDescription = null,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable(onClick = {
                                        mqState.togglePin(index)
                                    }),
                            )
                            Text(
                                text = makeTimeString(remainingTimeMs),
                                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clickable(onClick = {
                                        mqState.togglePin(index)
                                    }),
                            )
                        }
                    }
                }
            }

            if (isEditAllowed) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = null,
//                        modifier = Modifier.draggableHandle()
                )
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
                .clickable(enabled = mqEnabled && !landscape) {
                    mqState.toggleExpand()
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
        ) {
            Text(
                text = mqState.getQueueTitle() ?: "",
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            IconButton(
                enabled = mqEnabled && !landscape,
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
                text = mqState.getQueuePositionStr(),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = makeTimeString(mqState.getQueueLength()),
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
        if (mqState.getQueueListSize() == 0) {
            item {
                EmptyPlaceholder(
                    icon = Icons.AutoMirrored.Rounded.List,
                    text = stringResource(R.string.oh_no),
                    modifier = Modifier.animateItem()
                )
            }
        }
        itemsIndexed(
            items = mqState.inactiveQueues,
            key = { _, item -> item.id },
        ) { index, mq ->
            MqListItem(
                mqState = mqState,
                index = index,
                mq = mq,
                isActiveQueue = false,
                isInactiveActiveQueue = mq == mqState.detachedQueue,
                onClick = {
                    if (mqState.detachedQueue != mq) {
                        mqState.detach(mq)
                        // TODO: scroll to when click
                    }
                },
                modifier = Modifier
                    .animateItem()
            )
        }
        mqState.activeQueue?.let {
            item {
                MqListItem(
                    mqState = mqState,
                    index = mqState.getQueueListSize() - 1,
                    mq = it,
                    isActiveQueue = true,
                    isInactiveActiveQueue = false,
                    onClick = {
                        if (mqState.isDetached()) {
                            mqState.resetHead()
                        }
                    },
                    modifier = Modifier
                        .animateItem()
                )
            }
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
            val expiry = mqState.getCurrentQueue()?.expiry?.collectAsState(initial = null)
            val isPinned = expiry == null
            ActionDropdown(
                actions = listOf(
                    DropdownItem(
                        title = stringResource(R.string.add_to_queue),
                        leadingIcon = null,
                        action = {},
                    ),
                    DropdownItem(
                        title = stringResource(R.string.play_next),
                        leadingIcon = null,
                        action = {},
                    ),
                    DropdownItem(
                        title = stringResource(R.string.add_to_playlist),
                        leadingIcon = null,
                        action = {},
                    ),
                    DropdownItem(
                        title = stringResource(R.string.rename),
                        leadingIcon = null,
                        action = {},
                    ),
                    DropdownItem(
                        title = stringResource(
                            if (isPinned) R.string.mq_pin_queue else R.string.mq_unpin_queue
                        ),
                        leadingIcon = null,
                        action = {
                            mqState.togglePin()
                        },
                    ),
                    DropdownItem(
                        title = "DEBUG: Age 2hrs",
                        leadingIcon = null,
                        action = {
                            mqState.age()
                        },
                    ),
                )
            )
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


// clean up later
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

// clean up later


class MqState(
    private val coroutineScope: CoroutineScope,
    private val instance: MediaBrowser,
    private val playlistQueueSheet: PlaylistQueueSheet?,
    private val onDetachHead: (() -> Unit)?,
    private val onResetHead: (() -> Unit)?,
) : Player.Listener {
    val isPlaying = MutableStateFlow(instance.isPlaying)

    // shuffle and repeat modes do not need to be manually set for queue loads, they will be set automatically
    val shuffleModeEnabled = MutableStateFlow(instance.shuffleModeEnabled)
    val repeatMode = MutableStateFlow(instance.repeatMode)
    var expanded by mutableStateOf(false)
        private set

    var detachedQueue: MultiQueueObject? by mutableStateOf(null)
        private set

    var activeQueue: MultiQueueObject? by mutableStateOf(null)
        private set

    var inactiveQueues = mutableStateListOf<MultiQueueObject>()
        private set

    init {
        instance.addListener(this)
        init()
    }

    private fun init() {
        coroutineScope.launch {
            activeQueue = null
            detachedQueue = null
            inactiveQueues.clear()

            instance.getQueue()?.let {
                activeQueue = it
            }
            instance.getInactiveQueues().toMutableList().let {
                inactiveQueues.addAll(it)
            }
        }
    }

    fun getQueueListSize(): Int = inactiveQueues.size + if (activeQueue == null) 0 else 1

    fun getQueueTitle(): String? {
        return if (!isDetached()) {
            activeQueue?.title
        } else {
            detachedQueue?.title
        }
    }

    fun getQueueLength(): Long {
        return if (!isDetached()) {
            activeQueue?.getDuration() ?: 0L
        } else detachedQueue?.getDuration() ?: 0L
    }

    fun getQueuePositionStr(): String {
        return if (!isDetached()) {
            activeQueue?.let {
                "${(instance.currentMediaItemIndex) + 1} / ${it.getSize()}"
            }
        } else {
            detachedQueue?.let {
                "${it.startIndex + 1} / ${it.getSize()}"
            }
        } ?: "–/–"
    }

    fun isDetached(): Boolean = detachedQueue != null

    fun detach(index: Int) {
        onDetachHead?.invoke()
        detachedQueue = inactiveQueues.getOrNull(index)
        playlistQueueSheet?.forceUpdate(index)
    }

    fun detach(mq: MultiQueueObject) {
        onDetachHead?.invoke()
        detachedQueue = mq
        playlistQueueSheet?.forceUpdate(inactiveQueues.indexOf(mq))
    }

    fun resetHead(updateSongList: Boolean = true) {
        onResetHead?.invoke()
        detachedQueue = null
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

    private fun expand() {
        expanded = true
    }

    private fun collapse() {
        expanded = false
        resetHead()
    }

    fun removeQueue(index: Int = getQueueListSize() - 1) {
        val status = instance.deleteQueue(index)
        if (!status) return
        coroutineScope.launch {
            init()
        }
        detachedQueue?.repeatMode?.let {
            onRepeatModeChanged(it)
        }
        detachedQueue?.shuffleModeEnabled?.let {
            onShuffleModeEnabledChanged(it)
        }
    }

    fun loadDetached() {
        instance.loadQueue(inactiveQueues.indexOf(detachedQueue))
        expanded = false
        resetHead(false)
        coroutineScope.launch {
            delay(500)
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
        instance.shuffleModeEnabled = !instance.shuffleModeEnabled
    }


    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying.value = isPlaying
    }

    override fun onRepeatModeChanged(repeatMode: @Player.RepeatMode Int) {
        this.repeatMode.value = repeatMode
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        this.shuffleModeEnabled.value = shuffleModeEnabled
    }

    fun togglePin(index: Int = inactiveQueues.indexOf(getCurrentQueue())) {
        // in the UI, active queue is appended onto the end of inactives
        val index = if (index >= inactiveQueues.size) {
            -1
        } else {
            index
        }
        val queue = (if (index == -1) activeQueue else inactiveQueues[index])!!

        if (queue.expiry.value != null) {
            if (instance.pinQueue(index)) {
                queue.expiry.value = null
            }
        } else {
            val expiry = instance.unpinQueue(index)
            if (expiry != -1L) {
                queue.expiry.value = expiry
            }
        }
    }

    /**
     * Get currently visible queue in the ui. Do not assume the media item list is complete.
     */
    fun getCurrentQueue() = detachedQueue ?: activeQueue
    fun age() {
        instance.age()
    }
}

@Composable
fun rememberMqState(
    coroutineScope: CoroutineScope,
    instance: MediaBrowser,
    playlistQueueSheet: PlaylistQueueSheet?,
    onDetachHead: (() -> Unit)?,
    onResetHead: (() -> Unit)?,
): MqState {
    return remember {
        MqState(coroutineScope, instance, playlistQueueSheet, onDetachHead, onResetHead)
    } // TODO: rememberSaveable
}
