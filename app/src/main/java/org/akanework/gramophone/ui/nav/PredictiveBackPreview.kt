package org.akanework.gramophone.ui.nav

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.SceneState
import androidx.navigationevent.NavigationEvent
import org.akanework.gramophone.ui.LocalDarkTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Renders the current and previous entries the way Android's cross-activity predictive back does:
 * the current page scales down and follows the finger while the previous page waits underneath a
 * scrim, then both settle with a small fling overshoot once the gesture is committed.
 *
 * Entries are rendered through the same decorated [SceneState] that NavDisplay uses, so their
 * content is moved here via movableContentOf instead of being recreated.
 *
 * Adapted from tuned (ink.duo3.tuned.navigation.AndroidPredictiveBackPreview).
 */
@Composable
internal fun <T : Any> AndroidPredictiveBackPreview(
    state: AndroidPredictiveBackState,
    sceneState: SceneState<T>,
    modifier: Modifier = Modifier,
) {
    val previousEntry = sceneState.previousScenes
        .lastOrNull()
        ?.entries
        ?.lastOrNull()
    val currentEntry = sceneState.currentScene.entries.lastOrNull()
    if (previousEntry == null || currentEntry == null) return

    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        AndroidPredictiveBackEntry(state, PredictiveBackRole.Previous, previousEntry::Content)
        AndroidPredictiveBackScrim(state)
        AndroidPredictiveBackEntry(state, PredictiveBackRole.Current, currentEntry::Content)
    }
}

@Composable
internal fun AndroidPredictiveBackScrim(state: AndroidPredictiveBackState) {
    val maxAlpha = if (LocalDarkTheme.current) MAX_SCRIM_ALPHA_DARK else MAX_SCRIM_ALPHA_LIGHT
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = (maxAlpha * (1f - state.commitProgress)).coerceIn(0f, 1f) }
            .background(Color.Black)
    )
}

@Composable
private fun AndroidPredictiveBackEntry(
    state: AndroidPredictiveBackState,
    role: PredictiveBackRole,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().predictiveBackRole(state, role)) {
        content()
    }
}

/**
 * Applies the predictive-back transform of [role] (scale, follow-the-finger offsets, device
 * corner clipping, fade on commit) as a graphics layer. All gesture state is read inside the
 * layer block, so per-frame updates never recompose. [enabled] false yields the identity
 * transform plus [idleTranslationX].
 */
@Composable
internal fun Modifier.predictiveBackRole(
    state: AndroidPredictiveBackState,
    role: PredictiveBackRole,
    enabled: Boolean = true,
    idleTranslationX: () -> Float = { 0f },
): Modifier {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val baseCornerRadius = windowCornerRadius()
    val geometry = remember(size, density) {
        PredictiveBackGeometry(
            width = size.width.toFloat(),
            height = size.height.toFloat(),
            margin = with(density) { PREDICTIVE_BACK_MARGIN.roundToPx().toFloat() },
            enteringOffset = with(density) { NAV_TRANSITION_DISTANCE.roundToPx().toFloat() },
        )
    }
    return this
        .onSizeChanged { size = it }
        .graphicsLayer {
            val transform = if (enabled) calculateTransform(state, role, geometry)
            else PredictiveBackTransform()
            scaleX = transform.scale
            scaleY = transform.scale
            translationX = transform.translationX +
                    if (!enabled || state.phase == PredictiveBackPhase.Idle) idleTranslationX() else 0f
            translationY = transform.translationY
            alpha = transform.alpha
            shape = RoundedCornerShape(baseCornerRadius * cornerRadiusFraction(state))
            clip = transform.clip
        }
}

/**
 * Same transform as [predictiveBackRole], applied at draw time instead of as a layer. A layer
 * would move the node's coordinates, and interop views (the fragment pages) re-derive their
 * window insets from their position, which makes their content shift inside the scaled page.
 * [hidden] skips drawing entirely.
 */
@Composable
internal fun Modifier.predictiveBackRoleDrawn(
    state: AndroidPredictiveBackState,
    role: PredictiveBackRole,
    enabled: Boolean = true,
    hidden: () -> Boolean = { false },
): Modifier {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val baseCornerRadius = windowCornerRadius()
    val geometry = remember(size, density) {
        PredictiveBackGeometry(
            width = size.width.toFloat(),
            height = size.height.toFloat(),
            margin = with(density) { PREDICTIVE_BACK_MARGIN.roundToPx().toFloat() },
            enteringOffset = with(density) { NAV_TRANSITION_DISTANCE.roundToPx().toFloat() },
        )
    }
    return this
        .onSizeChanged { size = it }
        .drawWithContent {
            if (hidden()) return@drawWithContent
            val transform = if (enabled) calculateTransform(state, role, geometry)
            else PredictiveBackTransform()
            if (transform == PredictiveBackTransform()) {
                drawContent()
                return@drawWithContent
            }
            val radius = (baseCornerRadius * cornerRadiusFraction(state)).toPx()
            drawIntoCanvas { canvas ->
                val bounds = Rect(0f, 0f, this.size.width, this.size.height)
                val layered = transform.alpha < 1f
                if (layered) {
                    canvas.saveLayer(bounds, Paint().apply { alpha = transform.alpha })
                } else {
                    canvas.save()
                }
                canvas.translate(transform.translationX, transform.translationY)
                canvas.translate(bounds.center.x, bounds.center.y)
                canvas.scale(transform.scale, transform.scale)
                canvas.translate(-bounds.center.x, -bounds.center.y)
                if (transform.clip) {
                    canvas.clipPath(Path().apply {
                        addRoundRect(RoundRect(bounds, CornerRadius(radius, radius)))
                    })
                }
                this@drawWithContent.drawContent()
                canvas.restore()
            }
        }
}

