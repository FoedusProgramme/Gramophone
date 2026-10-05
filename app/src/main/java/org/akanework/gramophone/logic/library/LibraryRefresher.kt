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
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.AppUiEvent
import org.akanework.gramophone.logic.AppUiEvents
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.utils.SdScanner
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.reader.FlowReader

/**
 * Reloads the library: optionally asks MediaStore to pick up changed files first, then re-reads
 * it. Runs on the application scope so a refresh finishes even if the screen that asked goes away,
 * and tells the user through [AppUiEvents] and toasts on the application context.
 *
 * Refreshes are not serialized: two calls run side by side.
 */
class LibraryRefresher(
    context: Context,
    private val reader: FlowReader,
    private val scope: CoroutineScope,
    private val uiEvents: AppUiEvents,
) {
    private val context = context.applicationContext

    /**
     * Refreshes the library, smart-scanning first if [smartScanFirst]. Then, if [reportToUser],
     * posts [AppUiEvent.LibraryRefreshed], and calls [onDone] on the main thread. [onDone]
     * outlives the caller's screen, so it must not hold on to it (its context, dialogs or
     * composition).
     */
    fun refresh(
        smartScanFirst: Boolean = hasScopedStorageV2(),
        reportToUser: Boolean = false,
        onDone: (() -> Unit)? = null,
    ) {
        scope.launch {
            if (smartScanFirst) MediaStoreCompat.smartScan(context)
            reader.refresh()
            if (reportToUser) {
                uiEvents.post(AppUiEvent.LibraryRefreshed(reader.songListFlow.first().size))
            }
            if (onDone != null) withContext(Dispatchers.Main) { onDone() }
        }
    }

    /**
     * Has MediaStore rescan every mounted volume, toasting the progress every
     * [PROGRESS_INTERVAL_MS]. On R and later, a finished scan is followed by a [refresh] that is
     * reported to the user; before R, nothing follows it, and the reader picks up MediaStore's
     * changes by itself.
     */
    fun fullRescan() {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SdScanner.scanEverything(context, PROGRESS_INTERVAL_MS.toInt()) { progress ->
                    if (progress.step != SdScanner.SimpleProgress.Step.DONE) {
                        toast(if (progress.percentage == null) {
                            context.getString(R.string.refreshing_wait)
                        } else {
                            context.getString(
                                R.string.still_refreshing,
                                progress.step.ordinal,
                                SdScanner.SimpleProgress.Step.DONE.ordinal - 1,
                                "${progress.percentage}%"
                            )
                        })
                    } else {
                        refresh(smartScanFirst = false, reportToUser = true)
                    }
                }
            } else {
                val scan = launch(Dispatchers.IO) { MediaStoreCompat.scanEverything(context) }
                while (!scan.isCompleted) {
                    delay(PROGRESS_INTERVAL_MS)
                    toast(context.getString(R.string.refreshing_wait))
                }
            }
        }
    }

    /** A short toast on the application context; callable from any thread. */
    private fun toast(text: String) {
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 5000L
    }
}
