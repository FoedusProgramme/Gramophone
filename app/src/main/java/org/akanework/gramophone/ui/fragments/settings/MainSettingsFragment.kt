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
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.nav.aboutSettingsKey
import org.akanework.gramophone.ui.nav.appearanceSettingsKey
import org.akanework.gramophone.ui.nav.audioSettingsKey
import org.akanework.gramophone.ui.nav.behaviorSettingsKey
import org.akanework.gramophone.ui.nav.experimentalSettingsKey
import org.akanework.gramophone.ui.nav.playerSettingsKey

class MainSettingsFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_top, rootKey)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val activity = requireActivity() as MainActivity
        when (preference.key) {
            "appearance" -> activity.navigateTo(appearanceSettingsKey())
            "behavior" -> activity.navigateTo(behaviorSettingsKey())
            "about" -> activity.navigateTo(aboutSettingsKey())
            "player" -> activity.navigateTo(playerSettingsKey())
            "audio" -> activity.navigateTo(audioSettingsKey())
            "experimental" -> activity.navigateTo(experimentalSettingsKey())
        }
        return super.onPreferenceTreeClick(preference)
    }

}
