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

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.CalculationUtils
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlay
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlayState
import org.akanework.gramophone.ui.components.lyrics.LyricsPadding
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ACTION_BAR_VERTICAL_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ACTION_BUTTON_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ARTIST_LINE_HEIGHT
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ARTIST_PROGRESS_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ART_TOP_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LANDSCAPE_MARGIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LAND_ART_BOTTOM
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LAND_ART_START
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LAND_ART_TOP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LYRIC_COVER_FADE_MS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.PORTRAIT_MARGIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.PROGRESS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_TRACK_ALPHA
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TIME_LINE_HEIGHT
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TIME_TRANSPORT_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TITLE_ARTIST_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TITLE_LINE_HEIGHT
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TOP_BUTTON_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TRANSPORT_BUTTON_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.expandedContentAlpha
import org.akanework.gramophone.ui.components.player.PlayerUtilities.noRippleClickable

@Composable
internal fun FullPlayerContent(
    state: NowPlayingSheetState,
    /** The sheet at the current progress, read in the layout and draw phases. */
    frame: () -> SheetFrame,
    geometry: SheetGeometry,
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    lyrics: LyricsOverlayState,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    val expanding by remember(state) { derivedStateOf { state.progress > 0f } }

    // Fade out the player content once the lyrics fully cover it. Derived, so the lyrics' fade
    // and back gesture don't recompose the player every frame.
    val lyricsCovering by remember(lyrics) { derivedStateOf { lyrics.covering } }
    val playerVisibility by animateFloatAsState(
        targetValue = if (lyricsCovering) 0f else 1f,
        animationSpec = tween(LYRIC_COVER_FADE_MS),
        label = "player lyrics fade",
    )
    val playerShown by remember { derivedStateOf { playerVisibility > 0f } }

    Box(Modifier.onSheet(frame)) {
        // The lyrics fill the whole screen under the player, padded for the system bars
        LyricsOverlay(
            state = lyrics,
            scheme = scheme,
            padding = LyricsPadding(
                left = geometry.leftInset.toInt(),
                top = geometry.statusTop.toInt(),
                right = geometry.rightInset.toInt(),
                bottom = geometry.bottomInset.toInt(),
            ),
            modifier = Modifier.pageFollowingCover(frame),
        )
        if (expanding && playerShown) {
            Box(
                Modifier
                    .pageFollowingCover(frame) {
                        alpha = expandedContentAlpha(it.progress) * playerVisibility
                    }
                    .drawBehind { drawRect(scheme.color { surface }) }
                    .then(if (lyricsCovering) Modifier else Modifier.sheetDrag(state)),
            ) {
                FullPlayerScaffold(geometry, player, actions, scheme, onOpenDialog)
            }
        }
    }
}

@Composable
private fun FullPlayerScaffold(
    geometry: SheetGeometry,
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    val density = LocalDensity.current
    // Clear of the system bars and cutouts
    val insets = with(density) {
        PaddingValues(
            start = geometry.leftInset.toDp(),
            top = geometry.statusTop.toDp(),
            end = geometry.rightInset.toDp(),
            bottom = geometry.bottomInset.toDp(),
        )
    }
    val coverSize = with(density) { geometry.expandedArtSize.toDp() }
    if (geometry.isWideLandscape) {
        LandscapeScaffold(coverSize, insets, player, actions, scheme, onOpenDialog)
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            TopButtonRow(player, actions, scheme, onOpenDialog)
            Spacer(Modifier.height(ART_TOP_GAP))
            // The cover's slot. Its size already leaves the controls below their natural height
            // (see SheetGeometry), so they sit centered in what is left.
            Spacer(Modifier.height(coverSize))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PlayerControls(player, actions, scheme, PORTRAIT_MARGIN, Modifier.fillMaxWidth())
            }
            ActionBarRow(player, actions, scheme)
        }
    }
}

