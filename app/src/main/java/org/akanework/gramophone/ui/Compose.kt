package org.akanework.gramophone.ui

import org.akanework.gramophone.ui.theme.rememberAppFontEnabled
import org.akanework.gramophone.ui.theme.appTypography
import org.akanework.gramophone.ui.theme.LocalAppFontEnabled
import org.akanework.gramophone.ui.theme.AppFont
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.materialkolor.hct.Hct
import com.materialkolor.ktx.animateColorScheme
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import org.akanework.gramophone.logic.enableEdgeToEdgeProperly
import org.akanework.gramophone.ui.theme.isDark
import org.akanework.gramophone.ui.theme.rememberThemeSettings
import org.akanework.gramophone.ui.theme.themeColorScheme

val LocalCardSurface = staticCompositionLocalOf { Color.Unspecified }

/** Whether the app draws its dark theme, whatever the system is set to. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** How long a change of palette, seed or brightness takes to cross over. */
const val THEME_ANIMATION_MS = 400

private const val DARK_CARD_CHROMA = 8.0
private const val DARK_CARD_TONE = 10.0
/** How far a dark card sits above the page, which differs between colour spec versions. */
private const val DARK_CARD_TONE_LIFT = 4.0

internal fun cardSurface(scheme: ColorScheme, dark: Boolean): Color =
    if (dark) {
        val pageTone = scheme.surfaceContainerLow.toHct().tone
        scheme.primary.tonal(DARK_CARD_CHROMA, maxOf(DARK_CARD_TONE, pageTone + DARK_CARD_TONE_LIFT))
    } else scheme.surfaceBright

/** This colour's hue at the given [chroma] and [tone]. */
fun Color.tonal(chroma: Double, tone: Double): Color = Hct.from(toHct().hue, chroma, tone).toColor()

/**
 * The app theme from the stored theme settings. Colours cross over when the settings change,
 * and the system bars are kept matched to the brightness.
 */
@Composable
fun GramophoneTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = rememberThemeSettings()
    val dark = settings.mode.isDark(isSystemInDarkTheme())
    val target = remember(context, settings, dark) { themeColorScheme(context, settings, dark) }
    val colorScheme = animateColorScheme(target, animationSpec = { tween(THEME_ANIMATION_MS) })
    val cardSurface by animateColorAsState(cardSurface(target, dark), tween(THEME_ANIMATION_MS))
    val appFont = rememberAppFontEnabled()
    val typography = remember(appFont) { appTypography(AppFont.fontFamily(appFont)) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect { view.context.findActivity()?.enableEdgeToEdgeProperly(dark) }
    }
    MaterialTheme(colorScheme = colorScheme, typography = typography) {
        CompositionLocalProvider(
            LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.surface),
            LocalCardSurface provides cardSurface,
            LocalDarkTheme provides dark,
            LocalAppFontEnabled provides appFont,
        ) {
            content()
        }
    }
}

tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
