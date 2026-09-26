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

import android.graphics.Matrix
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_ARTWORK
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_ARTWORK_CORNER

/*
 * The cookie cover: a twelve-pointed rounded star the expanded cover is cut to when the setting
 * is on. On the way up it morphs from the mini bar's rounded square, so the shared artwork
 * changes shape as it grows instead of switching at the top.
 */

private const val COOKIE_POINTS = 12
private const val COOKIE_INNER_RADIUS = 0.86f
private const val COOKIE_ROUNDING = 0.09f

/** The mini cover's corner as a fraction of its side, in the unit square the polygons live in. */
private val MINI_ROUNDING = (MINI_ARTWORK_CORNER / MINI_ARTWORK)

private val MINI_SQUARE = RoundedPolygon.rectangle(
    width = 1f, height = 1f,
    rounding = CornerRounding(MINI_ROUNDING),
    centerX = 0.5f, centerY = 0.5f,
)

private val COOKIE = RoundedPolygon.star(
    numVerticesPerRadius = COOKIE_POINTS,
    radius = 0.5f,
    innerRadius = 0.5f * COOKIE_INNER_RADIUS,
    rounding = CornerRounding(COOKIE_ROUNDING),
    centerX = 0.5f, centerY = 0.5f,
)

private val MINI_TO_COOKIE = Morph(MINI_SQUARE, COOKIE)

/*
 * The play button's backdrop: Material's circle while paused, blooming into its twelve-sided
 * cookie while playing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val CIRCLE_TO_FLOWER = Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided)

/** [morph] at [progress], stretched from its unit square to the shape's bounds. */
private class UnitMorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        path.transform(Matrix().apply { setScale(size.width, size.height) })
        return Outline.Generic(path.asComposePath())
    }

    override fun equals(other: Any?): Boolean =
        other is UnitMorphShape && other.morph === morph && other.progress == progress
    override fun hashCode(): Int = 31 * morph.hashCode() + progress.hashCode()
}

/** The cover's shape [progress] of the way from the mini bar's rounded square to the cookie. */
fun CookieMorphShape(progress: Float): Shape = UnitMorphShape(MINI_TO_COOKIE, progress.coerceIn(0f, 1f))

/**
 * The play button's backdrop [progress] of the way from the paused circle to the playing cookie.
 * Left unclamped so a springy [progress] overshoots into the shape instead of stopping dead.
 */
fun PlayButtonMorphShape(progress: Float): Shape = UnitMorphShape(CIRCLE_TO_FLOWER, progress)
