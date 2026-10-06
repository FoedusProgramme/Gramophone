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

import android.net.Uri
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.error
import coil3.size.Precision
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.showsPause
import org.akanework.gramophone.logic.utils.AudioFormatDetector
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.home.rememberDefaultCoverPainter
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
import org.akanework.gramophone.ui.theme.harmonizeBy
import kotlin.math.roundToInt

/** The system bar and cutout insets (px) the sheet spans, which its content keeps clear of. */
@Immutable
data class SheetInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Playback state the mini bar and the full player render, written from the MediaController and
 * the service by the sheet's host. Snapshot state, only written on the main thread.
 */
@Stable
class PlayerSheetPlayerState(
    /** Where playback is, shared with the lyrics. */
    val clock: PlaybackClock,
) {
    var isPlaying by mutableStateOf(false)

    /** What the play/pause buttons show, see [showsPause]. */
    var showPause by mutableStateOf(false)
    var title by mutableStateOf<CharSequence?>(null)
    var artist by mutableStateOf<CharSequence?>(null)
    var artworkUri by mutableStateOf<Uri?>(null)
    val positionFraction: Float
        get() = clock.fraction

    // Full player
    val positionMs: Long
        get() = clock.positionMs
    val durationMs: Long
        get() = clock.durationMs
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var shuffleMode by mutableStateOf(false)
    var isFavorite by mutableStateOf(false)
    var timerActive by mutableStateOf(false)
    var qualityIcon by mutableStateOf<Int?>(null)
    var qualityText by mutableStateOf<String?>(null)

    /** The playback path the service last reported, shown in the audio signal chain dialog. */
    var audioFormat by mutableStateOf<AudioFormatDetector.AudioFormats?>(null)
}

/** Actions the mini bar and the full player trigger on the MediaController / host. */
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
    val showLyrics: () -> Unit,
    val openAlbum: () -> Unit,
    val openArtist: () -> Unit,
    /** The expanded cover was tapped: open the song's details. */
    val openSongDetails: () -> Unit,
)

/**
 * The floating mini bar, which [state] expands into the full screen player, over a scrim. It
 * spans the system bars and cutouts, [insets], and keeps its content clear of them. The full
 * player's bottom row brings the [queue]'s [queueContent] up over it.
 */
@Composable
fun PlayerSheet(
    state: NowPlayingSheetState,
    player: PlayerSheetPlayerState,
    insets: SheetInsets,
    /** Whether the current page is themed from a cover, where the bar isn't harmonized. */
    pageTinted: Boolean,
    lyrics: LyricsOverlayState,
    queue: QueueRevealState,
    actions: FullPlayerActions,
    dialogCallbacks: PlayerDialogCallbacks,
    modifier: Modifier = Modifier,
    queueContent: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val cookieCover = rememberBooleanPreference("cookie_cover", false).value
    val expandedArtCorner = rememberIntPreference(
        "album_round_corner", integerResource(R.integer.round_corner_radius)
    ).value.dp
    // Its colours are read in the draw phase where they can be, so a song change's colour
    // animation mostly redraws instead of recomposing the player every frame.
    val targetScheme = rememberArtworkColorScheme(player.artworkUri)
    // Without a cover it's the app theme, which animates by itself when the theme changes
    val coverScheme = rememberAnimatedColorScheme(
        targetScheme, live = targetScheme === MaterialTheme.colorScheme,
    )
    var activeDialog by remember { mutableStateOf<PlayerDialog?>(null) }
    PlayerDialogs(
        activeDialog, coverScheme.target, dialogCallbacks, onDismiss = { activeDialog = null },
    )

    // How far the bar leans towards the app's hue: fully, except on a page themed from a cover,
    // which shows the cover's own colours. Animated with the page transition. Only read in the
    // draw phase and in the mini bar content, so the animation does not recompose the sheet on
    // every frame.
    val harmony = animateFloatAsState(
        if (pageTinted) 0f else 1f,
        tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
        label = "page harmony",
    )
    val appPrimary = rememberUpdatedState(MaterialTheme.colorScheme.primary)
    val barColors = remember(coverScheme) {
        derivedStateOf { nowPlayingColors(appPrimary.value, harmony.value, coverScheme::color) }
    }
    val miniColors = remember(coverScheme) {
        derivedStateOf {
            val primary = appPrimary.value
            val f = harmony.value
            MiniBarColors(
                content = coverScheme.color { onSurface }.harmonizeBy(primary, f),
                playButton = coverScheme.color { secondaryContainer }.harmonizeBy(primary, f),
                onPlayButton = coverScheme.color { onSecondaryContainer }.harmonizeBy(primary, f),
            )
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()
        val isWideLandscape = maxWidth >= WIDE_LANDSCAPE_MIN_WIDTH.dp && maxWidth > maxHeight
        val pageCorner = deviceScreenCornerRadius()
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val geometry = remember(
            rootW, rootH, insets, isWideLandscape, isRtl, pageCorner, density, expandedArtCorner,
        ) {
            SheetGeometry(
                rootWidth = rootW,
                rootHeight = rootH,
                statusTop = insets.top.toFloat(),
                bottomInset = insets.bottom.toFloat(),
                leftInset = insets.left.toFloat(),
                rightInset = insets.right.toFloat(),
                isWideLandscape = isWideLandscape,
                isRtl = isRtl,
                pageCorner = pageCorner,
                expandedArtCorner = expandedArtCorner,
                density = density,
            )
        }
        // The sheet at the current progress. Only read in the layout and draw phases, so a moving
        // sheet lays out and draws again without recomposing every frame.
        val current = remember(geometry) { derivedStateOf { geometry.frameAt(state.progress) } }
        val frame = remember(current) { { current.value } }
        state.travelPx = geometry.travelPx
        queue.travelPx = geometry.queueTravel

        // Scrim behind the sheet (only meaningful mid/late morph; fully covered at progress = 1,
        // where it isn't drawn).
        // Faded in its own layer, so it doesn't redraw what's around it every frame.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (frame().progress * SCRIM_MAX_ALPHA).coerceIn(0f, 1f)
                    // A single rect: no offscreen buffer needed to fade it
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .drawBehind { if (!state.coversScreen) drawRect(Color.Black) },
        )

        Box(
            Modifier
                .fillMaxSize()
                // Slide the whole floating sheet fully below the screen when there's nothing to
                // show.
                .graphicsLayer {
                    translationY = (1f - state.shownFraction) * geometry.collapsedFootprint
                },
        ) {
            // Under the lyrics while they cover the whole screen, the surface isn't drawn
            SheetSurface(frame, hidden = { state.coversScreen && lyrics.covering }) {
                barColors.value.bar
            }
            ProgressFill(player, frame) { barColors.value.fill }
            FullPlayerContent(
                state, frame, geometry, player, actions, coverScheme, lyrics, queue,
                onOpenDialog = { activeDialog = it },
            )
            val fullyExpanded by remember(state) {
                derivedStateOf { state.expandedTarget && state.progress > SETTLED_EPS }
            }
            if (!fullyExpanded) {
                SheetInteraction(state, player, geometry, frame, { miniColors.value }, actions)
            }
            SharedArtwork(
                state, player, lyrics, queue, frame, geometry.rootWidth, cookie = cookieCover,
                // Beside the queue in landscape, it stays
                fadesOverQueue = !geometry.isWideLandscape,
                onClick = actions.openSongDetails,
            )
            QueueDrawer(
                state, frame, geometry, player, actions, coverScheme, lyrics, queue, queueContent,
            )
        }
    }
}

