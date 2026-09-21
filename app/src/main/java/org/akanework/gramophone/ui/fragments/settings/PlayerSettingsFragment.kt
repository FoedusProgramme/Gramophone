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

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.color.DynamicColors
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.nav.lyricSettingsKey

class PlayerSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_player, rootKey)
        findPreference<Preference>("content_based_color")!!
            .isVisible = DynamicColors.isDynamicColorAvailable()

        val cookieCoverPref = findPreference<SwitchPreferenceCompat>("cookie_cover")
        val roundCornerPref = findPreference<SeekBarPreference>("album_round_corner")

        cookieCoverPref?.setOnPreferenceChangeListener { _, newValue ->
            roundCornerPref?.isEnabled = !(newValue as Boolean)
            true
        }

        roundCornerPref?.isEnabled = !(cookieCoverPref?.isChecked ?: false)

    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "lyrics") {
            (requireActivity() as MainActivity).navigateTo(lyricSettingsKey())
        }
        return super.onPreferenceTreeClick(preference)
    }

}
