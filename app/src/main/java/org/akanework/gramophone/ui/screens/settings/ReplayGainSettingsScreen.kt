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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.compose.rememberStringPreference
import org.akanework.gramophone.ui.components.settings.DropdownPreferenceRow
import org.akanework.gramophone.ui.components.settings.InfoPreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.PreferenceSectionHeader
import org.akanework.gramophone.ui.components.settings.SliderPreferenceRow
import org.akanework.gramophone.ui.components.settings.SwitchPreferenceRow

/** The pre amp slider stores 0..30 for -15..15 dB. */
private const val PREAMP_OFFSET_DB = 15

@Composable
fun ReplayGainSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val mode = rememberStringPreference("rg_mode", "0")
    val drc = rememberBooleanPreference("rg_drc", true)
    val preamp = rememberIntPreference("rg_rg_gain", 19)
    val attenuation = rememberIntPreference("rg_no_rg_gain", 0)

    PreferenceScreen(title = stringResource(R.string.settings_replaygain), onBack = onBack, modifier = modifier) {
        PreferenceSectionHeader(stringResource(R.string.settings_replaygain))
        PreferenceGroup(
            { shape ->
                DropdownPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_replaygain_mode),
                    entries = stringArrayResource(R.array.rg_mode_switch).toList(),
                    values = stringArrayResource(R.array.rg_mode_switch_val).toList(),
                    value = mode.value,
                    onValueChange = { mode.set(it) },
                )
            },
            { shape -> InfoPreferenceRow(shape, stringResource(R.string.settings_replaygain_mode_desc)) },
            { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_replaygain_drc),
                    subtitle = stringResource(R.string.settings_replaygain_drc_desc),
                    checked = drc.value,
                    onCheckedChange = { drc.set(it) },
                )
            },
        )

        PreferenceSectionHeader(stringResource(R.string.settings_replaygain_preamp))
        PreferenceGroup(
            { shape ->
                SliderPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_replaygain_preamp),
                    value = preamp.value,
                    range = 0..30,
                    onValueChange = { preamp.set(it) },
                    valueText = { stringResource(R.string.d_db, it - PREAMP_OFFSET_DB) },
                )
            },
            { shape -> InfoPreferenceRow(shape, stringResource(R.string.settings_replaygain_preamp_desc)) },
        )

        PreferenceSectionHeader(stringResource(R.string.settings_replaygain_no_rg_amp))
        PreferenceGroup(
            { shape ->
                SliderPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_replaygain_no_rg_amp),
                    value = attenuation.value,
                    range = 0..15,
                    onValueChange = { attenuation.set(it) },
                    valueText = { stringResource(R.string.d_db, it) },
                )
            },
            { shape -> InfoPreferenceRow(shape, stringResource(R.string.settings_replaygain_no_rg_amp_desc)) },
        )
    }
}
