/*
 *     Copyright (C) 2026 The Gramophone authors
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

package org.akanework.gramophone.logic.init

import androidx.media3.common.util.Log

/** Routes library loggers through media3's [Log]. */
object LoggingInitializer {

    // `setprop log.tag.GramophoneApplication INFO` keeps media3's own logger; the tag predates
    // this class, so keep it for existing debugging instructions.
    private const val TAG = "GramophoneApplication"

    fun install() {
        org.nift4.mediastorecompat.Log.setLogger(object : org.nift4.mediastorecompat.Log.Logger {
            override fun d(
                tag: String,
                message: String,
                throwable: Throwable?
            ) {
                Log.d(tag, message, throwable)
            }

            override fun i(
                tag: String,
                message: String,
                throwable: Throwable?
            ) {
                Log.i(tag, message, throwable)
            }

            override fun w(
                tag: String,
                message: String,
                throwable: Throwable?
            ) {
                Log.w(tag, message, throwable)
            }

            override fun e(
                tag: String,
                message: String,
                throwable: Throwable?
            ) {
                Log.e(tag, message, throwable)
            }
        })
        if (!android.util.Log.isLoggable(TAG, android.util.Log.INFO)) {
            Log.setLogger(object : Log.Logger {
                override fun d(
                    tag: String,
                    message: String,
                    throwable: Throwable?
                ) {
                    android.util.Log.e(tag, "[DEBUG] $message", throwable)
                }

                override fun i(
                    tag: String,
                    message: String,
                    throwable: Throwable?
                ) {
                    android.util.Log.e(tag, "[INFO] $message", throwable)
                }

                override fun w(
                    tag: String,
                    message: String,
                    throwable: Throwable?
                ) {
                    android.util.Log.e(tag, "[WARN] $message", throwable)
                }

                override fun e(
                    tag: String,
                    message: String,
                    throwable: Throwable?
                ) {
                    android.util.Log.e(tag, "[ERROR] $message", throwable)
                }
            })
        }
    }
}
