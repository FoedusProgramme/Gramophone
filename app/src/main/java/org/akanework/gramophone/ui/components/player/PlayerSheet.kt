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

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.animateColorAsState
import android.net.Uri
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import com.materialkolor.ktx.animateColorScheme
import com.materialkolor.ktx.harmonize
import kotlinx.coroutines.flow.MutableStateFlow
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlayState
import org.akanework.gramophone.ui.components.player.PlayerUtilities.COVER_CLICK_MIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.FALLBACK_PAGE_CORNER
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LYRIC_COVER_FADE_MS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_ART_TO_TEXT_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_BUTTON_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_ICON_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SCRIM_MAX_ALPHA
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SETTLED_EPS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TAP_EXPAND_LIMIT
import org.akanework.gramophone.ui.components.player.PlayerUtilities.WIDE_LANDSCAPE_MIN_WIDTH
import org.akanework.gramophone.ui.components.player.PlayerUtilities.absolute
import org.akanework.gramophone.ui.components.player.PlayerUtilities.miniContentAlpha
import org.akanework.gramophone.ui.nav.NAV_TRANSITION_MS
import org.akanework.gramophone.ui.nav.NavAxisEasing
import kotlin.math.roundToInt

/** Insets and visibility of the sheet, provided by [PlayerSheetController]. */
data class SheetChrome(
    val shown: Boolean,
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int,
)

/** Playback state the mini bar + full player render, bridged from the MediaController by the host. */
class PlayerSheetPlayerState {
    val hasMedia = MutableStateFlow(false)
    val isPlaying = MutableStateFlow(false)
    val title: MutableStateFlow<CharSequence?> = MutableStateFlow(null)
    val artist: MutableStateFlow<CharSequence?> = MutableStateFlow(null)
    val artworkUri: MutableStateFlow<Uri?> = MutableStateFlow(null)
    val positionFraction = MutableStateFlow(0f)

    // Full player
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    val repeatMode = MutableStateFlow(0) // Player.REPEAT_MODE_OFF/ONE/ALL
    val shuffleMode = MutableStateFlow(false)
    val isFavorite = MutableStateFlow(false)
    val timerActive = MutableStateFlow(false)
    val qualityIcon: MutableStateFlow<Int?> = MutableStateFlow(null)
    val qualityText: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Whether the lyrics overlay fully covers the player (see [LyricsOverlayState.covering]). */
    val lyricsCovering = MutableStateFlow(false)
}

/**
 * Actions the full player triggers on the MediaController / host. Wired in the host. The not-yet-
 * migrated ones (dialogs, toggles) are filled in over the incremental steps.
 */
class FullPlayerActions(
    val playPause: () -> Unit,
    val previous: () -> Unit,
    val next: () -> Unit,
    val seekBack: () -> Unit,
    val seekForward: () -> Unit,
    val seekTo: (Long) -> Unit,
    val minimize: () -> Unit,
    val cycleRepeat: () -> Unit,
    val toggleShuffle: (Boolean) -> Unit,
    val toggleFavorite: (Boolean) -> Unit,
    val showQueue: () -> Unit,
    val showLyrics: () -> Unit,
    val openAlbum: () -> Unit,
    val openArtist: () -> Unit,
)

