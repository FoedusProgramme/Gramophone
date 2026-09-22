package org.akanework.gramophone.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import org.akanework.gramophone.R

@Composable
fun GramophoneTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    pureDark: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = (if (useDarkTheme) {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                dynamicDarkColorScheme(LocalContext.current)
            else
                legacyColorScheme(dark = true)).let {
                if (pureDark) {
                    it.copy(
                        background = Color.Black,
                        surface = Color.Black,
                        surfaceVariant = Color.Black,
                        surfaceContainerLowest = Color.Black,
                        surfaceContainerLow = Color.Black,
                        surfaceContainer = Color.Black,
                        surfaceContainerHigh = Color.Black,
                        surfaceContainerHighest = Color.Black,
                    )
                } else it
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                dynamicLightColorScheme(LocalContext.current)
            else
                legacyColorScheme(dark = false)
        }), content = {
            CompositionLocalProvider(
                LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surface),
            ) {
                content()
            }
        }
    )
}

/**
 * Pre-Android-12 colors: the same `md_theme_*` palette the XML theme (`PreV31.Theme.Gramophone`)
 * uses, so View and Compose screens match. The resources are day/night qualified, so they
 * resolve to the variant matching the current configuration.
 */
@Composable
private fun legacyColorScheme(dark: Boolean): ColorScheme {
    val primary = colorResource(R.color.md_theme_primary)
    val onPrimary = colorResource(R.color.md_theme_onPrimary)
    val primaryContainer = colorResource(R.color.md_theme_primaryContainer)
    val onPrimaryContainer = colorResource(R.color.md_theme_onPrimaryContainer)
    val inversePrimary = colorResource(R.color.md_theme_inversePrimary)
    val secondary = colorResource(R.color.md_theme_secondary)
    val onSecondary = colorResource(R.color.md_theme_onSecondary)
    val secondaryContainer = colorResource(R.color.md_theme_secondaryContainer)
    val onSecondaryContainer = colorResource(R.color.md_theme_onSecondaryContainer)
    val tertiary = colorResource(R.color.md_theme_tertiary)
    val onTertiary = colorResource(R.color.md_theme_onTertiary)
    val tertiaryContainer = colorResource(R.color.md_theme_tertiaryContainer)
    val onTertiaryContainer = colorResource(R.color.md_theme_onTertiaryContainer)
    val background = colorResource(R.color.md_theme_background)
    val onBackground = colorResource(R.color.md_theme_onBackground)
    val surface = colorResource(R.color.md_theme_surface)
    val onSurface = colorResource(R.color.md_theme_onSurface)
    val surfaceVariant = colorResource(R.color.md_theme_surfaceVariant)
    val onSurfaceVariant = colorResource(R.color.md_theme_onSurfaceVariant)
    val inverseSurface = colorResource(R.color.md_theme_inverseSurface)
    val inverseOnSurface = colorResource(R.color.md_theme_inverseOnSurface)
    val error = colorResource(R.color.md_theme_error)
    val onError = colorResource(R.color.md_theme_onError)
    val errorContainer = colorResource(R.color.md_theme_errorContainer)
    val onErrorContainer = colorResource(R.color.md_theme_onErrorContainer)
    val outline = colorResource(R.color.md_theme_outline)
    val outlineVariant = colorResource(R.color.md_theme_outlineVariant)
    val surfaceBright = colorResource(R.color.md_theme_surfaceBright)
    val surfaceDim = colorResource(R.color.md_theme_surfaceDim)
    val surfaceContainer = colorResource(R.color.md_theme_surfaceContainer)
    val surfaceContainerHigh = colorResource(R.color.md_theme_surfaceContainerHigh)
    val surfaceContainerHighest = colorResource(R.color.md_theme_surfaceContainerHighest)
    val surfaceContainerLow = colorResource(R.color.md_theme_surfaceContainerLow)
    val surfaceContainerLowest = colorResource(R.color.md_theme_surfaceContainerLowest)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary, onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface, inverseOnSurface = inverseOnSurface,
        error = error, onError = onError,
        errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        outline = outline, outlineVariant = outlineVariant,
        surfaceBright = surfaceBright, surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
    )
}
