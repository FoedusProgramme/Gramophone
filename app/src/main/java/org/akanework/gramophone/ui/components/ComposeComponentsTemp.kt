package org.akanework.gramophone.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.MultiQueueObject
import org.akanework.gramophone.logic.deleteQueue
import org.akanework.gramophone.logic.getInactiveQueues
import org.akanework.gramophone.logic.getQueue
import org.akanework.gramophone.logic.loadQueue
import org.akanework.gramophone.logic.utils.Flags.MQ_PREVIEW

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
    Row( // wrapper
        modifier = Modifier
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
                Text(
                    text = "${index + 1}. ${mq.title}",
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
                )
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
    mqEnabled: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current

    val mqExpand = mqState.expanded
    val animatedMinHeight by animateDpAsState(
        targetValue = if (mqExpand) 300.dp else 0.dp,
        label = "queueListHeight"
    )

    // clean up later
    val MediumCornerRadius = 12.dp
    val landscape = false
    // clean up later

    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {

        // queue info
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp, 4.dp)
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
                        imageVector = if (mqExpand) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
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

        val lazyQueuesListState = rememberLazyListState()
        LazyColumn(
            state = lazyQueuesListState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(0.dp, animatedMinHeight),
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
                        mqState.detach(mq)
                    },
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
                            mqState.resetHead()
                        },
                    )
                }
            }
            if (mqState.isDetached())
                item {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        IconButton(
                            onClick = {
                                mqState.loadDetached()
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play_arrow),
                                contentDescription = null
                            )
                        }
                    }
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
    private val instance: MediaBrowser?,
    private val playlistQueueSheet: PlaylistQueueSheet?,
) {
    var expanded by mutableStateOf(false)
        private set

    var detachedQueue: MultiQueueObject? by mutableStateOf(null)
        private set

    var activeQueue: MultiQueueObject? by mutableStateOf(null)
        private set

    var inactiveQueues = mutableStateListOf<MultiQueueObject>()
        private set

    init {
        init()
    }

    private fun init() {
        coroutineScope.launch {
            activeQueue = null
            detachedQueue = null
            inactiveQueues.clear()

            instance?.getQueue()?.let {
                activeQueue = it
            }
            instance?.getInactiveQueues()?.toMutableList()?.let {
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
            activeQueue?.queue?.sumOf { it.mediaMetadata.durationMs ?: 0L } ?: 0L
        } else detachedQueue?.queue?.sumOf { it.mediaMetadata.durationMs ?: 0L } ?: 0L
    }

    fun getQueuePositionStr(): String {
        return if (!isDetached()) {
            activeQueue?.let {
                "${(instance?.currentMediaItemIndex ?: -1) + 1} / ${it.getSize()}"
            }
        } else {
            detachedQueue?.let {
                "${it.startIndex + 1} / ${it.getSize()}"
            }
        } ?: "–/–"
    }

    fun isDetached(): Boolean = detachedQueue != null

    fun detach(index: Int) {
        detachedQueue = inactiveQueues.getOrNull(index)
    }

    fun detach(mq: MultiQueueObject) {
        detachedQueue = mq
        playlistQueueSheet?.forceUpdate(inactiveQueues.indexOf(mq))
    }

    fun resetHead() {
        detachedQueue = null
        playlistQueueSheet?.forceUpdate(-1)
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

    fun removeQueue(index: Int) {
        instance?.deleteQueue(index)
        coroutineScope.launch {
            init()
        }
    }

    fun loadDetached() {
        instance?.loadQueue(inactiveQueues.indexOf(detachedQueue))
        expanded = false
        resetHead()
        coroutineScope.launch {
            delay(500)
            init()
        }
    }
}

@Composable
fun rememberMqState(
    coroutineScope: CoroutineScope,
    instance: MediaBrowser?,
    playlistQueueSheet: PlaylistQueueSheet?,
): MqState {
    return remember {
        MqState(coroutineScope, instance, playlistQueueSheet)
    } // TODO: rememberSaveable
}
