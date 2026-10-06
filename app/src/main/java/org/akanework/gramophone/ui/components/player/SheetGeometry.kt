/*
 *     Copyright (C) 2026 Akane Foundation
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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.ui.components.home.LIBRARY_COVER_START
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ARC_HORIZONTAL_EASING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.CORNER_SQUARE_START
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_ACTION_BAR
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_ART_MAX_HEIGHT_FRACTION
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_ART_SIDE_INSET
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_ART_TOP_OFFSET
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_CONTROLS_FIXED
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_CONTROLS_LINES
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_CONTROLS_MIN_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LAND_ART_BOTTOM
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LAND_ART_START
import org.akanework.gramophone.ui.components.player.PlayerUtilities.LAND_ART_TOP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_ARTWORK
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_ARTWORK_CORNER
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_CORNER
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_HEIGHT
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_PLATFORM_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_PLATFORM_MIN
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_SIDE_INSET
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_PREVIEW_HEIGHT
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SIZE_EASING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.TOP_BUTTON_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.absolute
import org.akanework.gramophone.ui.components.player.PlayerUtilities.absoluteUnbounded
import org.akanework.gramophone.ui.components.player.PlayerUtilities.arcFraction

/** The floating mini bar's margin (px) above the bottom of the screen, clear of [bottomInset]. */
private fun Density.miniBarMargin(bottomInset: Float): Float =
    maxOf(bottomInset + MINI_PLATFORM_GAP.toPx(), MINI_PLATFORM_MIN.toPx())

/**
 * The room (px) the floating mini bar takes at the bottom of the screen with [bottomInset] (px):
 * its margin plus the bar. Lists are padded by it so the bar doesn't cover them.
 */
fun collapsedFootprint(bottomInset: Float, density: Density): Float =
    with(density) { miniBarMargin(bottomInset) + MINI_HEIGHT.toPx() }

/**
 * What doesn't move with the sheet (px, in the root): the screen and its insets, and both ends of
 * the morph, the floating mini bar and the full screen player. The composition may read it.
 * [frameAt] gives the sheet at a progress.
 *
 * Everything is measured from the left, as the insets are, and placed without mirroring: right to
 * left ([isRtl]), the covers are put at the right here, where the mirrored rows have their start.
 */
