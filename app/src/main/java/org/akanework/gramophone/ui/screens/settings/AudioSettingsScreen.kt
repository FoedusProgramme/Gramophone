/*
 *     Copyright (C) 2025 Akane Foundation
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

package org.akanework.gramophone.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.PostAmpAudioOutputProvider
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.settings.InfoPreferenceRow
import org.akanework.gramophone.ui.components.settings.NavigationPreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.PreferenceSectionHeader
import org.akanework.gramophone.ui.components.settings.SliderPreferenceRow
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.ReplayGainSettingsKey

@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppNavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val boostGain = rememberIntPreference("rg_boost_gain", 0)
    // Boost needs something to amplify with: the Volume effect or the dynamics processor.
    val canBoost = remember {
        PostAmpAudioOutputProvider.isVolumeAvailable || PostAmpAudioOutputProvider.isDpeAvailable
    }

    PreferenceScreen(title = stringResource(R.string.settings_player_options), onBack = onBack, modifier = modifier) {
        PreferenceSectionHeader(stringResource(R.string.settings_category_misc))
        PreferenceGroup({ shape ->
            NavigationPreferenceRow(
                shape,
                title = stringResource(R.string.settings_replaygain),
                onClick = { onNavigate(ReplayGainSettingsKey()) },
            )
        })

        if (canBoost) {
            PreferenceSectionHeader(stringResource(R.string.settings_replaygain_boost))
            PreferenceGroup(
                { shape ->
                    SliderPreferenceRow(
                        shape,
                        title = stringResource(R.string.settings_replaygain_boost),
                        value = boostGain.value,
                        range = 0..15,
                        onValueChange = { boostGain.set(it) },
                        valueText = { stringResource(R.string.d_db, it) },
                    )
                },
                { shape -> InfoPreferenceRow(shape, stringResource(R.string.settings_replaygain_boost_desc)) },
            )
        }
    }
}
