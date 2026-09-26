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

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Draws any [Drawable] (layer lists, insets, tinted vectors...) the way an `ImageView` would,
 * at the composable's size, so resource icons render identically to the View UI.
 */
class DrawablePainter(val drawable: Drawable) : Painter(), RememberObserver {
    private var drawInvalidateTick by mutableIntStateOf(0)

    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(d: Drawable) {
            drawInvalidateTick++
        }

        override fun scheduleDrawable(d: Drawable, what: Runnable, time: Long) {
            MAIN_HANDLER.postAtTime(what, time)
        }

        override fun unscheduleDrawable(d: Drawable, what: Runnable) {
            MAIN_HANDLER.removeCallbacks(what)
        }
    }

    init {
        if (drawable.intrinsicWidth >= 0 && drawable.intrinsicHeight >= 0) {
            drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        }
    }

    override fun onRemembered() {
        drawable.callback = callback
        drawable.setVisible(true, true)
        if (drawable is Animatable) drawable.start()
    }

    override fun onAbandoned() = onForgotten()

    override fun onForgotten() {
        if (drawable is Animatable) drawable.stop()
        drawable.setVisible(false, false)
        drawable.callback = null
    }

    override fun applyAlpha(alpha: Float): Boolean {
        drawable.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        drawable.colorFilter = colorFilter?.asAndroidColorFilter()
        return true
    }

    override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean {
        return drawable.setLayoutDirection(
            when (layoutDirection) {
                LayoutDirection.Ltr -> android.util.LayoutDirection.LTR
                LayoutDirection.Rtl -> android.util.LayoutDirection.RTL
            }
        )
    }

    override val intrinsicSize: Size
        get() = if (drawable.intrinsicWidth >= 0 && drawable.intrinsicHeight >= 0)
            Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        else Size.Unspecified

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            @Suppress("UNUSED_EXPRESSION") drawInvalidateTick
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            canvas.withSave {
                drawable.draw(canvas.nativeCanvas)
            }
        }
    }

    private companion object {
        val MAIN_HANDLER by lazy(LazyThreadSafetyMode.NONE) { Handler(Looper.getMainLooper()) }
    }
}

/**
 * A [Painter] for a default cover. Those covers carry their colours as `?attr/...` tints, which
 * resolve against the Android XML theme; that theme is fixed, so the palette chosen in the
 * settings never reaches them and a themed app would keep showing them in the colours it
 * shipped with. Paint them here instead: the layer list's background takes the scheme's
 * `surfaceVariant` and the glyph over it `onSurface`.
 */
@Composable
fun rememberDefaultCoverPainter(@DrawableRes id: Int): Painter {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.surfaceVariant
    val glyph = MaterialTheme.colorScheme.onSurface
    return remember(context, id, background, glyph) {
        DrawablePainter(
            ContextCompat.getDrawable(context, id)!!.mutate().apply {
                if (this is LayerDrawable) {
                    for (index in 0 until numberOfLayers) {
                        val layer = getDrawable(index)?.mutate() ?: continue
                        layer.setTint((if (index == 0) background else glyph).toArgb())
                        setDrawable(index, layer)
                    }
                } else {
                    setTint(glyph.toArgb())
                }
            }
        )
    }
}
