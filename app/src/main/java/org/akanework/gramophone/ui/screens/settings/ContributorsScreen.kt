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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.platform.LocalLayoutDirection
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryIconButton
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.LayoutDirection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.akanework.gramophone.ui.components.home.GlassTitleBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.data.Contributors
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val CONTRIBUTORS_URL =
    "https://github.com/FoedusProgramme/Gramophone/graphs/contributors"

/** Margin between the logo and the safe area's sides. */
private val MARK_MARGIN = 24.dp

/** Room under the names, above the navigation bar. */
private val MARK_BOTTOM_GAP = 24.dp

/** The back button sits at the bar's own 4dp plus 6dp, as on the settings pages. */
private val BACK_BUTTON_INSET = 4.dp + 6.dp
private val BAR_TITLE_INSET = 4.dp

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

/** Zoom range of the pinch, and the zoom a double tap goes to. */
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Shows all developers and translators as one block of names laid out inside the app logo. All
 * names use the same size and are never cut off by the shape.
 *
 * Unlike the other settings pages this is not a scrolling [PreferenceScreen][org.akanework
 * .gramophone.ui.components.settings.PreferenceScreen]: the names fill the whole window, edge to
 * edge, so pinching and panning never scroll the page by accident. Only the shared glass bar,
 * with its back button and small title, floats over them.
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
    val hazeState = remember { HazeState() }
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        NameMark(names, Modifier.fillMaxSize().hazeSource(hazeState))
        // Nothing scrolls under the bar, so it keeps its small title all the time, as on the
        // licenses page. Its frost stays on for the names a zoom moves under it.
        GlassTitleBar(
            hazeState = hazeState,
            title = stringResource(R.string.settings_contributors),
            scrolled = { Float.MAX_VALUE },
            toolbarPaddingStart = BACK_BUTTON_INSET,
            titlePaddingStart = BAR_TITLE_INSET,
            navigationIcon = {
                LibraryIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    iconSize = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
            },
        )
    }
}

/**
 * The names laid out in the app logo. The gestures and the zoom cover the whole window, while at
 * no zoom the logo is fitted inside the safe area, clear of the bars and the back button.
 * Tapping opens the contributors page on GitHub, pinching zooms into the small names and a
 * double tap toggles the zoom.
 *
 * Planning the layout rasterizes the logo and measures every name many times, which takes far
 * longer than a frame, so it runs on a background thread and the names fade in once it is done.
 * The last plans are cached, so coming back to the page shows them at once.
 */
