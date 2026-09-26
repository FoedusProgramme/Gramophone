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

package org.akanework.gramophone.ui.components.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.ui.components.home.LIBRARY_COVER_START
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ARC_HORIZONTAL_EASING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.CORNER_SQUARE_START
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_ACTION_BAR
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_ART_CORNER
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_ART_MAX_HEIGHT_FRACTION
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_ART_SIDE_INSET
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_ART_TOP_OFFSET
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_CONTROLS_FIXED
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_CONTROLS_MIN_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.EXPANDED_CONTROLS_TEXT
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
import org.akanework.gramophone.ui.components.player.PlayerUtilities.SIZE_EASING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.arcFraction

class PlayerSheetMetrics(
    val eased: Float,
    val rootWidth: Float,
    val rootHeight: Float,
    val sheetLeft: Float,
    val sheetTop: Float,
    val sheetWidth: Float,
    val sheetHeight: Float,
    val cornerDp: Dp,
    val artSize: Float,
    val artLeftRoot: Float,
    val artTopRoot: Float,
    val artCornerDp: Dp,
    val collapsedArtSize: Float,
    val collapsedArtLeft: Float,
    val collapsedHeight: Float,
    val collapsedFootprint: Float,
    val contentFollowTop: Float,
    val travelPx: Float,
    // Expanded album-cover rect
    val expandedArtSize: Float,
    val expandedArtLeft: Float,
    val expandedArtTop: Float,
    val isWideLandscape: Boolean,
    val statusTop: Float,
    val bottomInset: Float,
    val leftInset: Float,
    val rightInset: Float,
)

@Composable
@Suppress("LongParameterList")
fun playerSheetMetrics(
    progress: Float,
    rootWidth: Float,
    rootHeight: Float,
    statusTop: Float,
    bottomInset: Float,
    leftInset: Float,
    rightInset: Float,
    isWideLandscape: Boolean,
    pageCorner: Dp,
    density: Density,
    expandedArtCorner: Dp = EXPANDED_ART_CORNER,
): PlayerSheetMetrics {
    val clamped = progress.coerceIn(0f, 1f) // Sanitize

    fun Dp.px() = with(density) { toPx() }

    // Floating mini bar
    val platform = maxOf(bottomInset + MINI_PLATFORM_GAP.px(), MINI_PLATFORM_MIN.px())
    val collapsedHeight = MINI_HEIGHT.px()
    val sheetTopCollapsed = rootHeight - platform - collapsedHeight
    val sheetTop = lerp(sheetTopCollapsed, 0f, progress)
    val sheetBottom = rootHeight - lerp(platform, 0f, progress)
    val collapsedLeft = maxOf(MINI_SIDE_INSET.px(), leftInset)
    val collapsedRight = maxOf(MINI_SIDE_INSET.px(), rightInset)
    val sheetLeftPx = lerp(collapsedLeft, 0f, progress)
    val sheetRightPx = lerp(collapsedRight, 0f, progress)

    val collapsedArtSize = MINI_ARTWORK.px()
    // Lined up with the covers of the home's list rows.
    val collapsedArtLeft = leftInset + LIBRARY_COVER_START.px()
    val collapsedArtTop = sheetTopCollapsed + (collapsedHeight - collapsedArtSize) / 2f

    val safeWidth = (rootWidth - leftInset - rightInset).coerceAtLeast(0f)
    val expandedArtTop: Float
    val expandedArtSize: Float
    val expandedArtLeft: Float
    if (isWideLandscape) {
        expandedArtTop = statusTop + LAND_ART_TOP.px()
        expandedArtSize = (rootHeight - statusTop - bottomInset - LAND_ART_TOP.px() - LAND_ART_BOTTOM.px())
            .coerceAtLeast(0f)
        expandedArtLeft = leftInset + LAND_ART_START.px()
    } else {
        expandedArtTop = statusTop + EXPANDED_ART_TOP_OFFSET.px()
        // Whatever height the controls below the cover leave over, so short screens shrink the
        // cover rather than the controls.
        val controlsHeight = EXPANDED_CONTROLS_MIN_GAP.px() + EXPANDED_CONTROLS_FIXED.px() +
            with(density) { EXPANDED_CONTROLS_TEXT.toPx() } + EXPANDED_ACTION_BAR.px()
        expandedArtSize =
            minOf(
                safeWidth - EXPANDED_ART_SIDE_INSET.px() * 2f,
                (rootHeight - expandedArtTop) * EXPANDED_ART_MAX_HEIGHT_FRACTION,
                rootHeight - bottomInset - expandedArtTop - controlsHeight,
            ).coerceAtLeast(0f)
        // Full width it lines up with the top buttons, smaller it is centered.
        expandedArtLeft = leftInset + (safeWidth - expandedArtSize) / 2f
    }

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

    // Round the rising card's corners up to the device screen corner, then square off in the final
    // sliver where the display's own corners take over at full screen.
    val cornerDp =
        when {
            clamped <= CORNER_SQUARE_START -> lerp(MINI_CORNER, pageCorner, clamped / CORNER_SQUARE_START)
            else -> lerp(pageCorner, 0.dp, (clamped - CORNER_SQUARE_START) / (1f - CORNER_SQUARE_START))
        }

    return PlayerSheetMetrics(
        eased = progress,
        rootWidth = rootWidth,
        rootHeight = rootHeight,
        sheetLeft = sheetLeftPx,
        sheetTop = sheetTop,
        sheetWidth = rootWidth - sheetLeftPx - sheetRightPx,
        sheetHeight = sheetBottom - sheetTop,
        cornerDp = cornerDp,
        artSize = artSize,
        artLeftRoot = centerX - artSize / 2f,
        artTopRoot = centerY - artSize / 2f,
        artCornerDp = lerp(MINI_ARTWORK_CORNER, expandedArtCorner, clamped),
        collapsedArtSize = collapsedArtSize,
        collapsedArtLeft = collapsedArtLeft,
        collapsedHeight = collapsedHeight,
        collapsedFootprint = platform + collapsedHeight,
        contentFollowTop = if (isWideLandscape) 0f else centerY - (expandedArtTop + expandedArtSize / 2f),
        travelPx = sheetTopCollapsed.coerceAtLeast(1f),
        expandedArtSize = expandedArtSize,
        expandedArtLeft = expandedArtLeft,
        expandedArtTop = expandedArtTop,
        isWideLandscape = isWideLandscape,
        statusTop = statusTop,
        bottomInset = bottomInset,
        leftInset = leftInset,
        rightInset = rightInset,
    )
}