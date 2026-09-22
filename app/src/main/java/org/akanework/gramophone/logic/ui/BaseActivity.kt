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

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBooleanStrict

open class BaseActivity : AppCompatActivity() {
    lateinit var prefs: SharedPreferences
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "pureDark" && (resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        ) {
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = defaultPrefs
        if (prefs.getBooleanStrict("pureDark", false) &&
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        ) {
            setTheme(R.style.Theme_Gramophone_PureDark)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }
}
