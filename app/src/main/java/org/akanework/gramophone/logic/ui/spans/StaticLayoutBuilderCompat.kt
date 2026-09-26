/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.akanework.gramophone.logic.ui.spans

import android.annotation.SuppressLint
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.Layout.HYPHENATION_FREQUENCY_NORMAL
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.IntRange
import kotlin.math.max
import kotlin.math.min

/**
 * Class to create StaticLayout using StaticLayout.Builder on API23+ and a hidden StaticLayout
 * constructor before that.
 *
 *
 * Usage:
 *
 * <pre>`StaticLayout staticLayout =
 * StaticLayoutBuilderCompat.obtain("Lorem Ipsum", new TextPaint(), 100)
 * .setAlignment(Alignment.ALIGN_NORMAL)
 * .build();
`</pre> *
 */
class StaticLayoutBuilderCompat private constructor(
    source: CharSequence,
    paint: TextPaint,
    width: Int
) {
    private var source: CharSequence?
    private val paint: TextPaint
    private val width: Int
    private var start: Int
    private var end: Int

    private var alignment: Layout.Alignment
    private var maxLines: Int
    private var lineSpacingAdd: Float
    private var lineSpacingMultiplier: Float
    private var hyphenationFrequency: Int
    private var includePad: Boolean
    private var isRtl: Boolean? = null
    private var ellipsize: TextUtils.TruncateAt?

    // @Nullable private StaticLayoutBuilderConfigurer staticLayoutBuilderConfigurer;
    init {
        this.source = source
        this.paint = paint
        this.width = width
        this.start = 0
        this.end = source.length
        this.alignment = Layout.Alignment.ALIGN_NORMAL
        this.maxLines = Int.MAX_VALUE
        this.lineSpacingAdd = DEFAULT_LINE_SPACING_ADD
        this.lineSpacingMultiplier = DEFAULT_LINE_SPACING_MULTIPLIER
        this.hyphenationFrequency = HYPHENATION_FREQUENCY_NORMAL
        this.includePad = true
        this.ellipsize = null
    }

    /**
     * Set the alignment. The default is [Layout.Alignment.ALIGN_NORMAL].
     *
     * @param alignment Alignment for the resulting [StaticLayout]
     * @return this builder, useful for chaining
     */
    fun setAlignment(alignment: Layout.Alignment): StaticLayoutBuilderCompat {
        this.alignment = alignment
        return this
    }

    /**
     * Set whether to include extra space beyond font ascent and descent (which is needed to avoid
     * clipping in some languages, such as Arabic and Kannada). The default is `true`.
     *
     * @param includePad whether to include padding
     * @return this builder, useful for chaining
     * @see android.widget.TextView.setIncludeFontPadding
     */
    fun setIncludePad(includePad: Boolean): StaticLayoutBuilderCompat {
        this.includePad = includePad
        return this
    }

    /**
     * Set the index of the start of the text
     *
     * @return this builder, useful for chaining
     */
    fun setStart(@IntRange(from = 0) start: Int): StaticLayoutBuilderCompat {
        this.start = start
        return this
    }

    /**
     * Set the index + 1 of the end of the text
     *
     * @return this builder, useful for chaining
     * @see android.widget.TextView.setIncludeFontPadding
     */
    fun setEnd(@IntRange(from = 0) end: Int): StaticLayoutBuilderCompat {
        this.end = end
        return this
    }

    /**
     * Set maximum number of lines. This is particularly useful in the case of ellipsizing, where it
     * changes the layout of the last line. The default is unlimited.
     *
     * @param maxLines maximum number of lines in the layout
     * @return this builder, useful for chaining
     * @see android.widget.TextView.setMaxLines
     */
    fun setMaxLines(@IntRange(from = 0) maxLines: Int): StaticLayoutBuilderCompat {
        this.maxLines = maxLines
        return this
    }

    /**
     * Set the line spacing addition and multiplier frequency. Only available on API level 23+.
     *
     * @param spacingAdd Line spacing addition for the resulting [StaticLayout]
     * @param lineSpacingMultiplier Line spacing multiplier for the resulting [StaticLayout]
     * @return this builder, useful for chaining
     * @see android.widget.TextView.setLineSpacing
     */
    fun setLineSpacing(spacingAdd: Float, lineSpacingMultiplier: Float): StaticLayoutBuilderCompat {
        this.lineSpacingAdd = spacingAdd
        this.lineSpacingMultiplier = lineSpacingMultiplier
        return this
    }

    /**
     * Set the hyphenation frequency. Only available on API level 23+.
     *
     * @param hyphenationFrequency Hyphenation frequency for the resulting [StaticLayout]
     * @return this builder, useful for chaining
     * @see android.widget.TextView.setHyphenationFrequency
     */
    fun setHyphenationFrequency(hyphenationFrequency: Int): StaticLayoutBuilderCompat {
        this.hyphenationFrequency = hyphenationFrequency
        return this
    }

    /**
     * Set ellipsizing on the layout. Causes words that are longer than the view is wide, or exceeding
     * the number of lines (see #setMaxLines).
     *
     * @param ellipsize type of ellipsis behavior
     * @return this builder, useful for chaining
     * @see android.widget.TextView.setEllipsize
     */
    fun setEllipsize(ellipsize: TextUtils.TruncateAt?): StaticLayoutBuilderCompat {
        this.ellipsize = ellipsize
        return this
    }

    /**
     * Set the {link StaticLayoutBuilderConfigurer} which allows additional custom configurations on
     * the static layout.
     * @ NonNull
     * @ CanIgnoreReturnValue
     * public StaticLayoutBuilderCompat setStaticLayoutBuilderConfigurer(
     * @ Nullable StaticLayoutBuilderConfigurer staticLayoutBuilderConfigurer) {
     * this.staticLayoutBuilderConfigurer = staticLayoutBuilderConfigurer;
     * return this;
     * } */
    @Throws(StaticLayoutBuilderCompatException::class)  /* A method that allows to create a StaticLayout with maxLines on all supported API levels. */ fun build(): StaticLayout {
        if (source == null) {
            source = ""
        }

        val availableWidth: Int = max(0, width)
        var textToDraw = source
        if (maxLines == 1) {
            textToDraw = TextUtils.ellipsize(source, paint, availableWidth.toFloat(), ellipsize)
        }

        end = min(textToDraw!!.length, end)
        val textDirectionHeuristic = if (isRtl == true)
            TextDirectionHeuristics.RTL
        else if (isRtl == false)
            TextDirectionHeuristics.LTR
        else
            TextDirectionHeuristics.FIRSTSTRONG_LTR
        if (isRtl == true && maxLines == 1) {
            alignment = Layout.Alignment.ALIGN_OPPOSITE
        }
        // Marshmallow introduced StaticLayout.Builder which allows us not to use
        // the hidden constructor.
        val builder =
            StaticLayout.Builder.obtain(
                textToDraw, start, end, paint, availableWidth
            )
        builder.setAlignment(alignment)
        builder.setIncludePad(includePad)
        builder.setTextDirection(textDirectionHeuristic)
        if (ellipsize != null) {
            builder.setEllipsize(ellipsize)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        } else {
            @SuppressLint("WrongConstant")
            builder.setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
        }
        builder.setMaxLines(maxLines)
        if (lineSpacingAdd != DEFAULT_LINE_SPACING_ADD
            || lineSpacingMultiplier != DEFAULT_LINE_SPACING_MULTIPLIER
        ) {
            builder.setLineSpacing(lineSpacingAdd, lineSpacingMultiplier)
        }
        if (maxLines > 1) {
            builder.setHyphenationFrequency(hyphenationFrequency)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUseLineSpacingFromFallbacks(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            builder.setUseBoundsForWidth(true)
            builder.setShiftDrawingOffsetForStartOverhang(true)
        }

        //if (staticLayoutBuilderConfigurer != null) {
        //	staticLayoutBuilderConfigurer.configure(builder);
        //}
        return builder.build()
    }

    fun setIsRtl(isRtl: Boolean?): StaticLayoutBuilderCompat {
        this.isRtl = isRtl
        return this
    }

    /**
     * Class representing a StaticLayoutBuilder exception from initializing a StaticLayout.
     */
    class StaticLayoutBuilderCompatException internal constructor(cause: Throwable) :
        Exception("Error thrown initializing StaticLayout " + cause.message, cause)

    companion object {
        // Default line spacing values to match android.text.Layout constants.
        const val DEFAULT_LINE_SPACING_ADD: Float = 0.0f
        const val DEFAULT_LINE_SPACING_MULTIPLIER: Float = 1.0f

        /**
         * Obtain a builder for constructing StaticLayout objects.
         *
         * @param source The text to be laid out, optionally with spans
         * @param paint The base paint used for layout
         * @param width The width in pixels
         * @return a builder object used for constructing the StaticLayout
         */
        fun obtain(
            source: CharSequence, paint: TextPaint, @IntRange(from = 0) width: Int
        ): StaticLayoutBuilderCompat {
            return StaticLayoutBuilderCompat(source, paint, width)
        }
    }
}

