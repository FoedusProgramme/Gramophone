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

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.library.LibraryReadiness
import org.akanework.gramophone.logic.library.LibraryRefresher
import org.koin.compose.koinInject
import uk.akane.libphonograph.reader.FlowReader

/** The permissions to ask for so the library can be read on API level [sdkInt]. */
internal fun requiredLibraryPermissions(sdkInt: Int): Array<String> = when {
    hasScopedStorageWithMediaTypes(sdkInt) -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    hasScopedStorageV2(sdkInt) -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    else -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
}

/**
 * Asks for audio permission if needed, then runs the first library scan of the process unless one
 * already happened, and marks [LibraryReadiness] ready. Must sit at an always-composed spot of the
 * root.
 *
 * Permission is asked at most once per screen (kept across recreation); a denial calls [onDenied]
 * and is never re-asked automatically. The grant is checked again on every resume, so coming back
 * from the app's settings with permission granted goes on.
 *
 * @param smartScanFirst passed to [LibraryRefresher.refresh] for the first scan.
 * @param startSplashTimeout called right before the first scan starts, to bound how long a splash
 *   screen waits for it.
 */
@Composable
fun LibraryGate(
    smartScanFirst: Boolean = hasScopedStorageV2(),
    onDenied: () -> Unit,
    startSplashTimeout: () -> Unit = {},
) {
    val context = LocalContext.current
    val reader = koinInject<FlowReader>()
    val refresher = koinInject<LibraryRefresher>()
    val readiness = koinInject<LibraryReadiness>()
    val currentOnDenied by rememberUpdatedState(onDenied)
    val currentStartSplashTimeout by rememberUpdatedState(startSplashTimeout)
    // Saved so a recreated screen doesn't ask again while the system dialog is still up; its
    // result is delivered to the launcher below.
    var requested by rememberSaveable { mutableStateOf(false) }
    // Not saved: a recreated screen goes through this again (marking ready is idempotent).
    var proceeded by remember { mutableStateOf(false) }

    val proceed = remember(reader, refresher, readiness, smartScanFirst) {
        {
            if (!proceeded) {
                proceeded = true
                if (!reader.hadFirstRefresh) {
                    currentStartSplashTimeout()
                    refresher.refresh(smartScanFirst) { readiness.markReady() }
                } else {
                    readiness.markReady()
                }
            }
        }
    }
    val permissions = remember { requiredLibraryPermissions(Build.VERSION.SDK_INT) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Like the old onRequestPermissionsResult: the first permission decides.
        if (result[permissions[0]] == true) proceed() else currentOnDenied()
    }
    // Runs after the launcher is registered, so launch() below cannot hit an unregistered one.
    LaunchedEffect(Unit) {
        if (context.hasAudioPermission()) {
            proceed()
        } else if (!requested) {
            requested = true
            launcher.launch(permissions)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!proceeded && context.hasAudioPermission()) proceed()
    }
}
