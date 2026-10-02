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

package org.akanework.gramophone.ui.components.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ENDPOINT_THRESHOLD
import org.akanework.gramophone.ui.components.player.PlayerUtilities.FLING_VELOCITY
import org.akanework.gramophone.ui.components.player.PlayerUtilities.PROGRESS_THRESHOLD
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SPATIAL_DAMPING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SPATIAL_STIFFNESS
import kotlin.math.abs

/** Past half way, a let go queue opens the rest of the way, short of it it closes. */
private const val QUEUE_COMMIT_THRESHOLD = 0.5f

/**
 * How far the queue has come up over the full player (see QueueDrawer), from 0, below the screen
 * under the player's bottom row, to 1, filling the screen. [scope] runs the animations.
 */
@Stable
class QueueRevealState(private val scope: CoroutineScope) {
    /** Read in the layout and draw phases, as it changes every frame of motion. */
    var progress by mutableFloatStateOf(0f)
        private set

    /** Whether any of the queue shows. Derived, so its readers are only invalidated as it flips. */
    val shown: Boolean by derivedStateOf { progress > 0f }

    /** Whether it's all the way up, covering what it comes up over. Derived like [shown]. */
    val revealed: Boolean by derivedStateOf { progress >= 1f }

    /** How far (px) the queue comes up, see [SheetGeometry.queueTravel]. */
    var travelPx: Float = 1f

    private val animationSpec: AnimationSpec<Float> =
        spring(
            dampingRatio = SPATIAL_DAMPING,
            stiffness = SPATIAL_STIFFNESS,
            visibilityThreshold = PROGRESS_THRESHOLD,
        )

    private var animation: Job? = null

    /** Moves the queue by a drag of [deltaPx] (down is positive), returning how far it moved. */
    fun onDrag(deltaPx: Float): Float {
        animation?.cancel()
        val previous = progress
        progress = (progress - deltaPx / travelPx).coerceIn(0f, 1f)
        return (previous - progress) * travelPx
    }

    fun settle(velocityPx: Float) {
        val target =
            when {
                velocityPx < -FLING_VELOCITY -> 1f
                velocityPx > FLING_VELOCITY -> 0f
                progress > QUEUE_COMMIT_THRESHOLD -> 1f
                else -> 0f
            }
        if (abs(progress - target) <= ENDPOINT_THRESHOLD) {
            animation?.cancel()
            progress = target
            return
        }
        animateTo(target, initialVelocity = -velocityPx / travelPx)
    }

    fun reveal() = animateTo(1f)

    fun hide() {
        if (progress > 0f) animateTo(0f)
    }

    /** Follows the back gesture, which takes the queue back down. */
    fun onBackProgress(fraction: Float) {
        animation?.cancel()
        progress = (1f - fraction).coerceIn(0f, 1f)
    }

    private fun animateTo(target: Float, initialVelocity: Float = 0f) {
        animation?.cancel()
        animation =
            scope.launch {
                animate(
                    initialValue = progress,
                    targetValue = target,
                    initialVelocity = initialVelocity,
                    animationSpec = animationSpec,
                ) { value, _ -> progress = value.coerceIn(0f, 1f) }
                progress = target
            }
    }
}

/**
 * Drags the bottom row, or the preview over the queue: up brings the [queue] up over the player,
 * and down takes it back, or, while it's down, pulls the [sheet] down like the rest of the player.
 */
internal class BottomRowDrag(
    private val sheet: NowPlayingSheetState,
    private val queue: QueueRevealState,
) {
    /** Which one this drag moves, picked by its first move. */
    private var movesSheet: Boolean? = null

    val state = DraggableState { delta ->
        val sheetDrag = movesSheet ?: (!queue.shown && delta > 0f).also { movesSheet = it }
        if (sheetDrag) sheet.onDrag(delta) else queue.onDrag(delta)
    }

    fun onStopped(velocity: Float) {
        when (movesSheet) {
            true -> sheet.settle(velocity)
            false -> queue.settle(velocity)
            null -> {}
        }
        movesSheet = null
    }
}

internal fun Modifier.bottomRowDrag(drag: BottomRowDrag): Modifier =
    draggable(
        state = drag.state,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> drag.onStopped(velocity) },
    )
