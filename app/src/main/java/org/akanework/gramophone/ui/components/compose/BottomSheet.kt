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

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.pow
import kotlin.math.roundToInt

// TODO: explore performance of val queueComposed by remember(state, queue) { derivedStateOf { (state.expandedTarget && state.progress >= 1f) || queue.shown } }
// TODO: explore collapsed -> expanded no fade alpha
/**
 * Bottom Sheet
 * Modified from [OuterTune](https://github.com/OuterTune/OuterTune)
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    contentBackground: @Composable (BoxScope.() -> Unit) = { },
    onDismiss: (() -> Unit)? = null,
    collapsedWidth: Float = 1f,
    collapsedContent: @Composable BoxScope.() -> Unit,
    contentShape: RoundedCornerShape? = RoundedCornerShape(
        topStart = lerp(16.dp, 0.dp, state.progress),
        topEnd = lerp(16.dp, 0.dp, state.progress)
    ),
    content: @Composable BoxScope.() -> Unit,
) {
    /* performant blur background fade
    Box(
        modifier = modifier
            .graphicsLayer {
                // background fades during about 10%-61% progress
                alpha = bgProgressCurve(state.progress)
            }
            .fillMaxSize(),
        content = contentBackground
    )
     */
    // 1.4 (x-0.1)^5, for 0<=x<=1
    fun bgProgressCurve(x: Float) = (1.4f * (x.coerceIn(0.1f, 1f) - 0.1f).pow(0.5f)).coerceIn(0f, 1f)

    val modifier = modifier
        .layout { measurable, constraints ->
            val expandedPx = state.expandedBound.roundToPx()
            val placeable = measurable.measure(
                constraints.copy(
                    minWidth = constraints.maxWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = expandedPx,
                    maxHeight = expandedPx,
                )
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                val y = constraints.maxHeight - state.value.roundToPx()
                if (collapsedWidth == 1f) {
                    placeable.place(0, y)
                } else {
                    // animate collapsed content width to will width
                    val collapsedOffset = constraints.maxWidth - (constraints.maxWidth * collapsedWidth).roundToInt()
                    val progress = bgProgressCurve(state.progress)
                    val x = (collapsedOffset * (1f - progress)).roundToInt()
                    placeable.placeRelative(x, y)
                }
            }
        }
        .pointerInput(state) {
            val velocityTracker = VelocityTracker()

            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    velocityTracker.addPointerInputChange(change)
                    state.dispatchRawDelta(dragAmount)
                },
                onDragCancel = {
                    velocityTracker.resetTracking()
                    state.snapTo(state.collapsedBound)
                },
                onDragEnd = {
                    val velocity = -velocityTracker.calculateVelocity().y
                    velocityTracker.resetTracking()
                    state.performFling(velocity, onDismiss)
                }
            )
        }

    Box(
        modifier = contentShape?.let {
            modifier.clip(it)
        } ?: modifier
    ) {
        // main
        if (!state.isCollapsed) {
            PredictiveBackHandler(!state.isDismissed) { backEvents ->
                val start = state.value
                try {
                    backEvents.collect { event ->
                        val travel = event.progress * 0.35f
                        state.snapTo(lerp(start, state.collapsedBound, travel))
                    }
                    state.collapseSoft()
                } catch (e: CancellationException) {
                    if (start >= state.expandedBound) {
                        state.expandSoft()
                    } else {
                        state.collapseSoft()
                    }
                    throw e
                }
            }
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = bgProgressCurve(state.progress)
                    }
                    .fillMaxSize(),
                content = contentBackground
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = ((state.progress - 0.15f) * 4).coerceIn(0f, 1f)
                    },
                content = content
            )
        }

        // collapsed content
        if (!state.isExpanded) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = 1f - (state.progress * 4).coerceAtMost(1f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = state::expandSoft
                    )
                    .fillMaxWidth()
                    .height(state.collapsedBound)
                    .testTag("bottom_sheet"),
                content = collapsedContent
            )
        }
    }
}

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    val collapsedBound: Dp,
    private val dismissable: Boolean,
) : DraggableState by draggableState {

    companion object {
        const val EXPANDED_ANCHOR = 2
        const val COLLAPSED_ANCHOR = 1
        const val DISMISSED_ANCHOR = 0
    }

    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value <= collapsedBound
    }

    val isExpanded by derivedStateOf {
        progress >= 1.0f
    }

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
    }

    fun collapse(animationSpec: AnimationSpec<Dp>) {
        onAnchorChanged(COLLAPSED_ANCHOR)
        coroutineScope.launch {
            animatable.animateTo(collapsedBound, animationSpec)
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>) {
        onAnchorChanged(EXPANDED_ANCHOR)
        coroutineScope.launch {
            animatable.animateTo(animatable.upperBound!!, animationSpec)
        }
    }

    private fun collapse() {
        collapse(BottomSheetAnimationSpec)
    }

    private fun expand() {
        expand(BottomSheetAnimationSpec)
    }

    fun collapseSoft() {
        collapse(BottomSheetSoftAnimationSpec)
    }

    fun expandSoft() {
        expand(BottomSheetSoftAnimationSpec)
    }

    fun forceDismiss() = dismiss(true)

    /**
     * Dismiss the bottom sheet.
     *
     * @param force Setting to true will ignore the value of [dismissable] and dismiss anyway.
     */
    fun dismiss(force: Boolean = false) {
        if (!force && !dismissable) {
            collapseSoft()
            return
        }
        onAnchorChanged(DISMISSED_ANCHOR)
        coroutineScope.launch {
            animatable.animateTo(animatable.lowerBound!!)
        }
    }

    fun snapTo(value: Dp) {
        coroutineScope.launch {
            animatable.snapTo(value)
        }
    }

    fun performFling(velocity: Float, onDismiss: (() -> Unit)?) {
        if (velocity > 250) {
            expand()
        } else if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse()
            }
        } else {
            val l0 = dismissedBound
            val l1 = (collapsedBound - dismissedBound) / 2
            val l2 = (expandedBound - collapsedBound) / 2
            val l3 = expandedBound

            when (value) {
                in l0..l1 -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse()
                    }
                }

                in l1..l2 -> collapse()
                in l2..l3 -> expand()
                else -> Unit
            }
        }
    }

    /* outertune stuff that may be useful in the future
    val preUpPostDownNestedScrollConnection
        get() = object : NestedScrollConnection {
            var isTopReached = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isExpanded && available.y < 0) {
                    isTopReached = false
                }

                return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!isTopReached) {
                    isTopReached = consumed.y == 0f && available.y > 0
                }

                return if (isTopReached && source == NestedScrollSource.UserInput) {
                    dispatchRawDelta(available.y)
                    available
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                return if (isTopReached) {
                    val velocity = -available.y
                    performFling(velocity, null)

                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                isTopReached = false
                return Velocity.Zero
            }
        }
     */
}

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = BottomSheetState.DISMISSED_ANCHOR,
    dismissable: Boolean = false,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable = remember {
        Animatable(0.dp, Dp.VectorConverter)
    }

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope) {
        val initialValue = when (previousAnchor) {
            BottomSheetState.EXPANDED_ANCHOR -> expandedBound
            BottomSheetState.COLLAPSED_ANCHOR -> collapsedBound
            BottomSheetState.DISMISSED_ANCHOR -> dismissedBound
            else -> error("Unknown BottomSheet anchor")
        }

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        coroutineScope.launch {
            animatable.animateTo(initialValue, NavigationBarAnimationSpec)
        }

        BottomSheetState(
            draggableState = DraggableState { delta ->
                coroutineScope.launch {
                    animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                }
            },
            onAnchorChanged = { previousAnchor = it },
            coroutineScope = coroutineScope,
            animatable = animatable,
            collapsedBound = collapsedBound,
            dismissable = dismissable,
        )
    }
}

val NavigationBarAnimationSpec = spring<Dp>(stiffness = Spring.StiffnessMedium)
val BottomSheetAnimationSpec = spring<Dp>(stiffness = Spring.StiffnessMedium)
val BottomSheetSoftAnimationSpec = spring<Dp>(stiffness = Spring.StiffnessMediumLow)
