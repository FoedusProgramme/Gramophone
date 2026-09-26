/*
 *     Copyright (C) 2024 nift4
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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderNode
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.SparseArray
import android.util.TypedValue
import android.view.animation.AnimationUtils
import android.view.animation.PathInterpolator
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.text.getSpans
import androidx.core.util.forEach
import androidx.media3.common.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.hasRenderNodes
import org.akanework.gramophone.logic.ui.spans.MyForegroundColorSpan
import org.akanework.gramophone.logic.ui.spans.MyGradientSpan
import org.akanework.gramophone.logic.ui.spans.StaticLayoutBuilderCompat
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerpInv
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SpeakerEntity
import org.akanework.gramophone.logic.utils.findBidirectionalBarriers
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.theme.AppFont
import org.akanework.gramophone.ui.theme.LocalAppFontEnabled
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates

private const val TAG = "NewLyrics"

/** How long a user scroll keeps the lyrics from following playback again. */
private const val USER_SCROLL_HOLD_MS = 5000L

/**
 * The v2 lyrics: every line is laid out as a StaticLayout and drawn directly onto the canvas, with
 * word-by-word gradients, per-line scale and colour fades, and a staggered "drop" of the lines
 * below when scrolling to the next line.
 *
 * Scrolling is a [ScrollableState] over [NewLyricsRenderer.scrollY]. The renderer runs its own
 * follow-playback smooth scroll between frames and pauses it for [USER_SCROLL_HOLD_MS] after a
 * user scroll or fling.
 */
@Composable
internal fun NewLyrics(
    lyrics: SemanticLyrics?,
    visible: Boolean,
    positionTick: () -> Int,
    colors: LyricsColors,
    padding: LyricsPadding,
    playback: LyricsPlayback,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val frame = remember { mutableIntStateOf(0) }
    val frameRequests = remember { Channel<Unit>(Channel.CONFLATED) }
    val renderer = remember(playback) {
        NewLyricsRenderer(context, playback) { frameRequests.trySend(Unit) }
    }

    val center = rememberBooleanPreference("lyric_center", false).value
    val bold = rememberBooleanPreference("lyric_bold", false).value
    val noAnimation = rememberBooleanPreference("lyric_no_animation", false).value
    val autoWord = rememberBooleanPreference("translation_auto_word", false).value
    val textSize = rememberIntPreference("lyric_text_size", 34).value
    // AppFont reads the setting itself. Keying on the theme's copy of it rebuilds on a change
    val appFont = LocalAppFontEnabled.current
    val typeface = remember(appFont, bold) {
        AppFont.typeface(context, if (bold) 700 else 500)
    }
    SideEffect {
        renderer.visible = visible
        renderer.setPadding(padding.left, padding.top, padding.right, padding.bottom)
        renderer.applyPrefs(center, autoWord, noAnimation, textSize, typeface)
        renderer.updateTextColor(colors.default, colors.highlight, colors.highlightTl)
        renderer.updateLyrics(lyrics)
    }

    // Each invalidate request triggers a redraw on the next frame
    LaunchedEffect(renderer) {
        for (request in frameRequests) {
            withFrameNanos { frame.intValue++ }
        }
    }
    LaunchedEffect(renderer) {
        snapshotFlow { positionTick() }.collect { renderer.updateLyricPositionFromPlaybackPos() }
    }
    // User scroll hold: invalidate once it expires so the lyrics follow playback again
    LaunchedEffect(renderer) {
        for (request in renderer.resumeRequests) {
            while (true) {
                val wait = renderer.resumeAt - SystemClock.uptimeMillis()
                if (wait <= 0) break
                delay(wait)
            }
            if (renderer.isCallbackQueued) {
                renderer.isCallbackQueued = false
                renderer.invalidate()
            }
        }
    }

    val scrollState = remember(renderer) {
        ScrollableState { delta -> renderer.scrollByUser(delta) }
    }
    renderer.isUserInteracting = { scrollState.isScrollInProgress }
    val overscroll = rememberOverscrollEffect()
    Spacer(
        modifier
            .clipToBounds()
            .then(
                if (!visible) Modifier
                else Modifier
                    // A touch stops the follow-playback scroll
                    .pointerInput(renderer) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            renderer.abortSmoothScroll()
                        }
                    }
                    .scrollable(
                        scrollState,
                        Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = overscroll,
                    )
                    .pointerInput(renderer) {
                        // A double tap listener delays single taps until it's clear there's no
                        // second one, and swallows double taps
                        detectTapGestures(
                            onDoubleTap = {},
                            onTap = { renderer.onSingleTapConfirmed(it.y) },
                        )
                    }
            )
            .overscroll(overscroll)
            .drawBehind {
                frame.intValue
                // Read the parameter (not the renderer's copy) so a change of visibility triggers a redraw
                if (!visible) return@drawBehind
                drawIntoCanvas {
                    renderer.draw(it.nativeCanvas, size.width.roundToInt(), size.height.roundToInt())
                }
            },
    )
}

/**
 * Drawing and follow-playback logic of the v2 lyrics. [draw] lays the lines out when needed,
 * advances the follow-playback scroll, draws a frame and decides where to scroll next.
 * [invalidate] requests another frame.
 */
