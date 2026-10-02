package org.akanework.gramophone.ui.components.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import kotlin.math.roundToInt

object PlayerUtilities {
    const val TWO_PI = (Math.PI * 2f).toFloat()

    const val SQUIGGLY_TRACK_ALPHA = 0.30f
    const val LYRIC_COVER_FADE_MS = 125

    // Speed dialog
    const val SPEED_MIN = 0.25f
    const val SPEED_MAX = 4.0f
    const val SPEED_STEPS = 374

    // Player sheet UI
    val MINI_ART_TO_TEXT_GAP = 12.dp
    val MINI_BUTTON_SIZE = 40.dp
    val MINI_ICON_SIZE = 20.dp
    val FALLBACK_PAGE_CORNER = 28.dp
    const val SCRIM_MAX_ALPHA = 0.32f
    const val TAP_EXPAND_LIMIT = 0.35f
    const val CONTENT_CROSSFADE_START = 0.35f

    // The preview (mini-bar content ) fades out early, before the full player fades in
    const val MINI_FADE_END = 0.1f
    const val COVER_CLICK_MIN = 0.95f
    const val ARTWORK_SEED_SIZE = 24
    /** With the colour accuracy setting on: more of the cover goes into the seed. */
    const val ARTWORK_SEED_SIZE_ACCURATE = 128
    const val ARTWORK_QUANTIZE_MAX = 128

    const val SETTLED_EPS = 0.99f

    // Floating mini bar
    val MINI_HEIGHT = 56.dp
    val MINI_SIDE_INSET = 16.dp
    val MINI_ARTWORK = 44.dp
    val MINI_CORNER = 24.dp
    val MINI_ARTWORK_CORNER = 6.dp
    val MINI_PLATFORM_GAP = 8.dp
    val MINI_PLATFORM_MIN = 24.dp

    // The full player's layout: FullPlayer lays it out, SheetGeometry sums it up to size the cover
    /** The top buttons, in a row above the cover (portrait) or a column beside it (landscape). */
    val TOP_BUTTON_SIZE = 52.dp
    /** Between the top button row and the cover. */
    val ART_TOP_GAP = 16.dp
    val TITLE_LINE_HEIGHT = 32.sp
    val ARTIST_LINE_HEIGHT = 25.sp
    /** The position and duration line under the progress bar. */
    val TIME_LINE_HEIGHT = 24.sp
    val TITLE_ARTIST_GAP = 3.dp
    val ARTIST_PROGRESS_GAP = 12.dp
    val PROGRESS_BAR_HEIGHT = 48.dp
    val TIME_TRANSPORT_GAP = 18.dp
    /** The previous, play and next buttons, which shrink as squares if they have to. */
    val TRANSPORT_BUTTON_SIZE = 90.dp
    /** The bottom row's buttons: lyrics, repeat, shuffle, favourite and queue. */
    val ACTION_BUTTON_SIZE = 48.dp
    val ACTION_BAR_VERTICAL_PADDING = 4.dp

    // Expanded artwork
    val EXPANDED_ART_SIDE_INSET = 24.dp
    val EXPANDED_ART_TOP_OFFSET = TOP_BUTTON_SIZE + ART_TOP_GAP
    val EXPANDED_ART_CORNER = 22.dp
    const val EXPANDED_ART_MAX_HEIGHT_FRACTION = 0.5f
    // What the portrait player keeps below the cover, so the cover shrinks on short screens
    // instead of squashing the controls: the gaps, the progress bar and the transport row, the
    // title, artist and time lines at their line heights (which grow with the font scale), the
    // bottom button row, and the least room around the controls (split above the title and below
    // the transport row) so they never touch the cover.
    val EXPANDED_CONTROLS_FIXED =
        TITLE_ARTIST_GAP + ARTIST_PROGRESS_GAP + PROGRESS_BAR_HEIGHT + TIME_TRANSPORT_GAP +
            TRANSPORT_BUTTON_SIZE
    // Converted to px one by one, as the text does: with non-linear font scaling (Android 14)
    // their sum in sp would grow less than the lines themselves.
    val EXPANDED_CONTROLS_LINES = listOf(TITLE_LINE_HEIGHT, ARTIST_LINE_HEIGHT, TIME_LINE_HEIGHT)
    val EXPANDED_ACTION_BAR = ACTION_BUTTON_SIZE + ACTION_BAR_VERTICAL_PADDING * 2
    val EXPANDED_CONTROLS_MIN_GAP = 48.dp

