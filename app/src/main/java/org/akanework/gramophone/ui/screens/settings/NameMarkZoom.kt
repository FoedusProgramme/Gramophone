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

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize

/*
 * Pinch to zoom, pan and taps for the contributors page's names.
 */

/** Zoom range of the pinch, and the zoom a double tap goes to. */
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Length of the double tap's zoom in or out. */
private const val ZOOM_TOGGLE_MS = 300

/**
 * Zoom of the names, scaled about the centre of the box and then moved by [offset]. The offset is
 * kept within the zoomed overhang, so the names always cover the whole box.
 */
internal class MarkZoom {
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
        animate(0f, 1f, animationSpec = tween(ZOOM_TOGGLE_MS)) { fraction, _ ->
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
internal fun Modifier.markZoomGestures(
    zoom: MarkZoom,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
): Modifier = onSizeChanged { zoom.size = it.toSize() } then
    MarkZoomGesturesElement(zoom, onTap, onDoubleTap)

private data class MarkZoomGesturesElement(
    val zoom: MarkZoom,
    val onTap: () -> Unit,
    val onDoubleTap: () -> Unit,
) : ModifierNodeElement<MarkZoomGesturesNode>() {
    override fun create() = MarkZoomGesturesNode(zoom, onTap, onDoubleTap)

    override fun update(node: MarkZoomGesturesNode) = node.update(zoom, onTap, onDoubleTap)

    override fun InspectorInfo.inspectableProperties() {
        name = "markZoomGestures"
        properties["zoom"] = zoom
    }
}

/**
 * Runs the gesture loop of [markZoomGestures]. New callbacks are picked up by the running loop,
 * which only starts over for another [MarkZoom].
 */
private class MarkZoomGesturesNode(
    private var zoom: MarkZoom,
    private var onTap: () -> Unit,
    private var onDoubleTap: () -> Unit,
) : DelegatingNode() {
    private val input = delegate(
        SuspendingPointerInputModifierNode {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                if (!trackGesture(zoom, first)) return@awaitEachGesture
                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                    awaitFirstDown(requireUnconsumed = false)
                }
                if (second == null) {
                    onTap()
                } else if (trackGesture(zoom, second)) {
                    onDoubleTap()
                }
            }
        }
    )

    fun update(zoom: MarkZoom, onTap: () -> Unit, onDoubleTap: () -> Unit) {
        this.onTap = onTap
        this.onDoubleTap = onDoubleTap
        if (zoom !== this.zoom) {
            this.zoom = zoom
            input.resetPointerInputHandler()
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
