/*
 *     Copyright (C) 2026 Akane Foundation
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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

object CardWidgetBitmapUtils {

    fun getRoundedBitmap(src: Bitmap, cornerRadiusPx: Float): Bitmap {
        return try {
            val width = src.width.coerceAtLeast(1)
            val height = src.height.coerceAtLeast(1)
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(src, 0f, 0f, paint)
            output
        } catch (_: Throwable) {
            src
        }
    }

    fun getCircularBitmap(src: Bitmap): Bitmap {
        return try {
            val size = minOf(src.width, src.height).coerceAtLeast(1)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = size / 2f
            canvas.drawCircle(radius, radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val left = (size - src.width) / 2f
            val top = (size - src.height) / 2f
            canvas.drawBitmap(src, left, top, paint)
            output
        } catch (_: Throwable) {
            src
        }
    }
}
