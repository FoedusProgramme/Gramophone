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

package org.akanework.gramophone.ui.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.ui.HomeTab
import uk.akane.libphonograph.reader.FlowReader

/** Activity-scoped holder of the home tab states, so they outlive the home screen composition. */
class HomeViewModel(
    application: Application,
    private val reader: FlowReader,
) : AndroidViewModel(application) {
    private val prefs = application.defaultPrefs
    private val states = HashMap<HomeTab, LibraryTabState<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> tabState(spec: LibraryTabSpec<T>): LibraryTabState<T> =
        states.getOrPut(spec.tab) {
            LibraryTabState(spec, prefs, reader, viewModelScope)
        } as LibraryTabState<T>

    private val folderStates = HashMap<Boolean, FolderTabState>()

    fun folderState(isDetailed: Boolean): FolderTabState =
        folderStates.getOrPut(isDetailed) {
            FolderTabState(isDetailed, prefs, reader, viewModelScope)
        }
}
