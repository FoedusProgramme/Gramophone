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

package org.akanework.gramophone.logic.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes

/**
 * Owns the preferences that drive the media library filters and turns them into the flows
 * [uk.akane.libphonograph.reader.FlowReader] consumes. Runs the one-time preference migration and
 * the initial load on [scope], then follows preference changes.
 */
class SettingsRepository(
    private val context: Context,
    scope: CoroutineScope,
) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val prefs = context.defaultPrefs

    private val _minSongLengthSecondsFlow = MutableSharedFlow<Long>(replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _blackListSetFlow = MutableSharedFlow<Set<String>>(replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _whiteListSetFlow = MutableSharedFlow<Set<String>>(replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    // With scoped storage the album_covers preference has no effect: null means "load if
    // permission is granted".
    private val _shouldUseEnhancedCoverReadingFlow: MutableSharedFlow<Boolean?> =
        if (hasScopedStorageWithMediaTypes()) MutableStateFlow(null) else
            MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val minSongLengthSecondsFlow: SharedFlow<Long> = _minSongLengthSecondsFlow
    val blackListSetFlow: SharedFlow<Set<String>> = _blackListSetFlow
    val whiteListSetFlow: SharedFlow<Set<String>> = _whiteListSetFlow
    val shouldUseEnhancedCoverReadingFlow: SharedFlow<Boolean?> = _shouldUseEnhancedCoverReadingFlow
    val recentlyAddedFilterSecondFlow: StateFlow<Long> = MutableStateFlow(1_209_600L)

    val extraDisallowedFolders = setOf(
        Environment.DIRECTORY_RINGTONES,
        Environment.DIRECTORY_NOTIFICATIONS,
        Environment.DIRECTORY_ALARMS,
        Environment.DIRECTORY_PODCASTS,
        "Android/media",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Environment.DIRECTORY_AUDIOBOOKS else "Audiobooks",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Environment.DIRECTORY_RECORDINGS else "Recordings"
    )

    init {
        // Off the main thread: first access reads the preference file from disk.
        scope.launch {
            migrateIfNeeded()
            onSharedPreferenceChanged(prefs, null) // reload all values
            // SharedPreferences holds listeners weakly; this singleton is the strong reference.
            prefs.registerOnSharedPreferenceChangeListener(this@SettingsRepository)
        }
    }

    private fun migrateIfNeeded() {
        if (prefs.getBoolean("needToAdd_isMusicBlacklist", true)) {
            prefs.edit(true) {
                putBoolean("needToAdd_isMusicBlacklist", false)
                if (prefs.contains("folderFilter")) {
                    putStringSet(
                        "folderFilter", (prefs.getStringSet(
                            "folderFilter", setOf()
                        ) ?: setOf()) + extraDisallowedFolders
                    )
                }
                if (prefs.getInt("mediastore_filter", 0) == 60) {
                    putInt("mediastore_filter",
                        context.resources.getInteger(R.integer.filter_default_sec))
                }
            }
        }
    }

    // Every flow has replay = 1 with DROP_OLDEST, so tryEmit never fails.
    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == null || key == "mediastore_filter") {
            _minSongLengthSecondsFlow.tryEmit(
                prefs.getInt(
                    "mediastore_filter",
                    context.resources.getInteger(R.integer.filter_default_sec)
                ).toLong()
            )
        }
        if (key == null || key == "folderFilter") {
            _blackListSetFlow.tryEmit(prefs.getStringSet("folderFilter",
                extraDisallowedFolders) ?: extraDisallowedFolders)
        }
        if (key == null || key == "folderAllow") {
            _whiteListSetFlow.tryEmit(prefs.getStringSet("folderAllow", setOf()) ?: setOf())
        }
        if ((key == null || key == "album_covers") && !hasScopedStorageWithMediaTypes()) {
            _shouldUseEnhancedCoverReadingFlow.tryEmit(prefs.getBoolean("album_covers", true))
        }
    }
}