@Composable
private fun LandscapeScaffold(
    coverSize: Dp,
    insets: PaddingValues,
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(insets),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .padding(start = LAND_ART_START, top = LAND_ART_TOP, bottom = LAND_ART_BOTTOM)
                    .fillMaxHeight()
                    .width(coverSize),
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 10.dp, end = TOP_BUTTON_SIZE),
            ) {
                PlayerControls(
                    player, actions, scheme, LANDSCAPE_MARGIN,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    verticalArrangement = Arrangement.Center,
                )
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

/** The title and artist, the progress bar and times, and the transport row. */
@Composable
private fun PlayerControls(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    horizontalMargin: Dp,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
) {
    Column(modifier, verticalArrangement = verticalArrangement) {
        TitleArtist(player, actions, scheme, horizontalMargin)
        Spacer(Modifier.height(ARTIST_PROGRESS_GAP))
        ProgressSection(player, actions, scheme, horizontalMargin)
        Spacer(Modifier.height(TIME_TRANSPORT_GAP))
        TransportRow(player, actions, scheme)
    }
}

@Composable
private fun TopButtonColumn(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        MinimizeButton(scheme, actions.minimize)
        SpeedButton(scheme, onOpenDialog)
        TimerButton(player, scheme, onOpenDialog)
    }
}

@Composable
private fun TopButtonRow(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(TOP_BUTTON_SIZE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(24.dp))
        MinimizeButton(scheme, actions.minimize)
        Spacer(Modifier.weight(1f))
        TimerButton(player, scheme, onOpenDialog)
        SpeedButton(scheme, onOpenDialog)
        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun MinimizeButton(scheme: AnimatedColorScheme, onClick: () -> Unit) {
    IconSlot(
        Icons.Outlined.ExpandMore, R.string.expand_less, scheme.colorProducer { onSurface },
        box = TOP_BUTTON_SIZE, icon = 28.dp, onClick = onClick,
    )
}

@Composable
private fun SpeedButton(scheme: AnimatedColorScheme, onOpenDialog: (PlayerDialog) -> Unit) {
    IconSlot(
        Icons.Outlined.Speed, R.string.playback_speed, scheme.colorProducer { onSurface },
        box = TOP_BUTTON_SIZE, icon = 24.dp, onClick = { onOpenDialog(PlayerDialog.Speed) },
    )
}

@Composable
private fun TimerButton(
    player: PlayerSheetPlayerState,
    scheme: AnimatedColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    IconSlot(
        image = if (player.timerActive) Icons.Outlined.AlarmOn else Icons.Outlined.AlarmOff,
        description = R.string.timer,
        tint = scheme.colorProducer { onSurface },
        box = TOP_BUTTON_SIZE,
        icon = 24.dp,
        onClick = { onOpenDialog(PlayerDialog.Timer) },
    )
}

@Composable
private fun IconSlot(
    image: ImageVector,
    @StringRes description: Int,
    tint: ColorProducer,
    box: Dp,
    icon: Dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(box)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TintedIcon(image, tint, Modifier.size(icon), stringResource(description))
    }
}

/** How long the title and artist take to fade out, and then in, when the song changes. */
private const val TEXT_FADE_MS = 300

/** How far in from each end the scrolling title and artist fade out. */
private val MARQUEE_FADE_EDGE = 16.dp

@Composable
private fun TitleArtist(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    horizontalMargin: Dp,
) {
    val bold = rememberBooleanPreference("bold_title", true).value
    val centered = rememberBooleanPreference("centered_title", false).value
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FadingMarqueeText(
            text = player.title?.toString().orEmpty(),
            color = scheme.colorProducer { primary },
            fontSize = 24.sp,
            lineHeight = TITLE_LINE_HEIGHT,
            fontWeight = if (bold) FontWeight.W600 else FontWeight.W400,
            align = align,
            onClick = actions.openAlbum,
        )
        Spacer(Modifier.height(TITLE_ARTIST_GAP))
        FadingMarqueeText(
            text = player.artist?.toString().orEmpty(),
            color = scheme.colorProducer { secondary },
            fontSize = 19.sp,
            lineHeight = ARTIST_LINE_HEIGHT,
            fontWeight = FontWeight.W500,
            align = align,
            onClick = actions.openArtist,
        )
    }
}

