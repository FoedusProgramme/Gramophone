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

package org.akanework.gramophone.ui.components.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/*
 * The app's mark, from gramophone_rounder.svg (220.9 x 185.2 viewport), as an ImageVector so
 * the home bar can tint it like any icon. Its default size is [LOGO_HEIGHT] with the SVG's
 * aspect ratio, which is how the bar shows it.
 */

private const val LOGO_VIEWPORT_WIDTH = 220.90429f
private const val LOGO_VIEWPORT_HEIGHT = 185.20018f

/** The height the bar shows the mark at, centred on its action buttons. */
val LOGO_HEIGHT = 32.dp

val GramophoneLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "GramophoneLogo",
        defaultWidth = LOGO_HEIGHT * (LOGO_VIEWPORT_WIDTH / LOGO_VIEWPORT_HEIGHT),
        defaultHeight = LOGO_HEIGHT,
        viewportWidth = LOGO_VIEWPORT_WIDTH,
        viewportHeight = LOGO_VIEWPORT_HEIGHT,
    ).apply {
        group(translationX = -10.0417f, translationY = -27.893825f) {
            addPath(
                fill = SolidColor(Color.Black),
                pathData = addPathNodes(
                    "m 113.659,90.1114 c 9.941,0 18,-4.3778 18,-9.7781 0,-5.4002 -8.059,-9.778 " +
                        "-18,-9.778 -9.941,0 -17.9996,4.3778 -17.9996,9.778 0,5.4003 8.0586,9.7781 " +
                        "17.9996,9.7781 z"
                ),
            )
            addPath(
                fill = SolidColor(Color.Black),
                pathData = addPathNodes(
                    "M 220.49,79.643 130.127,30.3509 c -5.999,-3.2761 -13.242,-3.2761 -19.242,0 " +
                        "L 20.5101,79.643 C 14.0583,83.1701 10.0417,89.9357 10.0417,97.2786 " +
                        "v 46.4304 c 0,7.355 4.0166,14.121 10.4684,17.635 l 90.3629,49.293 " +
                        "c 5.999,3.276 13.242,3.276 19.242,0 l 90.362,-49.293 " +
                        "c 6.452,-3.527 10.469,-10.292 10.469,-17.635 V 97.2786 " +
                        "c 0,-7.3555 -4.017,-14.121 -10.469,-17.6356 z " +
                        "m -106.831,28.958 c -28.7316,0 -52.0283,-12.6529 -52.0283,-28.2677 " +
                        "0,-15.6148 23.2967,-28.2673 52.0283,-28.2673 28.732,0 52.028,12.6525 " +
                        "52.028,28.2673 0,15.6148 -23.296,28.2677 -52.028,28.2677 z " +
                        "M 198.222,96.5757 143.37,126.5 c -0.741,0.402 -1.569,0.615 -2.41,0.615 " +
                        "h -13.795 c -2.774,0 -5.021,-2.247 -5.021,-5.021 0,-2.774 2.247,-5.021 " +
                        "5.021,-5.021 h 12.515 l 46.066,-25.129 c -1.243,-1.5439 -1.507,-3.7405 " +
                        "-0.49,-5.5731 1.331,-2.4351 4.393,-3.3138 6.816,-1.9832 l 6.163,3.389 " +
                        "c 1.607,0.8787 2.611,2.5732 2.598,4.4058 -0.012,1.8326 -1.004,3.5271 " +
                        "-2.611,4.4058 z"
                ),
            )
        }
    }.build()
}
