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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.SECTION_HEADER_TOP_GAP
import org.akanework.gramophone.ui.components.settings.SliderPreferenceRow
import org.akanework.gramophone.ui.components.settings.SwitchPreferenceRow

@Composable
fun LyricSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val bold = rememberBooleanPreference("lyric_bold", false)
    val center = rememberBooleanPreference("lyric_center", false)
    val textSize = rememberIntPreference("lyric_text_size", 34)
    val statusBar = rememberBooleanPreference("status_bar_lyrics", false)
    val notification = rememberBooleanPreference("notification_lyrics", false)
    val trim = rememberBooleanPreference("trim_lyrics", true)
    val noAnimation = rememberBooleanPreference("lyric_no_animation", false)
    val autoWordTranslation = rememberBooleanPreference("translation_auto_word", false)

    PreferenceScreen(title = stringResource(R.string.settings_lyric), onBack = onBack, modifier = modifier) {
        // No header here: keep its gap so the first row sits where a header's text would.
        Spacer(Modifier.height(SECTION_HEADER_TOP_GAP))
        PreferenceGroup(
            { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_lyrics_bold),
                    subtitle = stringResource(R.string.settings_lyrics_bold_summary),
                    checked = bold.value,
                    onCheckedChange = { bold.set(it) },
                )
            },
            { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_lyrics_center),
                    subtitle = stringResource(R.string.settings_lyrics_center_summary),
                    checked = center.value,
                    onCheckedChange = { center.set(it) },
                )
            },
            { shape ->
                SliderPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_font_size),
                    value = textSize.value,
                    range = 26..40,
                    onValueChange = { textSize.set(it) },
                )
            },
            { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_status_bar_lyrics_title),
                    subtitle = stringResource(R.string.settings_status_bar_lyrics_summary),
                    checked = statusBar.value,
                    onCheckedChange = { statusBar.set(it) },
                )
            },
            { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_notification_lyrics_title),
                    subtitle = stringResource(R.string.settings_notification_lyrics_summary),
                    checked = notification.value,
                    onCheckedChange = { notification.set(it) },
                )
            },
            { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_trim_lyrics),
                    subtitle = stringResource(R.string.settings_trim_lyrics_summary),
                    checked = trim.value,
                    onCheckedChange = { trim.set(it) },
                )
            },
            { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_lyrics_no_animation),
                    subtitle = stringResource(R.string.settings_lyrics_no_animation_summary),
                    checked = noAnimation.value,
                    onCheckedChange = { noAnimation.set(it) },
                )
            },
            { shape ->
                SwitchPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_translation_auto_word),
                    subtitle = stringResource(R.string.settings_translation_auto_word_summary),
                    checked = autoWordTranslation.value,
                    onCheckedChange = { autoWordTranslation.set(it) },
                )
            },
        )
    }
}
