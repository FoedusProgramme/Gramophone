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

package org.akanework.gramophone.ui.fragments.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.Preference
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.TabOrderPreference
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingsActivity
import org.akanework.gramophone.ui.widget.CardWidgetColorResolver
import org.akanework.gramophone.ui.widget.DesktopWidgetManager

class AppearanceSettingsActivity : BaseSettingsActivity(
    R.string.settings_category_appearance,
    { AppearanceSettingsFragment() })

class AppearanceSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance, rootKey)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "theme_mode" -> {
                when (sharedPreferences?.getString("theme_mode", "0")) {
                    "0" -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    }

                    "1" -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    }

                    "2" -> {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }
            }

            CardWidgetColorResolver.PREF_WIDGET_THEME -> {
                CardWidgetColorResolver.clearCache()
                context?.let { DesktopWidgetManager.updateAllWidgets(it) }
            }
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is TabOrderPreference) {
            val f = TabOrderPreference.TabOrderDialog.newInstance(preference.key)
            @Suppress("deprecation") // original class does it like this as well
            f.setTargetFragment(this, 0)
            f.show(getParentFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG")
        } else super.onDisplayPreferenceDialog(preference)
    }

}
