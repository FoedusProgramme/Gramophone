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

package org.akanework.gramophone.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.data.Contributors
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import kotlin.random.Random

private const val CONTRIBUTORS_URL =
    "https://github.com/FoedusProgramme/Gramophone/graphs/contributors"

/** Horizontal margin between the logo and the screen edges. */
private val MARK_MARGIN = 24.dp

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

/**
 * Shows all developers and translators as one block of names laid out inside the app logo. All
 * names use the same size and are never cut off by the shape.
 *
 * The logo is rasterized and scanned line by line, and the filled runs of each line are filled
 * with whole names.
 */
@Composable
fun ContributorsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    // Developers and translators in one list, shuffled with a fixed seed.
    val names = remember {
        buildList {
            Contributors.LIST.forEach { add(it.name ?: it.login) }
            Contributors.TRANSLATORS.filterNotNull().forEach { add(it) }
        }.shuffled(Random(1712))
    }
    PreferenceScreen(
        title = stringResource(R.string.settings_contributors),
        onBack = onBack,
        modifier = modifier,
    ) {
        NameMark(names)
    }
}

/** The names laid out in the app logo. Tapping it opens the contributors page on GitHub. */
@Composable
private fun NameMark(names: List<String>, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.primary
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    val fade by animateFloatAsState(if (arrived) 1f else 0f, tween(700), label = "credits")
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = MARK_MARGIN)
            .aspectRatio(1f)
            .clickable { open(context, CONTRIBUTORS_URL) },
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val plan = remember(names, width, height, color) {
            planMark(context, measurer, names, width, height, color)
        }
        Canvas(Modifier.fillMaxSize()) {
            val mark = plan.mark
            // Scale the logo's pixel bounds up to the box. Otherwise the empty space around the
            // vector would shrink every name.
            val grow = minOf(
                width.toFloat() / mark.width.coerceAtLeast(1),
                height.toFloat() / mark.height.coerceAtLeast(1),
            )
            val shiftX = (width - mark.width * grow) / 2f - mark.left * grow
            val shiftY = (height - mark.height * grow) / 2f - mark.top * grow
            translate(shiftX, shiftY) {
                scale(grow, grow, pivot = Offset.Zero) {
                    plan.names.forEach { name ->
                        drawText(
                            textLayoutResult = name.layout,
                            color = color,
                            topLeft = Offset(name.x, name.y),
                            alpha = fade,
                        )
                    }
                }
            }
        }
    }
}

/** A placed name, in the pixel space of the [Mark]. */
private class Placed(val layout: TextLayoutResult, val x: Float, val y: Float)

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

/** The placed names and the [Mark] they were placed in. */
private class MarkPlan(val names: List<Placed>, val mark: Mark)

/** One line of the logo: its top, and its filled horizontal runs. */
private class MarkLine(val top: Float, val runs: List<IntRange>)

/**
 * Places every name in the logo. The text size is stepped down until all names fit, then refined
 * by bisection towards the largest size that fits, so the names fill the whole shape.
 */
private fun planMark(
    context: Context,
    measurer: TextMeasurer,
    names: List<String>,
    width: Int,
    height: Int,
    color: Color,
): MarkPlan {
    if (width <= 0 || height <= 0) return MarkPlan(emptyList(), EMPTY_MARK)
    val mark = markPixels(context, width, height)
    if (mark.pixels.isEmpty()) return MarkPlan(emptyList(), mark)

    /** Places every name at [sizeSp], or returns null if some names do not fit. */
    fun attempt(sizeSp: Float): List<Placed>? {
        val style = TextStyle(color = color, fontSize = sizeSp.sp)
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
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_gramophone_monochrome)!!
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas.nativeCanvas)
    val pixels = IntArray(width * height)
    bitmap.readPixels(pixels, 0, 0, width, height)
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if ((pixels[row + x] ushr 24) != 0) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
    }
    if (right < left || bottom < top) return EMPTY_MARK
    return Mark(pixels, width, left, top, right - left + 1, bottom - top + 1)
}

private fun open(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_found, Toast.LENGTH_LONG).show()
    }
}
