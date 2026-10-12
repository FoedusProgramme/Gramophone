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

package org.akanework.gramophone.ui.components.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_OFF
import coil3.compose.AsyncImage
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.compose.TileButton
import org.akanework.gramophone.ui.components.compose.TintedIcon
import org.akanework.gramophone.ui.components.home.EDITABLE_ROW_ICON_END
import org.akanework.gramophone.ui.components.home.EDITABLE_ROW_ICON_START
import org.akanework.gramophone.ui.components.home.FloorCenterVertically
import org.akanework.gramophone.ui.components.home.LIST_ROUND_CORNER_SIZE
import org.akanework.gramophone.ui.components.home.SingleLineText
import org.akanework.gramophone.ui.components.home.rememberDefaultCoverPainter
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlayState
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LANDSCAPE_MARGIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LYRIC_COVER_FADE_MS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.PORTRAIT_MARGIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_BOTTOM_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_THICKNESS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_TOP_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_HANDLE_WIDTH
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_PREVIEW_BOTTOM_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_PREVIEW_PROGRESS_HEIGHT
import org.akanework.gramophone.ui.components.player.PlayerUtilities.absolute
import org.akanework.gramophone.ui.components.player.PlayerUtilities.absoluteUnbounded
import org.akanework.gramophone.ui.components.player.PlayerUtilities.expandedContentAlpha
import org.akanework.gramophone.ui.fragments.compose.MqList
import org.akanework.gramophone.ui.fragments.compose.MqState
import org.akanework.gramophone.ui.fragments.compose.QueueInfo
import org.akanework.gramophone.ui.fragments.compose.rememberMqState
import kotlin.math.pow

/*
 * The queue, its handle over its songs, under the full player's bottom row and below the screen.
 * Dragged up by the row, the two come up together over the player, the row fading out as the
 * queue fades in, and the player fading out under them as the song playing fades in at its top,
 * above the handle.
 */

/** The preview's play and next buttons: tall tiles side by side, rounded off at the end. */
private val PREVIEW_BUTTON_HEIGHT = 48.dp
private val PREVIEW_BUTTON_CORNER = 6.dp
private val PREVIEW_BUTTON_END_CORNER = 20.dp
private val PREVIEW_ICON_SIZE = 22.dp

/** How far up the queue is when the bottom row has faded out, and the queue starts to fade in. */
private const val ROW_FADE_END = 0.3f

/** The bottom row's opacity with the queue [progress] of the way up. */
private fun rowAlpha(progress: Float) = (1f - progress / ROW_FADE_END).coerceIn(0f, 1f)

/** The queue's, its handle's and songs', opacity with it [progress] of the way up. */
private fun queueAlpha(progress: Float) =
    ((progress - ROW_FADE_END) / (1f - ROW_FADE_END)).coerceIn(0f, 1f)

/** The drag handle's colour's opacity, as Material's. */
private const val HANDLE_ALPHA = 0.4f

