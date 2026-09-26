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

package org.akanework.gramophone.ui.components.player

import android.net.Uri
import androidx.collection.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.harmonize
import com.materialkolor.ktx.quantize
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ARTWORK_QUANTIZE_MAX
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ARTWORK_SEED_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ARTWORK_SEED_SIZE_ACCURATE
import org.akanework.gramophone.ui.tonal

/**
 * The colour scheme seeded from a song's cover, as the player and the lists paint it. Falls back
 * to the app theme when content based colour is off or the cover has no usable colour.
 */
@Composable
fun rememberArtworkColorScheme(artworkUri: Uri?): ColorScheme {
    val contentBasedColor = rememberBooleanPreference("content_based_color", true).value
    val colorAccuracy = rememberBooleanPreference("color_accuracy", false).value
    return rememberArtworkColorScheme(if (contentBasedColor) artworkUri else null, colorAccuracy)
}

@Composable
private fun rememberArtworkColorScheme(artworkUri: Uri?, accurate: Boolean): ColorScheme {
    val theme = MaterialTheme.colorScheme
    val seed = rememberArtworkSeed(artworkUri, accurate)
    // Match the applied theme's light/dark rather than the raw system setting.
    val isDark = theme.surface.luminance() < 0.5f
    return if (seed == null) theme
    else rememberDynamicColorScheme(seedColor = seed, isDark = isDark, style = PaletteStyle.TonalSpot)
}

/**
 * The colours the playing song stands out in, on the collapsed player and on its list row: the
 * cover's, leant towards the app's hue since they sit among the app's own surfaces.
 */
@Immutable
data class NowPlayingColors(
    /** The collapsed bar behind its progress. */
    val bar: Color,
    /** The progress on the collapsed bar, and the playing row's card. */
    val fill: Color,
    /** Text and icons on [fill]. */
    val onFill: Color,
    /** Secondary text on [fill]. */
    val onFillVariant: Color,
)

/** By night the cover scheme's surface is nearly black and its primary container dull, so the
 *  bar and its progress take the cover's hue at tones of their own. */
private const val DARK_BAR_CHROMA = 14.0
private const val DARK_BAR_TONE = 12.0
private const val DARK_BAR_FILL_CHROMA = 32.0
private const val DARK_BAR_FILL_TONE = 35.0
private const val ON_FILL_VARIANT_ALPHA = 0.8f

fun nowPlayingColors(cover: ColorScheme, appPrimary: Color): NowPlayingColors {
    val isDark = cover.surface.luminance() < 0.5f
    val primary = cover.primary.harmonize(appPrimary)
    val onFill = cover.onPrimaryContainer.harmonize(appPrimary)
    return NowPlayingColors(
        bar = if (isDark) primary.tonal(DARK_BAR_CHROMA, DARK_BAR_TONE)
            else cover.surface.harmonize(appPrimary),
        fill = if (isDark) primary.tonal(DARK_BAR_FILL_CHROMA, DARK_BAR_FILL_TONE)
            else cover.primaryContainer.harmonize(appPrimary),
        onFill = onFill,
        onFillVariant = onFill.copy(alpha = ON_FILL_VARIANT_ALPHA),
    )
}

/** Seeds by cover, one cache per accuracy since the two decodes can score differently. */
private val artworkSeedCache = LruCache<Uri, Color>(64)
private val accurateArtworkSeedCache = LruCache<Uri, Color>(64)

@Composable
private fun rememberArtworkSeed(artworkUri: Uri?, accurate: Boolean): Color? {
    val context = LocalPlatformContext.current
    val cache = if (accurate) accurateArtworkSeedCache else artworkSeedCache
    var seed by remember { mutableStateOf(artworkUri?.let { cache[it] }) }
    LaunchedEffect(artworkUri, accurate) {
        if (artworkUri == null) {
            seed = null
            return@LaunchedEffect
        }
        cache[artworkUri]?.let {
            seed = it
            return@LaunchedEffect
        }
        // Rethrow cancellation instead of treating it as a failed decode: a superseded effect
        // (the cover URI changed mid-decode) must not overwrite the newer effect's seed.
        seed = try {
            extractArtworkSeed(context, artworkUri, accurate)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }?.also { cache.put(artworkUri, it) }
    }
    return seed
}

private suspend fun extractArtworkSeed(
    context: PlatformContext,
    uri: Uri,
    accurate: Boolean,
): Color? = withContext(Dispatchers.Default) {
    // A tiny decode is plenty for a stable dominant colour and keeps quantisation near-free.
    val size = if (accurate) ARTWORK_SEED_SIZE_ACCURATE else ARTWORK_SEED_SIZE
    val request = ImageRequest.Builder(context).data(uri).size(size).allowHardware(false).build()
    val image = (context.imageLoader.execute(request) as? SuccessResult)?.image
    val bitmap = (image as? BitmapImage)?.bitmap?.asImageBitmap() ?: return@withContext null
    val population = QuantizerCelebi.quantize(bitmap, ARTWORK_QUANTIZE_MAX)
    val scored = Score.score(population, desired = 1, fallbackColorArgb = null, filter = true)
        .ifEmpty { Score.score(population, desired = 1, fallbackColorArgb = null, filter = false) }
    scored.firstOrNull()?.let { Color(it) }
}
