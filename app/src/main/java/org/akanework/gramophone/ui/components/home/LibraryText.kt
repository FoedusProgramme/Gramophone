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

import org.akanework.gramophone.ui.theme.LocalAppFontEnabled
import org.akanework.gramophone.ui.theme.AppFont
import android.content.Context
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.graphics.TypefaceCompat

/** Cached font families by weight, for the platform font and the app font. */
private val viewTypefaces = HashMap<Int, FontFamily>()
private val appFontTypefaces = HashMap<Int, FontFamily>()

/**
 * `sans-serif` (or the app font) at the given weight. Uses [TypefaceCompat] so the weighted
 * constructor is only used where the platform supports it, same as the lyrics.
 */
private fun viewTypeface(context: Context, weight: Int, appFont: Boolean): FontFamily =
    (if (appFont) appFontTypefaces else viewTypefaces).getOrPut(weight) {
        // One map per font, so cached entries stay valid when the app font setting changes.
        FontFamily(Typeface(if (appFont) AppFont.typeface(context, weight)
            else TypefaceCompat.create(context, null, weight, false)))
    }

/**
 * A TextView look-alike: `sans-serif`, no letter spacing, natural line height and
 * `includeFontPadding=true`, so text metrics match the XML layouts. The size is rounded to
 * whole pixels the way `TypedArray.getDimensionPixelSize` rounds an `android:textSize`.
 */
@Composable
fun textViewStyle(size: TextUnit, weight: Int, color: Color): TextStyle {
    val density = LocalDensity.current
    val context = LocalContext.current
    val roundedSize = with(density) {
        val px = size.toPx()
        (px + 0.5f).toInt().coerceAtLeast(1).toSp()
    }
    return TextStyle(
        color = color,
        fontSize = roundedSize,
        fontWeight = FontWeight(weight),
        fontFamily = viewTypeface(context, weight, LocalAppFontEnabled.current),
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
