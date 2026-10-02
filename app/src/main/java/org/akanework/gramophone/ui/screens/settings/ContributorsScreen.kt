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

package org.akanework.gramophone.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.data.Contributors
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.GlassTitleBar
import kotlin.random.Random

private const val CONTRIBUTORS_URL =
    "https://github.com/FoedusProgramme/Gramophone/graphs/contributors"
private const val WEBLATE_URL = "https://hosted.weblate.org/engage/gramophone/"

/** Margin between the logo and the safe area's sides. */
private val MARK_MARGIN = 24.dp

/** Room under the names, above the navigation bar. */
private val MARK_BOTTOM_GAP = 24.dp

/** Seed of the shuffle the names are shown in, so the logo always looks the same. */
private const val NAME_SHUFFLE_SEED = 1712

/** Fade-in of names that had to be planned first. */
private const val NAME_FADE_IN_MS = 200

/**
 * Shows all developers and translators as one block of names laid out inside the app logo. All
 * names use the same size and are never cut off by the shape.
 *
 * Unlike the other settings pages this is not a scrolling [PreferenceScreen][org.akanework
 * .gramophone.ui.components.settings.PreferenceScreen]: the names fill the whole window, edge to
 * edge, so pinching and panning never scroll the page by accident. Only the shared glass bar,
 * with its back button and small title, floats over them.
 *
 * The names are laid out by [planMark] and zoomed by [MarkZoom].
 */
@Composable
fun ContributorsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    // Developers and translators in one list. Screen readers read it in this order, the logo
    // shows it shuffled with a fixed seed.
    val credits = remember {
        buildList {
            Contributors.LIST.forEach { add(it.name ?: it.login) }
            Contributors.TRANSLATORS.filterNotNull().forEach { add(it) }
        }
    }
    val names = remember(credits) { credits.shuffled(Random(NAME_SHUFFLE_SEED)) }
    val description = remember(credits) { credits.joinToString() }
    val hazeState = remember { HazeState() }
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    Box(modifier.fillMaxSize()) {
        // The background is painted inside the blur source, so the recorded layer is opaque:
        // a transparent one would let the sharp names show through the frosted bar.
        NameMark(
            names, description,
            Modifier.fillMaxSize().hazeSource(hazeState).background(background),
        )
        // Nothing scrolls under the bar, so it keeps its small title all the time, as on the
        // licenses page. Its frost stays on for the names a zoom moves under it.
        GlassTitleBar(
            hazeState = hazeState,
            title = stringResource(R.string.settings_contributors),
            scrolled = { Float.MAX_VALUE },
            onBack = onBack,
        )
    }
}

/**
 * The names laid out in the app logo. The gestures and the zoom cover the whole window, while at
 * no zoom the logo is fitted inside the safe area, clear of the bars and the back button.
 * Tapping opens the contributors page on GitHub, pinching zooms into the small names and a
 * double tap toggles the zoom. Screen readers read [description] instead of the drawn names, and
 * get an extra action for the translators' page on Weblate.
 *
 * Planning the layout rasterizes the logo and measures every name many times, which takes far
 * longer than a frame, so it runs on a background thread and the names fade in once it is done.
 * The last plans are cached, so coming back to the page shows them at once.
 */
@Composable
private fun NameMark(names: List<String>, description: String, modifier: Modifier = Modifier) {
    if (names.isEmpty()) return
    val context = LocalContext.current
    val viewContributors = stringResource(R.string.view_contributors_on_github)
    val viewTranslators = stringResource(R.string.view_translators_on_weblate)
    val appContext = context.applicationContext
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val color = MaterialTheme.colorScheme.primary
    val zoom = remember { MarkZoom() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val padLeft = with(density) { (safe.calculateLeftPadding(direction) + MARK_MARGIN).roundToPx() }
    val padRight = with(density) { (safe.calculateRightPadding(direction) + MARK_MARGIN).roundToPx() }
    // Under the glass bar at the top, and a gap above the navigation bar at the bottom.
    val padTop = with(density) { (safe.calculateTopPadding() + GLASS_BAR_HEIGHT).roundToPx() }
    val padBottom = with(density) { (safe.calculateBottomPadding() + MARK_BOTTOM_GAP).roundToPx() }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick(viewContributors) { open(context, CONTRIBUTORS_URL); true }
                customActions = listOf(
                    CustomAccessibilityAction(viewTranslators) { open(context, WEBLATE_URL); true },
                )
            }
            .markZoomGestures(
                zoom,
                onTap = { open(context, CONTRIBUTORS_URL) },
                onDoubleTap = { scope.launch { zoom.toggle() } },
            ),
    ) {
        // The safe area the logo is fitted into at no zoom.
        val width = (constraints.maxWidth - padLeft - padRight).coerceAtLeast(0)
        val height = (constraints.maxHeight - padTop - padBottom).coerceAtLeast(0)
        val key = PlanKey(names, width, height, density.density, density.fontScale, direction)
        val plan by produceState(MarkPlanCache[key], key) {
            value = MarkPlanCache[key] ?: withContext(Dispatchers.Default) {
                // A measurer of its own: the composition's one is not meant to be shared across
                // threads.
                val measurer = TextMeasurer(fontFamilyResolver, density, direction)
                planMark(appContext, measurer, names, width, height)
            }.also { MarkPlanCache[key] = it }
        }
        // Shown at once when the plan was cached, faded in when it had to be made.
        val fade = remember { Animatable(if (plan != null) 1f else 0f) }
        val ready = plan != null
        LaunchedEffect(ready) { if (ready) fade.animateTo(1f, tween(NAME_FADE_IN_MS)) }
        Canvas(Modifier.fillMaxSize()) {
            val mark = plan ?: return@Canvas
            // Scale the logo's pixel bounds up to the safe area. Otherwise the empty space around
            // the vector would shrink every name.
            val grow = minOf(
                width.toFloat() / mark.width.coerceAtLeast(1),
                height.toFloat() / mark.height.coerceAtLeast(1),
            )
            val shiftX = padLeft + (width - mark.width * grow) / 2f - mark.left * grow
            val shiftY = padTop + (height - mark.height * grow) / 2f - mark.top * grow
            val alpha = fade.value
            // Zoomed in the draw pass rather than a layer, so the names are drawn sharp.
            translate(zoom.offset.x, zoom.offset.y) {
                scale(zoom.scale, zoom.scale, pivot = center) {
                    translate(shiftX, shiftY) {
                        scale(grow, grow, pivot = Offset.Zero) {
                            mark.names.forEach { name ->
                                drawText(
                                    textLayoutResult = name.layout,
                                    color = color,
                                    topLeft = Offset(name.x, name.y),
                                    alpha = alpha,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun open(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_found, Toast.LENGTH_LONG).show()
    }
}
