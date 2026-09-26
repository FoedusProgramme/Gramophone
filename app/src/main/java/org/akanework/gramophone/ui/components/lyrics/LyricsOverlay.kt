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

package org.akanework.gramophone.ui.components.lyrics

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference

/** Alpha of the primary colour, over the surface, that lines which aren't sung are drawn in. */
private const val LYRIC_DEFAULT_ALPHA = 0.30f

/** Alpha of the primary colour, over the surface, of a translation line being sung. */
private const val LYRIC_HIGHLIGHT_TL_ALPHA = 0.784f

/** The lyric colours as ARGB ints, as the text paints take them. */
@Immutable
internal data class LyricsColors(val default: Int, val highlight: Int, val highlightTl: Int)

/** Insets (px) the lyric text is padded by. The lyrics still scroll underneath them. */
@Immutable
data class LyricsPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * The lyrics overlay: the current song's lyrics on the cover scheme's surface, following
 * playback. The `lyric_ui_v2` preference picks the v2 (canvas-drawn, word-synced) or the v1
 * (plain list) lyrics. [state] carries its visibility, fade and back-gesture scale.
 */
@Composable
fun LyricsOverlay(
    state: LyricsOverlayState,
    scheme: ColorScheme,
    padding: LyricsPadding,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playback = remember(context) { LyricsPlayback(context.findMainActivity()) }
    DisposableEffect(playback) { onDispose { playback.destroy() } }
    val colors = remember(scheme) {
        LyricsColors(
            default = scheme.primary.copy(alpha = LYRIC_DEFAULT_ALPHA).compositeOver(scheme.surface).toArgb(),
            highlight = scheme.primary.toArgb(),
            highlightTl = scheme.primary.copy(alpha = LYRIC_HIGHLIGHT_TL_ALPHA).compositeOver(scheme.surface).toArgb(),
        )
    }
    val v2 = rememberBooleanPreference("lyric_ui_v2", true).value
    val visible = state.visible

    // Keep the screen on while the lyrics are visible
    val view = LocalView.current
    DisposableEffect(view, visible) {
        if (visible) view.keepScreenOn = true
        onDispose { if (visible) view.keepScreenOn = false }
    }

    Box(
        modifier
            .graphicsLayer {
                alpha = state.alpha.value
                scaleX = state.scale.value
                scaleY = state.scale.value
            }
            // When hidden, nothing is drawn and no input is taken
            .drawWithContent { if (state.visible) drawContent() }
            .then(if (visible) Modifier.background(scheme.surface) else Modifier),
    ) {
        val positionTick = { state.positionTick }
        if (v2) {
            NewLyrics(state.lyrics, visible, positionTick, colors, padding, playback, Modifier.fillMaxSize())
        } else {
            LegacyLyrics(state.lyrics, visible, positionTick, colors, padding, playback, Modifier.fillMaxSize())
        }
    }
}

private tailrec fun Context.findMainActivity(): MainActivity = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.findMainActivity()
    else -> error("Lyrics hosted outside MainActivity")
}
