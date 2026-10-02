package org.akanework.gramophone.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

val NAV_TRANSITION_DISTANCE = 96.dp
internal const val NAV_TRANSITION_MS = 450
private const val NAV_FADE_MS = 83
private const val NAV_OPEN_FADE_DELAY_MS = 50
private const val NAV_CLOSE_FADE_DELAY_MS = 35

internal val NavAxisEasing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    }
)

// Warms up androidx.graphics.path native so loading
// So DiskReadViolation won't be triggered
//
// P.S. 0f will skip loading hence 0.5f
fun warmUpNavAxisEasing() {
    NavAxisEasing.transform(0.5f)
}

fun navOpenTransition(horizontalOffset: Int): ContentTransform =
    (
        slideInHorizontally(
            animationSpec = tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
            initialOffsetX = { horizontalOffset },
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = NAV_FADE_MS,
                delayMillis = NAV_OPEN_FADE_DELAY_MS,
                easing = LinearEasing,
            ),
        )
    ) togetherWith slideOutHorizontally(
        animationSpec = tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
        targetOffsetX = { -horizontalOffset },
    )

fun navCloseTransition(horizontalOffset: Int): ContentTransform =
    slideInHorizontally(
        animationSpec = tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
        initialOffsetX = { -horizontalOffset },
    ) togetherWith (
        slideOutHorizontally(
            animationSpec = tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
            targetOffsetX = { horizontalOffset },
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = NAV_FADE_MS,
                delayMillis = NAV_CLOSE_FADE_DELAY_MS,
                easing = LinearEasing,
            ),
        )
    )

/**
 * Pop transition for every pop NavDisplay plays itself. When the [AndroidPredictiveBackPreview]
 * has just animated the swap, NavDisplay must snap in silently instead of playing the close
 * animation a second time.
 */
fun navPopTransition(suppress: Boolean, horizontalOffset: Int): ContentTransform =
    if (suppress) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        navCloseTransition(horizontalOffset)
    }
