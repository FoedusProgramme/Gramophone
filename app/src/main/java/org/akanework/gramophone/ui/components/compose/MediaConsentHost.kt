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

package org.akanework.gramophone.ui.components.compose

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.util.Log
import kotlinx.coroutines.flow.first
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.logic.library.MediaConsentRequester
import org.akanework.gramophone.logic.library.PendingWrite
import org.koin.compose.koinInject

private const val TAG = "MediaConsentHost"

/**
 * Shows the MediaStore consent dialogs queued on [MediaConsentRequester], one at a time, and hands
 * each result to [LibraryWriteRepository]. Must sit at an always-composed spot of the root: the
 * pending write is saved with the composition so the result is still handled after the activity
 * or the process was recreated while the system dialog was up.
 */
@Composable
fun MediaConsentHost() {
    val requester = koinInject<MediaConsentRequester>()
    val repository = koinInject<LibraryWriteRepository>()
    var pending by rememberSaveable { mutableStateOf<PendingWrite?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val write = pending
        pending = null
        repository.onConsentResult(write, result.resultCode, result.data)
    }
    // Runs after the launcher is registered, so launch() below cannot hit an unregistered one.
    LaunchedEffect(requester) {
        while (true) {
            // A write restored from saved state is still waiting for its result.
            snapshotFlow { pending }.first { it == null }
            val request = requester.next()
            pending = request.payload
            try {
                launcher.launch(IntentSenderRequest.Builder(request.sender).build())
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "error launching consent dialog", e)
                pending = null
                repository.onConsentResult(request.payload, Activity.RESULT_FIRST_USER, null)
            }
        }
    }
}
