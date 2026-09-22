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

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.nav.LocalHomeCovered
import kotlin.random.Random

/** The song playing right now. It drives the equaliser icon on the matching list row. */
@Stable
class NowPlayingState {
    var currentMediaId: String? by mutableStateOf(null)
        internal set
    var isPlaying: Boolean by mutableStateOf(false)
        internal set
}

@Composable
fun rememberNowPlayingState(
    controllerViewModel: MediaControllerViewModel,
    lifecycle: Lifecycle,
): NowPlayingState {
    val state = remember { NowPlayingState() }
    DisposableEffect(controllerViewModel, lifecycle) {
        controllerViewModel.addRecreationalPlayerListener(
            lifecycle,
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    state.currentMediaId = mediaItem?.mediaId
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    val c = controllerViewModel.get()!!
                    state.isPlaying = playWhenReady && c.playbackState != Player.STATE_ENDED
                            && c.playbackState != Player.STATE_IDLE
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val c = controllerViewModel.get()!!
                    state.isPlaying = c.playWhenReady && playbackState != Player.STATE_ENDED
                            && c.playbackState != Player.STATE_IDLE
                }
            }
        ) {
            state.currentMediaId = it.currentMediaItem?.mediaId
            state.isPlaying = it.playWhenReady && it.playbackState != Player.STATE_ENDED
                    && it.playbackState != Player.STATE_IDLE
        }
        onDispose { }
    }
    return state
}

private const val PADDING = 160f
private const val SIZE = 960f
private const val BAR_WIDTH = 160f
private const val BAR_HEIGHT = 640
private const val BAR_HEIGHT_MIN = 30
private const val ANIM_DURATION = 200f

/**
 * The same drawing as `NowPlayingDrawable`: three bars on a 960-unit canvas. Level 1 keeps
 * picking random bar heights (playing), level 0 rests at the minimum (paused), level 2 shrinks
 * to nothing and then reports [onHidden].
 */
@Composable
fun NowPlayingBars(
    level: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onHidden: () -> Unit = {},
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    }
    val bars = remember { NowPlayingBarsState(animationsEnabled) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(level) { bars.setLevel(level) }
    val covered = LocalHomeCovered.current
    LaunchedEffect(bars, covered) {
        if (covered) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            bars.step(level)
            if (bars.settledAtLevel2 && level == 2) onHidden()
            frame++
        }
    }
    Canvas(modifier.size(48.dp).padding(12.dp)) {
        @Suppress("UNUSED_EXPRESSION") frame
        val sx = size.width / SIZE
        val sy = size.height / SIZE
        fun bar(left: Float, height: Float) {
            drawRect(
                color = color,
                topLeft = Offset((PADDING + left) * sx, ((SIZE - PADDING) - height) * sy),
                size = Size(BAR_WIDTH * sx, height * sy),
            )
        }
        bar(0f, bars.lc)
        bar(240f, bars.mc)
        bar(480f, bars.rc)
    }
}

private class NowPlayingBarsState(private val animationsEnabled: Boolean) {
    private val rng = Random
    var lc = 0f; private var li = 0f; private var lt = BAR_HEIGHT_MIN.toFloat()
    var mc = 0f; private var mi = 0f; private var mt = BAR_HEIGHT_MIN.toFloat()
    var rc = 0f; private var ri = 0f; private var rt = BAR_HEIGHT_MIN.toFloat()
    private var ts = if (animationsEnabled) SystemClock.elapsedRealtime() else 0L
    var settledAtLevel2 = false

    private fun random() = rng.nextInt(BAR_HEIGHT - BAR_HEIGHT_MIN).toFloat() + BAR_HEIGHT_MIN

    fun setLevel(level: Int) {
        li = lc; mi = mc; ri = rc
        when (level) {
            0 -> { lt = BAR_HEIGHT_MIN.toFloat(); mt = lt; rt = lt }
            1 -> { lt = random(); mt = random(); rt = random() }
            2 -> { lt = 0f; mt = 0f; rt = 0f }
            else -> throw IllegalStateException()
        }
        ts = SystemClock.elapsedRealtime()
        settledAtLevel2 = false
    }

    fun step(level: Int) {
        val ld = if (lc == lt && level == 1) { lt = random(); li = lc; true } else false
        val md = if (mc == mt && level == 1) { mt = random(); mi = mc; true } else false
        val rd = if (rc == rt && level == 1) { rt = random(); ri = rc; true } else false
        if ((ld || md || rd) && animationsEnabled) ts = SystemClock.elapsedRealtime()
        val scale = if (animationsEnabled)
            ((SystemClock.elapsedRealtime() - ts) / ANIM_DURATION).coerceAtMost(1f)
        else 1f
        lc = lerp(li, lt, scale)
        mc = lerp(mi, mt, scale)
        rc = lerp(ri, rt, scale)
        if (level != 1 && lc == lt && mc == mt && rc == rt) {
            ts = 0L
            if (level == 2) settledAtLevel2 = true
        }
    }

    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
}

/**
 * The 48dp equaliser slot of a song row. Composes nothing when this row is not the current
 * song, except while the "song changed" shrink animation is still running.
 */
@Composable
fun NowPlayingIndicator(isCurrent: Boolean, isPlaying: Boolean, color: Color) {
    var visible by remember { mutableStateOf(isCurrent) }
    if (isCurrent) visible = true
    if (!visible) return
    NowPlayingBars(
        level = if (!isCurrent) 2 else if (isPlaying) 1 else 0,
        color = color,
        onHidden = { visible = false },
    )
}
