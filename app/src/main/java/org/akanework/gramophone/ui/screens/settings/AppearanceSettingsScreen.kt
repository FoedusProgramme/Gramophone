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

import org.akanework.gramophone.ui.theme.AppFont
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberStringPreference
import org.akanework.gramophone.ui.components.settings.NavigationPreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.PreferenceSectionHeader
import org.akanework.gramophone.ui.components.settings.SwitchPreferenceRow
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.ThemeSettingsKey

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppNavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = rememberStringPreference("tabs", "")
    val showFileNames = rememberBooleanPreference("show_file_names", true)
    val appFont = rememberBooleanPreference(AppFont.PREF_KEY, AppFont.PREF_DEFAULT)
    var tabOrderOpen by remember { mutableStateOf(false) }

    PreferenceScreen(title = stringResource(R.string.settings_category_appearance), onBack = onBack, modifier = modifier) {
        PreferenceSectionHeader(stringResource(R.string.settings_preference_category_application))
        PreferenceGroup(buildList {
            add { shape ->
                NavigationPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = stringResource(R.string.settings_theme_summary),
                    onClick = { onNavigate(ThemeSettingsKey()) },
                )
            }
            // Only shown where the system has the font. Otherwise the platform font is used.
            if (AppFont.isAvailable) add { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_app_font),
                    subtitle = stringResource(R.string.settings_app_font_summary),
                    checked = appFont.value,
                    onCheckedChange = { appFont.set(it) },
                )
            }
        })

        PreferenceSectionHeader(stringResource(R.string.settings_preference_category_home))
        PreferenceGroup({ shape ->
            NavigationPreferenceRow(
                shape,
                title = stringResource(R.string.tab_order),
                subtitle = stringResource(R.string.tab_order_summary),
                onClick = { tabOrderOpen = true },
            )
        })

        PreferenceSectionHeader(stringResource(R.string.settings_preference_category_folders_filesystem))
        PreferenceGroup({ shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.show_file_names),
                subtitle = stringResource(R.string.show_file_names_summary),
                checked = showFileNames.value,
                onCheckedChange = { showFileNames.set(it) },
            )
        })
    }

    if (tabOrderOpen) {
        TabOrderDialog(
            initial = tabs.value,
            onDismiss = { tabOrderOpen = false },
            onConfirm = {
                tabs.set(it)
                tabOrderOpen = false
            },
        )
    }
}
