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

package org.akanework.gramophone.ui.components.lyrics

import android.animation.ArgbEvaluator
import android.view.animation.PathInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.theme.AppFont
import org.akanework.gramophone.ui.theme.LocalAppFontEnabled

private const val LYRIC_SCROLL_DURATION = 650f

/** Scale of lines other than the current one. The current line is drawn at 1. */
private const val DEFAULT_SIZE_FACTOR = .97f

private val LINE_MARGIN = 16.dp
private val LINE_PADDING_HORIZONTAL = 12.5.dp
private val LIST_TOP_PADDING = 36.dp
private val LIST_BOTTOM_PADDING = 640.dp

/** Where the current line sits below the top padding once scrolled to. */
private val SCROLL_TARGET_OFFSET = 72.dp

/** Downward offset applied to the lines below the target line while the list scrolls. */
private val DROP_DEPTH = 15.dp

private fun PathInterpolator.asEasing() = Easing { getInterpolation(it) }

private val scrollEasing = PathInterpolator(0.4f, 0.2f, 0f, 1f).asEasing()
private val dropInEasing = PathInterpolator(0.96f, 0.43f, 0.72f, 1f).asEasing()
private val dropOutEasing = PathInterpolator(0.17f, 0f, -0.15f, 1f).asEasing()

/** One line of the v1 lyrics. */
private data class Lyric(
    val timeStamp: Long? = null,
    val content: String = "",
    val isTranslation: Boolean = false,
)

/** A line's animated state: how highlighted it is (0..1, scale and colour) and its drop offset. */
private class LyricLineAnim(highlighted: Boolean) {
    val highlight = Animatable(if (highlighted) 1f else 0f)
    val translationY = Animatable(0f)
    var highlightJob: Job? = null
}

private fun SemanticLyrics?.convertForLegacy(): List<Lyric>? {
    if (this == null) return null
    if (this is SemanticLyrics.SyncedLyrics) {
        return this.text.map {
            Lyric(it.start.toLong(), it.text, it.isTranslated)
        }
    }
    return listOf(
        Lyric(
            null,
            this.unsyncedText.joinToString("\n") { it.first }, false
        )
    )
}

/**
 * The v1 lyrics: a plain list of lines. The current line is highlighted (full size and colour) and
 * scrolled to 72dp below the top. While scrolling, the lines below are offset downwards and animated
 * back with a staggered delay.
 */
