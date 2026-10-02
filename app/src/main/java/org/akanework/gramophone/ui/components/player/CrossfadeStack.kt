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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A stack of values, each fading in over the ones under it, so a new one can come in while the
 * last is still fading in. Once one is opaque, the ones under it are dropped. The bottom one is
 * always opaque. Snapshot state: read [layers] where they're drawn.
 */
@Stable
internal class CrossfadeStack<T>(initial: T) {
    /** A value, [fraction] of the way faded in over the ones under it. */
    class Layer<T>(value: T, fraction: Float) {
        /** Snapshot state: [retargetTop] can move it on while it fades in. */
        var value by mutableStateOf(value)
            internal set
        val fraction = Animatable(fraction)
    }

    private val stack = mutableStateListOf(Layer(initial, 1f))

    /** The layers from the bottom up. */
    val layers: List<Layer<T>>
        get() = stack

    /** The value on top, which the stack fades to. */
    val top: T
        get() = stack.last().value

    /** Makes [value] the top one in place of the current top one, going on with its fade. */
    fun retargetTop(value: T) {
        stack.last().value = value
    }

    /** Stacks [value] on top, transparent until it's faded in with [fadeIn]. */
    fun push(value: T): Layer<T> = Layer(value, 0f).also { stack += it }

    /** Fades [layer] in with [spec], then drops the layers under it. */
    suspend fun fadeIn(layer: Layer<T>, spec: AnimationSpec<Float>) {
        layer.fraction.animateTo(1f, spec)
        // Opaque now: drop the ones under it.
        val index = stack.indexOf(layer)
        if (index > 0) stack.removeRange(0, index)
    }
}