    const val WIDE_LANDSCAPE_MIN_WIDTH = 600
    // Around the landscape cover
    val LAND_ART_START = 24.dp
    val LAND_ART_TOP = 16.dp
    val LAND_ART_BOTTOM = 25.dp
    val PORTRAIT_MARGIN = 36.dp
    val LANDSCAPE_MARGIN = 28.dp

    const val CORNER_SQUARE_START = 0.9f

    val ARC_HORIZONTAL_EASING = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
    const val ARC_STRENGTH = 1f
    val SIZE_EASING = CubicBezierEasing(0.1f, 0f, 0.4f, 1f)

    const val POSITION_POLL_MS = 500L
    const val FULL_POLL_MS = 100L

    val SQUIGGLY_WAVELENGTH = 27.dp
    val SQUIGGLY_AMPLITUDE = 2.7.dp
    val SQUIGGLY_PHASE_SPEED = 8.dp
    val SQUIGGLY_STROKE_WIDTH = 3.4.dp
    const val SQUIGGLY_AMP_IN_MS = 800
    const val SQUIGGLY_AMP_OUT_MS = 550
    const val SQUIGGLY_TRANSITION_PERIODS = 1.5f
    val SQUIGGLY_THUMB_WIDTH = 5.dp
    val SQUIGGLY_THUMB_HEIGHT = 20.dp

    val TIMER_MINUTES = listOf(0, 1, 3, 5, 10, 15, 20, 30, 45, 60, 90)

    const val COMMIT_THRESHOLD = 0.4f
    const val FLING_VELOCITY = 1000f
    const val SPATIAL_DAMPING = 1f
    const val SPATIAL_STIFFNESS = 500f
    const val PROGRESS_THRESHOLD = 0.0001f
    const val ENDPOINT_THRESHOLD = 0.001f

    fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
        clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    }

    fun miniContentAlpha(progress: Float): Float =
        (1f - progress / MINI_FADE_END).coerceIn(0f, 1f)

    fun expandedContentAlpha(progress: Float): Float =
        ((progress - CONTENT_CROSSFADE_START) / (1f - CONTENT_CROSSFADE_START)).coerceIn(0f, 1f)

    fun arcFraction(
        progress: Float,
        easing: Easing,
    ): Float = lerp(progress, easing.transform(progress), ARC_STRENGTH)

    /**
     * Lays the content out at [bounds] (px, in the parent's coordinates, mirrored in RTL like an
     * offset), within the parent's constraints and in its own layer, so moving it doesn't redraw
     * the parent. [bounds] is read in the layout phase, so a moving sheet lays out again without
     * recomposing.
     */
    fun Modifier.absolute(bounds: () -> Rect): Modifier = placeAt(bounded = true, bounds)

    /** Like [absolute], but at its full size even past the parent, and never mirrored. */
    fun Modifier.absoluteUnbounded(bounds: () -> Rect): Modifier = placeAt(bounded = false, bounds)

    // The modifier takes the whole parent. An offset would leave its own box, the size of the
    // content, at the parent's corner, which shows up there with layout bounds on.
    private fun Modifier.placeAt(bounded: Boolean, bounds: () -> Rect): Modifier =
        layout { measurable, constraints ->
            val b = bounds()
            val size = Constraints.fixed(
                b.width.roundToInt().coerceAtLeast(0),
                b.height.roundToInt().coerceAtLeast(0),
            )
            val placeable = measurable.measure(if (bounded) constraints.constrain(size) else size)
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
            val height =
                if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
            layout(width, height) {
                val x = b.left.roundToInt()
                val y = b.top.roundToInt()
                if (bounded) placeable.placeRelativeWithLayer(x, y) else placeable.place(x, y)
            }
        }
}