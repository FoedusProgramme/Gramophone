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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.ui.components.player.PlayerUtilities
import kotlin.math.PI
import kotlin.math.cos

/** How much the lyrics shrink at the end of a predictive back gesture. */
private const val LYRICS_BACK_MAX_SHRINK = 0.1f

/** The platform's default animator curve (AccelerateDecelerateInterpolator) as an [Easing]. */
internal val AccelerateDecelerateEasing = Easing { (cos((it + 1f) * PI) / 2.0 + 0.5).toFloat() }

/**
 * State of the lyrics overlay above the full player: its lyrics, visibility, fade and back-gesture
 * scale. Hoisted in the player host so it outlives the composition that draws it, which keeps the
 * scroll position across hide and show.
 *
 * [scope] runs the fade and scale animations. It needs a MonotonicFrameClock, so the host passes
 * a scope on AndroidUiDispatcher.Main.
 */
@Stable
class LyricsOverlayState(private val scope: CoroutineScope) {
    /** The current song's lyrics, or null for none (the "no lyrics" line). */
    var lyrics by mutableStateOf<SemanticLyrics?>(null)

    /** Whether the overlay is shown at all. */
    var visible by mutableStateOf(false)
        private set

    val alpha = Animatable(1f)
    val scale = Animatable(1f)

    /**
     * Whether the overlay fully covers the player: shown, opaque and not scaled by a back gesture.
     * The player fades its own content out underneath only then.
     */
    val covering: Boolean
        get() = visible && alpha.value == 1f && scale.value == 1f

    /** Bumped by the host's position poll. The lyrics re-check the playback position on each change. */
    var positionTick by mutableIntStateOf(0)
        private set

    private var fadeJob: Job? = null
    private var scaleJob: Job? = null

    fun updateLyricPositionFromPlaybackPos() {
        positionTick++
    }

    /** Fades the overlay in from transparent over [durationMs]. */
    fun fadeIn(durationMs: Int = PlayerUtilities.LYRIC_COVER_FADE_MS) {
        fadeJob?.cancel()
        visible = true
        fadeJob = scope.launch {
            alpha.snapTo(0f)
            alpha.animateTo(1f, tween(durationMs, easing = AccelerateDecelerateEasing))
        }
    }

    /**
     * Fades the overlay out and hides it, then runs [completion]. The fade takes the part of
     * [durationMs] the current alpha is away from transparent.
     */
    fun fadeOut(durationMs: Int = PlayerUtilities.LYRIC_COVER_FADE_MS, completion: (() -> Unit)? = null) {
        if (!visible) {
            completion?.invoke()
            return
        }
        fadeJob?.cancel()
        fadeJob = scope.launch {
            alpha.animateTo(
                0f,
                tween(lerp(0f, durationMs.toFloat(), alpha.value).toInt(), easing = AccelerateDecelerateEasing),
            )
            visible = false
            completion?.invoke()
        }
    }

    /** Cancels running overlay animations when a back gesture starts. */
    fun onBackStarted() {
        fadeJob?.cancel()
        scaleJob?.cancel()
    }

    /** Scales the lyrics down with back gesture [progress]. */
    fun onBackProgressed(progress: Float) {
        scaleJob?.cancel()
        scaleJob = scope.launch { scale.snapTo(1f - LYRICS_BACK_MAX_SHRINK * progress) }
    }

    fun onBackPressed() {
        fadeOut { scaleJob = scope.launch { scale.snapTo(1f) } }
    }

    fun onBackCancelled() {
        scaleJob?.cancel()
        scaleJob = scope.launch {
            scale.animateTo(1f, tween(PlayerUtilities.LYRIC_COVER_FADE_MS, easing = AccelerateDecelerateEasing))
        }
    }
}