@Composable
fun PlayerSheet(
    state: NowPlayingSheetState,
    player: PlayerSheetPlayerState,
    chrome: State<SheetChrome>,
    /** Color the current page wants the bar harmonized to, or null for the app's primary. */
    pageAccent: () -> Color?,
    lyrics: LyricsOverlayState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onCoverClick: () -> Unit,
    onExpandedTargetChanged: (Boolean) -> Unit,
    actions: FullPlayerActions,
    dialogCallbacks: PlayerDialogCallbacks,
) {
    val density = LocalDensity.current
    val c = chrome.value
    val hasMedia by player.hasMedia.collectAsState()
    val artworkUri by player.artworkUri.collectAsState()
    val cookieCover = rememberBooleanPreference("cookie_cover", false).value
    val expandedArtCorner = rememberIntPreference(
        "album_round_corner", integerResource(R.integer.round_corner_radius)
    ).value.dp
    val coverScheme = animateColorScheme(rememberArtworkColorScheme(artworkUri))
    var activeDialog by remember { mutableStateOf<PlayerDialog?>(null) }
    PlayerDialogs(activeDialog, coverScheme, dialogCallbacks, onDismiss = { activeDialog = null })

    val expandedTarget = state.expandedTarget
    SideEffect { onExpandedTargetChanged(expandedTarget) }

    // Slide the whole sheet down by its collapsed height when there is nothing to show. Uses the
    // page transition's duration and easing, so the bar does not disappear faster than the page
    // when entering or leaving settings.
    val showFraction by animateFloatAsState(
        targetValue = if (c.shown && hasMedia) 1f else 0f,
        animationSpec = tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
        label = "sheet show",
    )

    // Accent the bar is harmonized to: the current page's cover color, or the app primary.
    // Animated with the page transition. Only read in the draw phase and in the mini bar
    // content, so the animation does not recompose the sheet on every frame.
    val accent = animateColorAsState(
        pageAccent() ?: MaterialTheme.colorScheme.primary,
        tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
        label = "page accent",
    )
    val barColors = remember(coverScheme) {
        derivedStateOf { nowPlayingColors(coverScheme, accent.value) }
    }
    val miniColors = remember(coverScheme) {
        derivedStateOf {
            MiniBarColors(
                content = coverScheme.onSurface.harmonize(accent.value),
                playButton = coverScheme.secondaryContainer.harmonize(accent.value),
                onPlayButton = coverScheme.onSecondaryContainer.harmonize(accent.value),
            )
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()
        val isWideLandscape = maxWidth >= WIDE_LANDSCAPE_MIN_WIDTH.dp && maxWidth > maxHeight
        val metrics = playerSheetMetrics(
            progress = state.progress,
            rootWidth = rootW,
            rootHeight = rootH,
            statusTop = c.top.toFloat(),
            bottomInset = c.bottom.toFloat(),
            leftInset = c.left.toFloat(),
            rightInset = c.right.toFloat(),
            isWideLandscape = isWideLandscape,
            pageCorner = deviceScreenCornerRadius(),
            density = density,
            expandedArtCorner = expandedArtCorner,
        )
        state.travelPx = metrics.travelPx

        // Scrim behind the sheet (only meaningful mid/late morph; fully covered at progress = 1).
        Box(
            Modifier
                .fillMaxSize()
                .alpha((metrics.eased * SCRIM_MAX_ALPHA).coerceIn(0f, 1f))
                .background(Color.Black),
        )

        Box(
            Modifier
                .fillMaxSize()
                // Slide the whole floating sheet fully below the screen when there's nothing to show.
                .graphicsLayer { translationY = (1f - showFraction) * metrics.collapsedFootprint },
        ) {
            SheetSurface(metrics) { barColors.value.bar }
            ProgressFill(player, metrics) { barColors.value.fill }
            FullPlayerContent(
                state, metrics, player, actions, coverScheme, lyrics,
                onOpenDialog = { activeDialog = it },
            )
            val fullyExpanded = state.expandedTarget && metrics.eased > SETTLED_EPS
            if (!fullyExpanded) {
                SheetInteraction(
                    state, player, metrics, { miniColors.value }, onPlayPause, onNext,
                )
            }
            SharedArtwork(state, player, metrics, cookie = cookieCover, onCoverClick = onCoverClick)
        }
    }
}

/** Mini bar text and play button colors. */
@Immutable
private data class MiniBarColors(val content: Color, val playButton: Color, val onPlayButton: Color)

/** Sheet background: [collapsedColor] when collapsed, the surface color when expanded. */
@Composable
private fun SheetSurface(metrics: PlayerSheetMetrics, collapsedColor: () -> Color) {
    val expanded = MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .absolute(metrics.sheetLeft, metrics.sheetTop, metrics.sheetWidth, metrics.sheetHeight)
            .clip(RoundedCornerShape(metrics.cornerDp))
            .drawBehind {
                drawRect(lerp(collapsedColor(), expanded, metrics.eased.coerceIn(0f, 1f)))
            },
    )
}

// Progress bar
@Composable
private fun ProgressFill(
    player: PlayerSheetPlayerState,
    metrics: PlayerSheetMetrics,
    color: () -> Color,
) {
    val fraction by player.positionFraction.collectAsState()
    val alpha = miniContentAlpha(metrics.eased)
    if (alpha <= 0f) return
    Box(
        Modifier
            .absolute(metrics.sheetLeft, metrics.sheetTop, metrics.sheetWidth, metrics.sheetHeight)
            .alpha(alpha)
            .clip(RoundedCornerShape(metrics.cornerDp)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .drawBehind { drawRect(color()) },
        )
    }
}

