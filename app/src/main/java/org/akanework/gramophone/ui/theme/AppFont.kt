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

package org.akanework.gramophone.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.TypefaceCompat
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference

/*
 * App typeface: the system's Google Sans Flex when the device has it (Pixels register it as a
 * named family) and the setting is on. Otherwise the platform's sans-serif.
 */

object AppFont {
    const val PREF_KEY = "google_sans_flex"
    const val PREF_DEFAULT = true

    private const val FAMILY = "google-sans-flex"

    /**
     * The system-registered family, or null if missing. An unknown family name resolves to
     * [Typeface.DEFAULT], which is how a missing font is detected.
     */
    private val flexBase: Typeface? by lazy {
        Typeface.create(FAMILY, Typeface.NORMAL).takeIf { it !== Typeface.DEFAULT }
    }

    /** Whether this device has Google Sans Flex. */
    val isAvailable: Boolean get() = flexBase != null

    /** Whether the app uses Google Sans Flex: it is available and the setting is on. */
    fun isEnabled(context: Context): Boolean =
        isAvailable && context.defaultPrefs.getBooleanStrict(PREF_KEY, PREF_DEFAULT)

    /** The app typeface at [weight], for views and paints. */
    fun typeface(context: Context, weight: Int, italic: Boolean = false): Typeface =
        TypefaceCompat.create(context, if (isEnabled(context)) flexBase else null, weight, italic)

    /** The app font family for Compose, or null for the platform default. */
    fun fontFamily(enabled: Boolean): FontFamily? = if (enabled && isAvailable) flexFamily else null

    private val flexFamily: FontFamily by lazy {
        FontFamily(
            (100..900 step 100).flatMap { weight ->
                listOf(FontStyle.Normal, FontStyle.Italic).map { style ->
                    Font(DeviceFontFamilyName(FAMILY), FontWeight(weight), style)
                }
            }
        )
    }
}

/** Whether text in this composition uses Google Sans Flex, as decided by the theme. */
val LocalAppFontEnabled = staticCompositionLocalOf { false }

/** Whether the app font setting is on and the device has the font, as Compose state. */
@Composable
fun rememberAppFontEnabled(): Boolean =
    AppFont.isAvailable && rememberBooleanPreference(AppFont.PREF_KEY, AppFont.PREF_DEFAULT).value

/** Material's default type scale using [family], or unchanged when [family] is null. */
fun appTypography(family: FontFamily?): Typography {
    val base = Typography()
    if (family == null) return base
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}
