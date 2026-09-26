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

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.settings.NavigationPreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.PreferenceSectionHeader
import org.akanework.gramophone.ui.components.settings.SliderPreferenceRow
import org.akanework.gramophone.ui.components.settings.SwitchPreferenceRow
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.LyricSettingsKey

@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppNavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundCorner = rememberIntPreference("album_round_corner", integerResource(R.integer.round_corner_radius))
    val boldTitle = rememberBooleanPreference("bold_title", true)
    val centeredTitle = rememberBooleanPreference("centered_title", false)
    val contentBasedColor = rememberBooleanPreference("content_based_color", true)
    val defaultProgressBar = rememberBooleanPreference("default_progress_bar", false)
    val audioQualityInfo = rememberBooleanPreference("audio_quality_info", false)
    val cookieCover = rememberBooleanPreference("cookie_cover", false)
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val fullPlayer = buildList<@Composable (Shape) -> Unit> {
        add { shape ->
            // The cookie cover has its own outline, so the corner radius is moot with it on.
            SliderPreferenceRow(
                shape,
                title = stringResource(R.string.settings_album_round_corner),
                value = roundCorner.value,
                range = 0..28,
                onValueChange = { roundCorner.set(it) },
                enabled = !cookieCover.value,
            )
        }
        add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_title_bold),
                subtitle = stringResource(R.string.settings_title_bold_summary),
                checked = boldTitle.value,
                onCheckedChange = { boldTitle.set(it) },
            )
        }
        add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_title_center),
                subtitle = stringResource(R.string.settings_title_center_summary),
                checked = centeredTitle.value,
                onCheckedChange = { centeredTitle.set(it) },
            )
        }
        if (dynamicColor) add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_content_based_color),
                subtitle = stringResource(R.string.settings_content_based_color_summary),
                checked = contentBasedColor.value,
                onCheckedChange = { contentBasedColor.set(it) },
            )
        }
        add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_default_progress_bar),
                subtitle = stringResource(R.string.settings_default_progress_bar_summary),
                checked = defaultProgressBar.value,
                onCheckedChange = { defaultProgressBar.set(it) },
            )
        }
        add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_audio_quality_info),
                subtitle = stringResource(R.string.settings_audio_quality_info_summary),
                checked = audioQualityInfo.value,
                onCheckedChange = { audioQualityInfo.set(it) },
            )
        }
        add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_cookie_info),
                subtitle = stringResource(R.string.settings_cookie_summary),
                checked = cookieCover.value,
                onCheckedChange = { cookieCover.set(it) },
            )
        }
    }

    PreferenceScreen(title = stringResource(R.string.settings_player_ui), onBack = onBack, modifier = modifier) {
        PreferenceSectionHeader(stringResource(R.string.settings_category_full_player))
        PreferenceGroup(fullPlayer)

        PreferenceSectionHeader(stringResource(R.string.settings_category_misc))
        PreferenceGroup({ shape ->
            NavigationPreferenceRow(
                shape,
                title = stringResource(R.string.settings_lyrics_configuration),
                subtitle = stringResource(R.string.settings_lyrics_configuration_summary),
                onClick = { onNavigate(LyricSettingsKey()) },
            )
        })
    }
}
