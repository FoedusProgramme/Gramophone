/*
 *     Copyright (C) 2025 Akane Foundation
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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.AlarmOn
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlay
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlayState
import org.akanework.gramophone.ui.components.lyrics.LyricsPadding
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LANDSCAPE_MARGIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LANDSCAPE_TOP_BUTTON_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.PORTRAIT_MARGIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.absolute
import org.akanework.gramophone.ui.components.player.PlayerUtilities.absoluteUnbounded
import org.akanework.gramophone.ui.components.player.PlayerUtilities.expandedContentAlpha
import org.akanework.gramophone.ui.components.player.PlayerUtilities.noRippleClickable

@Composable
internal fun FullPlayerContent(
    state: NowPlayingSheetState,
    metrics: PlayerSheetMetrics,
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: ColorScheme,
    lyrics: LyricsOverlayState,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    val p = state.progress
    val contentAlpha = expandedContentAlpha(p)

    // Fade out the player content once the lyrics fully cover it
    val lyricsCovering by player.lyricsCovering.collectAsState()
    val playerVisibility by animateFloatAsState(
        targetValue = if (lyricsCovering) 0f else 1f,
        animationSpec = tween(PlayerUtilities.LYRIC_COVER_FADE_MS),
        label = "player lyrics fade",
    )

    Box(
        Modifier
            .absolute(metrics.sheetLeft, metrics.sheetTop, metrics.sheetWidth, metrics.sheetHeight)
            .clip(RoundedCornerShape(metrics.cornerDp)),
    ) {
        // The lyrics fill the whole screen under the player, padded for the system bars
        LyricsOverlay(
            state = lyrics,
            scheme = scheme,
            padding = LyricsPadding(
                left = metrics.leftInset.toInt(),
                top = metrics.statusTop.toInt(),
                right = metrics.rightInset.toInt(),
                bottom = metrics.bottomInset.toInt(),
            ),
            modifier = Modifier
                .absoluteUnbounded(-metrics.sheetLeft, 0f, metrics.rootWidth, metrics.rootHeight)
                .graphicsLayer { translationY = metrics.contentFollowTop - metrics.sheetTop },
        )
        if (state.progress > 0f && playerVisibility > 0f) {
            Box(
                Modifier
                    .absoluteUnbounded(-metrics.sheetLeft, 0f, metrics.rootWidth, metrics.rootHeight)
                    .graphicsLayer {
                        translationY = metrics.contentFollowTop - metrics.sheetTop
                        alpha = contentAlpha * playerVisibility
                    }
                    .background(scheme.surface)
                    .then(
                        if (lyricsCovering) Modifier
                        else Modifier.draggable(
                            state = rememberDraggableState { delta -> state.onDrag(delta) },
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> state.settle(velocity) },
                        )
                    ),
            ) {
                FullPlayerScaffold(metrics, player, actions, scheme, onOpenDialog)
            }
        }
    }
}

@Composable
private fun FullPlayerScaffold(
    metrics: PlayerSheetMetrics,
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: ColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    val density = LocalDensity.current
    val statusDp = with(density) { metrics.statusTop.toDp() }
    val navDp = with(density) { metrics.bottomInset.toDp() }
    val leftDp = with(density) { metrics.leftInset.toDp() }
    val rightDp = with(density) { metrics.rightInset.toDp() }
    val coverDp = with(density) { metrics.expandedArtSize.toDp() }
    if (metrics.isWideLandscape) {
        LandscapeScaffold(coverDp, statusDp, navDp, leftDp, rightDp, player, actions, scheme, onOpenDialog)
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = statusDp, bottom = navDp, start = leftDp, end = rightDp),
        ) {
            TopButtonRow(player, actions, scheme, onOpenDialog)
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.height(coverDp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(min = 250.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                TitleArtist(player, actions, scheme, PORTRAIT_MARGIN)
                Spacer(Modifier.height(12.dp))
                ProgressSection(player, actions, scheme, PORTRAIT_MARGIN)
                Spacer(Modifier.height(18.dp))
                TransportRow(player, actions, scheme)
            }
            ActionBarRow(player, actions, scheme)
        }
    }
}

@Composable
private fun LandscapeScaffold(
    coverDp: Dp,
    statusDp: Dp,
    navDp: Dp,
    leftDp: Dp,
    rightDp: Dp,
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: ColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = statusDp, bottom = navDp, start = leftDp, end = rightDp),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .padding(start = 24.dp, top = 16.dp, bottom = 25.dp)
                    .fillMaxHeight()
                    .width(coverDp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 10.dp, end = LANDSCAPE_TOP_BUTTON_SIZE),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    TitleArtist(player, actions, scheme, LANDSCAPE_MARGIN)
                    Spacer(Modifier.height(12.dp))
                    ProgressSection(player, actions, scheme, LANDSCAPE_MARGIN)
                    Spacer(Modifier.height(18.dp))
                    TransportRow(player, actions, scheme)
                }
                ActionBarRow(player, actions, scheme)
            }
        }
        TopButtonColumn(
            player, actions, scheme, onOpenDialog,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp),
        )
    }
}

@Composable
private fun TopButtonColumn(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: ColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timerActive by player.timerActive.collectAsState()
    Column(modifier) {
        IconSlot(Icons.Outlined.ExpandMore, scheme.onSurface, 52.dp, 28.dp, actions.minimize)
        IconSlot(Icons.Outlined.Speed, scheme.onSurface, 52.dp, 24.dp) { onOpenDialog(PlayerDialog.Speed) }
        IconSlot(
            image = if (timerActive) Icons.Outlined.AlarmOn else Icons.Outlined.AlarmOff,
            tint = scheme.onSurface, box = 52.dp, icon = 24.dp, onClick = { onOpenDialog(PlayerDialog.Timer) },
        )
    }
}

@Composable
private fun TopButtonRow(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: ColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    val timerActive by player.timerActive.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(24.dp))
        IconSlot(Icons.Outlined.ExpandMore, scheme.onSurface, 52.dp, 28.dp, actions.minimize)
        Spacer(Modifier.weight(1f))
        IconSlot(
            image = if (timerActive) Icons.Outlined.AlarmOn else Icons.Outlined.AlarmOff,
            tint = scheme.onSurface, box = 52.dp, icon = 24.dp, onClick = { onOpenDialog(PlayerDialog.Timer) },
        )
        IconSlot(Icons.Outlined.Speed, scheme.onSurface, 52.dp, 24.dp) { onOpenDialog(PlayerDialog.Speed) }
        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun IconSlot(image: ImageVector, tint: Color, box: Dp, icon: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(box)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(image, contentDescription = null, tint = tint, modifier = Modifier.size(icon))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleArtist(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: ColorScheme,
    horizontalMargin: Dp,
) {
    val title by player.title.collectAsState()
    val artist by player.artist.collectAsState()
    val bold = rememberBooleanPreference("bold_title", true).value
    val centered = rememberBooleanPreference("centered_title", false).value
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title?.toString().orEmpty(),
            color = scheme.primary,
            fontSize = 24.sp,
            fontWeight = if (bold) FontWeight.W600 else FontWeight.W400,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee()
                .noRippleClickable(actions.openAlbum),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = artist?.toString().orEmpty(),
            color = scheme.secondary,
            fontSize = 19.sp,
            fontWeight = FontWeight.W500,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee()
                .noRippleClickable(actions.openArtist),
        )
    }
}

@Composable
private fun ProgressSection(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: ColorScheme,
    horizontalMargin: Dp,
) {
    val positionMs by player.positionMs.collectAsState()
    val durationMs by player.durationMs.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val qualityIcon by player.qualityIcon.collectAsState()
    val qualityText by player.qualityText.collectAsState()
    // While scrubbing, the drag fraction drives both the bar and the position text (no live seek)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val liveFraction = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val fraction = scrub ?: liveFraction
    val displayPositionMs = scrub?.let { (it * duration).toLong() } ?: positionMs
    val defaultProgressBar = rememberBooleanPreference("default_progress_bar", false).value

    val barModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = horizontalMargin)
        .height(48.dp)
    if (defaultProgressBar) {
        Slider(
            value = fraction,
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { actions.seekTo((it * duration).toLong()) }
                scrub = null
            },
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.primary.copy(alpha = PlayerUtilities.SQUIGGLY_TRACK_ALPHA),
            ),
            modifier = barModifier,
        )
    } else {
        SquigglyProgressBar(
            fraction = fraction,
            animating = isPlaying && scrub == null,
            color = scheme.primary,
            trackColor = scheme.primary.copy(alpha = PlayerUtilities.SQUIGGLY_TRACK_ALPHA),
            modifier = barModifier,
            onScrub = { scrub = it },
            onSeek = {
                actions.seekTo((it * duration).toLong())
                scrub = null
            },
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            CalculationUtils.convertDurationToTimeStamp(displayPositionMs),
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
        )
        Spacer(Modifier.weight(1f))
        val quality = qualityText
        if (!quality.isNullOrEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                qualityIcon?.let {
                    Icon(
                        painterResource(it),
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    quality,
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Text(
            CalculationUtils.convertDurationToTimeStamp(durationMs),
            color = scheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
        )
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun TransportRow(player: PlayerSheetPlayerState, actions: FullPlayerActions, scheme: ColorScheme) {
    val isPlaying by player.isPlaying.collectAsState()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            image = Icons.Outlined.SkipPrevious, tint = scheme.onSurface, icon = 38.dp,
            onClick = actions.previous, onLongClick = actions.seekBack,
        )
        Spacer(Modifier.width(8.dp))
        val bg = AnimatedImageVector.animatedVectorResource(R.drawable.bg_play_anim)
        val bgPainter = rememberAnimatedVectorPainter(bg, atEnd = isPlaying)
        Box(
            Modifier
                .size(90.dp)
                .noRippleClickable(actions.playPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = bgPainter,
                contentDescription = null,
                tint = scheme.secondaryContainer,
                modifier = Modifier.size(90.dp),
            )
            PlayPauseIcon(playing = isPlaying, tint = scheme.onSecondaryContainer, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.width(8.dp))
        TransportButton(
            image = Icons.Outlined.SkipNext, tint = scheme.onSurface, icon = 38.dp,
            onClick = actions.next, onLongClick = actions.seekForward,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransportButton(
    image: ImageVector,
    tint: Color,
    icon: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .size(90.dp)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(image, contentDescription = null, tint = tint, modifier = Modifier.size(icon))
    }
}

@Composable
private fun ActionBarRow(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: ColorScheme,
) {
    val repeatMode by player.repeatMode.collectAsState()
    val shuffle by player.shuffleMode.collectAsState()
    val favorite by player.isFavorite.collectAsState()
    val checkTint = { on: Boolean -> if (on) scheme.onSurface else scheme.outlineVariant }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 38.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSlot(Icons.AutoMirrored.Outlined.Article, scheme.onSurface, 48.dp, 24.dp, actions.showLyrics)
        IconSlot(
            image = if (repeatMode == PlayerUtilities.PLAYER_REPEAT_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
            tint = checkTint(repeatMode != PlayerUtilities.PLAYER_REPEAT_OFF),
            box = 48.dp, icon = 24.dp, onClick = actions.cycleRepeat,
        )
        IconSlot(Icons.Outlined.Shuffle, checkTint(shuffle), 48.dp, 24.dp) { actions.toggleShuffle(!shuffle) }
        IconSlot(
            image = if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            tint = if (favorite) scheme.tertiary else scheme.onSurface,
            box = 48.dp, icon = 24.dp, onClick = { actions.toggleFavorite(!favorite) },
        )
        IconSlot(Icons.AutoMirrored.Outlined.PlaylistPlay, scheme.onSurface, 48.dp, 24.dp, actions.showQueue)
    }
}