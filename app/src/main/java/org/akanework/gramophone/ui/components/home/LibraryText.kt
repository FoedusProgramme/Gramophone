/*
 *     Copyright (C) 2024 Akane Foundation
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

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val viewTypefaces = HashMap<Int, FontFamily>()

/** `sans-serif` at the given weight, created the way `ViewCompatInflater` does for text views. */
fun viewTypeface(weight: Int): FontFamily = viewTypefaces.getOrPut(weight) {
    val base = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    FontFamily(Typeface(android.graphics.Typeface.create(base, weight, false)))
}

/**
 * A TextView look-alike: `sans-serif`, no letter spacing, natural line height and
 * `includeFontPadding=true`, so text metrics match the XML layouts. The size is rounded to
 * whole pixels the way `TypedArray.getDimensionPixelSize` rounds an `android:textSize`.
 */
@Composable
fun textViewStyle(size: TextUnit, weight: Int, color: Color): TextStyle {
    val density = LocalDensity.current
    val roundedSize = with(density) {
        val px = size.toPx()
        (px + 0.5f).toInt().coerceAtLeast(1).toSp()
    }
    return TextStyle(
        color = color,
        fontSize = roundedSize,
        fontWeight = FontWeight(weight),
        fontFamily = viewTypeface(weight),
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
    )
}

/** `android:singleLine="true"` TextView: one line, never wrapped, ellipsized at the end. */
@Composable
fun SingleLineText(
    text: String,
    size: TextUnit,
    weight: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = textViewStyle(size, weight, color),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
