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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
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
import org.akanework.gramophone.logic.ApplicationScope
import org.koin.compose.koinInject
import kotlinx.coroutines.async
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Deferred
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

/**
 * [harmony] is how far the colours lean towards [appPrimary], from 0 (the cover's own, on a page
 * already themed from a cover) to 1 (fully harmonized, among the app's own surfaces).
 */
fun nowPlayingColors(cover: ColorScheme, appPrimary: Color, harmony: Float = 1f): NowPlayingColors {
    val isDark = cover.surface.luminance() < 0.5f
    val primary = cover.primary.harmonizeBy(appPrimary, harmony)
    val onFill = cover.onPrimaryContainer.harmonizeBy(appPrimary, harmony)
    return NowPlayingColors(
        bar = if (isDark) primary.tonal(DARK_BAR_CHROMA, DARK_BAR_TONE)
            else cover.surface.harmonizeBy(appPrimary, harmony),
        fill = if (isDark) primary.tonal(DARK_BAR_FILL_CHROMA, DARK_BAR_FILL_TONE)
            else cover.primaryContainer.harmonizeBy(appPrimary, harmony),
        onFill = onFill,
        onFillVariant = onFill.copy(alpha = ON_FILL_VARIANT_ALPHA),
    )
}

/** This colour leant towards [target] by [fraction]: unchanged at 0, harmonized at 1. */
fun Color.harmonizeBy(target: Color, fraction: Float): Color = when {
    fraction <= 0f -> this
    fraction >= 1f -> harmonize(target)
    else -> lerp(this, harmonize(target), fraction)
}

/**
 * Whether cover colours shown here lean towards the theme's hue. Off on a page already themed
 * from a cover, where they are shown as they are.
 */
val LocalHarmonizeCovers = staticCompositionLocalOf { true }

/** Seeds by cover, one cache per accuracy since the two decodes can score differently. */
private val artworkSeedCache = LruCache<Uri, Color>(64)
private val accurateArtworkSeedCache = LruCache<Uri, Color>(64)

/**
 * Seed extractions still running, so the list row, player and queue asking for the same cover at
 * once share one decode. Only touched on the main thread.
 */
private val inFlightSeeds = HashMap<Pair<Uri, Boolean>, Deferred<Color?>>()

/**
 * The cache key for a cover: its URI without the `hd` flag. The seed comes from a tiny decode,
 * so the full and HD artwork give the same colour, and the player's switch from one to the other
 * mid-song must not start a second extraction.
 */
internal fun seedKey(uri: Uri): Uri {
    if (uri.getQueryParameter("hd") == null) return uri
    return uri.buildUpon().clearQuery().apply {
        for (name in uri.queryParameterNames) {
            if (name == "hd") continue
            for (value in uri.getQueryParameters(name)) appendQueryParameter(name, value)
        }
    }.build()
}

@Composable
private fun rememberArtworkSeed(artworkUri: Uri?, accurate: Boolean): Color? {
    val context = LocalPlatformContext.current
    val appScope = koinInject<ApplicationScope>()
    val cache = if (accurate) accurateArtworkSeedCache else artworkSeedCache
    val key = artworkUri?.let(::seedKey)
    var seed by remember { mutableStateOf(key?.let { cache[it] }) }
    LaunchedEffect(key, accurate) {
        if (artworkUri == null || key == null) {
            seed = null
            return@LaunchedEffect
        }
        cache[key]?.let {
            seed = it
            return@LaunchedEffect
        }
        // The decode runs on the app scope, not this effect: a superseded or disposed caller
        // only stops waiting, while the shared result still lands in the cache for the others.
        val flight = key to accurate
        val job = inFlightSeeds.getOrPut(flight) {
            val appContext = context.applicationContext
            appScope.async {
                try {
                    extractArtworkSeed(appContext, artworkUri, accurate)
                        ?.also { cache.put(key, it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) { inFlightSeeds.remove(flight) }
                }
            }
        }
        seed = job.await()
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
