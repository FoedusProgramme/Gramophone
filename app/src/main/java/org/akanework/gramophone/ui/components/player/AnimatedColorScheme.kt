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

import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The cover scheme, animating to each new one with the spring materialkolor's animateColorScheme
 * uses. Its colours are read where they're used, through [color]: read in the draw phase, a frame
 * of the animation only redraws, where a scheme animated in composition recomposes everything
 * painted in it every frame.
 */
@Stable
class AnimatedColorScheme(initial: ColorScheme, initialLive: Boolean = false) {
    /** The schemes fading in over the ones before them. */
    private val stack = CrossfadeStack(initial)

    /** The scheme it animates to. Changes as a new one comes in, not every frame. */
    val target: ColorScheme
        get() = stack.top

    /** [role] of the scheme as it is this frame. */
    fun color(role: ColorScheme.() -> Color): Color {
        val layers = stack.layers
        var color = layers[0].value.role()
        for (i in 1 until layers.size) {
            val layer = layers[i]
            color = lerp(color, layer.value.role(), layer.fraction.value)
        }
        return color
    }

    /** Reads [role] where it's called, see [color]. */
    fun colorProducer(role: ColorScheme.() -> Color): ColorProducer = ColorProducer { color(role) }

    // Whether the top layer shows a live scheme, see show
    private var topIsLive = initialLive

    /**
     * Fades to [scheme] from the colours as they are, in [scope]. A [live] scheme animates by
     * itself, like the app theme while it changes: from one live scheme to the next, the top layer
     * takes the new one on rather than a fade being stacked for every frame of it.
     */
    fun show(scheme: ColorScheme, live: Boolean, scope: CoroutineScope) {
        if (scheme === target) return
        if (live && topIsLive) {
            stack.retargetTop(scheme)
            return
        }
        topIsLive = live
        val layer = stack.push(scheme)
        scope.launch { stack.fadeIn(layer, spring()) }
    }
}

/**
 * An [AnimatedColorScheme] that follows [target]. [live]: whether [target] animates by itself,
 * see [AnimatedColorScheme.show].
 */
@Composable
fun rememberAnimatedColorScheme(target: ColorScheme, live: Boolean = false): AnimatedColorScheme {
    val scheme = remember { AnimatedColorScheme(target, live) }
    val latest by rememberUpdatedState(target to live)
    // One effect for all targets: a new one fades in over the fades still running
    LaunchedEffect(scheme) {
        val fades = this
        snapshotFlow { latest }.collect { (target, live) -> scheme.show(target, live, fades) }
    }
    return scheme
}
