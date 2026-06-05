package org.akanework.gramophone.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.TypefaceCompat
import androidx.core.text.getSpans
import androidx.media3.common.util.Log
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getIntStrict
import org.akanework.gramophone.logic.ui.spans.MyForegroundColorSpan
import org.akanework.gramophone.logic.ui.spans.MyGradientSpan
import org.akanework.gramophone.logic.ui.spans.StaticLayoutBuilderCompat
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerpInv
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SpeakerEntity
import org.akanework.gramophone.logic.utils.findBidirectionalBarriers
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates

private const val TAG = "NewLyricsView"

class NewLyricsView(context: Context, attrs: AttributeSet?) : ScrollingView2(context, attrs),
    GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {
    private val smallSizeFactor = 0.97f
    private var lyricAnimTime by Delegates.notNull<Float>()

    private val scaleInAnimTime
        get() = lyricAnimTime / 2f
    private val isElegantTextHeight =
        false // TODO this was causing issues, but target 36 can't turn this off anymore... needs rework
    private val scaleColorInterpolator = PathInterpolator(0.4f, 0.2f, 0f, 1f)
    private val scrollInterpolator = PathInterpolator(0.4f, 0.2f, 0f, 1f)
    private val delayedInInterpolator = PathInterpolator(0.96f, 0.43f, 0.72f, 1f)
    private val delayedOutInterpolator = PathInterpolator(0.17f, 0f, -0.15f, 1f)
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private lateinit var typeface: Typeface
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
    private var posForRender = 0uL
    lateinit var instance: Callbacks
    private val gestureDetector = GestureDetector(context, this)
    private var currentScrollTarget: Int? = null
    private var currentSmoothScroll: Pair<Pair<Float, Float>, Pair<Float, Float>>? = null
    private var delayedScrollAnimation: Pair<Long, Pair<Int, Int>>? = null
    private var stateOverrides = hashMapOf<Int, Float>()
    private var stateTime = 0uL
    private var isCallbackQueued = false
    private val invalidateCallback = Runnable { isCallbackQueued = false; invalidate() }
    private var defaultTextColor = 0
    private var highlightTextColor = 0
    private var highlightTlTextColor = 0
    private val defaultTextPaint = TextPaint().apply {
        color = Color.RED
        isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
    }
    private val translationTextPaint = TextPaint().apply {
        color = Color.GREEN
        isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
    }
    private val translationBackgroundTextPaint = TextPaint().apply {
        color = Color.BLUE
        isElegantTextHeight = this@NewLyricsView.isElegantTextHeight
    }
    private var wordActiveSpan = MyForegroundColorSpan(Color.CYAN)
    private var wordActiveTlSpan = MyForegroundColorSpan(Color.CYAN)
    private var gradientSpanPool = mutableListOf<MyGradientSpan>()
    private var gradientTlSpanPool = mutableListOf<MyGradientSpan>()
    private fun makeGradientSpan() =
        MyGradientSpan(grdWidth, defaultTextColor, highlightTextColor)

    private fun makeGradientTlSpan() =
        MyGradientSpan(grdWidth, defaultTextColor, highlightTlTextColor)

    init {
        applyTypefaces()
        applySize()
        loadLyricAnimTime()
    }

    interface Callbacks {
        fun getCurrentPosition(): ULong
        fun seekTo(position: ULong)
        fun setPlayWhenReady(play: Boolean)
        fun speed(): Float
        fun destroy()
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
            invalidate()
        }
    }

    fun updateTextColor(newColor: Int) {
        if (defaultTextColor != newColor) {
            defaultTextColor = newColor
            defaultTextPaint.color = defaultTextColor
            translationTextPaint.color = defaultTextColor
            translationBackgroundTextPaint.color = defaultTextColor
            gradientSpanPool.clear()
            repeat(3) { gradientSpanPool.add(makeGradientSpan()) }
            gradientTlSpanPool.clear()
            repeat(2) { gradientTlSpanPool.add(makeGradientTlSpan()) }
            spForRender?.second?.forEach {
                it.text.getSpans<MyGradientSpan>()
                    .forEach { s -> it.text.removeSpan(s) }
            }
            invalidate()
        }
    }

    fun updateHighlightColor(newHighlightColor: Int) {
        if (highlightTextColor != newHighlightColor) {
            highlightTextColor = newHighlightColor
            wordActiveSpan.color = highlightTextColor
            gradientSpanPool.clear()
            repeat(3) { gradientSpanPool.add(makeGradientSpan()) }
            spForRender?.second?.forEach {
                it.text.getSpans<MyGradientSpan>()
                    .forEach { s -> it.text.removeSpan(s) }
            }
            invalidate()
        }
    }

    fun updateHighlightTlColor(newHighlightTlColor: Int) {
        if (highlightTlTextColor != newHighlightTlColor) {
            highlightTlTextColor = newHighlightTlColor
            wordActiveTlSpan.color = highlightTlTextColor
            gradientTlSpanPool.clear()
            repeat(2) { gradientTlSpanPool.add(makeGradientTlSpan()) }
            spForRender?.second?.forEach {
                it.text.getSpans<MyGradientSpan>()
                    .forEach { s -> it.text.removeSpan(s) }
            }
            invalidate()
        }
    }

    fun updateLyrics(parsedLyrics: SemanticLyrics?) {
        spForRender = null
        spForMeasure = null
        requestLayout()
        lyrics = parsedLyrics
        stateOverrides.clear()
    }

    fun updateLyricPositionFromPlaybackPos() {
        if (instance.getCurrentPosition() != posForRender) // if not playing, might stay same
            invalidate()
    }

    fun onPrefsChanged(key: String) {
        if (key == "lyric_no_animation") {
            loadLyricAnimTime()
            return
        }
        if (key == "lyric_bold")
            applyTypefaces()
        if (key == "lyric_text_size")
            applySize()
        spForRender = null
        spForMeasure = null
        requestLayout()
    }

    private fun loadLyricAnimTime() {
        lyricAnimTime = if (prefs.getBooleanStrict("lyric_no_animation", false)) 0f else 650f
    }

    private fun applySize() {
        val newTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            prefs.getIntStrict("lyric_text_size", 34).toFloat(),
            context.resources.displayMetrics)
        globalPaddingHorizontal = 28.5f.dpToPx(context) * newTextSize / defaultTextSize
        depth = 15f.dpToPx(context) * newTextSize / defaultTextSize
        paddingVerticalTl = 2f * newTextSize / defaultTextSize
        paddingVerticalDefault = 18f * newTextSize / defaultTextSize
        defaultTextPaint.textSize = newTextSize
        translationTextPaint.textSize = newTextSize * translationTextSize / defaultTextSize
        translationBackgroundTextPaint.textSize =
            newTextSize * translationBackgroundTextSize / defaultTextSize
    }

    private fun applyTypefaces() {
        typeface = if (prefs.getBooleanStrict("lyric_bold", false)) {
            TypefaceCompat.create(context, null, 700, false)
        } else {
            TypefaceCompat.create(context, null, 500, false)
        }
        defaultTextPaint.typeface = typeface
        translationTextPaint.typeface = typeface
        translationBackgroundTextPaint.typeface = typeface
    }

    override fun onDrawForChild(canvas: Canvas) {
        posForRender = instance.getCurrentPosition().also {
            if (posForRender > it && posForRender - it < 1000uL)
                Log.w(
                    TAG,
                    "regressing position by ${posForRender - it}ms from $posForRender to $it!"
                )
        }
        if (spForRender == null) {
            requestLayout()
            return
        }
        var animating = currentSmoothScroll != null
        var delayedScrollDoneForFrame = false
        val globalPaddingTop = spForRender!!.first[2]
        var heightSoFar = globalPaddingTop.toDouble()
        var heightSoFarWithoutTranslated = heightSoFar
        var determineTimeUntilNext = false
        var timeUntilNext = 0uL // TODO: remove if useless
        var firstScrollTarget: Pair<Int, Int>? = null
        var lastScrollTarget: Pair<Int, Int>? = null
        canvas.save()
        canvas.translate(globalPaddingHorizontal, globalPaddingTop.toFloat())
        val width = width - globalPaddingHorizontal * 2
        val cat = AnimationUtils.currentAnimationTimeMillis().toFloat()
        spForRender!!.second.forEachIndexed { i, it ->
            var spanEnd = -1
            var spanStartGradient = -1
            var realGradientStart = -1
            var realGradientEnd = -1
            var wordIdx: Int? = null
            var gradientProgress = Float.NEGATIVE_INFINITY
            val firstTs = it.line?.start ?: ULong.MIN_VALUE
            val lastTs = min(it.line?.end ?: Int.MAX_VALUE.toULong(), Int.MAX_VALUE.toULong())
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
            val fadeOutStart = if (it.line?.endIsImplicit == false) lastTs
            else lastTs - timeOffsetForUse.toULong()
            val fadeOutEnd = if (it.line?.endIsImplicit == false)
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
            val isRtl = it.layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
            val alignmentNormal = if (isRtl) it.layout.alignment == Layout.Alignment.ALIGN_OPPOSITE
            else it.layout.alignment == Layout.Alignment.ALIGN_NORMAL
            if (((scaleInProgress >= -.1f && scaleInProgress <= 1f) ||
                (scaleOutProgress >= -.1f && scaleOutProgress <= 1f)) &&
                timeOffsetForUse > 0f
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
                firstScrollTarget = heightSoFarWithoutTranslated.toInt() to i
                determineTimeUntilNext = true
            }
            if (posForRender >= fadeInStart && it.line?.isTranslated != true
                && it.speaker?.isBackground != true
            ) {
                lastScrollTarget = heightSoFar.toInt() to i
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
                val duration = (lyricAnimTime * 0.278).toLong()
                val durationReturn = (lyricAnimTime * 0.722).toLong()
                val durationStep = (lyricAnimTime * 0.1).toLong()
                val start = delayedScrollAnimation!!.first.toFloat()
                val end = start + duration + durationReturn + ii * durationStep
                if (end > cat) { // animation is still ongoing
                    if (!culledDown) {
                        val middle = start + duration
                        delayedScrollOffset += if (middle <= cat) {
                            val progress = lerpInv(middle, end, cat)
                            val p = delayedOutInterpolator.getInterpolation(progress)
                            lerp(depth, 0f, p)
                        } else {
                            val progress = lerpInv(start, middle, cat)
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
                                lastTs.toFloat(),
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
                if (gradientProgress >= -.1f && gradientProgress <= 1f)
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
                }
            }
            if (inColorAnim && spanEndWithoutGradient != -1) {
                if (colorSpan!! == wordActiveSpanForLine)
                    throw IllegalStateException("colorSpan == wordActiveSpan")
                colorSpan.color = col
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
                    gradientSpan.progress = gradientProgress.coerceAtMost(1f)
                }
                it.layout.draw(canvas)
                if (highlight || !alignmentNormal)
                    canvas.restore()
            }
            canvas.translate(0f, (it.layout.height.toFloat()) / hlScaleFactor -
                    (it.layout.height.toFloat() / hlScaleFactor - it.layout.height.toFloat()) / 2
                    + it.paddingBottom.toFloat() - delayedScrollOffset)
            heightSoFar += it.layout.height + it.paddingBottom
        }
        //heightSoFar += globalPaddingBottom
        canvas.restore()
        if (animating)
            invalidate()
        if (isUserInteractingWithScrollView) {
            handler.removeCallbacks(invalidateCallback)
            handler.postDelayed(invalidateCallback, 5000)
            isCallbackQueued = true
            if (spForRender!!.first[3] == 1)
                currentScrollTarget = null
        } else if (!isCallbackQueued && currentSmoothScroll == null) {
            val scrollTarget = max(0, (firstScrollTarget?.first ?:
            lastScrollTarget?.first ?: 0) - (height - paddingTop - paddingBottom) / 6)
            val scrollTargetIndex = firstScrollTarget?.second ?: lastScrollTarget?.second
            if (scrollTarget != currentScrollTarget) {
                if (lyricAnimTime == 0f) {
                    scrollTo(0, scrollTarget)
                } else {
                    currentSmoothScroll = (AnimationUtils.currentAnimationTimeMillis().toFloat() to
                            lyricAnimTime) to (scrollY.toFloat() to scrollTarget.toFloat())
                    currentScrollTarget = scrollTarget
                    if (scrollY < scrollTarget) {
                        delayedScrollAnimation = if (scrollTargetIndex != null) AnimationUtils
                            .currentAnimationTimeMillis() to (scrollTargetIndex to scrollY)
                        else null
                    }
                }
            }
        }
    }

    override fun onTouchEventForChild(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    override fun onMeasureForChild(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val myWidth = getDefaultSize(minimumWidth, widthMeasureSpec)
        if (spForMeasure == null || spForMeasure!!.first[0] != myWidth)
            spForMeasure = buildSpForMeasure(lyrics, myWidth)
        setChildMeasuredDimension(
            myWidth,
            getDefaultSize(spForMeasure!!.first[1], heightMeasureSpec)
        )
    }

    override fun onLayoutForChild(left: Int, top: Int, right: Int, bottom: Int) {
        if (spForMeasure == null || spForMeasure!!.first[0] != right - left
            || spForMeasure!!.first[1] != bottom - top
        )
            spForMeasure = buildSpForMeasure(lyrics, right - left)
        spForRender = spForMeasure!!
        invalidate()
    }

    fun buildSpForMeasure(lyrics: SemanticLyrics?, width: Int): Pair<IntArray, List<SbItem>> {
        val lines =
            lyrics?.unsyncedText ?: listOf(context.getString(R.string.no_lyric_found) to null)
        val syncedLines = (lyrics as? SemanticLyrics.SyncedLyrics?)?.text
        var lastNonTranslated: SemanticLyrics.LyricLine? = null
        val spLines = lines.mapIndexed { i, it ->
            val syncedLine = syncedLines?.get(i)
            if (syncedLine?.isTranslated != true)
                lastNonTranslated = syncedLine
            val words =
                syncedLine?.words ?: if (prefs.getBooleanStrict("translation_auto_word", false) &&
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
                if (prefs.getBooleanStrict("lyric_center", false) || speaker?.isGroup == true)
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
        val globalPaddingTop = if (lyrics is SemanticLyrics.SyncedLyrics) (measuredHeight -
                paddingBottom - paddingTop) / 6 else
            context.resources.getDimensionPixelSize(R.dimen.lyric_top_padding)
        val lastIdx = spLines.indexOfLast { it.speaker?.isBackground != true &&
                it.line?.isTranslated != true }.takeIf { it != -1 }
        val globalPaddingBottom = if (lyrics is SemanticLyrics.SyncedLyrics) max(0,
            ((measuredHeight - paddingBottom - paddingTop) * (5f / 6f)).toInt() -
                    (lastIdx?.let { heights.subList(it, heights.size).sum() } ?: 0))
        else if (lyrics != null) context.resources.getDimensionPixelSize(R.dimen.lyric_bottom_padding) else 0
        return Pair(
            intArrayOf(
                width,
                heights.sum() + globalPaddingTop + globalPaddingBottom,
                globalPaddingTop,
                if (lyrics is SemanticLyrics.SyncedLyrics) 1 else 0
            ), spLines
        )
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (spForRender == null) {
            requestLayout()
            return true
        }
        val y = e.y
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
        handler.removeCallbacks(invalidateCallback)
        isCallbackQueued = false
        if (foundItem != null) {
            instance.seekTo(foundItem.start)
            instance.setPlayWhenReady(true)
            performClick()
        }
        return true
    }

    fun handleSeek(from: ULong, to: ULong) {
        // Don't clear stateOverrides, let it stack, it's ok
        stateTime = to
        spForRender?.second?.forEachIndexed { i, it ->
            val firstTs = it.line?.start ?: ULong.MIN_VALUE
            val lastTs = min(it.line?.end ?: Int.MAX_VALUE.toULong(), Int.MAX_VALUE.toULong())
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
            val fadeOutStart = if (it.line?.endIsImplicit == false) lastTs
            else lastTs - timeOffsetForUse.toULong()
            val fadeOutEnd = if (it.line?.endIsImplicit == false)
                lastTs + (timeOffsetForUse * 2).toULong()
            else lastTs + timeOffsetForUse.toULong()
            val highlight = from in fadeInStart..fadeOutEnd
            val animPosNow = if (!highlight) 0f else if (from >= fadeInEnd)
                min(1f, 1f - lerpInv(fadeOutStart.toFloat(),
                    fadeOutEnd.toFloat(), from.toFloat()))
            else lerpInv(fadeInStart.toFloat(),
                fadeInEnd.toFloat(), from.toFloat())
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
                        from >= fadeInEnd -> -from.toFloat() // negative signals freeze
                        else -> lerp(fadeInStart.toFloat(), fadeInEnd.toFloat(),
                            animPosNow)
                    }
        }
    }

    override fun computeScroll() {
        if (currentSmoothScroll != null) {
            val progress = lerpInv(currentSmoothScroll!!.first.first,
                currentSmoothScroll!!.first.first + currentSmoothScroll!!.first.second,
                AnimationUtils.currentAnimationTimeMillis().toFloat())
            val interpolatedProgress = scrollInterpolator.getInterpolation(min(1f,
                progress))
            scrollTo(0, lerp(currentSmoothScroll!!.second.first,
                currentSmoothScroll!!.second.second, interpolatedProgress).toInt())
            if (progress >= 1f) {
                currentSmoothScroll = null
            }
        }
        super.computeScroll()
    }

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        currentSmoothScroll = null
        return super.startNestedScroll(axes, type)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        return false
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        return true
    }

    override fun onShowPress(e: MotionEvent) {
        // do nothing
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return false // handled by parent
    }

    override fun onLongPress(e: MotionEvent) {
        // do nothing
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return false // handled by parent
    }

    data class SbItem(
        val layout: StaticLayout, val text: SpannableStringBuilder,
        val paddingTop: Int, val paddingBottom: Int, val theWords: List<SemanticLyrics.Word>?,
        val words: List<List<Int>>?, val rlm: List<Int>?, val speaker: SpeakerEntity?,
        val line: SemanticLyrics.LyricLine?
    )

}
