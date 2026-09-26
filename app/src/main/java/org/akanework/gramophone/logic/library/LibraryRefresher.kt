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

package org.akanework.gramophone.logic.library

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.reader.FlowReader

/**
 * Reloads the library: optionally asks MediaStore to pick up changed files first, then re-reads
 * it. Runs on the application scope so a refresh finishes even if the screen that asked goes away.
 *
 * Refreshes are not serialized: two calls run side by side, as they always have.
 */
class LibraryRefresher(
    context: Context,
    private val reader: FlowReader,
    private val scope: CoroutineScope,
) {
    private val context = context.applicationContext

    /**
     * Refreshes the library, smart-scanning first if [smartScanFirst], then calls [onDone] on the
     * main thread. [onDone] outlives the caller's screen, so it should only touch things that
     * tolerate that.
     */
    fun refresh(
        smartScanFirst: Boolean = hasScopedStorageV2(),
        onDone: (() -> Unit)? = null,
    ) {
        scope.launch {
            if (smartScanFirst) MediaStoreCompat.smartScan(context)
            reader.refresh()
            if (onDone != null) withContext(Dispatchers.Main) { onDone() }
        }
    }
}
