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

package org.akanework.gramophone.logic.utils

import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [CalculationUtils] contains some methods for internal
 * calculation.
 */
object CalculationUtils {

    /**
     * [convertDurationToTimeStamp] makes a string format
     * of duration (presumably long) converts into timestamp
     * like 300 to 5:00.
     *
     * @param duration
     * @return
     */
    fun convertDurationToTimeStamp(duration: Long, zero: Boolean = false): String {
        val hours = duration / 1000 / 60 / 60
        val minutes = duration / 1000 / 60 % 60
        val seconds = duration / 1000 % 60
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        }
        return String.format(Locale.getDefault(), if (zero) "%02d:%02d" else "%d:%02d", minutes, seconds)
    }

    /**
     * convertUnixTimestampToMonthDay:
     *   Converts unix timestamp to Month - Day format.
     */
    fun convertUnixTimestampToMonthDay(unixTimestamp: Long): String =
        SimpleDateFormat(
            "MM-dd",
            Locale.getDefault()
        ).format(
            Date(unixTimestamp * 1000)
        )

    /**
     * Set the alpha component of `color` to be `alpha`.
     */
    @ColorInt
    fun setAlphaComponent(
        @ColorInt color: Int,
        @IntRange(from = 0x0, to = 0xFF) alpha: Int
    ): Int {
        require(!(alpha < 0 || alpha > 255)) { "alpha must be between 0 and 255." }
        return color and 0x00ffffff or (alpha shl 24)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }

    /**
     * Returns the interpolation scalar (s) that satisfies the equation: `value = `[ ][.lerp]`(a, b, s)`
     *
     *
     * If `a == b`, then this function will return 0.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun lerpInv(a: Float, b: Float, value: Float): Float {
        return if (a != b) (value - a) / (b - a) else 0.0f
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun lerp(start: Double, stop: Double, amount: Double): Double {
        return start + (stop - start) * amount
    }

    /**
     * Returns the interpolation scalar (s) that satisfies the equation: `value = `[ ][.lerp]`(a, b, s)`
     *
     *
     * If `a == b`, then this function will return 0.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun lerpInv(a: Double, b: Double, value: Double): Double {
        return if (a != b) (value - a) / (b - a) else 0.0
    }

    /** Returns the single argument constrained between [0.0, 1.0].  */
    private fun saturate(value: Float): Float {
        return value.coerceAtLeast(0f).coerceAtMost(1f)
    }

    /** Returns the saturated (constrained between [0, 1]) result of [.lerpInv].  */
    fun lerpInvSat(a: Float, b: Float, value: Float): Float {
        return saturate(lerpInv(a, b, value))
    }

}

fun Long.convertDurationToTimeStamp(zero: Boolean = false) =
    CalculationUtils.convertDurationToTimeStamp(this, zero)