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

package org.akanework.gramophone.ui.screens.settings

import android.content.Context
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.akanework.gramophone.R

/*
 * The layout of the contributors page's names inside the app logo. The logo is rasterized and
 * scanned line by line, and the filled runs of each line are filled with whole names.
 */

/**
 * Range of name text sizes to search, in sp. The floor is below 5sp because names in scripts
 * Roboto has no glyphs for are measured in a wider fallback font, and the full list only fits a
 * phone screen at a little under 5sp.
 */
private const val NAME_MAX_SP = 22f
private const val NAME_MIN_SP = 4f
private const val NAME_STEP = 0.9f

/** Number of bisection steps used to refine the largest fitting size. */
private const val NAME_SEARCH_STEPS = 5

/** Line height and gap between names, as multiples of the text height. */
private const val LINE_SPACING = 1.22f
private const val GAP_SPACING = 0.68f

/** Filled runs narrower than this are skipped. */
private const val MIN_RUN_PX = 24f

/** Padding at both ends of a run, so no name touches the edge of the logo. */
private const val EDGE_PX = 1.5f

/** Everything a [MarkPlan] depends on. The colour is not: it is applied when drawing. */
internal data class PlanKey(
    val names: List<String>,
    val width: Int,
    val height: Int,
    val density: Float,
    val fontScale: Float,
    val direction: LayoutDirection,
)

/** The last few plans, so returning to the page or rotating back does not plan again. */
internal object MarkPlanCache {
    private const val SIZE = 2
    private val plans = LinkedHashMap<PlanKey, MarkPlan>(SIZE + 1, 1f, true)

    @Synchronized
    operator fun get(key: PlanKey): MarkPlan? = plans[key]

    @Synchronized
    operator fun set(key: PlanKey, plan: MarkPlan) {
        plans[key] = plan
        while (plans.size > SIZE) plans.remove(plans.keys.first())
    }
}

/** A placed name, in the pixel space of the [Mark]. */
internal class Placed(val layout: TextLayoutResult, val x: Float, val y: Float)