@Composable
internal fun LegacyLyrics(
    lyrics: SemanticLyrics?,
    visible: Boolean,
    positionTick: () -> Int,
    colors: LyricsColors,
    padding: LyricsPadding,
    playback: LyricsPlayback,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val noLyricFound = context.getString(R.string.no_lyric_found)
    val controller = remember(playback) {
        LegacyLyricsController(playback, listState, scope, density, noLyricFound)
    }
    val noAnimation = rememberBooleanPreference("lyric_no_animation", false).value
    SideEffect {
        controller.forceNoAnimation = noAnimation
        controller.updateLyrics(lyrics)
    }
    LaunchedEffect(controller, visible) {
        if (visible) snapshotFlow { positionTick() }
            .collect { controller.updateLyricPositionFromPlaybackPos() }
    }

    val bold = rememberBooleanPreference("lyric_bold", false).value
    val centered = rememberBooleanPreference("lyric_center", false).value
    val fontFamily = AppFont.fontFamily(LocalAppFontEnabled.current)
    val evaluator = remember { ArgbEvaluator() }
    val defaultColor = colors.default
    val highlightColor = colors.highlight

    with(density) {
        LazyColumn(
            modifier,
            state = listState,
            contentPadding = PaddingValues(
                start = padding.left.toDp(),
                top = padding.top.toDp(),
                end = padding.right.toDp(),
                bottom = padding.bottom.toDp(),
            ),
            userScrollEnabled = visible,
        ) {
            itemsIndexed(controller.lyricList) { position, lyric ->
                val anim = controller.anims[position]
                val isLast = position == controller.lyricList.lastIndex
                Box(
                    Modifier
                        .padding(
                            top = if (position == 0) LIST_TOP_PADDING else 0.dp,
                            bottom = if (isLast) LIST_BOTTOM_PADDING else 0.dp,
                        )
                        .padding(horizontal = LINE_MARGIN)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = visible,
                        ) {
                            lyric.timeStamp?.let {
                                ViewCompat.performHapticFeedback(
                                    view, HapticFeedbackConstantsCompat.CONTEXT_CLICK
                                )
                                playback.setPlayWhenReady(true)
                                playback.seekTo(it.toULong())
                            }
                        },
                ) {
                    if (lyric.content.isNotEmpty()) {
                        val paddingTop = if (lyric.isTranslation) 2.dp else 18.dp
                        val paddingBottom = if (position + 1 < controller.lyricList.size &&
                            controller.lyricList[position + 1].isTranslation
                        ) 2.dp else 18.dp
                        BasicText(
                            text = lyric.content,
                            style = TextStyle(
                                fontSize = if (lyric.isTranslation) 20.sp else 34.25.sp,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight(if (bold) 700 else 500),
                                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                                // Include font padding (Compose excludes it by default)
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                                lineBreak = LineBreak(
                                    LineBreak.Strategy.HighQuality,
                                    LineBreak.Strictness.Default,
                                    LineBreak.WordBreak.Default,
                                ),
                                hyphens = Hyphens.None,
                            ),
                            color = {
                                Color(
                                    evaluator.evaluate(
                                        anim.highlight.value, defaultColor, highlightColor
                                    ) as Int
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    val scale = lerp(DEFAULT_SIZE_FACTOR, 1f, anim.highlight.value)
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    translationY = anim.translationY.value
                                }
                                .padding(
                                    start = LINE_PADDING_HORIZONTAL,
                                    top = paddingTop,
                                    end = LINE_PADDING_HORIZONTAL,
                                    bottom = paddingBottom,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * List and highlight state of the v1 lyrics. The focused line (and the translation right under it)
 * animate to highlighted, the others back, and each new focused line is scrolled to.
 */
private class LegacyLyricsController(
    private val playback: LyricsPlayback,
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val density: Density,
    private val noLyricFound: String,
) {
    var lyricList by mutableStateOf(emptyList<Lyric>())
        private set
    var anims = emptyList<LyricLineAnim>()
        private set
    private var lyrics: SemanticLyrics? = null
    private var lyricsSet = false
    private var currentFocusPos = -1
    private var currentTranslationPos = -1
    private val speed
        get() = playback.speed()
    var forceNoAnimation = false

    fun updateLyrics(newLyric: SemanticLyrics?) {
        if (lyricsSet && lyrics === newLyric) return
        lyricsSet = true
        lyrics = newLyric
        val parsedLyrics = newLyric.convertForLegacy()
        if (lyricList != parsedLyrics) {
            val list = if (parsedLyrics?.isEmpty() != false)
                listOf(Lyric(null, noLyricFound))
            else parsedLyrics
            lyricList = list
            smoothScrollTo(0)
            updateHighlight(0)
            // A whole new list: every line starts out in its final state
            anims = list.indices.map {
                LyricLineAnim(it == currentFocusPos || it == currentTranslationPos)
            }
        }
    }

    fun updateLyricPositionFromPlaybackPos() {
        if (lyricList.isNotEmpty()) {
            val newIndex = updateNewIndex()

            if (newIndex != -1 &&
                newIndex != currentFocusPos
            ) {
                if (lyricList[newIndex].content.isNotEmpty() || newIndex == 0) {
                    smoothScrollTo(newIndex, newIndex == 0)
                }

                updateHighlight(newIndex)
            }
        }
    }

    private fun updateNewIndex(): Int {
        val position = playback.getCurrentPosition().toLong()
        val filteredList = lyricList.filterIndexed { _, lyric ->
            (lyric.timeStamp ?: 0) <= position
        }

        return if (filteredList.isNotEmpty()) {
            filteredList.indices.maxBy {
                filteredList[it].timeStamp ?: 0
            }
        } else {
            -1
        }
    }

    private fun updateHighlight(position: Int) {
        if (currentFocusPos == position) return
        if (position >= 0) {
            animateHighlight(currentFocusPos, false)
            currentFocusPos = position
            animateHighlight(currentFocusPos, true)

            if (position + 1 < lyricList.size &&
                lyricList[position + 1].isTranslation
            ) {
                animateHighlight(currentTranslationPos, false)
                currentTranslationPos = position + 1
                animateHighlight(currentTranslationPos, true)
            } else if (currentTranslationPos != -1) {
                animateHighlight(currentTranslationPos, false)
                currentTranslationPos = -1
            }
        } else {
            currentFocusPos = -1
            currentTranslationPos = -1
        }
    }

    private fun animateHighlight(position: Int, highlighted: Boolean) {
        val anim = anims.getOrNull(position) ?: return
        anim.highlightJob?.cancel()
        anim.highlightJob = scope.launch {
            anim.highlight.animateTo(
                if (highlighted) 1f else 0f,
                tween((LYRIC_SCROLL_DURATION * speed).toInt(), easing = scrollEasing),
            )
        }
    }

    /**
     * Scrolls [position] to [SCROLL_TARGET_OFFSET] below the top padding in [LYRIC_SCROLL_DURATION]
     * (scaled by playback speed), dropping the lines below unless [noAnimation].
     */
    private fun smoothScrollTo(position: Int, noAnimation: Boolean = false) {
        val noDrop = noAnimation || forceNoAnimation
        scope.launch {
            val targetOffset = with(density) { SCROLL_TARGET_OFFSET.roundToPx() }
            val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == position }
            if (target == null) {
                // Target not laid out yet: scroll to it by index, without the drop animation
                listState.animateScrollToItem(position, -targetOffset)
                return@launch
            }
            val distance = target.offset - targetOffset
            if (distance == 0) return@launch
            if (position > 1 && !noDrop) dropLinesBelow(position)
            listState.animateScrollBy(
                distance.toFloat(),
                tween((LYRIC_SCROLL_DURATION * speed).toInt(), easing = scrollEasing),
            )
        }
    }

    /** Offsets the lines below [targetPosition] downwards and animates them back with a staggered delay. */
    private fun dropLinesBelow(targetPosition: Int) {
        val firstPosition = targetPosition + 1
        val lastPosition = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return) + 2
        val depth = with(density) { DROP_DEPTH.roundToPx().toFloat() }
        val duration = (LYRIC_SCROLL_DURATION * 0.278 * speed).toInt()
        val durationReturn = (LYRIC_SCROLL_DURATION * 0.722 * speed).toInt()
        val durationStep = (LYRIC_SCROLL_DURATION * 0.1 * speed).toInt()
        for (i in firstPosition..lastPosition) {
            val anim = anims.getOrNull(i) ?: continue
            if (i == firstPosition && lyricList[i].isTranslation) continue
            val ii = i - firstPosition - if (lyricList[i].isTranslation) 1 else 0
            scope.launch {
                anim.translationY.snapTo(0f)
                anim.translationY.animateTo(depth, tween(duration, easing = dropInEasing))
                anim.translationY.animateTo(0f, tween(durationReturn + ii * durationStep, easing = dropOutEasing))
            }
        }
    }
}
