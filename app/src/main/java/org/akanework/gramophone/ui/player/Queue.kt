/*
 *     Copyright (C) 2026 The Gramophone authors
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

package org.akanework.gramophone.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.compose.BottomSheet
import org.akanework.gramophone.ui.components.compose.BottomSheetState
import org.akanework.gramophone.ui.components.player.AnimatedColorScheme
import org.akanework.gramophone.ui.components.player.FullPlayerActions
import org.akanework.gramophone.ui.components.player.HANDLE_ALPHA
import org.akanework.gramophone.ui.components.player.PlayerSheetPlayerState
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_CORNER
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_BOTTOM_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_THICKNESS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_TOP_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_WIDTH
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_PREVIEW_HEIGHT
import org.akanework.gramophone.ui.components.player.queueAlpha
import org.akanework.gramophone.ui.components.player.rowAlpha
import org.akanework.gramophone.ui.player.components.rememberMqState
import org.akanework.gramophone.ui.player.components.ActionBarRow
import org.akanework.gramophone.ui.player.components.QueueBody
import org.akanework.gramophone.ui.player.components.QueueHeader
import kotlin.math.pow

@Composable
fun BottomSheetQueue(
    queueSheetState: BottomSheetState,
    playerLayout: PlayerLayout,
    playerState: PlayerSheetPlayerState,
    mediaController: MediaControllerViewModel,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    onClearQueue: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val dualPane = playerLayout.dualPane
    val fullHeight = playerLayout.fullHeight
    val mqEnabled = playerLayout.mqEnabled
    val queueInsets = playerLayout.queueInsets
    // QueueHeader is show when we have enough vertical space. Landscape (dual pane) takes priority and header is over in the left pane
    val showQueueHeader = fullHeight && !dualPane

    BottomSheet(
        state = queueSheetState,
        contentBackground = {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val down = scheme.color { surfaceContainerLow }
                        val alpha =
                            (queueSheetState.progress.toDouble().pow((1f / 3f).toDouble()) * 1.5).toFloat()
                                .coerceAtMost(1f)
                        drawRect(
                            color = lerp(down, scheme.color { surfaceContainer }, queueSheetState.progress),
                            alpha = alpha
                        )
                    },
            )
        },
        collapsedWidth = if (dualPane) 0.55f else 1f,
        collapsedContent = {
            ActionBarRow(
                queueSheetState, playerState, actions, scheme,
                horizontalMargin = 36.dp,
                modifier = Modifier
                    .fillMaxWidth(if (dualPane) 0.55f else 1f)
                    .align(Alignment.TopStart)
                    .graphicsLayer { alpha = rowAlpha(queueSheetState.progress) },
            )
        },
        contentShape = if (!showQueueHeader) {
            RoundedCornerShape(lerp(MINI_CORNER, 0.dp, queueSheetState.progress))
        } else null,
    ) {
        val mqState = rememberMqState(context, scope, mediaController, dualPane, onClearQueue)
            ?: return@BottomSheet
        DisposableEffect(mqState) {
            mqState.onShow()
            onDispose { mqState.onHide() }
        }

        Column(
            Modifier
                .windowInsetsPadding(queueInsets.only(INSETS_U))
                .padding(top = with(density) { queueInsets.getTop(density).toDp() })
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .drawBehind {
                    val down = scheme.color { surfaceContainerLow }
                    val alpha =
                        (queueSheetState.progress.toDouble().pow((1f / 3f).toDouble()) * 1.5).toFloat()
                            .coerceAtMost(1f)
                    drawRect(
                        lerp(down, scheme.color { surfaceContainer }, queueSheetState.progress),
                        alpha = alpha
                    )
                },
        ) {
            // queue drag handle
            val description = stringResource(R.string.current_playlist)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = if (queueSheetState.isExpanded) stringResource(R.string.expand_less) else null,
                        onClick = { if (queueSheetState.isExpanded) queueSheetState.collapseSoft() else queueSheetState.expandSoft() },
                    )
                    .semantics { contentDescription = description }
                    .padding(top = QUEUE_HANDLE_TOP_PADDING, bottom = QUEUE_HANDLE_BOTTOM_PADDING),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .size(QUEUE_HANDLE_WIDTH, QUEUE_HANDLE_THICKNESS)
                        .graphicsLayer { alpha = queueAlpha(queueSheetState.progress) }
                        .clip(CircleShape)
                        .drawBehind {
                            drawRect(scheme.color { onSurfaceVariant }.copy(alpha = HANDLE_ALPHA))
                        },
                )
            }

            // queue main content body
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                MaterialTheme(colorScheme = scheme.target) {
                    QueueBody(
                        mqState = mqState,
                        queueSheetState = queueSheetState,
                        onClearQueue = onClearQueue,
                        dualPane = dualPane,
                        fullHeight = fullHeight,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = queueAlpha(queueSheetState.progress) },
                    ) {
                        QueueHeader(
                            queueSheetState, playerState, mqState, actions, scheme,
                            queueInsets, dualPane, fullHeight, mqEnabled
                        )
                    }
                }
            }
        }

        // queue header
        if (showQueueHeader && !queueSheetState.isCollapsed) {
            Box(
                Modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        val p = queueSheetState.progress.coerceIn(0f, 1f)
                        translationY =
                            -(queueSheetState.expandedBound - queueSheetState.value + (QUEUE_PREVIEW_HEIGHT - 2.dp)).roundToPx() - size.height * (1f - p)
                        alpha = p
                    }
            ) {
                QueueHeader(
                    queueSheetState, playerState, mqState, actions, scheme,
                    queueInsets, dualPane, fullHeight, mqEnabled
                )
            }
        }
    }
}
