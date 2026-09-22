/*
 *     Copyright (C) 2025 nift4
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

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getIntStrict
import org.akanework.gramophone.logic.getStringStrict
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.compose.rememberStringPreference

const val PREF_THEME_MODE = "theme_mode"
const val PREF_PURE_DARK = "pureDark"
const val PREF_WALLPAPER_COLOR = "theme_wallpaper_color"
const val PREF_SEED_COLOR = "theme_seed_color"
const val PREF_PALETTE_STYLE = "theme_palette_style"

/** The seed of the default palette, the blue the app shipped with before Material You. */
val DEFAULT_SEED_COLOR = Color(0xFF38608F)

/** Seeds offered as one-tap choices in the theme settings. */
val PRESET_SEED_COLORS = listOf(
    Color(0xFF38608F), Color(0xFF6750A4), Color(0xFF00639B), Color(0xFF006A6A),
    Color(0xFF2E6C30), Color(0xFF5B6300), Color(0xFF7A5900), Color(0xFF9A4A00),
    Color(0xFFB3261E), Color(0xFF9C2B60), Color(0xFF7B4E7F), Color(0xFF5D5F6A),
)

/** Whether this device exposes wallpaper based colours. */
val supportsWallpaperColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Light or dark, as stored under [PREF_THEME_MODE]. */
enum class ThemeMode(val code: String) { System("0"), Dark("1"), Light("2") }

fun themeModeOf(code: String?): ThemeMode = ThemeMode.entries.firstOrNull { it.code == code } ?: ThemeMode.System

fun SharedPreferences.themeMode(): ThemeMode = themeModeOf(getStringStrict(PREF_THEME_MODE, ThemeMode.System.code))

fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
}

/**
 * Hands the choice to the system so resources loaded outside Compose, splash screen included,
 * carry the same night qualifier. Below Android 12 each activity overrides its own configuration
 * instead, see [overrideConfiguration].
 */
fun ThemeMode.applyToSystem(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    context.getSystemService(UiModeManager::class.java).setApplicationNightMode(
        when (this) {
            ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
        }
    )
}

/** The configuration override an activity below Android 12 needs for this mode, if any. */
fun ThemeMode.overrideConfiguration(): Configuration? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || this == ThemeMode.System) return null
    return Configuration().apply {
        fontScale = 0f
        uiMode = if (this@overrideConfiguration == ThemeMode.Dark) Configuration.UI_MODE_NIGHT_YES
        else Configuration.UI_MODE_NIGHT_NO
    }
}

fun paletteStyleOf(name: String?): PaletteStyle =
    PaletteStyle.entries.firstOrNull { it.name == name } ?: PaletteStyle.TonalSpot

/** Everything the user can set about the theme. */
@Immutable
data class ThemeSettings(
    val mode: ThemeMode,
    val pureDark: Boolean,
    val wallpaperColor: Boolean,
    val seedColor: Color,
    val style: PaletteStyle,
)

fun SharedPreferences.readThemeSettings(): ThemeSettings = ThemeSettings(
    mode = themeMode(),
    pureDark = getBooleanStrict(PREF_PURE_DARK, false),
    wallpaperColor = supportsWallpaperColor && getBooleanStrict(PREF_WALLPAPER_COLOR, true),
    seedColor = Color(getIntStrict(PREF_SEED_COLOR, DEFAULT_SEED_COLOR.toArgb())),
    style = paletteStyleOf(getStringStrict(PREF_PALETTE_STYLE, null)),
)

/** The stored theme settings as state, following every change. */
@Composable
fun rememberThemeSettings(): ThemeSettings {
    val mode = rememberStringPreference(PREF_THEME_MODE, ThemeMode.System.code).value
    val pureDark = rememberBooleanPreference(PREF_PURE_DARK, false).value
    val wallpaperColor = rememberBooleanPreference(PREF_WALLPAPER_COLOR, true).value
    val seedColor = rememberIntPreference(PREF_SEED_COLOR, DEFAULT_SEED_COLOR.toArgb()).value
    val style = rememberStringPreference(PREF_PALETTE_STYLE, PaletteStyle.TonalSpot.name).value
    return remember(mode, pureDark, wallpaperColor, seedColor, style) {
        ThemeSettings(
            mode = themeModeOf(mode),
            pureDark = pureDark,
            wallpaperColor = supportsWallpaperColor && wallpaperColor,
            seedColor = Color(seedColor),
            style = paletteStyleOf(style),
        )
    }
}

/** The wallpaper accent, as a seed for the palettes the system does not build itself. */
@RequiresApi(Build.VERSION_CODES.S)
fun wallpaperSeedColor(context: Context): Color =
    Color(context.getColor(android.R.color.system_accent1_500))

/** The seed the palette is built from under these settings. */
fun ThemeSettings.activeSeedColor(context: Context): Color =
    if (wallpaperColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) wallpaperSeedColor(context)
    else seedColor

/** The colour scheme for these settings in the given brightness. */
fun themeColorScheme(context: Context, settings: ThemeSettings, dark: Boolean): ColorScheme {
    val systemPalette = settings.wallpaperColor && settings.style == PaletteStyle.TonalSpot &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = if (systemPalette) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        dynamicColorScheme(
            seedColor = settings.activeSeedColor(context),
            isDark = dark,
            style = settings.style,
        )
    }
    return if (dark && settings.pureDark) scheme.pureDark() else scheme
}

private fun ColorScheme.pureDark(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
)
