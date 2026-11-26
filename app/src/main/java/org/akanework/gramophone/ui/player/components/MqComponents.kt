package org.akanework.gramophone.ui.player.components


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.MultiQueueObject
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.components.compose.QueueDropdownMenu


@Composable
fun MqListItem(
    mqState: MqState,
//    queueListState: ReorderableLazyListState, // sh.calvin.reorderable.ReorderableLazyListState
    index: Int,
    mq: MultiQueueObject,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 0.dp,
    isActiveQueue: Boolean = false,
    isHighlightedQueue: Boolean = false,
    isEditAllowed: Boolean = false,
    mqEnabled: Boolean = true,
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
                enabled = mqEnabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row( // row contents (wrapper is needed for margin)
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 56.dp)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, false)
            ) {
                if (mqEnabled && isEditAllowed) {
                    if (isPinned) {
                        IconButton(
                            onClick = {
                                mqState.togglePin(pinId)
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_keep_off),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
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
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                if (!mqEnabled) {
                    Spacer(Modifier.width(8.dp))
                }
                if (mqEnabled && isActiveQueue && !isEditAllowed) {
                    IconButton(
                        onClick = {
                            mqState.togglePin(pinId)
                        },
                    ) {
                        Icon(
                            painter = painterResource(if (isPinned) R.drawable.ic_keep_off else R.drawable.ic_keep),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
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
                            .padding(horizontal = 4.dp)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!isPinned && !isActiveQueue) {
                                    val remainingTimeMs = (expiry!! - System.currentTimeMillis())
                                    Icon(
                                        painter = painterResource(
                                            if (remainingTimeMs < 1800000) {
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
                                        text = convertDurationToTimeStamp(remainingTimeMs),
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
                if (isEditAllowed || isActiveQueue || mqState.expanded) {
                    QueueDropdownMenu(
                        mqState = mqState,
                        mq = mq,
                        isPinned = isPinned,
                        isActiveQueue = isActiveQueue,
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
fun QueueInfo(
    mqState: MqState,
    mqEnabled: Boolean,
    dualPane: Boolean,
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
                .clickable(enabled = mqEnabled && !dualPane) {
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
                    mqEnabled = mqEnabled,
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
                enabled = mqState.isEditAllowed || (mqEnabled && !mqState.inactiveQueues.isEmpty() && !dualPane),
                onClick = {
                    if (mqState.isEditAllowed) {
                        mqState.isEditAllowed = false
                    } else {
                        mqState.toggleExpand()
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                },
            ) {
                Icon(
                    imageVector = if (mqState.isEditAllowed) {
                        Icons.AutoMirrored.Outlined.ArrowBack
                    } else if (mqState.expanded) {
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
        ) { _, mq ->
            MqListItem(
                mqState = mqState,
                index = mq.index,
                mq = mq,
                isActiveQueue = false,
                isHighlightedQueue = mq == mqState.detachedQueue,
                isEditAllowed = mqState.isEditAllowed,
                onClick = {
                    if (mqState.detachedQueue != mq) {
                        mqState.detach(mq)
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