/** Mini bar text and play button colors. */
@Immutable
private data class MiniBarColors(
    val content: Color,
    val playButton: Color,
    val onPlayButton: Color,
)

/** Sheet background: [collapsedColor] when collapsed, the low surface container when expanded. */
@Composable
private fun SheetSurface(
    frame: () -> SheetFrame,
    hidden: () -> Boolean,
    collapsedColor: () -> Color,
) {
    val expanded = MaterialTheme.colorScheme.surfaceContainerLow
    Box(
        Modifier
            .onSheet(frame)
            .drawBehind {
                if (hidden()) return@drawBehind
                drawRect(lerp(collapsedColor(), expanded, frame().progress.coerceIn(0f, 1f)))
            },
    )
}

// Progress bar
@Composable
private fun ProgressFill(
    player: PlayerSheetPlayerState,
    frame: () -> SheetFrame,
    color: () -> Color,
) {
    val shown by remember(frame) { derivedStateOf { miniContentAlpha(frame().progress) > 0f } }
    if (!shown) return
    Box(
        Modifier
            .onSheet(frame)
            .graphicsLayer { alpha = miniContentAlpha(frame().progress) },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(player.positionFraction.coerceIn(0f, 1f))
                .drawBehind { drawRect(color()) },
        )
    }
}