@Suppress("LongParameterList")
class SheetGeometry(
    val rootWidth: Float,
    val rootHeight: Float,
    val statusTop: Float,
    val bottomInset: Float,
    val leftInset: Float,
    val rightInset: Float,
    val isWideLandscape: Boolean,
    private val isRtl: Boolean,
    /** The device screen's corner, which the rising sheet rounds its corners up to. */
    private val pageCorner: Dp,
    private val expandedArtCorner: Dp,
    density: Density,
) {
    private val miniBarMargin = density.miniBarMargin(bottomInset)
    val collapsedHeight = with(density) { MINI_HEIGHT.toPx() }

    /** See [collapsedFootprint]. */
    val collapsedFootprint = miniBarMargin + collapsedHeight
    private val collapsedTop = rootHeight - miniBarMargin - collapsedHeight
    // Its margin beyond the side system bars and cutouts, as the home's sheet has its own
    private val collapsedLeft = leftInset + with(density) { MINI_SIDE_INSET.toPx() }
    private val collapsedRight = rightInset + with(density) { MINI_SIDE_INSET.toPx() }

    /** Vertical distance of a full expand. */
    val travelPx = collapsedTop.coerceAtLeast(1f)

    // The mini bar's cover, lined up with the covers of the home's list rows
    val collapsedArtSize = with(density) { MINI_ARTWORK.toPx() }
    val collapsedArtLeft = with(density) {
        if (isRtl) rootWidth - rightInset - LIBRARY_COVER_START.toPx() - collapsedArtSize
        else leftInset + LIBRARY_COVER_START.toPx()
    }
    private val collapsedArtTop = collapsedTop + (collapsedHeight - collapsedArtSize) / 2f

    // The full player's cover
    val expandedArtSize: Float
    val expandedArtLeft: Float
    val expandedArtTop: Float

    /**
     * The queue, its handle over its songs, from the player's bottom row ([queueRowBounds]) down
     * below the screen, under the row. The two come up [queueTravel] together, the queue to fill
     * the screen under the [queuePreviewBounds], or in landscape under the status bar.
     */
    val queuePanelBounds: Rect
    val queueRowBounds: Rect
    val queueTravel: Float

    /**
     * The song playing, at the top of the player (under the status bar) once the queue is up.
     * Portrait only: in landscape, the cover beside the queue shows it.
     */
    val queuePreviewBounds: Rect

    init {
        fun Dp.px() = with(density) { toPx() }
        val safeWidth = (rootWidth - leftInset - rightInset).coerceAtLeast(0f)
        if (isWideLandscape) {
            expandedArtTop = statusTop + LAND_ART_TOP.px()
            expandedArtSize =
                (rootHeight - statusTop - bottomInset - LAND_ART_TOP.px() - LAND_ART_BOTTOM.px())
                    .coerceAtLeast(0f)
            expandedArtLeft =
                if (isRtl) rootWidth - rightInset - LAND_ART_START.px() - expandedArtSize
                else leftInset + LAND_ART_START.px()
        } else {
            expandedArtTop = statusTop + EXPANDED_ART_TOP_OFFSET.px()
            // Whatever height the controls below the cover leave over, so short screens shrink
            // the cover rather than the controls.
            val textHeight =
                with(density) { EXPANDED_CONTROLS_LINES.sumOf { it.toPx().toDouble() } }
            val controlsHeight = EXPANDED_CONTROLS_MIN_GAP.px() + EXPANDED_CONTROLS_FIXED.px() +
                textHeight.toFloat() + EXPANDED_ACTION_BAR.px()
            expandedArtSize =
                minOf(
                    safeWidth - EXPANDED_ART_SIDE_INSET.px() * 2f,
                    (rootHeight - expandedArtTop) * EXPANDED_ART_MAX_HEIGHT_FRACTION,
                    rootHeight - bottomInset - expandedArtTop - controlsHeight,
                ).coerceAtLeast(0f)
            // Full width it lines up with the top buttons, smaller it is centered.
            expandedArtLeft = leftInset + (safeWidth - expandedArtSize) / 2f
        }
        // Across the screen, or the controls' column between the landscape cover and the top
        // buttons
        val queueLeft = when {
            !isWideLandscape -> 0f
            isRtl -> leftInset + TOP_BUTTON_SIZE.px()
            else -> expandedArtLeft + expandedArtSize
        }
        val queueRight = when {
            !isWideLandscape -> rootWidth
            isRtl -> expandedArtLeft
            else -> rootWidth - rightInset - TOP_BUTTON_SIZE.px()
        }
        val rowHeight = EXPANDED_ACTION_BAR.px()
        val rowTop = rootHeight - bottomInset - rowHeight
        // In landscape the cover stays beside the queue, which comes up to the top: no preview
        val previewBottom =
            if (isWideLandscape) statusTop else statusTop + QUEUE_PREVIEW_HEIGHT.px()
        queueTravel = (rowTop - previewBottom).coerceAtLeast(1f)
        queueRowBounds = Rect(queueLeft, rowTop, queueRight, rowTop + rowHeight)
        queuePanelBounds = Rect(queueLeft, rowTop, queueRight, rowTop + rootHeight - previewBottom)
        queuePreviewBounds = Rect(queueLeft, 0f, queueRight, previewBottom)
    }

    /** The sheet [progress] of the way from the mini bar (0) to the full screen player (1). */
    fun frameAt(progress: Float): SheetFrame {
        val clamped = progress.coerceIn(0f, 1f) // Sanitize
        val sheetTop = lerp(collapsedTop, 0f, progress)
        val sheetBottom = rootHeight - lerp(miniBarMargin, 0f, progress)
        val sheetLeft = lerp(collapsedLeft, 0f, progress)
        val sheetRight = lerp(collapsedRight, 0f, progress)

        val horizontalFraction = arcFraction(clamped, ARC_HORIZONTAL_EASING)
        val artSize = lerp(collapsedArtSize, expandedArtSize, SIZE_EASING.transform(clamped))
        val centerX =
            lerp(
                collapsedArtLeft + collapsedArtSize / 2f,
                expandedArtLeft + expandedArtSize / 2f,
                horizontalFraction,
            )
        val centerY =
            lerp(
                collapsedArtTop + collapsedArtSize / 2f,
                expandedArtTop + expandedArtSize / 2f,
                progress,
            )

        // Round the rising card's corners up to the device screen corner, then square off in the
        // final sliver where the display's own corners take over at full screen.
        val corner =
            if (clamped <= CORNER_SQUARE_START) {
                lerp(MINI_CORNER, pageCorner, clamped / CORNER_SQUARE_START)
            } else {
                val squaring = (clamped - CORNER_SQUARE_START) / (1f - CORNER_SQUARE_START)
                lerp(pageCorner, 0.dp, squaring)
            }

        return SheetFrame(
            progress = progress,
            sheetBounds = Rect(
                Offset(sheetLeft, sheetTop),
                Size(rootWidth - sheetLeft - sheetRight, sheetBottom - sheetTop),
            ),
            corner = corner,
            artBounds = Rect(
                Offset(centerX - artSize / 2f - sheetLeft, centerY - artSize / 2f - sheetTop),
                Size(artSize, artSize),
            ),
            artCorner = lerp(MINI_ARTWORK_CORNER, expandedArtCorner, clamped),
            pageBounds = Rect(Offset(-sheetLeft, 0f), Size(rootWidth, rootHeight)),
            contentFollowTop =
                if (isWideLandscape) 0f else centerY - (expandedArtTop + expandedArtSize / 2f),
        )
    }
}

