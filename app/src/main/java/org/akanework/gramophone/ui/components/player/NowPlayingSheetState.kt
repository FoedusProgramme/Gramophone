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

package org.akanework.gramophone.ui.components.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.akanework.gramophone.ui.components.player.PlayerUtilities.COMMIT_THRESHOLD
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ENDPOINT_THRESHOLD
import org.akanework.gramophone.ui.components.player.PlayerUtilities.FLING_VELOCITY
import org.akanework.gramophone.ui.components.player.PlayerUtilities.PROGRESS_THRESHOLD
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SPATIAL_DAMPING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SPATIAL_STIFFNESS
import kotlin.math.abs

@Stable
class NowPlayingSheetState(
    private val scope: CoroutineScope,
    initialExpanded: Boolean = false,
) {
    var progress by mutableFloatStateOf(if (initialExpanded) 1f else 0f)
        private set
    var expandedTarget by mutableStateOf(initialExpanded)
        private set

    // Vertical distance (px) of a full expand
    var travelPx: Float = 1f

    private val animationSpec: AnimationSpec<Float> =
        spring(
            dampingRatio = SPATIAL_DAMPING,
            stiffness = SPATIAL_STIFFNESS,
            visibilityThreshold = PROGRESS_THRESHOLD,
        )

    private var animation: Job? = null

    // Consumes [deltaPx] of an upward-positive drag and returns how much progress actually moved
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
                progress > COMMIT_THRESHOLD -> 1f
                else -> 0f
            }
        if (abs(progress - target) <= ENDPOINT_THRESHOLD) {
            snapTo(target)
            return
        }
        // Hand the release velocity to the spring
        animateTo(target, initialVelocity = -velocityPx / travelPx)
    }

    fun expand() = animateTo(1f)

    fun collapse() = animateTo(0f)

    fun onBackProgress(fraction: Float) {
        animation?.cancel()
        progress = (1f - fraction).coerceIn(0f, 1f)
    }

    fun snapToCollapsed() = snapTo(0f)

    fun snapToExpanded() = snapTo(1f)

    private fun snapTo(target: Float) {
        animation?.cancel()
        expandedTarget = target == 1f
        progress = target
    }

    private fun animateTo(
        target: Float,
        initialVelocity: Float = 0f,
    ) {
        expandedTarget = target == 1f
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
