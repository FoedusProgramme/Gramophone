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
package org.akanework.gramophone.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import org.akanework.gramophone.logic.ui.BaseActivity
import org.akanework.gramophone.ui.components.compose.LibraryGate
import org.akanework.gramophone.ui.screens.PickerEntry
import org.akanework.gramophone.ui.screens.PickerScreen
import org.akanework.gramophone.ui.theme.GramophoneTheme
import org.koin.android.ext.android.inject
import uk.akane.libphonograph.reader.FlowReader

/**
 * The activities other apps call to pick a song or a playlist: the library's list, each row
 * returning its item as the result.
 */
abstract class PickerActivity<T : Any> : BaseActivity() {
    protected val reader: FlowReader by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GramophoneTheme {
                // A picker shows no splash and doesn't smart-scan before its first load.
                LibraryGate(smartScanFirst = false, onDenied = ::onLibraryPermissionDenied)
                val items by remember { itemsFlow() }.collectAsState(emptyList())
                val entries = remember(items) {
                    items.map { entryOf(it) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                }
                PickerScreen(
                    title = getTitleStr(),
                    entries = entries,
                    onPick = { onSelected(it) },
                    onBack = { finish() },
                )
            }
        }
    }

    protected abstract fun itemsFlow(): Flow<List<T>>
    protected abstract fun entryOf(item: T): PickerEntry<T>
    protected abstract fun getTitleStr(): String
    protected abstract fun onSelected(item: T)
}