/**
 * The sheet at one [progress] of the morph, made by [SheetGeometry.frameAt]. It changes every
 * frame of motion, so it's read in the layout and draw phases.
 */
class SheetFrame(
    /** From 0, the mini bar, to 1, the full screen player. */
    val progress: Float,
    /** The sheet's box in the root. */
    val sheetBounds: Rect,
    /** The sheet's corner radius. */
    val corner: Dp,
    /** The cover's box in the sheet. */
    val artBounds: Rect,
    /** The cover's corner radius. */
    val artCorner: Dp,
    /** The full screen page's box in the sheet, before it follows the cover. */
    val pageBounds: Rect,
    /** How far (px) the portrait full player sits off its place, following the moving cover. */
    val contentFollowTop: Float,
)

/** Clips to the sheet's rounded corners, read in the draw phase. */
internal fun Modifier.clipToSheet(frame: () -> SheetFrame): Modifier =
    graphicsLayer {
        shape = RoundedCornerShape(frame().corner)
        clip = true
    }

/** Lays the content out over the sheet and clips it to the sheet's corners. */
internal fun Modifier.onSheet(frame: () -> SheetFrame): Modifier =
    absolute { frame().sheetBounds }.clipToSheet(frame)

/**
 * Lays the content out over the whole screen, following the cover as it moves, in a layer that
 * [layer] may set more of (for [frame]).
 */
internal fun Modifier.pageFollowingCover(
    frame: () -> SheetFrame,
    layer: GraphicsLayerScope.(SheetFrame) -> Unit = {},
): Modifier =
    absoluteUnbounded { frame().pageBounds }
        .graphicsLayer {
            val f = frame()
            translationY = f.contentFollowTop - f.sheetBounds.top
            layer(f)
        }
