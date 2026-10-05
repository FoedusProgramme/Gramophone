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

package org.akanework.gramophone.ui.components.compose

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How long after it is composed [DrawWarmUp] waits, clear of the app's start. */
private const val WARM_UP_DELAY_MS = 1500L

/** Frames each step is drawn for: a blur records its source on one and draws from it after. */
private const val FRAMES_PER_STEP = 3

/** The ways a page transition and a menu draw what they show, one step of the warm-up each. */
private enum class WarmUpStep(
    val alpha: Float,
    val scale: Float = 1f,
    /** Partly off the screen to the end (1) or the start (-1). */
    val slide: Int = 0,
) {
    /** Fading in as it slides in: through an offscreen layer. */
    FadingIn(alpha = 0.5f, slide = 1),

    /**
     * Partly off the screen, like the page sliding in and the one sliding out: blurs are
     * clipped to the window, which puts what they blur at odd sizes and between pixels.
     */
    SlidingIn(alpha = 1f, slide = 1),
    SlidingOut(alpha = 1f, slide = -1),

    /** At rest: straight to the screen. */
    AtRest(alpha = 1f),

    /** Growing as a menu opens. */
    Opening(alpha = 0.5f, scale = 0.95f),
}

/** Corners like the home bar's buttons', which clip through the stencil rather than by maths. */
private val UnevenCorners = RoundedCornerShape(
    topStart = 4.dp, bottomStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp,
)

private val SAMPLE_SIZE = 48.dp

/** Above and below a menu's items. */
private val MENU_PADDING = 8.dp

/** Once per process, like the code it runs and the shaders it compiles. */
private var warmedUp = false

/**
 * Draws [page] once, unseen, a while after the app starts, so that opening it is smooth the
 * first time too. The first time a process shows a page its code runs for the first time and,
 * the first time after an install or an update, the GPU compiles the shaders it is drawn with:
 * both on the page's first frames, where they show as a stutter of the transition.
 *
 * [page] is drawn the ways a transition draws it, fading in, sliding and at rest, along with
 * what opening it from a menu draws: the menu's shadow and pressed ripples, clipped to a rectangle
 * like a menu item's and to uneven corners like the home bar's buttons'. Place it under opaque
 * content, which hides it, and give [page] callbacks that do nothing.
 */
@Composable
fun DrawWarmUp(page: @Composable () -> Unit) {
    var step by remember { mutableStateOf<WarmUpStep?>(null) }
    LaunchedEffect(Unit) {
        if (warmedUp) return@LaunchedEffect
        delay(WARM_UP_DELAY_MS)
        for (next in WarmUpStep.entries) {
            step = next
            repeat(FRAMES_PER_STEP) { withFrameNanos { } }
        }
        step = null
        warmedUp = true
    }
    val current = step ?: return
    Box(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    // An odd distance, a third of the way
                    placeable.place(current.slide * (placeable.width / 3 or 1), 0)
                }
            }
            .graphicsLayer {
                alpha = current.alpha
                scaleX = current.scale
                scaleY = current.scale
            },
    ) {
        page()
        Row {
            Surface(
                shape = MenuDefaults.shape,
                color = MenuDefaults.containerColor,
                tonalElevation = MenuDefaults.TonalElevation,
                shadowElevation = MenuDefaults.ShadowElevation,
            ) {
                // Clear of the menu's corners, like its items
                PressedRipple(RectangleShape, Modifier.padding(vertical = MENU_PADDING))
            }
            PressedRipple(UnevenCorners)
        }
    }
}

/** A ripple held pressed, clipped to [shape]. */
@Composable
private fun PressedRipple(shape: Shape, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    LaunchedEffect(interactions) {
        // Once the ripple is listening, a frame after it is composed
        withFrameNanos { }
        interactions.emit(PressInteraction.Press(Offset.Zero))
    }
    Box(modifier.size(SAMPLE_SIZE).clip(shape).indication(interactions, ripple()))
}