/**
 * A single scrolling line, which fades out and the new text in when it changes (like the View
 * player's setTextAnimation) and fades its ends while the text is too long to fit.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FadingMarqueeText(
    text: String,
    /** Read in the draw phase. */
    color: ColorProducer,
    fontSize: TextUnit,
    // A fixed line height, so a title falling back to a taller font (CJK after Latin, say) keeps
    // the same height and the controls below don't jump when the song changes.
    lineHeight: TextUnit,
    fontWeight: FontWeight,
    align: TextAlign,
    onClick: () -> Unit,
) {
    AnimatedContent(
        targetState = text,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            val fadeInNew = fadeIn(tween(TEXT_FADE_MS, delayMillis = TEXT_FADE_MS))
            (fadeInNew togetherWith fadeOut(tween(TEXT_FADE_MS))).using(null)
        },
        label = "player text fade",
    ) { shown ->
        // Width of the laid out line: wider than the slot only when it overflows and scrolls
        var lineWidth by remember { mutableIntStateOf(0) }
        BasicText(
            text = shown,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = align,
                lineHeight = lineHeight,
                lineHeightStyle = LineHeightStyle(
                    LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None,
                ),
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            onTextLayout = { lineWidth = it.size.width },
            modifier = Modifier
                .fillMaxWidth()
                .marqueeFadingEdges { lineWidth }
                .basicMarquee(iterations = Int.MAX_VALUE)
                .noRippleClickable(onClick),
        )
    }
}

/** Fades both ends of a [basicMarquee] line out while its text ([lineWidth]) overflows. */
private fun Modifier.marqueeFadingEdges(lineWidth: () -> Int): Modifier =
    graphicsLayer {
        val overflows = lineWidth() > size.width
        compositingStrategy =
            if (overflows) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        if (lineWidth() <= size.width) return@drawWithContent
        val edge = MARQUEE_FADE_EDGE.toPx().coerceAtMost(size.width / 2f)
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.Black), startX = 0f, endX = edge,
            ),
            size = Size(edge, size.height),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Black, Color.Transparent),
                startX = size.width - edge,
                endX = size.width,
            ),
            topLeft = Offset(size.width - edge, 0f),
            size = Size(edge, size.height),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun ProgressSection(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    horizontalMargin: Dp,
) {
    val positionMs = player.positionMs
    val durationMs = player.durationMs
    // While scrubbing, the drag fraction drives both the bar and the position text (no live seek)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val liveFraction = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val fraction = scrub ?: liveFraction
    val displayPositionMs = scrub?.let { (it * duration).toLong() } ?: positionMs
    val defaultProgressBar = rememberBooleanPreference("default_progress_bar", false).value

    val sliderDescription = stringResource(R.string.position_slider)
    val barModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = horizontalMargin)
        .height(PROGRESS_BAR_HEIGHT)
        .semantics { contentDescription = sliderDescription }
    if (defaultProgressBar) {
        Slider(
            value = fraction,
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { actions.seekTo((it * duration).toLong()) }
                scrub = null
            },
            colors = SliderDefaults.colors(
                thumbColor = scheme.color { primary },
                activeTrackColor = scheme.color { primary },
                inactiveTrackColor = scheme.color { primary }.copy(alpha = SQUIGGLY_TRACK_ALPHA),
            ),
            modifier = barModifier,
        )
    } else {
        SquigglyProgressBar(
            fraction = fraction,
            animating = player.isPlaying && scrub == null,
            color = scheme.colorProducer { primary },
            trackColor = scheme.colorProducer { primary.copy(alpha = SQUIGGLY_TRACK_ALPHA) },
            onScrub = { scrub = it },
            onSeek = {
                actions.seekTo((it * duration).toLong())
                scrub = null
            },
            onScrubCancel = { scrub = null },
            modifier = barModifier,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val timeColor = scheme.colorProducer { onSurfaceVariant }
        TimeText(CalculationUtils.convertDurationToTimeStamp(displayPositionMs), timeColor, 14.sp)
        Spacer(Modifier.weight(1f))
        val quality = player.qualityText
        if (!quality.isNullOrEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                player.qualityIcon?.let {
                    TintedIcon(painterResource(it), timeColor, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                TimeText(quality, timeColor, 12.sp)
            }
            Spacer(Modifier.weight(1f))
        }
        TimeText(CalculationUtils.convertDurationToTimeStamp(durationMs), timeColor, 14.sp)
    }
}

/** A line of the time row, in [color] read in the draw phase. */
@Composable
private fun TimeText(text: String, color: ColorProducer, fontSize: TextUnit) {
    BasicText(
        text,
        style = LocalTextStyle.current.copy(
            fontSize = fontSize,
            fontWeight = FontWeight.W600,
            lineHeight = TIME_LINE_HEIGHT,
        ),
        color = color,
    )
}

/** How far the play button's backdrop turns as it blooms into the cookie. */
private const val PLAY_MORPH_ROTATION = 30f

/** The expressive scheme's quick, slightly bouncy spring, for the play button's morph. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val PLAY_MORPH_SPEC = MotionScheme.expressive().fastSpatialSpec<Float>()

@Composable
private fun TransportRow(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
) {
    val showPause = player.showPause
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            image = Icons.Outlined.SkipPrevious, description = R.string.skip_previous,
            tint = scheme.colorProducer { onSurface }, icon = 38.dp,
            onClick = actions.previous, onLongClick = actions.seekBack,
        )
        Spacer(Modifier.width(8.dp))
        // Read in the draw phase, so the morph doesn't recompose the row every frame
        val morph = animateFloatAsState(
            targetValue = if (showPause) 1f else 0f,
            animationSpec = PLAY_MORPH_SPEC,
            label = "play button morph",
        )
        Box(
            Modifier
                .transportSlot()
                .noRippleClickable(actions.playPause),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = PLAY_MORPH_ROTATION * morph.value }
                    .drawBehind {
                        val outline = PlayButtonMorphShape(morph.value)
                            .createOutline(size, layoutDirection, this)
                        drawOutline(outline, scheme.color { secondaryContainer })
                    },
            )
            PlayPauseIcon(
                playing = showPause,
                tint = scheme.colorProducer { onSecondaryContainer },
                modifier = Modifier.size(42.dp),
                contentDescription = stringResource(R.string.play),
            )
        }
        Spacer(Modifier.width(8.dp))
        TransportButton(
            image = Icons.Outlined.SkipNext, description = R.string.skip_next,
            tint = scheme.colorProducer { onSurface }, icon = 38.dp,
            onClick = actions.next, onLongClick = actions.seekForward,
        )
    }
}

/** A square transport button that shrinks as a square, never squashed, if it has to. */
private fun Modifier.transportSlot(): Modifier =
    sizeIn(maxWidth = TRANSPORT_BUTTON_SIZE, maxHeight = TRANSPORT_BUTTON_SIZE)
        .aspectRatio(1f, matchHeightConstraintsFirst = true)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransportButton(
    image: ImageVector,
    @StringRes description: Int,
    tint: ColorProducer,
    icon: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        Modifier
            .transportSlot()
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TintedIcon(image, tint, Modifier.size(icon), stringResource(description))
    }
}

