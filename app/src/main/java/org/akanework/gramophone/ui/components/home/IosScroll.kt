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

package org.akanework.gramophone.ui.components.home

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sign

/*
 * iOS scroll physics, adapted from FundamentalApps/Weather (ui.components.IosOverscroll).
 *
 * UIScrollView's numbers, as reverse engineered by github.com/ktiays/fluid-scroll (Apache-2.0), the
 * same source FluidRecyclerView builds on. Using the real constants rather than hand-tuned ones is
 * what makes the motion read as iOS instead of merely bouncy.
 */

/** Rubber band coefficient: how much of a drag past the edge actually shows. */
private const val RubberBandCoefficient = 0.55f

/** UIScrollView.DecelerationRate.normal, per millisecond. */
private const val DecelerationRate = 0.998f

/**
 * Compose models decay as `v = v0 * e^(-4.2 * m * t)` with t in seconds, so matching iOS's
 * `v = v0 * 0.998^(t in ms)` means m = -ln(0.998) * 1000 / 4.2.
 */
private val FlingFrictionMultiplier = (-ln(DecelerationRate) * 1000f) / 4.2f

/** Spring-back is critically damped with this response time, so stiffness is (2π / response)². */
private const val SpringResponseSeconds = 0.575f
private val SpringStiffness = ((2f * PI.toFloat()) / SpringResponseSeconds).let { it * it }

/** fluid-scroll settles once the offset is under a tenth of a pixel. */
private const val SettleThreshold = 0.1f

@Stable
class IosOverscrollState {
    /** Rendered displacement in pixels. Positive means the content was pulled down or right. */
    var offset by mutableFloatStateOf(0f)
        internal set

    /** Extent of the container along the scrolling axis, which is the rubber band's range. */
    internal var extent = 0f

    internal var animation: Job? = null

    /** Damped displacement for a raw drag of [raw] past the edge. */
    private fun band(raw: Float): Float {
        val range = extent
        if (range <= 0f) return 0f
        val x = abs(raw)
        return sign(raw) * (1f - 1f / (x / range * RubberBandCoefficient + 1f)) * range
    }

    /** Raw drag that would produce the current [offset]. The band has to be applied to the total. */
    private fun bandInverse(shown: Float): Float {
        val range = extent
        if (range <= 0f) return 0f
        val y = abs(shown).coerceAtMost(range - 1e-3f)
        return sign(shown) * (range * y / (range - y)) / RubberBandCoefficient
    }

    /** Pulls further past the edge by [delta] of raw drag. */
    internal fun stretchBy(delta: Float) {
        offset = band(bandInverse(offset) + delta)
    }

    /**
     * Lets a drag back toward the content take up the current displacement first, the way a
     * stretched band goes slack before the list starts moving again. Returns how much of [delta]
     * was used.
     */
    internal fun releaseBy(delta: Float): Float {
        if (offset == 0f || sign(delta) == sign(offset)) return 0f
        val next = offset + delta
        return if (sign(next) != sign(offset) && next != 0f) {
            val used = -offset
            offset = 0f
            used
        } else {
            offset = next
            delta
        }
    }

    internal fun settle(scope: CoroutineScope, initialVelocity: Float) {
        animation?.cancel()
        animation = scope.launch {
            AnimationState(initialValue = offset, initialVelocity = initialVelocity).animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 1f,
                    stiffness = SpringStiffness,
                    visibilityThreshold = SettleThreshold,
                ),
            ) {
                offset = value
            }
        }
    }
}

@Composable
fun rememberIosOverscrollState(): IosOverscrollState = remember { IosOverscrollState() }

/**
 * iOS-style rubber band overscroll.
 *
 * Apply before the scrolling modifier so it sits above it in the nested scroll chain, and give the
 * scroller [rememberIosFlingBehavior] so a fling that runs into the end hands its leftover velocity
 * to the bounce instead of stopping dead.
 */
fun Modifier.iosOverscroll(
    state: IosOverscrollState,
    isVertical: Boolean = true,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val connection = remember(state, isVertical, scope) {
        object : NestedScrollConnection {
            private fun scrollAxis(offset: Offset) = if (isVertical) offset.y else offset.x
            private fun scrollOffset(value: Float) =
                if (isVertical) Offset(0f, value) else Offset(value, 0f)

            private fun velocityAxis(velocity: Velocity) = if (isVertical) velocity.y else velocity.x
            private fun axisVelocity(value: Float) =
                if (isVertical) Velocity(0f, value) else Velocity(value, 0f)

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                state.animation?.cancel()
                val used = state.releaseBy(scrollAxis(available))
                return if (used == 0f) Offset.Zero else scrollOffset(used)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = scrollAxis(available)
                // Only a finger stretches the band. A fling's leftover becomes the bounce below.
                if (delta == 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                state.stretchBy(delta)
                return scrollOffset(delta)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.offset == 0f) return Velocity.Zero
                val velocity = velocityAxis(available)
                state.settle(scope, velocity)
                return axisVelocity(velocity)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val velocity = velocityAxis(available)
                if (velocity == 0f) return Velocity.Zero
                // The list ran out of content mid-fling: carry the speed into the bounce.
                state.settle(scope, velocity)
                return axisVelocity(velocity)
            }
        }
    }

    this
        .onSizeChanged {
            state.extent = (if (isVertical) it.height else it.width).toFloat()
        }
        .nestedScroll(connection)
        .graphicsLayer {
            if (isVertical) translationY = state.offset else translationX = state.offset
        }
}

/**
 * Fling with iOS's exponential deceleration, which stops the moment the list can no longer move
 * so the remaining velocity reaches the overscroll bounce.
 */
@Composable
fun rememberIosFlingBehavior(
    scrollState: ScrollableState,
    decay: DecayAnimationSpec<Float> = exponentialDecay(frictionMultiplier = FlingFrictionMultiplier),
): FlingBehavior = remember(scrollState, decay) {
    object : FlingBehavior {
        override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
            if (abs(initialVelocity) <= 1f) return initialVelocity
            var velocityLeft = initialVelocity
            var last = 0f
            AnimationState(initialValue = 0f, initialVelocity = initialVelocity)
                .animateDecay(decay) {
                    val delta = value - last
                    val consumed = scrollBy(delta)
                    last = value
                    velocityLeft = this.velocity
                    if (abs(delta - consumed) > 0.5f) cancelAnimation()
                }
            return velocityLeft
        }
    }
}
