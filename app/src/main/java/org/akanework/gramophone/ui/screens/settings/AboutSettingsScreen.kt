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

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.data.Contributors
import org.akanework.gramophone.ui.components.settings.LinkPreferenceRow
import org.akanework.gramophone.ui.components.settings.NavigationPreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.PreferenceSectionHeader
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.ContributorsKey
import org.akanework.gramophone.ui.nav.OssLicensesKey

private const val REPOSITORY_URL = "https://github.com/FoedusProgramme/Gramophone"
private const val TELEGRAM_URL = "https://t.me/FoedusProgramme"
private const val COPYRIGHT = "\u00A9 2023-2026 United Software"

private val LICENSE_NOTICE = """
    Copyright (C) 2023-2024 AkaneTan
    Copyright (C) 2023-2026 nift4
    Copyright (C) 2024 The Gramophone authors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
""".trimIndent()

private enum class AboutDialog { About, PackageType, License }

@Composable
fun AboutSettingsScreen(
    onBack: () -> Unit,
    onNavigate: (AppNavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<AboutDialog?>(null) }
    val contributors = remember { Contributors.LIST.subList(0, 5).joinToString { it.login } }

    PreferenceScreen(title = stringResource(R.string.settings_about_app), onBack = onBack, modifier = modifier) {
        PreferenceSectionHeader(stringResource(R.string.settings_category_basic_info))
        PreferenceGroup(
            { shape ->
                NavigationPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_app_name),
                    subtitle = stringResource(R.string.app_name),
                    onClick = { dialog = AboutDialog.About },
                )
            },
            { shape ->
                NavigationPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_version),
                    subtitle = BuildConfig.MY_VERSION_NAME,
                    onClick = {},
                )
            },
            { shape ->
                NavigationPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_package_type),
                    subtitle = BuildConfig.RELEASE_TYPE,
                    onClick = { dialog = AboutDialog.PackageType },
                )
            },
            { shape ->
                NavigationPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_contributors),
                    subtitle = stringResource(R.string.settings_contributors_click, contributors),
                    onClick = { onNavigate(ContributorsKey()) },
                )
            },
            { shape ->
                NavigationPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_open_source_licenses),
                    onClick = { dialog = AboutDialog.License },
                )
            },
        )

        PreferenceSectionHeader(stringResource(R.string.settings_category_links))
        PreferenceGroup(
            { shape ->
                LinkPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_repository),
                    subtitle = REPOSITORY_URL,
                    url = REPOSITORY_URL,
                )
            },
            { shape ->
                LinkPreferenceRow(
                    shape,
                    title = stringResource(R.string.settings_telegram),
                    subtitle = stringResource(R.string.check_for_updates),
                    url = TELEGRAM_URL,
                )
            },
        )
    }

    when (dialog) {
        AboutDialog.About -> AboutAppDialog(onDismiss = { dialog = null })
        AboutDialog.PackageType -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_package_type)) },
            text = { Text(stringResource(R.string.package_type_explainer)) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.ok)) }
            },
        )
        AboutDialog.License -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_open_source_licenses)) },
            text = {
                Text(
                    text = LICENSE_NOTICE,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        // Not under Firebase Test Lab.
                        if (Settings.System.getString(context.contentResolver, "firebase.test.lab") != "true") {
                            onNavigate(OssLicensesKey())
                        }
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
        )
        null -> {}
    }
}

/** The app's mark, name, version and licence in a card, dismissed by tapping outside. */
@Composable
private fun AboutAppDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.opensource_info, COPYRIGHT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}
