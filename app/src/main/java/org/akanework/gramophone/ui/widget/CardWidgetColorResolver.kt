/*
 *     Copyright (C) 2026 SteveZMTstudios
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

package org.akanework.gramophone.ui.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import org.akanework.gramophone.R

enum class WidgetTheme(val key: String, val isAlbumArt: Boolean, val isNight: Boolean?) {
    WALLPAPER_FOLLOW_SYSTEM("wallpaper_follow_system", isAlbumArt = false, isNight = null),
    WALLPAPER_DARK("wallpaper_dark", isAlbumArt = false, isNight = true),
    WALLPAPER_LIGHT("wallpaper_light", isAlbumArt = false, isNight = false),
    ALBUM_ART_FOLLOW_SYSTEM("album_art_follow_system", isAlbumArt = true, isNight = null),
    ALBUM_ART_DARK("album_art_dark", isAlbumArt = true, isNight = true),
    ALBUM_ART_LIGHT("album_art_light", isAlbumArt = true, isNight = false);

    companion object {
        fun fromKey(key: String?): WidgetTheme {
            val found = entries.firstOrNull { it.key == key }
            if (found == null && key != null) {
                android.util.Log.w("CardWidgetColorResolver", "Unknown widget theme key: $key, falling back to WALLPAPER_FOLLOW_SYSTEM")
            }
            return found ?: WALLPAPER_FOLLOW_SYSTEM
        }
    }
}

data class CardWidgetColors(
    val background: Int,
    val primary: Int,
    val onPrimary: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int
)

object CardWidgetColorResolver {

    const val PREF_WIDGET_THEME = "widget_theme"

    private data class CacheKey(
        val theme: WidgetTheme,
        val isNight: Boolean,
        val bitmapIdentity: Int?
    )

    private val colorCache = LruCache<CacheKey, CardWidgetColors>(16)

    @VisibleForTesting
    fun clearCache() {
        colorCache.evictAll()
    }

    fun resolve(context: Context, artworkBitmap: Bitmap?): CardWidgetColors {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val themeKey = prefs.getString(PREF_WIDGET_THEME, WidgetTheme.WALLPAPER_FOLLOW_SYSTEM.key)
        val theme = WidgetTheme.fromKey(themeKey)

        val systemNight = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isNight = theme.isNight ?: systemNight

        // Use identity hash for bitmap caching if present and not recycled
        val bitmapKey = if (theme.isAlbumArt && artworkBitmap != null && !artworkBitmap.isRecycled) {
            System.identityHashCode(artworkBitmap)
        } else {
            null
        }

        val cacheKey = CacheKey(theme, isNight, bitmapKey)
        colorCache.get(cacheKey)?.let { return it }

        val resolvedColors = resolveColorsInternal(context, theme, isNight, artworkBitmap)
        colorCache.put(cacheKey, resolvedColors)
        return resolvedColors
    }

    private fun resolveColorsInternal(
        context: Context,
        theme: WidgetTheme,
        isNight: Boolean,
        artworkBitmap: Bitmap?
    ): CardWidgetColors {
        val newConfig = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    (if (isNight) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        val configContext = context.createConfigurationContext(newConfig)
        val themedContext = ContextThemeWrapper(configContext, R.style.Theme_Gramophone)

        val wrappedContext = try {
            if (theme.isAlbumArt && artworkBitmap != null && !artworkBitmap.isRecycled) {
                val options = DynamicColorsOptions.Builder()
                    .setContentBasedSource(artworkBitmap)
                    .build()
                DynamicColors.wrapContextIfAvailable(themedContext, options)
            } else {
                DynamicColors.wrapContextIfAvailable(themedContext)
            }
        } catch (_: Throwable) {
            themedContext
        }

        val fallbackBg = ContextCompat.getColor(wrappedContext, R.color.widget_background)
        val fallbackPrimary = ContextCompat.getColor(wrappedContext, R.color.widget_primary)
        val fallbackOnPrimary = ContextCompat.getColor(wrappedContext, R.color.widget_on_primary)
        val fallbackOnSurface = ContextCompat.getColor(wrappedContext, R.color.widget_on_surface)
        val fallbackOnSurfaceVariant = ContextCompat.getColor(wrappedContext, R.color.widget_on_surface_variant)

        val background = MaterialColors.getColor(
            wrappedContext,
            com.google.android.material.R.attr.colorSurfaceContainer,
            MaterialColors.getColor(
                wrappedContext,
                com.google.android.material.R.attr.colorSurface,
                fallbackBg
            )
        )
        val primary = MaterialColors.getColor(
            wrappedContext,
            androidx.appcompat.R.attr.colorPrimary,
            fallbackPrimary
        )
        val onPrimary = MaterialColors.getColor(
            wrappedContext,
            com.google.android.material.R.attr.colorOnPrimary,
            fallbackOnPrimary
        )
        val onSurface = MaterialColors.getColor(
            wrappedContext,
            com.google.android.material.R.attr.colorOnSurface,
            fallbackOnSurface
        )
        val onSurfaceVariant = MaterialColors.getColor(
            wrappedContext,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            fallbackOnSurfaceVariant
        )

        return CardWidgetColors(
            background = background,
            primary = primary,
            onPrimary = onPrimary,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant
        )
    }
}
