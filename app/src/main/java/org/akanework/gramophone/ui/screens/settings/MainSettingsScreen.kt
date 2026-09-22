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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.settings.NavigationPreferenceRow
import org.akanework.gramophone.ui.components.settings.PREFERENCE_GROUP_GAP
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.SECTION_HEADER_TOP_GAP
import org.akanework.gramophone.ui.nav.AboutSettingsKey
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.AppearanceSettingsKey
import org.akanework.gramophone.ui.nav.AudioSettingsKey
import org.akanework.gramophone.ui.nav.BehaviorSettingsKey
import org.akanework.gramophone.ui.nav.ExperimentalSettingsKey
import org.akanework.gramophone.ui.nav.PlayerSettingsKey

/** One of the settings pages, as the top page lists it. */
private class SettingsPage(
    val icon: ImageVector,
    val title: Int,
    val summary: Int,
    val key: () -> AppNavKey,
)

/** The pages in their groups: how it looks, how it behaves, what it is. */
private val PAGE_GROUPS = listOf(
    listOf(
        SettingsPage(Icons.Filled.Palette, R.string.settings_category_appearance, R.string.settings_appearance_summary) { AppearanceSettingsKey() },
        SettingsPage(Icons.Filled.PlayArrow, R.string.settings_player_ui, R.string.settings_player_ui_summary) { PlayerSettingsKey() },
    ),
    listOf(
        SettingsPage(Icons.Filled.PrecisionManufacturing, R.string.settings_category_behavior, R.string.settings_behavior_summary) { BehaviorSettingsKey() },
        SettingsPage(Icons.Filled.Headphones, R.string.settings_player_options, R.string.settings_player_options_summary) { AudioSettingsKey() },
        SettingsPage(Icons.Filled.Science, R.string.settings_experimental_settings, R.string.settings_experimental_settings_summary) { ExperimentalSettingsKey() },
    ),
    listOf(
        SettingsPage(Icons.Filled.Info, R.string.settings_about_app, R.string.settings_about_gramophone) { AboutSettingsKey() },
    ),
)

/** The top of the settings: one row per page, in groups. */
@Composable
fun MainSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppNavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceScreen(title = stringResource(R.string.home_menu_settings), onBack = onBack, modifier = modifier) {
        // No headers here: keep their gap so the first row sits where a header's text would.
        Spacer(Modifier.height(SECTION_HEADER_TOP_GAP))
        PAGE_GROUPS.forEachIndexed { index, pages ->
            if (index > 0) Spacer(Modifier.height(PREFERENCE_GROUP_GAP))
            PreferenceGroup(pages) { page, shape ->
                NavigationPreferenceRow(
                    shape,
                    title = stringResource(page.title),
                    subtitle = stringResource(page.summary),
                    icon = page.icon,
                    onClick = { onNavigate(page.key()) },
                )
            }
        }
    }
}
