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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.settings.LibraryFilterSettings
import org.akanework.gramophone.ui.components.compose.rememberDefaultPreferences
import org.akanework.gramophone.ui.components.home.LabelTabRow
import org.akanework.gramophone.ui.components.settings.CheckboxPreferenceRow
import org.akanework.gramophone.ui.components.settings.InfoPreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.koin.compose.koinInject
import uk.akane.libphonograph.reader.FlowReader

/**
 * The folder filters: which folders the library leaves out (the blacklist), and, on its own tab,
 * which ones it is limited to (the whitelist). Each is a set of paths in the preferences, which
 * LibraryFilterSettings turns into the flows the library reader filters by.
 *
 * Shows and edits the sets of those flows rather than the raw preferences: until the user sets a
 * blacklist, the library leaves out the default folders, so they show checked and stay left out
 * when another folder is checked.
 */
@Composable
fun BlacklistScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val reader = koinInject<FlowReader>()
    val filterSettings = koinInject<LibraryFilterSettings>()
    val prefs = rememberDefaultPreferences()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val isWhitelist = selectedTab == 1
    // Null only until the settings first load, right after the process started.
    val blacklist by filterSettings.blackListSetFlow
        .collectAsState(filterSettings.blackListSetFlow.replayCache.lastOrNull())
    val whitelist by filterSettings.whiteListSetFlow
        .collectAsState(filterSettings.whiteListSetFlow.replayCache.lastOrNull())
    val filter = if (isWhitelist) whitelist else blacklist
    val filterKey = if (isWhitelist) "folderAllow" else "folderFilter"
    val folders by remember(reader, isWhitelist) {
        if (isWhitelist) reader.foldersForWhitelistFlow else reader.foldersFlow
    }.collectAsState(emptySet())
    val sortedFolders = remember(folders) { folders.sorted() }

    PreferenceScreen(title = stringResource(R.string.settings_blacklist), onBack = onBack, modifier = modifier) {
        LabelTabRow(
            labels = listOf(
                stringResource(R.string.settings_blacklist),
                stringResource(R.string.settings_whitelist),
            ),
            selectedTab = selectedTab,
            offsetFraction = { 0f },
            onTabClick = { selectedTab = it },
        )
        Spacer(Modifier.height(8.dp))
        if (isWhitelist) {
            PreferenceGroup({ shape ->
                InfoPreferenceRow(shape, stringResource(R.string.whitelist_and_blacklist))
            })
            Spacer(Modifier.height(16.dp))
        }
        PreferenceGroup(sortedFolders) { folder, shape ->
            CheckboxPreferenceRow(
                shape,
                title = folder,
                checked = folder in filter.orEmpty(),
                onCheckedChange = { checked ->
                    filter?.let { current ->
                        prefs.edit {
                            putStringSet(filterKey, if (checked) current + folder else current - folder)
                        }
                    }
                },
            )
        }
    }
}
