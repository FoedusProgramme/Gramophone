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
package org.akanework.gramophone.ui.screens.settings

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.LocalDarkTheme
import org.akanework.gramophone.ui.THEME_ANIMATION_MS
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.compose.rememberStringPreference
import org.akanework.gramophone.ui.components.settings.DropdownPreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceLabels
import org.akanework.gramophone.ui.components.settings.PreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.PreferenceSectionHeader
import org.akanework.gramophone.ui.components.settings.SwitchPreferenceRow
import org.akanework.gramophone.ui.findActivity
import org.akanework.gramophone.ui.theme.DEFAULT_SEED_COLOR
import org.akanework.gramophone.ui.theme.PREF_PALETTE_STYLE
import org.akanework.gramophone.ui.theme.PREF_PURE_DARK
import org.akanework.gramophone.ui.theme.PREF_SEED_COLOR
import org.akanework.gramophone.ui.theme.PREF_THEME_MODE
import org.akanework.gramophone.ui.theme.PREF_WALLPAPER_COLOR
import org.akanework.gramophone.ui.theme.PRESET_SEED_COLORS
import org.akanework.gramophone.ui.theme.ThemeMode
import org.akanework.gramophone.ui.theme.activeSeedColor
import org.akanework.gramophone.ui.theme.applyToSystem
import org.akanework.gramophone.ui.theme.rememberThemeSettings
import org.akanework.gramophone.ui.theme.supportsWallpaperColor
import org.akanework.gramophone.ui.theme.themeModeOf

private val PAGE_MARGIN = 16.dp
private val SWATCH_SIZE = 40.dp
private val SWATCH_GAP = 12.dp
private val SWATCH_CHECK_SIZE = 20.dp
private val SWATCHES_TOP_GAP = 12.dp
private val HUE_TRACK_HEIGHT = 16.dp
private const val SEED_CHROMA = 48.0
private const val SEED_TONE = 40.0
private const val HUE_STOPS = 12
private const val HUE_MAX = 360f
private val STYLE_CARD_WIDTH = 84.dp
private val STYLE_BAND_HEIGHT = 34.dp
private val STYLE_BAND_GAP = 2.dp
private val STYLE_CARD_CORNER = 16.dp
private val STYLE_CARD_BORDER = 3.dp
private val STYLE_CARD_GAP = 12.dp
private val STYLE_LABEL_GAP = 8.dp
private val STYLE_ROW_BOTTOM_GAP = 24.dp

@Composable
fun ThemeSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val themeMode = rememberStringPreference(PREF_THEME_MODE, ThemeMode.System.code)
    val pureDark = rememberBooleanPreference(PREF_PURE_DARK, false)
    val wallpaperColor = rememberBooleanPreference(PREF_WALLPAPER_COLOR, true)
    val seedColor = rememberIntPreference(PREF_SEED_COLOR, DEFAULT_SEED_COLOR.toArgb())
    val paletteStyle = rememberStringPreference(PREF_PALETTE_STYLE, PaletteStyle.TonalSpot.name)
    val settings = rememberThemeSettings()
    val dark = LocalDarkTheme.current

    PreferenceScreen(title = stringResource(R.string.settings_theme_title), onBack = onBack, modifier = modifier) {
        PreferenceSectionHeader(stringResource(R.string.settings_theme_mode_section))
        PreferenceGroup(
            { shape ->
                DropdownPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_app_theme),
                    entries = stringArrayResource(R.array.theme_switch).toList(),
                    values = stringArrayResource(R.array.theme_switch_val).toList(),
                    value = themeMode.value,
                    onValueChange = { code ->
                        themeMode.set(code)
                        themeModeOf(code).applyToSystem(context)
                        // Before Android 12 the activity carries the night override itself.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) context.findActivity()?.recreate()
                    },
                )
            },
            { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_pure_dark),
                    subtitle = stringResource(R.string.settings_pure_dark_summary),
                    checked = pureDark.value,
                    onCheckedChange = { pureDark.set(it) },
                )
            },
        )

        PreferenceSectionHeader(stringResource(R.string.settings_theme_color_section))
        val colorRows = mutableListOf<@Composable (Shape) -> Unit>()
        if (supportsWallpaperColor) colorRows += { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_theme_wallpaper_color),
                subtitle = stringResource(R.string.settings_theme_wallpaper_color_summary),
                checked = settings.wallpaperColor,
                onCheckedChange = { wallpaperColor.set(it) },
            )
        }
        colorRows += { shape ->
            SeedColorRow(
                shape,
                enabled = !settings.wallpaperColor,
                selected = settings.seedColor,
                onSelect = { seedColor.set(it.toArgb()) },
            )
        }
        PreferenceGroup(colorRows)

        PreferenceSectionHeader(stringResource(R.string.settings_theme_style_section))
        PaletteStyleRow(
            seed = settings.activeSeedColor(context),
            dark = dark,
            selected = settings.style,
            onSelect = { paletteStyle.set(it.name) },
        )
        Spacer(Modifier.height(STYLE_ROW_BOTTOM_GAP))
    }
}

