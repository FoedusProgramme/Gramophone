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

package org.akanework.gramophone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.ui.components.compose.AppDialogHostState
import org.akanework.gramophone.ui.components.compose.LibraryGate
import org.akanework.gramophone.ui.components.compose.MediaConsentHost
import org.akanework.gramophone.ui.components.player.rememberPlayerSheetController
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.AppRoot
import org.akanework.gramophone.ui.nav.LocalReportFullyDrawn
import org.akanework.gramophone.ui.nav.warmUpNavAxisEasing
import org.koin.compose.koinInject

/**
 * The root composition of [MainActivity]: library permission gate, MediaStore consent host,
 * player sheet and navigation. [startSplashTimeout] and [reportFullyDrawn] drive the activity's
 * splash screen; [onLibraryPermissionDenied] leaves the app when audio access is refused.
 */
@Composable
internal fun MainRoot(
    backStack: SnapshotStateList<AppNavKey>,
    onLibraryPermissionDenied: () -> Unit,
    startSplashTimeout: () -> Unit,
    reportFullyDrawn: () -> Unit,
) {
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { warmUpNavAxisEasing() } }
    GramophoneTheme {
        val dialogs = remember { AppDialogHostState() }
        MediaConsentHost()
        LibraryGate(
            onDenied = onLibraryPermissionDenied,
            startSplashTimeout = startSplashTimeout,
        )
        val libraryWrites = koinInject<LibraryWriteRepository>()
        val playerSheet = rememberPlayerSheetController(
            toggleFavorite = libraryWrites::markFavorite
        )
        CompositionLocalProvider(LocalReportFullyDrawn provides reportFullyDrawn) {
            AppRoot(
                backStack = backStack,
                playerSheet = playerSheet,
                dialogs = dialogs,
                debug = BuildConfig.DEBUG,
            )
        }
    }
}