/** The rasterized logo: its pixels, and the bounding box of the non-transparent ones. */
private class Mark(
    val pixels: IntArray,
    val stride: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

private val EMPTY_MARK = Mark(IntArray(0), 1, 0, 0, 1, 1)

/**
 * The placed names and the bounding box of the [Mark] they were placed in. The mark's pixels are
 * not kept, so a cached plan stays small.
 */
internal class MarkPlan(
    val names: List<Placed>,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/** A plan of [names] placed in [mark]. */
private fun MarkPlan(names: List<Placed>, mark: Mark) =
    MarkPlan(names, mark.left, mark.top, mark.width, mark.height)

/** One line of the logo: its top, and its filled horizontal runs. */
private class MarkLine(val top: Float, val runs: List<IntRange>)

/**
 * Places every name in the logo, drawn [width] by [height] pixels. The text size is stepped down
 * until all names fit, then refined by bisection towards the largest size that fits, so the names
 * fill the whole shape.
 */
internal fun planMark(
    context: Context,
    measurer: TextMeasurer,
    names: List<String>,
    width: Int,
    height: Int,
): MarkPlan {
    if (width <= 0 || height <= 0) return MarkPlan(emptyList(), EMPTY_MARK)
    val mark = markPixels(context, width, height)
    if (mark.pixels.isEmpty()) return MarkPlan(emptyList(), mark)

    /** Places every name at [sizeSp], or returns null if some names do not fit. */
    fun attempt(sizeSp: Float): List<Placed>? {
        val style = TextStyle(fontSize = sizeSp.sp)
        val layouts = names.map {
            measurer.measure(AnnotatedString(it), style, maxLines = 1, softWrap = false)
        }
        val probe = measurer.measure(
            AnnotatedString("Hg"), style, maxLines = 1, softWrap = false,
        )
        val lineHeight = probe.size.height * LINE_SPACING
        return fillMark(
            layouts = layouts,
            lines = markLines(mark, lineHeight),
            lineHeight = lineHeight,
            bottom = (mark.top + mark.height).toFloat(),
            gap = probe.size.height * GAP_SPACING,
        )
    }

    var sizeSp = NAME_MAX_SP
    var tooBig = 0f
    var fitting: Pair<Float, List<Placed>>? = null
    while (sizeSp >= NAME_MIN_SP) {
        val placed = attempt(sizeSp)
        if (placed != null) {
            fitting = sizeSp to placed
            break
        }
        tooBig = sizeSp
        sizeSp *= NAME_STEP
    }
    val first = fitting ?: return MarkPlan(emptyList(), mark)
    if (tooBig <= 0f) return MarkPlan(first.second, mark)
    // The step is coarse and can leave most of a line empty at the bottom, so bisect between the
    // last size that did not fit and the first one that did.
    var small = first.first
    var large = tooBig
    var best = first
    repeat(NAME_SEARCH_STEPS) {
        val middle = (small + large) / 2f
        val placed = attempt(middle)
        if (placed == null) {
            large = middle
        } else {
            small = middle
            best = middle to placed
        }
    }
    return MarkPlan(best.second, mark)
}

/**
 * Fills the runs of each line with whole names that fit, spread evenly across the run. Returns
 * null when names are left over, so the caller retries with a smaller size.
 */
private fun fillMark(
    layouts: List<TextLayoutResult>,
    lines: List<MarkLine>,
    lineHeight: Float,
    bottom: Float,
    gap: Float,
): List<Placed>? {
    val waiting = ArrayDeque(layouts.indices.toList())
    val placed = ArrayList<Placed>(layouts.size)
    for (line in lines) {
        if (waiting.isEmpty()) break
        if (line.top + lineHeight > bottom) break
        for (run in line.runs) {
            val left = run.first + EDGE_PX
            val room = (run.last + 1 - EDGE_PX) - left
            if (room < MIN_RUN_PX) continue
            val chosen = ArrayList<Int>()
            var used = 0f
            var missed = 0
            while (missed < waiting.size && waiting.isNotEmpty()) {
                val index = waiting.removeFirst()
                val need = layouts[index].size.width + if (chosen.isEmpty()) 0f else gap
                if (used + need <= room) {
                    chosen += index
                    used += need
                    missed = 0
                } else {
                    waiting.addLast(index)
                    missed++
                }
            }
            if (chosen.isEmpty()) continue
            // Justify the names across the run.
            val step =
                if (chosen.size > 1) (room - used).coerceAtLeast(0f) / (chosen.size - 1) else 0f
            var x = left
            for (index in chosen) {
                val layout = layouts[index]
                placed += Placed(layout, x, line.top + (lineHeight - layout.size.height) / 2f)
                x += layout.size.width + gap + step
            }
        }
    }
    return if (waiting.isEmpty()) placed else null
}

/** For each text line, the horizontal runs that are filled on every pixel row of that line. */
private fun markLines(mark: Mark, lineHeight: Float): List<MarkLine> {
    val step = lineHeight.toInt().coerceAtLeast(1)
    val lines = ArrayList<MarkLine>(mark.height / step + 2)
    val endY = mark.top + mark.height
    val endX = mark.left + mark.width
    var top = mark.top
    while (top < endY) {
        val bottom = (top + step).coerceAtMost(endY)
        val covered = BooleanArray(endX) { true }
        for (y in top until bottom) {
            val row = y * mark.stride
            for (x in mark.left until endX) {
                if ((mark.pixels[row + x] ushr 24) == 0) covered[x] = false
            }
        }
        val runs = ArrayList<IntRange>(2)
        var start = -1
        for (x in mark.left..endX) {
            val inside = x < endX && covered[x]
            if (inside && start < 0) start = x
            else if (!inside && start >= 0) {
                runs += start until x
                start = -1
            }
        }
        if (runs.isNotEmpty()) lines += MarkLine(top.toFloat(), runs)
        top = bottom
    }
    return lines
}

/** Draws the app logo into a bitmap and returns its pixels and their bounding box. */
private fun markPixels(context: Context, width: Int, height: Int): Mark {
    val bitmap = ImageBitmap(width, height)
    val canvas = Canvas(bitmap)
    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_gramophone_monochrome)!!
    // The logo is square: fitted and centred in the box rather than stretched to it.
    val side = minOf(width, height)
    val left = (width - side) / 2
    val top = (height - side) / 2
    drawable.setBounds(left, top, left + side, top + side)
    drawable.draw(canvas.nativeCanvas)
    val pixels = IntArray(width * height)
    bitmap.readPixels(pixels, 0, 0, width, height)
    var minX = width
    var minY = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if ((pixels[row + x] ushr 24) != 0) {
                if (x < minX) minX = x
                if (x > right) right = x
                if (y < minY) minY = y
                if (y > bottom) bottom = y
            }
        }
    }
    if (right < minX || bottom < minY) return EMPTY_MARK
    return Mark(pixels, width, minX, minY, right - minX + 1, bottom - minY + 1)
}