@Composable
private fun SheetInteraction(
    state: NowPlayingSheetState,
    player: PlayerSheetPlayerState,
    geometry: SheetGeometry,
    frame: () -> SheetFrame,
    colors: () -> MiniBarColors,
    actions: FullPlayerActions,
) {
    val interaction = remember { MutableInteractionSource() }
    val contentShown by remember(frame) {
        derivedStateOf { miniContentAlpha(frame().progress) > 0f }
    }
    val tapExpands by remember(frame) { derivedStateOf { frame().progress < TAP_EXPAND_LIMIT } }

    Box(
        Modifier
            .absolute { frame().sheetBounds }
            .sheetDrag(state)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = tapExpands,
                onClick = state::expand,
            ),
    ) {
        if (contentShown) {
            val (contentColor, playButtonContainer, playButtonContent) = colors()
            val title = player.title
            val artist = player.artist

            Row(
                Modifier
                    // The mini bar's height across the sheet, leaving room for the cover slot.
                    // The cover is at the sheet's start, the right one right to left.
                    .layout { measurable, constraints ->
                        val sheet = frame().sheetBounds
                        val coverEnd = if (layoutDirection == LayoutDirection.Rtl) {
                            sheet.right - geometry.collapsedArtLeft
                        } else {
                            geometry.collapsedArtLeft - sheet.left + geometry.collapsedArtSize
                        }
                        val start = (coverEnd + MINI_ART_TO_TEXT_GAP.toPx()).roundToInt()
                        val width =
                            (constraints.maxWidth - start - 8.dp.roundToPx()).coerceAtLeast(0)
                        val height = geometry.collapsedHeight.roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(width, height))
                        layout(constraints.maxWidth, height) { placeable.placeRelative(start, 0) }
                    }
                    .graphicsLayer { alpha = miniContentAlpha(frame().progress) },
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
                            withStyle(SpanStyle(color = contentColor.copy(alpha = 0.7f))) {
                                append(a)
                            }
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
                    onClick = actions.playPause,
                    modifier = Modifier.size(MINI_BUTTON_SIZE),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = playButtonContainer,
                        contentColor = playButtonContent,
                    ),
                ) {
                    PlayPauseIcon(
                        playing = player.showPause,
                        tint = playButtonContent,
                        modifier = Modifier.size(MINI_ICON_SIZE),
                        contentDescription = stringResource(
                            if (player.showPause) R.string.pause else R.string.play
                        ),
                    )
                }
                IconButton(onClick = actions.next, modifier = Modifier.size(MINI_BUTTON_SIZE)) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = stringResource(R.string.skip_next),
                        tint = contentColor,
                        modifier = Modifier.size(MINI_ICON_SIZE),
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedArtwork(
    state: NowPlayingSheetState,
    player: PlayerSheetPlayerState,
    lyrics: LyricsOverlayState,
    queue: QueueRevealState,
    frame: () -> SheetFrame,
    rootWidth: Float,
    cookie: Boolean,
    fadesOverQueue: Boolean,
    onClick: () -> Unit,
) {
    val artwork = player.artworkUri
    // Fade the shared cover out of the way when the lyric overlay covers the player. Derived, so
    // the lyrics' fade and back gesture don't recompose the cover every frame.
    val lyricsCovering by remember(lyrics) { derivedStateOf { lyrics.covering } }
    val coverAlpha by animateFloatAsState(
        targetValue = if (lyricsCovering) 0f else 1f,
        animationSpec = tween(LYRIC_COVER_FADE_MS),
        label = "cover lyrics fade",
    )

    val context = LocalPlatformContext.current
    val coverDescription = stringResource(R.string.dialog_album)
    val requestSizePx = rootWidth.roundToInt().coerceAtLeast(1)
    // No artwork loads as a null request, which shows the default cover through the fallback.
    val request = remember(artwork, requestSizePx) {
        ImageRequest.Builder(context)
            .data(artwork)
            .size(requestSizePx)
            .precision(Precision.INEXACT)
            .build()
    }

    val coverClickable by remember(frame) {
        derivedStateOf { frame().progress > COVER_CLICK_MIN }
    }
    val clickModifier = if (coverClickable) Modifier.clickable(onClick = onClick) else Modifier
    val underQueue = fadesOverQueue && queue.revealed
    val gestures = if (lyricsCovering || queue.shown) {
        Modifier
    } else {
        Modifier.sheetDrag(state).then(clickModifier)
    }
    Box(Modifier.onSheet(frame)) {
        CrossfadeArtwork(
            request = request,
            modifier = Modifier
                .absolute { frame().artBounds }
                .graphicsLayer {
                    val f = frame()
                    // Out with the rest of the player as the queue comes up
                    alpha = if (fadesOverQueue) coverAlpha * (1f - queue.progress) else coverAlpha
                    shape = if (cookie) {
                        CookieMorphShape(f.progress)
                    } else {
                        RoundedCornerShape(f.artCorner)
                    }
                    clip = true
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .semantics {
                    contentDescription = coverDescription
                    if (lyricsCovering || underQueue) hideFromAccessibility()
                }
                .then(gestures),
        )
    }
}

/** How long a new cover takes to fade in over the previous one. */
private const val ART_CROSSFADE_MS = 300

/**
 * The cover, which keeps showing the previous song's cover until the new one has loaded (like the
 * View player's loadNoPlaceholder) and then fades the new one in over it.
 */
@Composable
private fun CrossfadeArtwork(request: ImageRequest, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    // Drawn with its glyph at DEFAULT_COVER_GLYPH_SHARE, like every other default cover.
    val defaultCover = rememberDefaultCoverPainter(R.drawable.ic_default_cover)
    // The very first cover shows as soon as it loads, later ones fade in over the last. A new one
    // is stacked after the composition that sees it, not by writing the stack while reading it.
    val covers = remember { CrossfadeStack(request) }
    SideEffect {
        if (covers.top != request) covers.push(request)
    }
    Box(modifier) {
        for (layer in covers.layers) {
            key(layer) {
                val onLoaded = {
                    scope.launch { covers.fadeIn(layer, tween(ART_CROSSFADE_MS)) }
                    Unit
                }
                AsyncImage(
                    model = layer.value,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    error = defaultCover,
                    fallback = defaultCover,
                    onSuccess = { onLoaded() },
                    onError = { onLoaded() },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = layer.fraction.value },
                )
            }
        }
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
