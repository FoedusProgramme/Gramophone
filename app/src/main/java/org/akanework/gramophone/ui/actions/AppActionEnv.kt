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

package org.akanework.gramophone.ui.actions

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.media3.session.MediaBrowser
import kotlinx.coroutines.CoroutineScope
import org.akanework.gramophone.logic.ApplicationScope
import org.akanework.gramophone.logic.library.LibraryRefresher
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.compose.AppDialogHostState
import org.akanework.gramophone.ui.components.compose.LocalAppDialogs
import org.akanework.gramophone.ui.components.player.LocalPlayerSheet
import org.akanework.gramophone.ui.components.player.PlayerSheetHandle
import org.akanework.gramophone.ui.nav.AppNavKey
import org.akanework.gramophone.ui.nav.NavViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel
import uk.akane.libphonograph.reader.FlowReader

/**
 * What the item, header and toolbar actions need from the screen they run on. Built by
 * [rememberAppActionEnv] where the actions are invoked; never stored in the list specs.
 */
class AppActionEnv(
    /** The screen's context: starts activities and share sheets, shows toasts, reads strings. */
    val context: Context,
    private val controller: MediaControllerViewModel,
    val navigate: (AppNavKey) -> Unit,
    val dialogs: AppDialogHostState,
    val playerSheet: PlayerSheetHandle,
    val writes: LibraryWriteRepository,
    val reader: FlowReader,
    val refresher: LibraryRefresher,
    /** Process-wide scope, for work that must outlive the screen. */
    val appScope: ApplicationScope,
    /** Scope of the calling composition, for UI work that may stop with it. */
    val scope: CoroutineScope,
) {
    /** The media controller, or null while it is not connected. */
    val player: MediaBrowser? get() = controller.get()

    fun getString(@StringRes id: Int): String = context.getString(id)
    fun getString(@StringRes id: Int, vararg args: Any?): String = context.getString(id, *args)
}

@Composable
fun rememberAppActionEnv(): AppActionEnv {
    val context = LocalContext.current
    val controller = koinActivityViewModel<MediaControllerViewModel>()
    val navViewModel = koinActivityViewModel<NavViewModel>()
    val dialogs = LocalAppDialogs.current
    val playerSheet = LocalPlayerSheet.current
    val writes = koinInject<LibraryWriteRepository>()
    val reader = koinInject<FlowReader>()
    val refresher = koinInject<LibraryRefresher>()
    val appScope = koinInject<ApplicationScope>()
    val scope = rememberCoroutineScope()
    return remember(
        context, controller, navViewModel, dialogs, playerSheet, writes, reader, refresher,
        appScope, scope,
    ) {
        AppActionEnv(
            context, controller, navViewModel::navigateTo, dialogs, playerSheet, writes, reader,
            refresher, appScope, scope,
        )
    }
}