internal class NewLyricsRenderer(
    private val context: Context,
    private val instance: LyricsPlayback,
    private val requestFrame: () -> Unit,
) {
    private val smallSizeFactor = 0.97f
    private var lyricAnimTime by Delegates.notNull<Float>()

    private val scaleInAnimTime
        get() = lyricAnimTime / 2f
    private val scaleColorInterpolator = PathInterpolator(0.4f, 0.2f, 0f, 1f)
    private val scrollInterpolator = PathInterpolator(0.4f, 0.2f, 0f, 1f)
    private val delayedInInterpolator = PathInterpolator(0.96f, 0.43f, 0.72f, 1f)
    private val delayedOutInterpolator = PathInterpolator(0.17f, 0f, -0.15f, 1f)
    private var typeface: Typeface? = null
    private val grdWidth = context.resources.getDimension(R.dimen.lyric_gradient_size)
    private val defaultTextSize = context.resources.getDimension(R.dimen.lyric_text_size)
    private val translationTextSize = context.resources.getDimension(R.dimen.lyric_tl_text_size)
    private val translationBackgroundTextSize =
        context.resources.getDimension(R.dimen.lyric_tl_bg_text_size)
    private var globalPaddingHorizontal = 28.5f.dpToPx(context)
    private var paddingVerticalTl = 2f
    private var paddingVerticalDefault = 18f
    private var depth = 15f.dpToPx(context)
    private var colorSpanPool = mutableListOf<MyForegroundColorSpan>()
    private var spForRender: Pair<IntArray, List<SbItem>>? = null
    private var spForMeasure: Pair<IntArray, List<SbItem>>? = null
    private var lyrics: SemanticLyrics? = null
    private var lyricsSet = false
    private var posForRender = 0uL
    private var currentScrollTarget: Int? = null
    private var currentSmoothScroll: Pair<Pair<Double, Double>, Pair<Float, Float>>? = null
    private var delayedScrollAnimation: Pair<Long, Pair<Int, Int>>? = null
    private var stateOverrides = hashMapOf<Int, Float>()
    private var stateTime = 0uL
    private var defaultTextColor = 0
    private var highlightTextColor = 0
    private var highlightTlTextColor = 0
    private val defaultTextPaint = TextPaint().apply {
        color = Color.RED
    }
    private val translationTextPaint = TextPaint().apply {
        color = Color.GREEN
    }
    private val translationBackgroundTextPaint = TextPaint().apply {
        color = Color.BLUE
    }
    private var wordActiveSpan = MyForegroundColorSpan(Color.CYAN)
    private var wordActiveTlSpan = MyForegroundColorSpan(Color.CYAN)
    private var gradientSpanPool = mutableListOf<MyGradientSpan>()
    private var gradientTlSpanPool = mutableListOf<MyGradientSpan>()
    private val cachedNodes = if (hasRenderNodes()) SparseArray<RenderNode>() else null
    private fun makeGradientSpan() =
        MyGradientSpan(grdWidth, defaultTextColor, highlightTextColor)

    private fun makeGradientTlSpan() =
        MyGradientSpan(grdWidth, defaultTextColor, highlightTlTextColor)

    // Preferences, pushed in by the composable
    private var center = false
    private var autoWord = false
    private var noAnimation: Boolean? = null
    private var textSizeSp = -1

    // Size, scroll offset and padding of the drawing area
    var visible = false
    private var width = 0
    private var height = 0
    private var paddingLeft = 0
    private var paddingTop = 0
    private var paddingRight = 0
    private var paddingBottom = 0
    private var scrollYf = 0f
    private val scrollY: Int
        get() = scrollYf.toInt()
    var isUserInteracting: () -> Boolean = { false }

    // User scroll hold, waited out by the composable
    val resumeRequests = Channel<Unit>(Channel.CONFLATED)
    var resumeAt = 0L
        private set
    var isCallbackQueued = false

    fun invalidate() {
        if (visible) requestFrame()
    }

    fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        if (left != paddingLeft || top != paddingTop || right != paddingRight || bottom != paddingBottom) {
            paddingLeft = left
            paddingTop = top
            paddingRight = right
            paddingBottom = bottom
            requestLayout()
        }
    }

    fun applyPrefs(center: Boolean, autoWord: Boolean, noAnimation: Boolean, textSize: Int, typeface: Typeface) {
        if (this.noAnimation != noAnimation) {
            this.noAnimation = noAnimation
            lyricAnimTime = if (noAnimation) 0f else 650f
        }
        var changed = false
        if (this.typeface !== typeface) {
            this.typeface = typeface
            defaultTextPaint.typeface = typeface
            translationTextPaint.typeface = typeface
            translationBackgroundTextPaint.typeface = typeface
            changed = true
        }
        if (textSizeSp != textSize) {
            textSizeSp = textSize
            applySize()
            changed = true
        }
        if (this.center != center || this.autoWord != autoWord) {
            this.center = center
            this.autoWord = autoWord
            changed = true
        }
        if (changed) requestLayout()
    }

    fun updateTextColor(
        newColor: Int, newHighlightColor: Int, newHighlightTlColor: Int
    ) {
        var changed = false
        var changedTl = false
        if (defaultTextColor != newColor) {
            defaultTextColor = newColor
            defaultTextPaint.color = defaultTextColor
            translationTextPaint.color = defaultTextColor
            translationBackgroundTextPaint.color = defaultTextColor
            changed = true
            changedTl = true
        }
        if (highlightTextColor != newHighlightColor) {
            highlightTextColor = newHighlightColor
            wordActiveSpan.color = highlightTextColor
            changed = true
        }
        if (highlightTlTextColor != newHighlightTlColor) {
            highlightTlTextColor = newHighlightTlColor
            wordActiveTlSpan.color = highlightTlTextColor
            changedTl = true
        }
        if (changed) {
            gradientSpanPool.clear()
            repeat(3) { gradientSpanPool.add(makeGradientSpan()) }
        }
        if (changedTl) {
            gradientTlSpanPool.clear()
            repeat(2) { gradientTlSpanPool.add(makeGradientTlSpan()) }
        }
        if (changed || changedTl) {
            spForRender?.second?.forEach {
                it.text.getSpans<MyGradientSpan>()
                    .forEach { s -> it.text.removeSpan(s) }
            }
            invalidateDeeply()
        }
    }

    /** Takes [parsedLyrics] if it is a different object than the one shown. */
    fun updateLyrics(parsedLyrics: SemanticLyrics?) {
        if (lyricsSet && lyrics === parsedLyrics) return
        lyricsSet = true
        spForRender = null
        spForMeasure = null
        requestLayout()
        lyrics = parsedLyrics
        stateOverrides.clear()
    }

    fun updateLyricPositionFromPlaybackPos() {
        if (instance.getCurrentPosition() != posForRender && lyrics is SemanticLyrics.SyncedLyrics)
            invalidate() // if not playing, might stay same
    }

    private fun applySize() {
        val newTextSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            textSizeSp.toFloat(),
            context.resources.displayMetrics
        )
        globalPaddingHorizontal = 28.5f.dpToPx(context) * newTextSize / defaultTextSize
        depth = 15f.dpToPx(context) * newTextSize / defaultTextSize
        paddingVerticalTl = 2f * newTextSize / defaultTextSize
        paddingVerticalDefault = 18f * newTextSize / defaultTextSize
        defaultTextPaint.textSize = newTextSize
        translationTextPaint.textSize = newTextSize * translationTextSize / defaultTextSize
        translationBackgroundTextPaint.textSize =
            newTextSize * translationBackgroundTextSize / defaultTextSize
    }

    private fun requestLayout() {
        spForMeasure = null
        invalidate()
    }

    private val scrollRange: Int
        get() = max(0, (spForRender?.first?.get(1) ?: 0) - (height - paddingTop - paddingBottom))

    private fun scrollTo(y: Int) {
        val clamped = y.coerceIn(0, scrollRange).toFloat()
        if (clamped != scrollYf) {
            scrollYf = clamped
            invalidate()
        }
    }

    /** Applies a drag or fling step of [delta] px and returns the consumed amount. */
    fun scrollByUser(delta: Float): Float {
        val new = (scrollYf + delta).coerceIn(0f, scrollRange.toFloat())
        val consumed = new - scrollYf
        if (consumed != 0f) {
            scrollYf = new
            invalidate()
        }
        return consumed
    }

    fun abortSmoothScroll() {
        currentSmoothScroll = null
    }

    private fun getScrollProgressAt(time: Double): Int {
        val progress = lerpInv(currentSmoothScroll!!.first.first, currentSmoothScroll!!
            .first.first + currentSmoothScroll!!.first.second, time).toFloat()
        val interpolatedProgress = scrollInterpolator.getInterpolation(min(1f,
            progress))
        return lerp(currentSmoothScroll!!.second.first, currentSmoothScroll!!
            .second.second, interpolatedProgress).toInt()
    }

    /** Advances the follow-playback scroll by one frame. */
    private fun computeScroll() {
        if (currentSmoothScroll == null) return
        val cat = AnimationUtils.currentAnimationTimeMillis().toDouble()
        val q = getScrollProgressAt(cat)
        if (cat >= currentSmoothScroll!!.first.first + currentSmoothScroll!!.first.second) {
            currentSmoothScroll = null
        }
        scrollTo(q)
        // Stop once the scroll hits the end of the content
        if (scrollY != q) currentSmoothScroll = null
        if (currentSmoothScroll != null) invalidate()
    }

    private fun ensureLayout() {
        val myWidth = width - paddingLeft - paddingRight
        val viewportHeight = height - paddingBottom - paddingTop
        if (spForMeasure == null || spForMeasure!!.first[0] != myWidth ||
            spForMeasure!!.first[4] != viewportHeight
        ) {
            spForMeasure = buildSpForMeasure(lyrics, myWidth, viewportHeight)
            spForRender = null
        }
        if (spForRender !== spForMeasure) {
            spForRender = spForMeasure
            invalidateDeeply()
            // Keep the scroll position in the (possibly smaller) new range
            scrollTo(scrollY)
        }
    }

    private fun invalidateDeeply() {
        if (hasRenderNodes()) {
            cachedNodes!!.forEach { _, it ->
                it.discardDisplayList()
            }
            cachedNodes.clear()
        }
        invalidate()
    }

    fun draw(canvas: Canvas, width: Int, height: Int) {
        if (this.width != width || this.height != height) {
            this.width = width
            this.height = height
        }
        if (width - paddingLeft - paddingRight <= 0 || typeface == null) return
        computeScroll()
        ensureLayout()
        val sc = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat() - scrollYf)
        onDrawForChild(canvas)
        canvas.restoreToCount(sc)
    }

    private fun onDrawForChild(canvas: Canvas) {
        posForRender = instance.getCurrentPosition().also {
            if (posForRender > it && posForRender - it < 1000uL)
                Log.w(
                    TAG,
                    "regressing position by ${posForRender - it}ms from $posForRender to $it!"
                )
        }
        val isPlaying = instance.isPlaying()
        val useRenderNodes = hasRenderNodes() && canvas.isHardwareAccelerated
        var animating = false
        var delayedScrollDoneForFrame = false
        val globalPaddingTop = spForRender!!.first[2]
        var heightSoFar = globalPaddingTop.toDouble()
        var heightSoFarWithoutTranslated = heightSoFar
        var determineTimeUntilNext = false
        var timeUntilNext = 0uL // TODO: remove if useless
        var firstScrollTarget: Int? = null
        var firstScrollTargetIdx: Int? = null
        var lastScrollTarget: Int? = null
        var lastScrollTargetIdx: Int? = null
        canvas.save()
        canvas.translate(globalPaddingHorizontal, globalPaddingTop.toFloat())
        val width = width - paddingLeft - paddingRight - globalPaddingHorizontal * 2
        val cat = AnimationUtils.currentAnimationTimeMillis().toDouble()
        spForRender!!.second.forEachIndexed { i, it ->
            var spanEnd = -1
            var spanStartGradient = -1
            var realGradientStart = -1
            var realGradientEnd = -1
            var wordIdx: Int? = null
            var gradientProgress = Float.NEGATIVE_INFINITY
            val firstTs = it.line?.start ?: ULong.MIN_VALUE
            var lastTs = min(it.line?.end ?: Int.MAX_VALUE.toULong(), Int.MAX_VALUE.toULong())
            var endIsImplicit = it.line?.endIsImplicit != false
            if (Flags.IGNORE_SMALL_ENDTIME_GAPS && it.line?.start != null && (!it.line.isTranslated
                        && it.theWords == null || it.line.isTranslated && spForRender!!.second
                    .subList(0, i).find { l -> l.line?.start == it.line.start }?.theWords == null)) {
                val j = spForRender!!.second.subList(i, spForRender!!.second.size).find { l ->
                    (l.line?.start ?: Int.MAX_VALUE.toULong()) > it.line.start }?.line?.start
                val nextStartTime = min(j ?: Int.MAX_VALUE.toULong(), Int.MAX_VALUE.toULong())
                if (j != null && abs(nextStartTime.toLong() - lastTs.toLong()) < lyricAnimTime) {
                    lastTs = nextStartTime - 1uL
                    endIsImplicit = true
                }
            }
            if (Flags.NO_ANIM_GRADIENT_LAST_FRAME_MONKEY_FIX && lyricAnimTime == 0f && it.theWords != null) {
                lastTs += 17.toUInt() // the assumption is that display is at least 60hz
            }
            val timeOffsetForUse = min(
                scaleInAnimTime, min(
                    lerp(
                        firstTs.toFloat(), lastTs.toFloat(),
                        0.5f
                    ) - firstTs.toFloat(),
                    max(firstTs.toFloat(), scaleInAnimTime)
                )
            )
            val fadeInStart = max(firstTs.toLong() - timeOffsetForUse.toLong(), 0L).toULong()
            val fadeInEnd = firstTs + timeOffsetForUse.toULong()
            // If end is implicit, it's the start point of next line, so animate smoothly.
            val fadeOutStart = if (!endIsImplicit) lastTs
            else lastTs - timeOffsetForUse.toULong()
            val fadeOutEnd = if (!endIsImplicit)
                lastTs + (timeOffsetForUse * 2).toULong()
            else lastTs + timeOffsetForUse.toULong()
            val highlightReal = posForRender in fadeInStart..fadeOutEnd
            val override = stateOverrides[i]
            val overridePos = override?.let {
                if (it >= 0f)
                    it.toULong() + posForRender - stateTime
                else (-it).toULong() // negative signals freeze
            } ?: posForRender
            val highlight = overridePos in fadeInStart..fadeOutEnd
            if (override != null) {
                val animPosReal = if (!highlightReal) 0f else if (posForRender >= fadeInEnd)
                    min(
                        1f, 1f - lerpInv(
                            fadeOutStart.toFloat(),
                            fadeOutEnd.toFloat(), posForRender.toFloat()
                        )
                    )
                else lerpInv(
                    fadeInStart.toFloat(),
                    fadeInEnd.toFloat(), posForRender.toFloat()
                )
                val animPos = if (!highlight) 0f else if (overridePos >= fadeInEnd) min(
                    1f,
                    1f - lerpInv(
                        fadeOutStart.toFloat(), fadeOutEnd.toFloat(),
                        overridePos.toFloat()
                    )
                ) else lerpInv(
                    fadeInStart.toFloat(),
                    fadeInEnd.toFloat(), overridePos.toFloat()
                )
                if (timeOffsetForUse == 0f || if (overridePos >= fadeInEnd) animPos <= animPosReal
                    else animPos >= animPosReal)
                    stateOverrides.remove(i)
            }
            val scrollTarget = posForRender in fadeInStart..(lastTs - timeOffsetForUse.toULong())
            val scaleInProgress = if (it.line == null) 1f else lerpInv(
                fadeInStart.toFloat(), fadeInEnd.toFloat(),
                overridePos.toFloat()
            )
            val scaleOutProgress = if (it.line == null) 1f else lerpInv(
                fadeOutStart.toFloat(),
                fadeOutEnd.toFloat(),
                overridePos.toFloat()
            )
            val hlScaleFactor = if (it.line == null) 1f else {
                // lerp() argument order is swapped because we divide by this factor
                if (scaleOutProgress in 0f..1f && timeOffsetForUse > 0f)
                    lerp(
                        smallSizeFactor,
                        1f,
                        scaleColorInterpolator.getInterpolation(scaleOutProgress)
                    )
                else if (scaleInProgress in 0f..1f && timeOffsetForUse > 0f)
                    lerp(
                        1f,
                        smallSizeFactor,
                        scaleColorInterpolator.getInterpolation(scaleInProgress)
                    )
                else if (highlight)
                    smallSizeFactor
                else 1f
            }
            val node = if (useRenderNodes) {
                val node = cachedNodes!![i]
                if (node == null) {
                    val newNode = RenderNode("NewLyrics_$i")
                    cachedNodes[i] = newNode
                    newNode
                } else node
            } else null
            var hasValidCachedNode = if (useRenderNodes) node!!.hasDisplayList() else false
            val isRtl = it.layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
            val alignmentNormal = if (isRtl) it.layout.alignment == Layout.Alignment.ALIGN_OPPOSITE
            else it.layout.alignment == Layout.Alignment.ALIGN_NORMAL
            if (((scaleInProgress >= -.1f && scaleInProgress <= 1f) ||
                        (scaleOutProgress >= -.1f && scaleOutProgress <= 1f)) &&
                timeOffsetForUse > 0f && it.line != null && isPlaying
            )
                animating = true
            if (it.line?.isTranslated != true && it.speaker?.isBackground != true) {
                if (determineTimeUntilNext) {
                    determineTimeUntilNext = false
                    timeUntilNext = max(0uL, (it.line?.start ?: 0uL) - posForRender)
                }
                heightSoFarWithoutTranslated = heightSoFar
            }
            if (scrollTarget && firstScrollTarget == null) {
                firstScrollTarget = heightSoFarWithoutTranslated.toInt()
                firstScrollTargetIdx = i
                determineTimeUntilNext = true
            }
            if (posForRender >= fadeInStart && it.line?.isTranslated != true
                && it.speaker?.isBackground != true
            ) {
                lastScrollTarget = heightSoFar.toInt()
                lastScrollTargetIdx = i
                if (firstScrollTarget == null)
                    determineTimeUntilNext = true
            }
            heightSoFar += it.paddingTop.toFloat()
            val culledDown = heightSoFar > scrollY + height
            var delayedScrollOffset = 0
            // TODO: is this +1 find check valid for tl+bg? the idea is that tl lines stick to
            //  their main line and are animated exactly the same.
            if (delayedScrollAnimation != null && delayedScrollAnimation!!.second.first < i &&
                !delayedScrollDoneForFrame && spForRender!!.second.subList(delayedScrollAnimation!!
                    .second.first + 1, i + 1).find { it.line?.isTranslated != true } != null) {
                val ii = spForRender!!.second.subList(delayedScrollAnimation!!.second.first + 1,
                    i + 1).sumOf { if (it.line?.isTranslated == true) 0 else 1 }
                val duration = lyricAnimTime * 0.278
                val durationReturn = lyricAnimTime * 0.722
                val durationStep = lyricAnimTime * 0.1
                val start = delayedScrollAnimation!!.first.toDouble()
                val end = start + duration + durationReturn + ii * durationStep
                if (end > cat) { // animation is still ongoing
                    if (!culledDown) {
                        val middle = start + duration
                        delayedScrollOffset += if (middle <= cat) {
                            val progress = lerpInv(middle, end, cat).toFloat()
                            val p = delayedOutInterpolator.getInterpolation(progress)
                            lerp(depth, 0f, p)
                        } else {
                            val progress = lerpInv(start, middle, cat).toFloat()
                            val p = delayedInInterpolator.getInterpolation(progress)
                            lerp(0f, depth, p)
                        }.toInt()
                        animating = true
                    } else {
                        delayedScrollDoneForFrame = true
                    }
                } else if (culledDown) {
                    delayedScrollAnimation = null
                }
            }
            canvas.translate(0f, it.paddingTop.toFloat() + delayedScrollOffset -
                    (it.layout.height.toFloat() / hlScaleFactor - it.layout.height.toFloat()) / 2)
            val culled = culledDown || scrollY - paddingTop > heightSoFar +
                    it.layout.height.toFloat() + it.paddingBottom
            if (!culled) {
                if (highlight) {
                    canvas.save()
                    canvas.scale(1f / hlScaleFactor, 1f / hlScaleFactor)
                    if (it.theWords != null) {
                        wordIdx = it.theWords.indexOfLast { it.timeRange.first <= posForRender }
                        if (wordIdx == -1) wordIdx = null
                        if (wordIdx != null) {
                            val word = it.theWords[wordIdx]
                            spanEnd = word.charRange.last + 1 // get exclusive end
                            val gradientEndTime = min(
                                fadeOutStart.toFloat(),
                                word.timeRange.last.toFloat()
                            )
                            val gradientStartTime = min(
                                max(
                                    word.timeRange.first.toFloat(),
                                    firstTs.toFloat()
                                ), gradientEndTime - 1f
                            )
                            gradientProgress = lerpInv(
                                gradientStartTime, gradientEndTime,
                                posForRender.toFloat()
                            )
                            val wordEndLine = it.layout.getLineForOffset(word.charRange.last)
                            val lastCharOnEndLineExcl = it.layout.getLineEnd(wordEndLine)
                            val lastWordOnLine = spanEnd >= lastCharOnEndLineExcl
                            // if we're here, this is the last active word on this line, but it may
                            // not be the last word on this line. if it isn't, keep rendering the
                            // gradient at 100% even after it ended (but only until next word is
                            // the last active word) to avoid kerning jumps due to switching to
                            // color span for parts of a line that should be in the same span.
                            if (gradientProgress >= 0f && (gradientProgress <= 1f || !lastWordOnLine)) {
                                spanStartGradient = word.charRange.first
                                // be greedy and eat as much as the line as can be eaten (text that is
                                // same line + is in same text direction). improves font rendering for
                                // Japanese if font rendering processes whole text in one pass
                                val wordStartLine = it.layout.getLineForOffset(word.charRange.first)
                                val firstCharOnStartLine = it.layout.getLineStart(wordStartLine)
                                realGradientStart = it.theWords.lastOrNull {
                                    it.charRange.last >= firstCharOnStartLine && it.charRange.last <
                                            word.charRange.first && it.isRtl != word.isRtl
                                }?.charRange?.last?.plus(1) ?: firstCharOnStartLine
                                realGradientEnd = it.theWords.firstOrNull {
                                    it.charRange.first > word.charRange.last && it.charRange.first <
                                            lastCharOnEndLineExcl && it.isRtl != word.isRtl
                                }?.charRange?.first ?: lastCharOnEndLineExcl
                            }
                        }
                    } else {
                        spanEnd = it.text.length
                    }
                }
                if (!alignmentNormal) {
                    if (!highlight)
                        canvas.save()
                    if (it.layout.alignment != Layout.Alignment.ALIGN_CENTER)
                        canvas.translate(width * (1 - smallSizeFactor / hlScaleFactor), 0f)
                    else // Layout.Alignment.ALIGN_CENTER
                        canvas.translate(width * ((1 - smallSizeFactor / hlScaleFactor) / 2), 0f)
                }
                if (gradientProgress >= -.1f && gradientProgress <= 1f && isPlaying)
                    animating = true
            }
            val spanEndWithoutGradient = if (realGradientStart == -1) spanEnd else realGradientStart
            val inColorAnim = ((scaleInProgress in 0f..1f && gradientProgress ==
                    Float.NEGATIVE_INFINITY) || scaleOutProgress in 0f..1f) &&
                    timeOffsetForUse > 0f
            var colorSpan = it.text.getSpans<MyForegroundColorSpan>().firstOrNull()
            val cachedEnd = colorSpan?.let { j -> it.text.getSpanEnd(j) } ?: -1
            val wordActiveSpanForLine = if (it.line?.isTranslated == true)
                wordActiveTlSpan else wordActiveSpan
            val col = if (!culled) {
                val highlightColorForLine = if (it.line?.isTranslated == true)
                    highlightTlTextColor else highlightTextColor
                if (inColorAnim) ColorUtils.blendARGB(
                    if (scaleOutProgress in 0f..1f) highlightColorForLine else
                        defaultTextColor,
                    if (scaleInProgress in 0f..1f && gradientProgress == Float
                            .NEGATIVE_INFINITY
                    ) highlightColorForLine
                    else defaultTextColor,
                    scaleColorInterpolator.getInterpolation(
                        if (scaleOutProgress in 0f..1f
                        ) scaleOutProgress else scaleInProgress
                    )
                ) else Color.GREEN
            } else Color.RED
            if (cachedEnd != spanEndWithoutGradient || inColorAnim != (colorSpan != wordActiveSpanForLine)) {
                if (cachedEnd != -1) {
                    it.text.removeSpan(colorSpan!!)
                    if (colorSpan != wordActiveSpanForLine && (!inColorAnim || spanEndWithoutGradient == -1)) {
                        if (colorSpanPool.size < 10)
                            colorSpanPool.add(colorSpan)
                        colorSpan = null
                    } else if (inColorAnim && colorSpan == wordActiveSpanForLine)
                        colorSpan = null
                    hasValidCachedNode = false
                }
                if (spanEndWithoutGradient != -1) {
                    if (inColorAnim && colorSpan == null)
                        colorSpan = colorSpanPool.removeFirstOrNull()
                            ?: @SuppressLint("DrawAllocation") MyForegroundColorSpan(col)
                    else if (!inColorAnim)
                        colorSpan = wordActiveSpanForLine
                    it.text.setSpan(
                        colorSpan, 0, spanEndWithoutGradient,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    hasValidCachedNode = false
                }
            }
            if (inColorAnim && spanEndWithoutGradient != -1) {
                if (colorSpan!! == wordActiveSpanForLine)
                    throw IllegalStateException("colorSpan == wordActiveSpan")
                if (colorSpan.color != col) {
                    colorSpan.color = col
                    hasValidCachedNode = false
                }
            }
            var gradientSpan = it.text.getSpans<MyGradientSpan>().firstOrNull()
            val gradientSpanStart = gradientSpan?.let { j -> it.text.getSpanStart(j) } ?: -1
            val gradientSpanEnd = gradientSpan?.let { j -> it.text.getSpanEnd(j) } ?: -1
            if (gradientSpanStart != realGradientStart || gradientSpanEnd != realGradientEnd) {
                val gradientSpanPoolForLine = if (it.line?.isTranslated == true)
                    gradientTlSpanPool else gradientSpanPool
                if (gradientSpanStart != -1) {
                    it.text.removeSpan(gradientSpan!!)
                    if (realGradientStart == -1) {
                        if (gradientSpanPoolForLine.size < 10)
                            gradientSpanPoolForLine.add(gradientSpan)
                        gradientSpan = null
                    }
                    hasValidCachedNode = false
                }
                if (realGradientStart != -1) {
                    if (gradientSpan == null)
                        gradientSpan = gradientSpanPoolForLine.removeFirstOrNull()
                            ?: if (it.line?.isTranslated == true) makeGradientTlSpan()
                            else makeGradientSpan()
                    it.text.setSpan(
                        gradientSpan, realGradientStart, realGradientEnd,
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    hasValidCachedNode = false
                }
            }
            if (!culled) {
                if (gradientSpan != null) {
                    gradientSpan.runCount = 0
                    gradientSpan.lastLineCount = -1
                    gradientSpan.lineOffsets = it.words!![wordIdx!!]
                    gradientSpan.totalCharsForProgress = spanEnd - spanStartGradient
                    // We get called once per run + one additional time per run if run direction isn't
                    // same as paragraph direction.
                    gradientSpan.runToLineMappings = it.rlm!!
                    val newProgress = gradientProgress.coerceAtMost(1f)
                    if (gradientSpan.progress != newProgress) {
                        gradientSpan.progress = newProgress
                        hasValidCachedNode = false
                    }
                }
                if (useRenderNodes) {
                    if (!hasValidCachedNode) {
                        node!!.setPosition(0, 0, width.toInt(),
                            it.layout.height)
                        val nodeCanvas = node.beginRecording()
                        it.layout.draw(nodeCanvas)
                        node.endRecording()
                    }
                    canvas.drawRenderNode(node!!)
                } else {
                    it.layout.draw(canvas)
                }
                if (highlight || !alignmentNormal)
                    canvas.restore()
            }
            canvas.translate(0f, (it.layout.height.toFloat()) / hlScaleFactor -
                    (it.layout.height.toFloat() / hlScaleFactor - it.layout.height.toFloat()) / 2
                    + it.paddingBottom.toFloat() - delayedScrollOffset)
            heightSoFar += it.layout.height + it.paddingBottom
        }
        canvas.restore()
        if (animating)
            invalidate()
        if (isUserInteracting()) {
            resumeAt = SystemClock.uptimeMillis() + USER_SCROLL_HOLD_MS
            isCallbackQueued = true
            resumeRequests.trySend(Unit)
            if (spForRender!!.first[3] == 1)
                currentScrollTarget = null
        } else if (!isCallbackQueued && currentSmoothScroll == null) {
            val scrollTarget = max(0, (firstScrollTarget ?: lastScrollTarget ?: 0) -
                    globalPaddingTop)
            val scrollTargetIndex = firstScrollTargetIdx ?: lastScrollTargetIdx
            if (scrollTarget != currentScrollTarget) {
                if (lyricAnimTime == 0f) {
                    scrollTo(scrollTarget)
                } else {
                    currentScrollTarget = scrollTarget
                    currentSmoothScroll = (AnimationUtils.currentAnimationTimeMillis().toDouble() to
                            lyricAnimTime.toDouble()) to (scrollY.toFloat() to scrollTarget.toFloat())
                    invalidate()
                    if (scrollY < scrollTarget) {
                        delayedScrollAnimation = if (scrollTargetIndex != null) AnimationUtils
                            .currentAnimationTimeMillis() to (scrollTargetIndex to scrollY)
                        else null
                    }
                }
            }
        }
    }

    private fun buildSpForMeasure(
        lyrics: SemanticLyrics?, width: Int, viewportHeight: Int
    ): Pair<IntArray, List<SbItem>> {
        val lines =
            lyrics?.unsyncedText ?: listOf(context.getString(R.string.no_lyric_found) to null)
        val syncedLines = (lyrics as? SemanticLyrics.SyncedLyrics?)?.text
        var lastNonTranslated: SemanticLyrics.LyricLine? = null
        val spLines = lines.mapIndexed { i, it ->
            val syncedLine = syncedLines?.get(i)
            if (syncedLine?.isTranslated != true)
                lastNonTranslated = syncedLine
            val words =
                syncedLine?.words ?: if (autoWord &&
                    syncedLine?.isTranslated == true && lastNonTranslated?.words != null
                )
                    listOf(
                        SemanticLyrics.Word(
                            lastNonTranslated.timeRange, 0..<syncedLine.text.length,
                            findBidirectionalBarriers(syncedLine.text).firstOrNull()?.second == true
                        )
                    ) else null
            val sb = SpannableStringBuilder(it.first)
            val speaker = syncedLine?.speaker ?: it.second
            val align =
                if (center || speaker?.isGroup == true)
                    Layout.Alignment.ALIGN_CENTER
                else if (speaker?.isVoice2 == true)
                    Layout.Alignment.ALIGN_OPPOSITE
                else Layout.Alignment.ALIGN_NORMAL
            val tl = syncedLine?.isTranslated == true
            val bg = speaker?.isBackground == true
            // TODO: width limiting to 85% if there is >1 singer
            //val widthLimit = speaker?.isWidthLimited == true
            val paddingTop = if (tl) paddingVerticalTl else paddingVerticalDefault
            val paddingBottom = if (i + 1 < (syncedLines?.size ?: -1) &&
                syncedLines?.get(i + 1)?.isTranslated == true
            ) paddingVerticalTl else paddingVerticalDefault
            val layout = StaticLayoutBuilderCompat.obtain(
                sb, when {
                    tl && bg -> translationBackgroundTextPaint
                    tl || bg -> translationTextPaint
                    else -> defaultTextPaint
                }, (width * smallSizeFactor).toInt() - globalPaddingHorizontal.toInt() * 2
            ).setAlignment(align).build()
            val paragraphRtl = layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
            val alignmentNormal = if (paragraphRtl) align == Layout.Alignment.ALIGN_OPPOSITE
            else align == Layout.Alignment.ALIGN_NORMAL
            var l: StaticLayout? = null
            val lineOffsets = words?.map {
                val ia = mutableListOf<Int>()
                val firstLine = layout.getLineForOffset(it.charRange.first)
                val lastLine = layout.getLineForOffset(it.charRange.last + 1)
                for (line in firstLine..lastLine) {
                    val lineStart = layout.getLineStart(line)
                    var lineEnd = layout.getLineEnd(line)
                    while (lineStart + 1 < lineEnd && (layout.text[lineEnd - 1] == '\n' || layout.text[lineEnd - 1] == '\r'))
                        lineEnd--
                    val firstInLine = max(it.charRange.first, lineStart)
                    val lastInLineExcl = min(it.charRange.last + 1, lineEnd)
                    val horizontalStart = if (paragraphRtl == it.isRtl)
                        layout.getPrimaryHorizontal(firstInLine)
                    else layout.getSecondaryHorizontal(firstInLine)
                    // Recycle the layout if we have multiple words in one line.
                    if (l == null || l.getLineStart(0) != lineStart
                        || (l.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT) != it.isRtl
                    ) {
                        // Use StaticLayout instead of Paint.measureText() for V+ useBoundsForWidth
                        // TODO is this working since moving to getPrimaryHorizontal() again?
                        /*
                         * TODO replace this code with something that does not need a new layout whenever possible.
                         * some ideas:
                         * https://developer.android.com/reference/android/text/Layout#fillCharacterBounds(int,%20int,%20float[],%20int) (API >=34)
                         * https://developer.android.com/reference/android/text/Layout#getSelectionPath(int,%20int,%20android.graphics.Path) (API >=26 or >=34 for path parsing)
                         * https://developer.android.com/reference/android/graphics/Paint#getRunCharacterAdvance(char[],%20int,%20int,%20int,%20int,%20boolean,%20int,%20float[],%20int) (API >=34)
                         * https://developer.android.com/reference/android/graphics/Paint#getRunAdvance(char[],%20int,%20int,%20int,%20int,%20boolean,%20int) (API >=23)
                         */
                        l = StaticLayoutBuilderCompat
                            .obtain(layout.text, layout.paint, Int.MAX_VALUE)
                            .setAlignment(
                                if (it.isRtl) Layout.Alignment.ALIGN_OPPOSITE
                                else Layout.Alignment.ALIGN_NORMAL
                            )
                            .setIsRtl(it.isRtl)
                            .setStart(lineStart)
                            .setEnd(lineEnd)
                            .build()
                    }
                    val w = (l.getPrimaryHorizontal(if (it.isRtl) firstInLine else lastInLineExcl)
                            - l.getPrimaryHorizontal(if (it.isRtl) lastInLineExcl else firstInLine)) +
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                                // just add a few pixels on top if RTL as approximation :D
                                if (it.isRtl) 5 else 0
                            } else 0
                    val horizontalEnd = horizontalStart + w * if (it.isRtl) -1 else 1
                    val horizontalLeft = min(horizontalStart, horizontalEnd)
                    val horizontalRight = max(horizontalStart, horizontalEnd)
                    ia.add(horizontalLeft.toInt()) // offset from left to start of word
                    ia.add((horizontalRight - horizontalLeft).roundToInt()) // width of text in this line
                    ia.add(firstInLine - it.charRange.first)
                    ia.add(lastInLineExcl - it.charRange.first)
                    ia.add(if (it.isRtl) -1 else 1)
                }
                return@map ia
            }
            SbItem(
                layout, sb, paddingTop.dpToPx(context).toInt(),
                paddingBottom.dpToPx(context).toInt(),
                words, lineOffsets, lineOffsets?.let { _ ->
                    (0..<layout.lineCount).map { line ->
                        findBidirectionalBarriers(
                            layout.text.subSequence(
                                layout.getLineStart(line), layout.getLineEnd(line)
                            )
                        ).flatMap {
                            if (it.second == alignmentNormal)
                                listOf(line, line)
                            else
                                listOf(line)
                        }
                    }.flatten()
                }, speaker, syncedLine
            )
        }
        val heights = spLines.map { it.layout.height + it.paddingTop + it.paddingBottom }
        val globalPaddingTop = if (lyrics is SemanticLyrics.SyncedLyrics) viewportHeight / 6 else
            context.resources.getDimensionPixelSize(R.dimen.lyric_top_padding)
        val lastIdx = spLines.indexOfLast { it.speaker?.isBackground != true &&
                it.line?.isTranslated != true }.takeIf { it != -1 }
        val globalPaddingBottom = if (lyrics is SemanticLyrics.SyncedLyrics) max(0,
            (viewportHeight * (5f / 6f)).toInt() -
                    (lastIdx?.let { heights.subList(it, heights.size).sum() } ?: 0))
        else if (lyrics != null) context.resources.getDimensionPixelSize(R.dimen.lyric_bottom_padding) else 0
        return Pair(
            intArrayOf(
                width,
                heights.sum() + globalPaddingTop + globalPaddingBottom,
                globalPaddingTop,
                if (lyrics is SemanticLyrics.SyncedLyrics) 1 else 0,
                viewportHeight,
            ), spLines
        )
    }

    /** A tap at [viewY] (px from the top of the lyrics): seek to the line under it. */
    fun onSingleTapConfirmed(viewY: Float) {
        if (spForRender == null) {
            requestLayout()
            return
        }
        // Convert to content coordinates
        val y = viewY + scrollY - paddingTop
        var foundItem: SemanticLyrics.LyricLine? = null
        if (lyrics is SemanticLyrics.SyncedLyrics) {
            var heightSoFar = spForRender!!.first[2]
            spForRender!!.second.forEach {
                val myHeight = it.paddingTop + it.layout.height + it.paddingBottom
                if (y >= heightSoFar && y <= heightSoFar + myHeight && it.line!!.isClickable)
                    foundItem = it.line
                heightSoFar += myHeight
            }
        }
        resumeAt = 0L
        isCallbackQueued = false
        if (foundItem != null) {
            // TODO: call handleSeek from onPositionDiscontinuity once there is a synchronized
            //  way of getting position (ie not relying on ExoPlayer and MediaController both
            //  anymore) - we can't call handleSeek a single frame too early or late from
            //  changing getCurrentPosition() or there are visible glitches
            handleSeek(instance.getCurrentPosition(), foundItem.start)
            instance.seekTo(foundItem.start)
            instance.setPlayWhenReady(true)
        }
    }

    private fun handleSeek(from: ULong, to: ULong) {
        spForRender?.second?.forEachIndexed { i, it ->
            val firstTs = it.line?.start ?: ULong.MIN_VALUE
            var lastTs = min(it.line?.end ?: Int.MAX_VALUE.toULong(), Int.MAX_VALUE.toULong())
            var endIsImplicit = it.line?.endIsImplicit != false
            if (Flags.IGNORE_SMALL_ENDTIME_GAPS && it.line?.start != null && (!it.line.isTranslated
                        && it.theWords == null || it.line.isTranslated && spForRender!!.second
                    .subList(0, i).find { l -> l.line?.start == it.line.start }?.theWords == null)) {
                val j = spForRender!!.second.subList(i, spForRender!!.second.size).find { l ->
                    (l.line?.start ?: Int.MAX_VALUE.toULong()) > it.line.start }?.line?.start
                val nextStartTime = min(j ?: Int.MAX_VALUE.toULong(), Int.MAX_VALUE.toULong())
                if (j != null && abs(nextStartTime.toLong() - lastTs.toLong()) < lyricAnimTime) {
                    lastTs = nextStartTime
                    endIsImplicit = true
                }
            }
            if (Flags.NO_ANIM_GRADIENT_LAST_FRAME_MONKEY_FIX && lyricAnimTime == 0f && it.theWords != null) {
                lastTs += 17.toUInt() // the assumption is that display is at least 60hz
            }
            val timeOffsetForUse = min(
                scaleInAnimTime, min(
                    lerp(
                        firstTs.toFloat(), lastTs.toFloat(),
                        0.5f
                    ) - firstTs.toFloat(),
                    max(firstTs.toFloat(), scaleInAnimTime)
                )
            )
            val fadeInStart = max(firstTs.toLong() - timeOffsetForUse.toLong(), 0L).toULong()
            val fadeInEnd = firstTs + timeOffsetForUse.toULong()
            // If end is implicit, it's the start point of next line, so animate smoothly.
            val fadeOutStart = if (!endIsImplicit) lastTs
            else lastTs - timeOffsetForUse.toULong()
            val fadeOutEnd = if (!endIsImplicit)
                lastTs + (timeOffsetForUse * 2).toULong()
            else lastTs + timeOffsetForUse.toULong()
            val override = stateOverrides[i]
            val overridePos = override?.let {
                // if there's an old override we'll continue there
                if (it >= 0f)
                    it.toULong() + from - stateTime
                else (-it).toULong() // negative signals freeze
            } ?: from
            val highlight = overridePos in fadeInStart..fadeOutEnd
            val animPosNow = if (!highlight) 0f else if (overridePos >= fadeInEnd)
                min(1f, 1f - lerpInv(fadeOutStart.toFloat(),
                    fadeOutEnd.toFloat(), overridePos.toFloat()))
            else lerpInv(fadeInStart.toFloat(),
                fadeInEnd.toFloat(), overridePos.toFloat())
            val highlightAfterSeek = to in fadeInStart..fadeOutEnd
            val animPosAfterSeek = if (!highlightAfterSeek) 0f else if (to >=
                fadeInEnd) min(1f, 1f - lerpInv(fadeOutStart.toFloat(),
                fadeOutEnd.toFloat(), to.toFloat()))
            else lerpInv(fadeInStart.toFloat(),
                fadeInEnd.toFloat(), to.toFloat())
            if (animPosNow != animPosAfterSeek && it.theWords == null)
                stateOverrides[i] =
                        // Now we have to decide what behavior towards infinity we wish to have...
                    when {
                        // If we are fading out or fully faded out at target, skip to fade out
                        // at current animation point
                        to !in fadeInStart..<fadeOutStart ->
                            lerp(fadeOutStart.toFloat(), fadeOutEnd.toFloat(),
                                1f - animPosNow)
                        // If we're fading in at target and are already fully faded in here,
                        // stay fully faded in and wait for target to finish fading in too.
                        overridePos in fadeInEnd..<fadeOutStart ->
                            -overridePos.toFloat() // negative signals freeze
                        else -> lerp(fadeInStart.toFloat(), fadeInEnd.toFloat(),
                            animPosNow)
                    }
            else if (override != null)
                stateOverrides.remove(i)
        }
        stateTime = to
    }

    private data class SbItem(
        val layout: StaticLayout, val text: SpannableStringBuilder,
        val paddingTop: Int, val paddingBottom: Int, val theWords: List<SemanticLyrics.Word>?,
        val words: List<List<Int>>?, val rlm: List<Int>?, val speaker: SpeakerEntity?,
        val line: SemanticLyrics.LyricLine?
    )
}
