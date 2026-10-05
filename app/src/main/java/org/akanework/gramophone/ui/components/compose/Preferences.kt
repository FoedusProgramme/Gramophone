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

package org.akanework.gramophone.ui.components.compose

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getIntStrict
import org.akanework.gramophone.logic.getStringStrict

@Composable
fun rememberDefaultPreferences(): SharedPreferences {
    val context = LocalContext.current
    return remember(context) { context.defaultPrefs }
}

/**
 * Observes one key of the default SharedPreferences as Compose state. [read] runs on the main
 * thread on every change of [key] (or on a full clear).
 */
@Composable
fun <T> rememberPreference(key: String, read: (SharedPreferences) -> T): State<T> {
    val prefs = rememberDefaultPreferences()
    val currentRead by rememberUpdatedState(read)
    val state = remember(prefs, key) { mutableStateOf(read(prefs)) }
    DisposableEffect(prefs, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, k ->
            if (k == null || k == key) state.value = currentRead(p)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

/**
 * One preference as state that writes through: [value] follows the stored one (whoever changed
 * it) and [set] stores a new one, which comes back through the change listener before the next
 * frame.
 */
@Stable
class PreferenceHandle<T>(private val state: State<T>, private val write: (T) -> Unit) {
    val value: T get() = state.value
    fun set(value: T) = write(value)
}

@Composable
fun rememberBooleanPreference(key: String, default: Boolean): PreferenceHandle<Boolean> {
    val prefs = rememberDefaultPreferences()
    val state = rememberPreference(key) { it.getBooleanStrict(key, default) }
    return remember(prefs, key) { PreferenceHandle(state) { v -> prefs.edit { putBoolean(key, v) } } }
}

@Composable
fun rememberIntPreference(key: String, default: Int): PreferenceHandle<Int> {
    val prefs = rememberDefaultPreferences()
    val state = rememberPreference(key) { it.getIntStrict(key, default) }
    return remember(prefs, key) { PreferenceHandle(state) { v -> prefs.edit { putInt(key, v) } } }
}

@Composable
fun rememberStringPreference(key: String, default: String): PreferenceHandle<String> {
    val prefs = rememberDefaultPreferences()
    val state = rememberPreference(key) { it.getStringStrict(key, default) ?: default }
    return remember(prefs, key) { PreferenceHandle(state) { v -> prefs.edit { putString(key, v) } } }
}

@Composable
fun rememberStringSetPreference(key: String): PreferenceHandle<Set<String>> {
    val prefs = rememberDefaultPreferences()
    val state = rememberPreference(key) { it.getStringSet(key, null)?.toSet() ?: emptySet() }
    return remember(prefs, key) { PreferenceHandle(state) { v -> prefs.edit { putStringSet(key, v) } } }
}

/** Emits the current value of [key] and again whenever it changes. */
fun SharedPreferences.booleanFlow(key: String, default: Boolean): Flow<Boolean> = callbackFlow {
    trySend(getBoolean(key, default))
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, k ->
        if (k == null || k == key) trySend(p.getBoolean(key, default))
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.conflate()
