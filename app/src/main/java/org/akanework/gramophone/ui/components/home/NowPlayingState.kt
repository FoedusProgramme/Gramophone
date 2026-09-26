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

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.THEME_ANIMATION_MS
import org.akanework.gramophone.ui.components.player.NowPlayingColors
import org.akanework.gramophone.ui.components.player.nowPlayingColors
import org.akanework.gramophone.ui.components.player.rememberArtworkColorScheme

/** The song playing right now. Its list row takes [colors], the collapsed player's. */
@Stable
class NowPlayingState {
    var currentMediaId: String? by mutableStateOf(null)
        internal set
    var artworkUri: Uri? by mutableStateOf(null)
        internal set
    var colors: NowPlayingColors? by mutableStateOf(null)
        internal set
}

@Composable
fun rememberNowPlayingState(
    controllerViewModel: MediaControllerViewModel,
    lifecycle: Lifecycle,
    /** Color the playing row is harmonized to. Uses the theme's primary when null. */
    accent: Color? = null,
): NowPlayingState {
    val state = remember { NowPlayingState() }
    DisposableEffect(controllerViewModel, lifecycle) {
        controllerViewModel.addRecreationalPlayerListener(
            lifecycle,
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    state.currentMediaId = mediaItem?.mediaId
                    state.artworkUri = mediaItem?.mediaMetadata?.artworkUri
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    state.artworkUri = mediaMetadata.artworkUri
                }
            }
        ) {
            state.currentMediaId = it.currentMediaItem?.mediaId
            state.artworkUri = it.currentMediaItem?.mediaMetadata?.artworkUri
        }
        onDispose { }
    }
    // Only this scope reads the scheme, so a new cover doesn't recompose the whole screen.
    NowPlayingColorsEffect(state, accent)
    return state
}

@Composable
private fun NowPlayingColorsEffect(state: NowPlayingState, accent: Color?) {
    val cover = rememberArtworkColorScheme(state.artworkUri)
    val colors = nowPlayingColors(cover, accent ?: MaterialTheme.colorScheme.primary)
    SideEffect { state.colors = colors }
}

/** How a song row paints itself, on the card the list already drew behind it. */
@Stable
data class LibraryRowColors(
    /** Laid over the card; transparent leaves the card as it is. */
    val container: Color,
    val containerShape: Shape,
    val title: Color,
    val subtitle: Color,
    val icon: Color,
    /** How far the row has crossed over to the playing song's look, 0 to 1. */
    val emphasis: Float,
)

@Composable
fun defaultLibraryRowColors(): LibraryRowColors = MaterialTheme.colorScheme.let {
    LibraryRowColors(Color.Transparent, RectangleShape, it.onSurface, it.onSurfaceVariant, it.onSurface, 0f)
}

/**
 * The row's colours, crossing over to [colors] while it is the playing song and back once
 * another one takes over. [containerShape] is for rows that sit on no card of their own.
 */
@Composable
fun nowPlayingRowColors(
    isCurrent: Boolean,
    colors: NowPlayingColors?,
    containerShape: Shape = RectangleShape,
): LibraryRowColors {
    val normal = defaultLibraryRowColors()
    val fraction by animateFloatAsState(
        if (isCurrent && colors != null) 1f else 0f,
        tween(THEME_ANIMATION_MS),
        label = "now playing",
    )
    if (fraction == 0f || colors == null) return normal
    val fill by animateColorAsState(colors.fill, tween(THEME_ANIMATION_MS), label = "fill")
    val onFill by animateColorAsState(colors.onFill, tween(THEME_ANIMATION_MS), label = "on fill")
    val onFillVariant by animateColorAsState(
        colors.onFillVariant, tween(THEME_ANIMATION_MS), label = "on fill variant",
    )
    return LibraryRowColors(
        container = fill.copy(alpha = fill.alpha * fraction),
        containerShape = containerShape,
        title = lerp(normal.title, onFill, fraction),
        subtitle = lerp(normal.subtitle, onFillVariant, fraction),
        icon = lerp(normal.icon, onFill, fraction),
        emphasis = fraction,
    )
}