@Composable
private fun NameMark(names: List<String>, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    val context = LocalContext.current
    val appContext = context.applicationContext
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val color = MaterialTheme.colorScheme.primary
    val zoom = remember { MarkZoom() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val padLeft = with(density) { (safe.calculateLeftPadding(direction) + MARK_MARGIN).roundToPx() }
    val padRight = with(density) { (safe.calculateRightPadding(direction) + MARK_MARGIN).roundToPx() }
    // Under the glass bar at the top, and a gap above the navigation bar at the bottom.
    val padTop = with(density) { (safe.calculateTopPadding() + GLASS_BAR_HEIGHT).roundToPx() }
    val padBottom = with(density) { (safe.calculateBottomPadding() + MARK_BOTTOM_GAP).roundToPx() }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .markZoomGestures(
                zoom,
                onTap = { open(context, CONTRIBUTORS_URL) },
                onDoubleTap = { scope.launch { zoom.toggle() } },
            ),
    ) {
        // The safe area the logo is fitted into at no zoom.
        val width = (constraints.maxWidth - padLeft - padRight).coerceAtLeast(0)
        val height = (constraints.maxHeight - padTop - padBottom).coerceAtLeast(0)
        val key = PlanKey(names, width, height, density.density, density.fontScale, direction)
        val plan by produceState(MarkPlanCache[key], key) {
            value = MarkPlanCache[key] ?: withContext(Dispatchers.Default) {
                // A measurer of its own: the composition's one is not meant to be shared across
                // threads.
                val measurer = TextMeasurer(fontFamilyResolver, density, direction)
                planMark(appContext, measurer, names, width, height)
            }.also { MarkPlanCache[key] = it }
        }
        // Shown at once when the plan was cached, faded in when it had to be made.
        val fade = remember { Animatable(if (plan != null) 1f else 0f) }
        val ready = plan != null
        LaunchedEffect(ready) { if (ready) fade.animateTo(1f, tween(200)) }
        Canvas(Modifier.fillMaxSize()) {
            val mark = plan ?: return@Canvas
            // Scale the logo's pixel bounds up to the safe area. Otherwise the empty space around
            // the vector would shrink every name.
            val grow = minOf(
                width.toFloat() / mark.width.coerceAtLeast(1),
                height.toFloat() / mark.height.coerceAtLeast(1),
            )
            val shiftX = padLeft + (width - mark.width * grow) / 2f - mark.left * grow
            val shiftY = padTop + (height - mark.height * grow) / 2f - mark.top * grow
            val alpha = fade.value
            // Zoomed in the draw pass rather than a layer, so the names are drawn sharp.
            translate(zoom.offset.x, zoom.offset.y) {
                scale(zoom.scale, zoom.scale, pivot = center) {
                    translate(shiftX, shiftY) {
                        scale(grow, grow, pivot = Offset.Zero) {
                            mark.names.forEach { name ->
                                drawText(
                                    textLayoutResult = name.layout,
                                    color = color,
                                    topLeft = Offset(name.x, name.y),
                                    alpha = alpha,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Everything a [MarkPlan] depends on. The colour is not: it is applied when drawing. */
private data class PlanKey(
    val names: List<String>,
    val width: Int,
    val height: Int,
    val density: Float,
    val fontScale: Float,
    val direction: LayoutDirection,
)

/** The last few plans, so returning to the page or rotating back does not plan again. */
private object MarkPlanCache {
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

/**
 * Zoom of the [NameMark], scaled about the centre of the box and then moved by [offset]. The
 * offset is kept within the zoomed overhang, so the names always cover the whole box.
 */
private class MarkZoom {
    var scale by mutableFloatStateOf(MIN_ZOOM)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var size = Size.Zero

    /** Zooms by [zoomChange] around [centroid] and pans by [pan], all in box pixels. */
    fun transform(centroid: Offset, pan: Offset, zoomChange: Float) {
        val newScale = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        // Keep the point under the fingers in place while the scale changes.
        val fromCenter = centroid - size.center
        val moved = fromCenter - (fromCenter - offset) * (newScale / scale) + pan
        scale = newScale
        offset = clamp(moved, newScale)
    }

    /** Goes back to no zoom when zoomed, or zooms into the centre. */
    suspend fun toggle() {
        val fromScale = scale
        val fromOffset = offset
        val toScale = if (scale > MIN_ZOOM) MIN_ZOOM else DOUBLE_TAP_ZOOM
        animate(0f, 1f, animationSpec = tween(300)) { fraction, _ ->
            scale = fromScale + (toScale - fromScale) * fraction
            offset = clamp(fromOffset * (1f - fraction), scale)
        }
    }

    private fun clamp(offset: Offset, scale: Float): Offset {
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
}

/**
 * Pinch to zoom and, once zoomed, drag to pan; single finger drags do nothing at no zoom. Taps
 * are detected here too rather than with a clickable, which on a full-window box would fire on
 * the lift of any swipe: a gesture only counts as a tap if one finger went down, stayed within
 * the touch slop, lifted before a long press and nothing else consumed it. A second such tap
 * within the double tap timeout makes a double tap instead of two single taps.
 */
private fun Modifier.markZoomGestures(
    zoom: MarkZoom,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
): Modifier = composed {
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    onSizeChanged { zoom.size = it.toSize() }.pointerInput(zoom) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            if (!trackGesture(zoom, first)) return@awaitEachGesture
            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                awaitFirstDown(requireUnconsumed = false)
            }
            if (second == null) {
                tap()
            } else if (trackGesture(zoom, second)) {
                doubleTap()
            }
        }
    }
}

/**
 * Follows one gesture from [down] until every finger is up, zooming and panning [zoom] as it
 * goes. Returns whether the gesture was a clean tap.
 */
private suspend fun AwaitPointerEventScope.trackGesture(
    zoom: MarkZoom,
    down: PointerInputChange,
): Boolean {
    val slop = viewConfiguration.touchSlop
    var panned = Offset.Zero
    var dragging = false
    var isTap = true
    do {
        val event = awaitPointerEvent()
        if (event.changes.any { it.isConsumed }) {
            isTap = false
            break
        }
        val pinching = event.changes.count { it.pressed } > 1
        if (pinching) isTap = false
        val finger = event.changes.firstOrNull { it.id == down.id }
        if (finger == null ||
            (finger.position - down.position).getDistance() > slop ||
            finger.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
        ) {
            isTap = false
        }
        if (!pinching && zoom.scale <= MIN_ZOOM) continue
        // Unspecified (NaN) on the event that lifts the last finger, when no pointer is down on
        // both sides of it, and a NaN offset would move the names out of sight.
        val centroid = event.calculateCentroid()
        if (!centroid.isSpecified) continue
        val pan = event.calculatePan()
        if (!pinching && !dragging) {
            panned += pan
            if (panned.getDistance() < slop) continue
        }
        dragging = true
        zoom.transform(centroid, pan, event.calculateZoom())
        event.changes.forEach { if (it.positionChanged()) it.consume() }
    } while (event.changes.any { it.pressed })
    return isTap && !dragging
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

/**
 * The placed names and the bounding box of the [Mark] they were placed in. The mark's pixels are
 * not kept, so a cached plan stays small.
 */
private class MarkPlan(
    val names: List<Placed>,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    constructor(names: List<Placed>, mark: Mark) :
        this(names, mark.left, mark.top, mark.width, mark.height)
}

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
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
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

private fun open(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_found, Toast.LENGTH_LONG).show()
    }
}
