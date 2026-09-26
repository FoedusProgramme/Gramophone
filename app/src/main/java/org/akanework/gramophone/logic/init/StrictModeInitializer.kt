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

import android.os.Build
import android.os.Debug
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.getSystemProperty

/** Debug-only StrictMode policies (skipped on ColorOS). */
object StrictModeInitializer {

    fun install() {
        if (BuildConfig.DEBUG && !isColorOS()) {
            // Use StrictMode to find antipattern issues
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectAll()
                    .let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            it.permitExplicitGc() // platform calls System.gc() on activity destroy
                        } else it
                    }
                    .let {
                        if (Debug.isDebuggerConnected() || isAlpsBoostFwkPresent())
                            it.permitDiskReads()
                        else it
                    }
                    .penaltyLog()
                    .penaltyDialog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectAll()
                    // detectAll does in fact not detect everything :)
                    .let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            it.detectImplicitDirectBoot()
                        } else it
                    }
                    .penaltyLog()
                    .let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            it.penaltyDeathOnFileUriExposure()
                        } else it
                    }
                    .build()
            )
        }
    }

    private fun isAlpsBoostFwkPresent(): Boolean {
        try {
            Class.forName("com.mediatek.boostfwk.BoostFwkManagerImpl")
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    private fun isColorOS(): Boolean {
        val props = listOf(
            "ro.build.version.opporom",
            "ro.oplus.os.version"
        )
        return props.any {
            !getSystemProperty(it).isNullOrBlank()
        }
    }
}