/** Preset seeds in a row, then a hue slider for any other seed. */
@Composable
private fun SeedColorRow(shape: Shape, enabled: Boolean, selected: Color, onSelect: (Color) -> Unit) {
    PreferenceRow(shape, enabled = enabled) {
        Column(Modifier.weight(1f)) {
            PreferenceLabels(
                stringResource(R.string.settings_theme_custom_color),
                subtitle = stringResource(R.string.settings_theme_custom_color_summary),
            )
            Spacer(Modifier.height(SWATCHES_TOP_GAP))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP),
            ) {
                PRESET_SEED_COLORS.forEach { color ->
                    ColorSwatch(color, selected = color == selected, enabled = enabled) { onSelect(color) }
                }
            }
            var hue by remember(selected) { mutableFloatStateOf(selected.toHct().hue.toFloat()) }
            HueSlider(
                hue = hue,
                enabled = enabled,
                onHueChange = { hue = it },
                onHueChangeFinished = { onSelect(Hct.from(hue.toDouble(), SEED_CHROMA, SEED_TONE).toColor()) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(color)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(SWATCH_CHECK_SIZE),
            )
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    enabled: Boolean,
    onHueChange: (Float) -> Unit,
    onHueChangeFinished: () -> Unit,
) {
    val brush = remember {
        Brush.horizontalGradient(
            (0..HUE_STOPS).map { Hct.from(it * HUE_MAX.toDouble() / HUE_STOPS, SEED_CHROMA, SEED_TONE).toColor() }
        )
    }
    Slider(
        value = hue,
        onValueChange = onHueChange,
        onValueChangeFinished = onHueChangeFinished,
        valueRange = 0f..HUE_MAX,
        enabled = enabled,
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HUE_TRACK_HEIGHT)
                    .clip(CircleShape)
                    .background(brush)
            )
        },
    )
}

/** One card per palette style, each showing the palette it builds from [seed]. */
@Composable
private fun PaletteStyleRow(seed: Color, dark: Boolean, selected: PaletteStyle, onSelect: (PaletteStyle) -> Unit) {
    val schemes = remember(seed, dark) {
        PaletteStyle.entries.associateWith { dynamicColorScheme(seedColor = seed, isDark = dark, style = it) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PAGE_MARGIN),
        horizontalArrangement = Arrangement.spacedBy(STYLE_CARD_GAP),
    ) {
        PaletteStyle.entries.forEach { style ->
            PaletteStyleCard(style, schemes.getValue(style), selected = style == selected) { onSelect(style) }
        }
    }
}

@Composable
private fun PaletteStyleCard(style: PaletteStyle, scheme: ColorScheme, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(STYLE_CARD_CORNER)
    val border by animateColorAsState(if (selected) accent else Color.Transparent, tween(THEME_ANIMATION_MS))
    val label by animateColorAsState(
        if (selected) accent else MaterialTheme.colorScheme.onSurface, tween(THEME_ANIMATION_MS),
    )
    Column(
        Modifier
            .width(STYLE_CARD_WIDTH)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.border(STYLE_CARD_BORDER, border, shape).clip(shape),
            verticalArrangement = Arrangement.spacedBy(STYLE_BAND_GAP),
        ) {
            listOf(
                scheme.primary, scheme.secondary, scheme.tertiary,
                scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer,
            ).forEach { band ->
                val color by animateColorAsState(band, tween(THEME_ANIMATION_MS))
                Box(Modifier.fillMaxWidth().height(STYLE_BAND_HEIGHT).background(color))
            }
        }
        Spacer(Modifier.height(STYLE_LABEL_GAP))
        Text(
            style.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = label,
        )
    }
}