@Composable
private fun ActionBarRow(
    player: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
) {
    val repeatMode = player.repeatMode
    val shuffle = player.shuffleMode
    val favorite = player.isFavorite
    val plainTint = scheme.colorProducer { onSurface }
    val checkTint = { on: Boolean ->
        scheme.colorProducer { if (on) onSurface else outlineVariant }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 38.dp, vertical = ACTION_BAR_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            Icons.AutoMirrored.Outlined.Article, R.string.dialog_lyrics, plainTint,
            actions.showLyrics,
        )
        ActionButton(
            image = if (repeatMode == Player.REPEAT_MODE_ONE) {
                Icons.Outlined.RepeatOne
            } else {
                Icons.Outlined.Repeat
            },
            description = R.string.repeat_mode,
            tint = checkTint(repeatMode != Player.REPEAT_MODE_OFF),
            onClick = actions.cycleRepeat,
        )
        ActionButton(Icons.Outlined.Shuffle, R.string.shuffle, checkTint(shuffle)) {
            actions.toggleShuffle(!shuffle)
        }
        ActionButton(
            image = if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
            description = R.string.playlist_favourite,
            tint = scheme.colorProducer { if (favorite) tertiary else onSurface },
            onClick = { actions.toggleFavorite(!favorite) },
        )
        ActionButton(
            Icons.AutoMirrored.Outlined.PlaylistPlay, R.string.current_playlist, plainTint,
            actions.showQueue,
        )
    }
}

/** A button of the bottom row. */
@Composable
private fun ActionButton(
    image: ImageVector,
    @StringRes description: Int,
    tint: ColorProducer,
    onClick: () -> Unit,
) = IconSlot(image, description, tint, box = ACTION_BUTTON_SIZE, icon = 24.dp, onClick = onClick)