/** The device's smallest rounded-corner radius (28dp when unknown), as the preview clip radius. */
@Composable
private fun windowCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val display = view.display
            val radiusPx = listOf(
                RoundedCorner.POSITION_TOP_LEFT,
                RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_RIGHT,
                RoundedCorner.POSITION_BOTTOM_LEFT,
            )
                .mapNotNull { display?.getRoundedCorner(it)?.radius }
                .filter { it > 0 }
                .minOrNull()
            radiusPx?.let { with(density) { it.toDp() } } ?: FALLBACK_CORNER_RADIUS
        } else {
            FALLBACK_CORNER_RADIUS
        }
    }
}

private fun cornerRadiusFraction(state: AndroidPredictiveBackState): Float {
    val gestureFraction = CornerRadiusEasing
        .transform((state.gestureProgress / CORNER_RADIUS_REVEAL_PROGRESS).coerceIn(0f, 1f))
    return if (state.phase == PredictiveBackPhase.Committing) {
        lerp(gestureFraction, 1f, NavAxisEasing.transform(state.commitProgress))
    } else {
        gestureFraction
    }
}

private fun calculateTransform(
    state: AndroidPredictiveBackState,
    role: PredictiveBackRole,
    geometry: PredictiveBackGeometry,
): PredictiveBackTransform =
    if (state.phase == PredictiveBackPhase.Idle || role == PredictiveBackRole.None) {
        PredictiveBackTransform()
    } else {
        val preCommit = calculatePreCommitTransform(state, role, geometry)
        if (state.phase == PredictiveBackPhase.Committing) {
            calculateCommitTransform(state, role, geometry, preCommit)
        } else {
            preCommit
        }
    }

private fun calculatePreCommitTransform(
    state: AndroidPredictiveBackState,
    role: PredictiveBackRole,
    geometry: PredictiveBackGeometry,
): PredictiveBackTransform {
    val progress = state.gestureProgress
    val scale = lerp(1f, PREDICTIVE_BACK_MAX_SCALE, progress)
    val verticalOffset =
        calculateVerticalOffset(state.touchDeltaY, geometry.height, scale, geometry.margin)
    return when (role) {
        PredictiveBackRole.Current -> PredictiveBackTransform(
            scale = scale,
            translationX = if (state.swipeEdge == NavigationEvent.EDGE_RIGHT) {
                0f
            } else {
                (geometry.width * (1f - PREDICTIVE_BACK_MAX_SCALE) / 2f - geometry.margin) * progress
            },
            translationY = verticalOffset,
            clip = true,
        )
        PredictiveBackRole.Previous -> PredictiveBackTransform(
            scale = scale,
            translationX = -geometry.enteringOffset,
            translationY = verticalOffset,
            clip = true,
        )
        PredictiveBackRole.None -> PredictiveBackTransform()
    }
}

private fun calculateCommitTransform(
    state: AndroidPredictiveBackState,
    role: PredictiveBackRole,
    geometry: PredictiveBackGeometry,
    preCommit: PredictiveBackTransform,
): PredictiveBackTransform {
    val progress = NavAxisEasing.transform(state.commitProgress)
    val flingScale = state.postCommitFlingScale
    return when (role) {
        PredictiveBackRole.Current -> PredictiveBackTransform(
            scale = lerp(preCommit.scale, 1f, progress) * flingScale,
            translationX = lerp(
                preCommit.translationX,
                preCommit.left(geometry.width) + geometry.enteringOffset,
                progress,
            ),
            translationY = lerp(preCommit.translationY, 0f, progress),
            alpha = max(1f - state.commitProgress * 5f, 0f),
            clip = true,
        )
        PredictiveBackRole.Previous -> PredictiveBackTransform(
            scale = lerp(preCommit.scale, 1f, progress) * flingScale,
            translationX = lerp(preCommit.translationX, 0f, progress),
            translationY = lerp(preCommit.translationY, 0f, progress),
            clip = true,
        )
        PredictiveBackRole.None -> PredictiveBackTransform()
    }
}

private fun calculateVerticalOffset(
    touchDeltaY: Float,
    height: Float,
    scale: Float,
    margin: Float,
): Float {
    if (height == 0f) return 0f
    val deltaRatio = min(height / 2f, abs(touchDeltaY)) / (height / 2f)
    val interpolatedRatio = 1f - (1f - deltaRatio) * (1f - deltaRatio)
    val direction = if (touchDeltaY < 0f) -1f else 1f
    return max(0f, height * (1f - scale) / 2f - margin) * interpolatedRatio * direction
}

private fun PredictiveBackTransform.left(width: Float): Float =
    translationX + width * (1f - scale) / 2f

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress

private data class PredictiveBackTransform(
    val scale: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val alpha: Float = 1f,
    val clip: Boolean = false,
)

private data class PredictiveBackGeometry(
    val width: Float,
    val height: Float,
    val margin: Float,
    val enteringOffset: Float,
)

internal enum class PredictiveBackRole {
    None,
    Current,
    Previous,
}

private val PREDICTIVE_BACK_MARGIN = 8.dp
private val FALLBACK_CORNER_RADIUS = 28.dp
private const val CORNER_RADIUS_REVEAL_PROGRESS = 0.25f
private const val MAX_SCRIM_ALPHA_DARK = 0.8f
private const val MAX_SCRIM_ALPHA_LIGHT = 0.2f
private val CornerRadiusEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
