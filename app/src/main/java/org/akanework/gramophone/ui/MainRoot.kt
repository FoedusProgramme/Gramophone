/*
 *     Copyright (C) 2026 The Gramophone authors
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.AppUiEvent
import org.akanework.gramophone.logic.AppUiEvents
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.ui.components.compose.AppDialogHostState
import org.akanework.gramophone.ui.components.compose.DrawWarmUp
import org.akanework.gramophone.ui.components.compose.LibraryGate
import org.akanework.gramophone.ui.components.compose.MediaConsentHost
import org.akanework.gramophone.ui.components.player.rememberPlayerSheetController
import org.akanework.gramophone.ui.nav.AppRoot
import org.akanework.gramophone.ui.nav.LocalReportFullyDrawn
import org.akanework.gramophone.ui.nav.warmUpNavAxisEasing
import org.akanework.gramophone.ui.screens.settings.MainSettingsScreen
import org.akanework.gramophone.ui.theme.GramophoneTheme
import org.koin.compose.koinInject

/**
 * The root composition of [MainActivity]: library permission gate, MediaStore consent host,
 * [AppUiEvents], player sheet and navigation. [startSplashTimeout] and [reportFullyDrawn] drive
 * the activity's splash screen; [onLibraryPermissionDenied] leaves the app when audio access is
 * refused.
 */
@Composable
internal fun MainRoot(
    onLibraryPermissionDenied: () -> Unit,
    startSplashTimeout: () -> Unit,
    reportFullyDrawn: () -> Unit,
) {
    LaunchedEffect(Unit) { withContext(Dispatchers.Default) { warmUpNavAxisEasing() } }
    GramophoneTheme {
        val dialogs = remember { AppDialogHostState() }
        MediaConsentHost()
        AppUiEventsHost(dialogs)
        LibraryGate(
            onDenied = onLibraryPermissionDenied,
            startSplashTimeout = startSplashTimeout,
        )
        val libraryWrites = koinInject<LibraryWriteRepository>()
        val playerSheet = rememberPlayerSheetController(
            toggleFavorite = libraryWrites::markFavorite
        )
        // Under the pages, which hide it
        DrawWarmUp { MainSettingsScreen(onBack = {}, onNavigate = {}) }
        CompositionLocalProvider(LocalReportFullyDrawn provides reportFullyDrawn) {
            AppRoot(
                playerSheet = playerSheet,
                dialogs = dialogs,
                debug = BuildConfig.DEBUG,
            )
        }
    }
}

/**
 * Shows what work on the application scope reports, on this activity's [dialogs]. Takes events
 * only while the activity is resumed: another MainActivity instance, stopped in its own task,
 * must not take one nobody would see.
 */
@Composable
private fun AppUiEventsHost(dialogs: AppDialogHostState) {
    // Read when an event comes, so the strings follow a configuration change
    val resources by rememberUpdatedState(LocalResources.current)
    val events = koinInject<AppUiEvents>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(events, dialogs, lifecycle) {
        val shown = this
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val event = events.next()
                // Shown outside the resumed window: the event is already taken, so a pause while
                // the snackbar is up (Recents, a consent dialog) must not take it down for good
                shown.launch {
                    when (event) {
                        is AppUiEvent.LibraryRefreshed -> dialogs.snackbar(
                            resources.getString(R.string.refreshed_songs, event.songCount),
                            resources.getString(R.string.dismiss),
                        )
                    }
                }
            }
        }
    }
}
