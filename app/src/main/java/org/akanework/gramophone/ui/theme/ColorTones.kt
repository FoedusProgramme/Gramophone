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

package org.akanework.gramophone.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.harmonize
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct

/*
 * Colours made from others, where the colour schemes' roles don't have the one wanted. They're
 * made in HCT, Material's hue, chroma (how colourful) and tone (how light), and all here.
 */

/** A chroma and tone, a colour once given a hue by [inHue] or [tonal]. */
@Immutable
data class ChromaTone(val chroma: Double, val tone: Double) {
    /** This chroma and tone in [hue], in degrees. */
    fun inHue(hue: Double): Color = Hct.from(hue, chroma, tone).toColor()
}

/** This colour's hue, in degrees. */
val Color.hue: Double
    get() = toHct().hue

/** This colour's tone, from 0, black, to 100, white. */
val Color.tone: Double
    get() = toHct().tone

/** This colour's hue at [chromaTone]. */
fun Color.tonal(chromaTone: ChromaTone): Color = chromaTone.inHue(hue)

/** This colour's hue at [chroma] and [tone]. */
fun Color.tonal(chroma: Double, tone: Double): Color = tonal(ChromaTone(chroma, tone))

/** This colour leant towards [target]'s hue by [fraction]: unchanged at 0, harmonized at 1. */
fun Color.harmonizeBy(target: Color, fraction: Float): Color = when {
    fraction <= 0f -> this
    fraction >= 1f -> harmonize(target)
    else -> lerp(this, harmonize(target), fraction)
}
