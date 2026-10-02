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

package org.akanework.gramophone

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Looper
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.settings.LibraryFilterSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LibraryFilterSettingsTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    // Runs the settings' start-up work synchronously inside the constructor.
    private val directScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.defaultPrefs
        prefs.edit(commit = true) { clear() }
    }

    private fun newSettings() = LibraryFilterSettings(context, directScope)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun <T> SharedFlow<T>.latest(): T = replayCache.last()

    @Test
    fun emitsPrefValuesAfterInit() {
        prefs.edit(commit = true) {
            putBoolean("needToAdd_isMusicBlacklist", false)
            putInt("mediastore_filter", 42)
            putStringSet("folderFilter", setOf("Music/Ignored"))
            putStringSet("folderAllow", setOf("Music/Allowed"))
        }
        val settings = newSettings()
        assertEquals(42L, settings.minSongLengthSecondsFlow.latest())
        assertEquals(setOf("Music/Ignored"), settings.blackListSetFlow.latest())
        assertEquals(setOf("Music/Allowed"), settings.whiteListSetFlow.latest())
    }

    @Test
    fun recentlyAddedCoversTwoWeeks() {
        assertEquals(1_209_600L, LibraryFilterSettings.RECENTLY_ADDED_WINDOW_SECONDS)
    }

    @Test
    fun minSongLengthDefaultsToResourceValue() {
        val settings = newSettings()
        val expected = context.resources.getInteger(R.integer.filter_default_sec).toLong()
        assertEquals(expected, settings.minSongLengthSecondsFlow.latest())
    }

    @Test
    fun blacklistFollowsFolderFilterAndFallsBackToExtraDisallowedFolders() {
        val settings = newSettings()
        assertEquals(settings.extraDisallowedFolders, settings.blackListSetFlow.latest())
        assertEquals(emptySet<String>(), settings.whiteListSetFlow.latest())

        prefs.edit(commit = true) { putStringSet("folderFilter", setOf("A", "B")) }
        idle()
        assertEquals(setOf("A", "B"), settings.blackListSetFlow.latest())

        prefs.edit(commit = true) { putInt("mediastore_filter", 17) }
        idle()
        assertEquals(17L, settings.minSongLengthSecondsFlow.latest())

        prefs.edit(commit = true) { remove("folderFilter") }
        idle()
        assertEquals(settings.extraDisallowedFolders, settings.blackListSetFlow.latest())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun albumCoversPrefAppliesWithoutScopedStorage() {
        prefs.edit(commit = true) { putBoolean("album_covers", false) }
        val settings = newSettings()
        assertEquals(false, settings.shouldUseEnhancedCoverReadingFlow.latest())

        prefs.edit(commit = true) { putBoolean("album_covers", true) }
        idle()
        assertEquals(true, settings.shouldUseEnhancedCoverReadingFlow.latest())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun albumCoversPrefIgnoredWithScopedStorage() {
        prefs.edit(commit = true) { putBoolean("album_covers", false) }
        val settings = newSettings()
        assertNull(settings.shouldUseEnhancedCoverReadingFlow.latest())

        prefs.edit(commit = true) { putBoolean("album_covers", true) }
        idle()
        assertNull(settings.shouldUseEnhancedCoverReadingFlow.latest())
    }

    @Test
    fun migrationRunsOnce() {
        prefs.edit(commit = true) {
            putStringSet("folderFilter", setOf("Custom"))
            putInt("mediastore_filter", 60)
        }
        val settings = newSettings()
        val defaultSec = context.resources.getInteger(R.integer.filter_default_sec)
        assertFalse(prefs.getBoolean("needToAdd_isMusicBlacklist", true))
        assertEquals(
            setOf("Custom") + settings.extraDisallowedFolders,
            prefs.getStringSet("folderFilter", null)
        )
        assertEquals(defaultSec, prefs.getInt("mediastore_filter", -1))
        assertEquals(defaultSec.toLong(), settings.minSongLengthSecondsFlow.latest())

        // A second start must not migrate again.
        prefs.edit(commit = true) {
            putStringSet("folderFilter", setOf("Custom"))
            putInt("mediastore_filter", 60)
        }
        newSettings()
        assertEquals(setOf("Custom"), prefs.getStringSet("folderFilter", null))
        assertEquals(60, prefs.getInt("mediastore_filter", -1))
    }

    @Test
    fun migrationDoesNotCreateFolderFilterWhenUnset() {
        newSettings()
        assertFalse(prefs.contains("folderFilter"))
        assertFalse(prefs.getBoolean("needToAdd_isMusicBlacklist", true))
    }
}
