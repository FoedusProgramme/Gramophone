package org.akanework.gramophone.logic.ui.spans

import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.RuntimeXfermode
import android.graphics.Shader
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.view.animation.PathInterpolator
import kotlin.math.abs
import kotlin.math.min
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerpInv

// Hacks, hacks, hacks...
private val gradientPathInterpolator = PathInterpolator(0.38f, 0.39f, 0f, 1f)
class MyGradientSpan(grdWidth: Float, val color: Int, highlightColor: Int,
    private val charScaling: Boolean) : CharacterStyle(), UpdateAppearance {
    private val matrix = Matrix()
    private val gradientWidth = grdWidth
    private lateinit var shader: LinearGradient
    var highlightColor = highlightColor
        set(value) {
            if (field != value) {
                field = value
                invalidateShader() // TODO(ASAP): can we avoid thrashing GC?
            }
        }
    init {
        invalidateShader()
    }
    fun invalidateShader() {
        shader = LinearGradient(
            0f, 1f, gradientWidth, 1f,
            highlightColor, color,
            //Color.GREEN, Color.YELLOW,
            Shader.TileMode.CLAMP
        )
    }
    var progress = 1f
    lateinit var lineOffsets: List<Int>
    lateinit var runToLineMappings: List<Int>
    var totalCharsForProgress = 0
    var runCount = 0
    var lastLineCount = -1
    override fun updateDrawState(tp: TextPaint) {
        tp.color = Color.WHITE
        val o = 5 * ((runToLineMappings[runCount % runToLineMappings.size]) % (lineOffsets.size / 5))
        if (o != lastLineCount) {
            val preOffsetFromLeft = lineOffsets[o].toFloat()
            val textLength = lineOffsets[o + 1]
            val isRtl = lineOffsets[o + 4] == -1
            val ourProgress = if (charScaling) {
                lerpInv(lineOffsets[o + 2].toFloat(), lineOffsets[o + 3].toFloat(),
                    lerp(0f, totalCharsForProgress.toFloat(), progress)).coerceIn(0f, 1f)
            } else {
                val pre = (0..<o/5).sumOf { lineOffsets[it * 5 + 1] }.toFloat()
                val post = (o/5+1..<lineOffsets.size/5).sumOf { lineOffsets[it * 5 + 1] }.toFloat()
                lerpInv(pre, pre + textLength, lerp(0f, pre + textLength + post, progress))
                    .coerceIn(0f, 1f)
            }
            val ourProgressD = if (isRtl) 1f - ourProgress else ourProgress
            shader.setLocalMatrix(matrix.apply {
                reset()
                // for RTL words, gradient is basically rotated and the animation runs in reverse
                // this allows for same code handling both RTL and LTR
                if (isRtl) postRotate(180f, gradientWidth / 2f, 1f)
                // Gradient start is at 0. Gradient end is at gradientWidth. Our goal is to skew the
                // gradient so that we _never_ start before 0 or end after textLength, while showing
                // least amount of pixels when progress is 0 and most amount of pixels if progress
                // is 1. If we're somewhere in the middle, we want the gradient to be as wide as
                // gradientWidth, and the gradient start and end should be offset by half of
                // gradientWidth so that at 50% the middle of the gradient is in the middle of text.
                val widthAtPos = (gradientPathInterpolator.getInterpolation(
                    1f - 2f * abs(ourProgressD - 0.5f)) * gradientWidth)
                    .coerceAtMost(min(textLength * ourProgressD, textLength * (1f - ourProgressD)))
                postScale(widthAtPos.coerceAtLeast(1f) / gradientWidth, 1f)
                postTranslate(preOffsetFromLeft + textLength * ourProgressD - widthAtPos * 0.5f, 0f)
            })
        }
        tp.shader = shader
        lastLineCount = o
        runCount++
    }
}