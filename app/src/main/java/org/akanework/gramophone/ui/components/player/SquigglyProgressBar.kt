package org.akanework.gramophone.ui.components.player

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import org.akanework.gramophone.logic.utils.CalculationUtils.lerpInvSat
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_AMPLITUDE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_AMP_IN_MS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_AMP_OUT_MS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_PHASE_SPEED
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_STROKE_WIDTH
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_THUMB_HEIGHT
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_THUMB_WIDTH
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_TRANSITION_PERIODS
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SQUIGGLY_WAVELENGTH
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TWO_PI
import kotlin.math.abs
import kotlin.math.cos

@Composable
fun SquigglyProgressBar(
    fraction: Float,
    animating: Boolean,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    onScrub: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    /** A drag was cancelled: drop the scrub without seeking. */
    onScrubCancel: () -> Unit,
) {
    // The gesture handlers outlive recompositions, so they read the latest callbacks
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrubCancel by rememberUpdatedState(onScrubCancel)
    val density = LocalDensity.current
    val waveLength = with(density) { SQUIGGLY_WAVELENGTH.toPx() }
    val amplitude = with(density) { SQUIGGLY_AMPLITUDE.toPx() }
    val phaseSpeed = with(density) { SQUIGGLY_PHASE_SPEED.toPx() }
    val strokeWidth = with(density) { SQUIGGLY_STROKE_WIDTH.toPx() }

    val heightFraction by animateFloatAsState(
        targetValue = if (animating) 1f else 0f,
        animationSpec = tween(if (animating) SQUIGGLY_AMP_IN_MS else SQUIGGLY_AMP_OUT_MS),
        label = "squiggly amplitude",
    )
    var phase by remember { mutableFloatStateOf(0f) }
    if (animating) {
        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                phase = (phase + (now - last) / 1_000_000_000f * phaseSpeed) % waveLength
                last = now
            }
        }
    }

    var dragFraction by remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { off -> currentOnSeek((off.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragFraction = (off.x / size.width).coerceIn(0f, 1f)
                        currentOnScrub(dragFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        currentOnScrub(dragFraction)
                    },
                    onDragEnd = { currentOnSeek(dragFraction) },
                    onDragCancel = { currentOnScrubCancel() },
                )
            },
    ) {
        val totalWidth = size.width
        val centerY = size.height / 2f
        val progress = fraction.coerceIn(0f, 1f)
        val totalProgressPx = totalWidth * progress
        val transitionPeriods = SQUIGGLY_TRANSITION_PERIODS

        fun amp(x: Float, sign: Float): Float {
            val length = transitionPeriods * waveLength
            val coeff = lerpInvSat(totalProgressPx + length / 2f, totalProgressPx - length / 2f, x)
            return sign * heightFraction * amplitude * coeff
        }

        val path = Path()
        val waveStart = -phase - waveLength / 2f
        path.moveTo(waveStart, 0f)
        var currentX = waveStart
        var sign = 1f
        var currentAmp = amp(currentX, sign)
        val dist = waveLength / 2f
        while (currentX < totalWidth) {
            sign = -sign
            val nextX = currentX + dist
            val midX = currentX + dist / 2f
            val nextAmp = amp(nextX, sign)
            path.cubicTo(midX, currentAmp, midX, nextAmp, nextX, nextAmp)
            currentAmp = nextAmp
            currentX = nextX
        }

        val clipTop = amplitude + strokeWidth
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        translate(0f, centerY) {
            clipRect(0f, -clipTop, totalProgressPx, clipTop) {
                drawPath(path, color, style = stroke)
            }
            clipRect(totalProgressPx, -clipTop, totalWidth, clipTop) {
                drawPath(path, trackColor, style = stroke)
            }
            val startAmp = cos(abs(waveStart) / waveLength * TWO_PI) * amplitude * heightFraction
            drawCircle(color, radius = strokeWidth / 2f, center = Offset(0f, startAmp))
            val thumbW = SQUIGGLY_THUMB_WIDTH.toPx()
            val thumbH = SQUIGGLY_THUMB_HEIGHT.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(totalProgressPx - thumbW / 2f, -thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f),
            )
        }
    }
}