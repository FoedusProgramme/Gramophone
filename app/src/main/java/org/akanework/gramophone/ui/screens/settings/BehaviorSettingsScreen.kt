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

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.hasImagePermission
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberIntPreference
import org.akanework.gramophone.ui.components.settings.NavigationPreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.PreferenceSectionHeader
import org.akanework.gramophone.ui.components.settings.SliderPreferenceRow
import org.akanework.gramophone.ui.components.settings.SwitchPreferenceRow
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.BlacklistKey

@Composable
fun BehaviorSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppNavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaStoreFilter = rememberIntPreference("mediastore_filter", integerResource(R.integer.filter_default_sec))
    val autoplay = rememberBooleanPreference("autoplay", false)
    val stopOnDismiss = rememberBooleanPreference("stopPlayingWhenDismissTask", false)
    val albumCovers = rememberBooleanPreference("album_covers", true)
    val alwaysSkipPrevious = rememberBooleanPreference("always_skip_previous", false)

    // Before Android 13 the switch is a setting that changes how covers are read. From 13 on it
    // mirrors the images permission instead, and tapping it goes to the app's settings page.
    val mirrorsPermission = hasScopedStorageWithMediaTypes()
    var imagePermission by remember { mutableStateOf(mirrorsPermission && context.hasImagePermission()) }
    if (mirrorsPermission) {
        LifecycleResumeEffect(Unit) {
            imagePermission = context.hasImagePermission()
            onPauseOrDispose {}
        }
    }

    val misc = buildList<@Composable (Shape) -> Unit> {
        add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_play_on_launch),
                subtitle = stringResource(R.string.settings_play_on_launch_summary),
                checked = autoplay.value,
                onCheckedChange = { autoplay.set(it) },
            )
        }
        add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_stop_on_dismiss),
                subtitle = stringResource(R.string.settings_stop_on_dismiss_summary),
                checked = stopOnDismiss.value,
                onCheckedChange = { stopOnDismiss.set(it) },
            )
        }
        if (!mirrorsPermission || !Flags.REMOVE_IMAGE_PERMISSION) add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.album_covers_enhance),
                subtitle = stringResource(R.string.album_covers_enhance_desc),
                checked = if (mirrorsPermission) imagePermission else albumCovers.value,
                onCheckedChange = { checked ->
                    if (mirrorsPermission) {
                        Toast.makeText(
                            context,
                            if (imagePermission) R.string.deny_images else R.string.grant_images,
                            Toast.LENGTH_LONG,
                        ).show()
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData("package:${context.packageName}".toUri())
                        )
                    } else {
                        albumCovers.set(checked)
                    }
                },
            )
        }
        add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_always_skip_previous),
                subtitle = stringResource(R.string.settings_always_skip_previous_summary),
                checked = alwaysSkipPrevious.value,
                onCheckedChange = { alwaysSkipPrevious.set(it) },
            )
        }
    }

    PreferenceScreen(title = stringResource(R.string.settings_category_behavior), onBack = onBack, modifier = modifier) {
        PreferenceSectionHeader(stringResource(R.string.settings_category_filters))
        PreferenceGroup(
            { shape ->
                SliderPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_mediastore_filter),
                    value = mediaStoreFilter.value,
                    range = 0..120,
                    onValueChange = { mediaStoreFilter.set(it) },
                )
            },
            { shape ->
                NavigationPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_blacklist_folder),
                    subtitle = stringResource(R.string.settings_blacklist_folder_summary),
                    onClick = { onNavigate(BlacklistKey()) },
                )
            },
        )

        PreferenceSectionHeader(stringResource(R.string.settings_category_misc))
        PreferenceGroup(misc)
    }
}
