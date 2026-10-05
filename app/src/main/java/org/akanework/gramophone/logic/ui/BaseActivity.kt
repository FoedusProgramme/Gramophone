/*
 *     Copyright (C) 2025 nift4
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

package org.akanework.gramophone.logic.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.ui.theme.overrideConfiguration
import org.akanework.gramophone.ui.theme.themeMode

open class BaseActivity : ComponentActivity() {
    lateinit var prefs: SharedPreferences

    override fun attachBaseContext(newBase: Context) {
        val override = newBase.defaultPrefs.themeMode().overrideConfiguration()
        super.attachBaseContext(
            if (override == null) newBase else newBase.createConfigurationContext(override)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = defaultPrefs
        super.onCreate(savedInstanceState)
    }

    /** Asks for audio permission in the app's settings instead, and closes the screen. */
    protected open fun onLibraryPermissionDenied() {
        openAppSettingsForAudio()
        finish()
    }

    /** Tells the user to grant audio access and opens the app's system settings page for it. */
    protected fun openAppSettingsForAudio() {
        Toast.makeText(this, getString(R.string.grant_audio), Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.setData("package:$packageName".toUri())
        startActivity(intent)
    }
}
