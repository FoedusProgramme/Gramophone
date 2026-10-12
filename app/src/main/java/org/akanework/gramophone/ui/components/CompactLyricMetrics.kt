/*
 *     Copyright (C) 2026 SteveZMTstudios
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


package org.akanework.gramophone.ui.components

/**
 * Visual design metrics and scaling factors for compact/non-fullscreen lyrics mode.
 *
 * @author SteveZMTstudios
 */
object CompactLyricMetrics {
    // Scales text size down to fit within the album cover bounds
    const val TEXT_SIZE_SCALE: Float = 0.72f
    // Scales vertical spacing between lyric lines to maintain dense presentation
    const val VERTICAL_SPACING_SCALE: Float = 0.25f
    // Horizontal padding inside the lyric view canvas
    const val HORIZONTAL_PADDING_DP: Float = 16f
    // 3D perspective depth factor for the scrolling viewport
    const val DEPTH_DP: Float = 10f
    // Legacy adapter item decoration scaling to center lyrics vertically in compact viewport
    const val DECORATION_PADDING_SCALE: Float = 0.35f
    // Squashes blank lines down using 10% text size to preserve paragraph rhythm without excessive empty space
    const val BLANK_LINE_TEXT_SCALE: Float = 0.1f
    // Reduces top/bottom line padding for blank lines
    const val BLANK_LINE_PADDING_SCALE: Float = 0.5f
}