@Composable
internal fun QueueSheet(
    state: NowPlayingSheetState,
    mediaController: MediaControllerViewModel,
    /** The sheet at the current progress, read in the layout and draw phases. */
    frame: () -> SheetFrame,
    geometry: SheetGeometry,
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    lyrics: LyricsOverlayState,
    queue: QueueRevealState,
) {
    val expanding by remember(state) { derivedStateOf { state.progress > 0f } }
    // Out of the lyrics' way with the rest of the player
    val lyricsCovering by remember(lyrics) { derivedStateOf { lyrics.covering } }
    val visibility by animateFloatAsState(
        targetValue = if (lyricsCovering) 0f else 1f,
        animationSpec = tween(LYRIC_COVER_FADE_MS),
        label = "queue lyrics fade",
    )
    val shown by remember { derivedStateOf { visibility > 0f } }
    if (!expanding || !shown) return

    // Composed once the player is open, so the queue is ready when it starts to come up
    val queueComposed by remember(state, queue) {
        derivedStateOf { (state.expandedTarget && state.progress >= 1f) || queue.shown }
    }
    val drag = remember(state, queue) { BottomRowDrag(state, queue) }
    // Clear of the cutouts and side navigation bars, which the landscape column already is
    val sides = with(LocalDensity.current) {
        if (geometry.isWideLandscape) {
            PaddingValues(0.dp)
        } else {
            PaddingValues.Absolute(
                left = geometry.leftInset.toDp(),
                right = geometry.rightInset.toDp(),
            )
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mqState = rememberMqState(context, scope, mediaController, geometry.isWideLandscape, queue::hide) ?: return
    DisposableEffect(mqState) {
        mqState.onShow()
        onDispose { mqState.onHide() }
    }

    Box(Modifier.onSheet(frame)) {
        Box(
            Modifier.pageFollowingCover(frame) {
                alpha = expandedContentAlpha(it.progress) * visibility
            },
        ) {
            // Two layers come up together: the queue, its handle over its songs, opaque so it
            // hides the player as it comes up over it, and over it the bottom row. The row fades
            // out first, then the queue in, see rowAlpha and queueAlpha. The queue's down in the
            // player's colour, so there's nothing to see of it but the row, and comes up in the
            // surface container's.
            val sheetShift = Modifier.graphicsLayer {
                translationY = -queue.progress * queue.travelPx
            }
            Column(
                Modifier
                    .absoluteUnbounded { geometry.queuePanelBounds }
                    .then(sheetShift)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .drawBehind {
                        val down = scheme.color { surfaceContainerLow }
                        val alpha =
                            (queue.progress.toDouble().pow((1f / 3f).toDouble()) * 1.5).toFloat()
                                .coerceAtMost(1f)
                        drawRect(
                            lerp(down, scheme.color { surfaceContainer }, queue.progress),
                            alpha = alpha
                        )
                    },
            ) {
                QueueHandle(
                    scheme, queue,
                    onClick = { if (queue.shown) queue.hide() else queue.reveal() },
                    modifier = Modifier.bottomRowDrag(drag),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (queueComposed) {
                        MaterialTheme(colorScheme = scheme.target) {
                            QueueBody(
                                mqState,
                                reveal = queue,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(sides)
                                    .graphicsLayer { alpha = queueAlpha(queue.progress) },
                            ) {
                                if (queue.shown && geometry.isWideLandscape) {
                                    QueueHeader(
                                        player, mqState, actions, scheme, queue, drag, geometry, sides
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Gone once faded out, so it doesn't take the touches of the queue under it
            if (!queue.revealed) {
                ActionBarRow(
                    player, actions, scheme,
                    horizontalMargin = if (geometry.isWideLandscape) {
                        LANDSCAPE_MARGIN
                    } else {
                        PORTRAIT_MARGIN
                    },
                    modifier = Modifier
                        .absoluteUnbounded { geometry.queueRowBounds }
                        .then(sheetShift)
                        .padding(sides)
                        .bottomRowDrag(drag)
                        .graphicsLayer { alpha = rowAlpha(queue.progress) },
                )
            }
            if (queue.shown && !geometry.isWideLandscape) {
                QueueHeader(
                    player, mqState, actions, scheme, queue, drag, geometry, sides,
                )
            }
        }
    }
}

/**
 * The queue's drag handle, only seen as the queue comes up: until then, it's under the bottom row.
 * Tapped, it brings the queue up or takes it down, for those who can't drag it: what the gone
 * queue button did.
 */
@Composable
private fun QueueHandle(
    scheme: AnimatedColorScheme,
    queue: QueueRevealState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.current_playlist)
    Box(
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = if (queue.shown) stringResource(R.string.expand_less) else null,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .padding(top = QUEUE_HANDLE_TOP_PADDING, bottom = QUEUE_HANDLE_BOTTOM_PADDING),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .size(QUEUE_HANDLE_WIDTH, QUEUE_HANDLE_THICKNESS)
                .graphicsLayer { alpha = queueAlpha(queue.progress) }
                .clip(CircleShape)
                .drawBehind {
                    drawRect(scheme.color { onSurfaceVariant }.copy(alpha = HANDLE_ALPHA))
                },
        )
    }
}

/**
 * The song playing, on the surface colour up to the top of the screen, over its progress. It
 * drags the queue back down, or puts it away when tapped.
 */
@Composable
private fun QueueHeader(
    player: PlayerSheetPlayerState,
    mqState: MqState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    queue: QueueRevealState,
    drag: BottomRowDrag,
    geometry: SheetGeometry,
    sides: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val mqEnabled = geometry.mqEnabled

    val statusTop = with(LocalDensity.current) { geometry.statusTop.toDp() }
    val defaultCover = rememberDefaultCoverPainter(R.drawable.ic_default_cover)

    val isWideLandscape = geometry.isWideLandscape

    Column(
        verticalArrangement = if (isWideLandscape) Arrangement.SpaceBetween else Arrangement.Top,
        modifier = modifier
            // Faded in as one, the status bar's part with it
            .graphicsLayer { alpha = queue.progress }
            .drawBehind {
                if (!isWideLandscape) {
                    drawRect(scheme.color { surface })
                }
            }
            .padding(top = statusTop)
            .bottomRowDrag(drag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.expand_less),
                onClick = queue::hide,
            )
            .padding(sides)
            // In line with the songs' drag handles and remove buttons under it
            .padding(start = EDITABLE_ROW_ICON_START, end = EDITABLE_ROW_ICON_END)
            .then(
                if (isWideLandscape) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier
                },
            ),
    ) {
        BackHandler(mqState.expanded || mqState.isDetached()) {
            if (mqState.isDetached()) {
                mqState.resetHead()
            } else if (!isWideLandscape && mqState.expanded) {
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
                landscape = isWideLandscape,
                onDismiss = queue::hide,
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
                        .heightIn(Dp.Unspecified, if (!isWideLandscape) 300.dp else Dp.Unspecified)
                )
            }
        }

        Column(

        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = FloorCenterVertically,
            ) {
                ActionBar(mqState, scheme, actions, player) {
                    AsyncImage(
                        model = player.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        error = defaultCover,
                        fallback = defaultCover,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(LIST_ROUND_CORNER_SIZE)),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 18.dp),
                    ) {
                        SingleLineText(
                            player.title?.toString().orEmpty(),
                            17.sp,
                            500,
                            scheme.color { onSurface },
                            Modifier.fillMaxWidth(),
                        )
                        SingleLineText(
                            player.artist?.toString().orEmpty(), 14.sp, 400,
                            scheme.color { onSurfaceVariant },
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { player.positionFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QUEUE_PREVIEW_PROGRESS_HEIGHT),
                color = scheme.color { primary },
                trackColor = scheme.color { secondaryContainer },
            )
            Spacer(Modifier.height(QUEUE_PREVIEW_BOTTOM_PADDING))
        }
    }
}


@Composable
fun ActionBar(
    mqState: MqState,
    scheme: AnimatedColorScheme,
    actions: FullPlayerActions,
    player: PlayerSheetPlayerState,
    modifier: Modifier = Modifier,
    centerContent: @Composable RowScope.() -> Unit,
) {
    val repeatMode by mqState.repeatMode.collectAsState()
    val shuffleModeEnabled by mqState.shuffleModeEnabled.collectAsState()

    val isDetached = mqState.isDetached()

    BackHandler(mqState.isEditAllowed) {
        mqState.isEditAllowed = false
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
    ) {
        // left options
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
        ) {
            TileButton(
                enabled = !isDetached,
                onClick = actions.cycleRepeat,
                modifier = Modifier.size(30.dp, PREVIEW_BUTTON_HEIGHT),
                shape = StartShape,
                container = scheme.colorProducer { if (isDetached) this.surfaceContainerLow else secondaryContainer },
            ) {
                TintedIcon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Outlined.RepeatOne
                    } else {
                        Icons.Outlined.Repeat
                    },
                    scheme.colorProducer { onSecondaryContainer.copy(if (repeatMode == REPEAT_MODE_OFF) 0.5f else 1f) },
                    Modifier.size(PREVIEW_ICON_SIZE),
                    stringResource(R.string.repeat_mode),
                )
            }
            Spacer(Modifier.width(3.dp))
            TileButton(
                enabled = !isDetached,
                onClick = {
                    actions.toggleShuffle(!shuffleModeEnabled)
                },
                modifier = Modifier.size(34.dp, PREVIEW_BUTTON_HEIGHT),
                shape = EndShape,
                container = scheme.colorProducer { if (isDetached) this.surfaceContainerLow else secondaryContainer },
            ) {
                TintedIcon(
                    Icons.Outlined.Shuffle,
                    scheme.colorProducer { onSecondaryContainer.copy(if (shuffleModeEnabled) 1f else 0.5f) },
                    Modifier.size(PREVIEW_ICON_SIZE),
                    stringResource(R.string.shuffle),
                )
            }
        }

        // center options
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .weight(1f, false),
            content = centerContent
        )

        // right options
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
        ) {
            TileButton(
                enabled = !isDetached,
                onClick = actions.previous,
                modifier = Modifier.size(30.dp, PREVIEW_BUTTON_HEIGHT),
                shape = StartShape,
                container = scheme.colorProducer { if (isDetached) this.surfaceContainerLow else tertiaryContainer },
            ) {
                TintedIcon(
                    Icons.Outlined.SkipPrevious,
                    scheme.colorProducer { onTertiaryContainer },
                    Modifier.size(PREVIEW_ICON_SIZE),
                    stringResource(R.string.skip_previous),
                )
            }
            Spacer(Modifier.width(3.dp))
            TileButton(
                onClick = {
                    if (isDetached) {
                        mqState.loadDetached()
                    } else {
                        actions.playPause()
                    }
                },
                modifier = Modifier.size(34.dp, PREVIEW_BUTTON_HEIGHT),
                shape = MiddleShape,
                container = scheme.colorProducer { primaryContainer },
            ) {
                PlayPauseIcon(
                    playing = !isDetached && player.showPause,
                    tint = scheme.colorProducer { onPrimaryContainer },
                    modifier = Modifier.size(PREVIEW_ICON_SIZE),
                    contentDescription = stringResource(R.string.play),
                )
            }
            Spacer(Modifier.width(3.dp))
            // Rounded off at the end of the pair
            TileButton(
                enabled = !isDetached,
                onClick = actions.next,
                modifier = Modifier.size(30.dp, PREVIEW_BUTTON_HEIGHT),
                shape = EndShape,
                container = scheme.colorProducer { if (isDetached) this.surfaceContainerLow else tertiaryContainer },
            ) {
                TintedIcon(
                    Icons.Outlined.SkipNext,
                    scheme.colorProducer { onTertiaryContainer },
                    Modifier.size(PREVIEW_ICON_SIZE),
                    stringResource(R.string.skip_next),
                )
            }

        }
    }
}


val StartShape = RoundedCornerShape(
    topStart = PREVIEW_BUTTON_END_CORNER,
    topEnd = PREVIEW_BUTTON_CORNER,
    bottomEnd = PREVIEW_BUTTON_CORNER,
    bottomStart = PREVIEW_BUTTON_END_CORNER,
)

val MiddleShape = RoundedCornerShape(PREVIEW_BUTTON_CORNER)

val EndShape = RoundedCornerShape(
    topStart = PREVIEW_BUTTON_CORNER,
    topEnd = PREVIEW_BUTTON_END_CORNER,
    bottomEnd = PREVIEW_BUTTON_END_CORNER,
    bottomStart = PREVIEW_BUTTON_CORNER,
)