@Composable
private fun SheetInteraction(
    state: NowPlayingSheetState,
    player: PlayerSheetPlayerState,
    metrics: PlayerSheetMetrics,
    colors: () -> MiniBarColors,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val contentAlpha = miniContentAlpha(metrics.eased)

    Box(
        Modifier
            .absolute(metrics.sheetLeft, metrics.sheetTop, metrics.sheetWidth, metrics.sheetHeight)
            .draggable(
                state = rememberDraggableState { delta -> state.onDrag(delta) },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> state.settle(velocity) },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = metrics.eased < TAP_EXPAND_LIMIT,
                onClick = state::expand,
            ),
    ) {
        if (contentAlpha > 0f) {
            val (contentColor, playButtonContainer, playButtonContent) = colors()
            val title by player.title.collectAsState()
            val artist by player.artist.collectAsState()
            val isPlaying by player.isPlaying.collectAsState()

            // Leave room for the cover slot
            val startPadding = with(density) {
                (metrics.collapsedArtLeft - metrics.sheetLeft + metrics.collapsedArtSize +
                    MINI_ART_TO_TEXT_GAP.toPx()).toDp()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(with(density) { metrics.collapsedHeight.toDp() })
                    .alpha(contentAlpha)
                    .padding(start = startPadding, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Title and artist on a single line (artist dimmed), ellipsised as a whole.
                val label = remember(title, artist, contentColor) {
                    buildAnnotatedString {
                        append(title?.toString().orEmpty())
                        val a = artist?.toString().orEmpty()
                        if (a.isNotEmpty()) {
                            append("  ·  ")
                            withStyle(SpanStyle(color = contentColor.copy(alpha = 0.7f))) { append(a) }
                        }
                    }
                }
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(MINI_BUTTON_SIZE),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = playButtonContainer,
                        contentColor = playButtonContent,
                    ),
                ) {
                    PlayPauseIcon(playing = isPlaying, tint = playButtonContent, modifier = Modifier.size(MINI_ICON_SIZE))
                }
                IconButton(onClick = onNext, modifier = Modifier.size(MINI_BUTTON_SIZE)) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(MINI_ICON_SIZE),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
internal fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val image = AnimatedImageVector.animatedVectorResource(R.drawable.ic_play_to_pause_anim)
    val painter = rememberAnimatedVectorPainter(image, atEnd = playing)
    Icon(painter = painter, contentDescription = null, tint = tint, modifier = modifier)
}

@Composable
private fun SharedArtwork(
    state: NowPlayingSheetState,
    player: PlayerSheetPlayerState,
    metrics: PlayerSheetMetrics,
    cookie: Boolean,
    onCoverClick: () -> Unit,
) {
    val artwork by player.artworkUri.collectAsState()
    // Fade the shared cover out of the way when the lyric overlay covers the player
    val lyricsCovering by player.lyricsCovering.collectAsState()
    val coverAlpha by animateFloatAsState(
        targetValue = if (lyricsCovering) 0f else 1f,
        animationSpec = tween(LYRIC_COVER_FADE_MS),
        label = "cover lyrics fade",
    )

    val shape = if (cookie) CookieMorphShape(metrics.eased) else RoundedCornerShape(metrics.artCornerDp)
    val context = LocalPlatformContext.current
    val requestSizePx = metrics.rootWidth.roundToInt().coerceAtLeast(1)
    val model: Any = artwork ?: R.drawable.ic_default_cover
    val request = remember(model, requestSizePx) {
        ImageRequest.Builder(context)
            .data(model)
            .size(requestSizePx)
            .precision(Precision.INEXACT)
            .build()
    }

    val clickModifier =
        if (metrics.eased > COVER_CLICK_MIN) Modifier.clickable(onClick = onCoverClick) else Modifier
    val dragState = rememberDraggableState { delta -> state.onDrag(delta) }
    val gestures =
        if (lyricsCovering) Modifier
        else Modifier
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity -> state.settle(velocity) },
            )
            .then(clickModifier)
    Box(
        Modifier
            .absolute(metrics.sheetLeft, metrics.sheetTop, metrics.sheetWidth, metrics.sheetHeight)
            .clip(RoundedCornerShape(metrics.cornerDp)),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .absolute(
                    metrics.artLeftRoot - metrics.sheetLeft,
                    metrics.artTopRoot - metrics.sheetTop,
                    metrics.artSize,
                    metrics.artSize,
                )
                .alpha(coverAlpha)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(gestures),
        )
    }
}

@Composable
private fun deviceScreenCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view) {
        val radiusPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val insets = view.rootWindowInsets
            intArrayOf(
                RoundedCorner.POSITION_TOP_LEFT,
                RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_LEFT,
                RoundedCorner.POSITION_BOTTOM_RIGHT,
            ).maxOf { insets?.getRoundedCorner(it)?.radius ?: 0 }
        } else {
            0
        }
        with(density) { radiusPx.toDp() }.takeIf { it > 0.dp } ?: FALLBACK_PAGE_CORNER
    }
}
