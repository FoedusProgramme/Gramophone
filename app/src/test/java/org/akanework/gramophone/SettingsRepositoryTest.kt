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

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Looper
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.settings.SettingsRepository
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

// Plain Application: the repository is constructed directly, without the Koin graph.
@Config(application = Application::class)
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    // Runs the repository's start-up work synchronously inside the constructor.
    private val directScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.defaultPrefs
        prefs.edit(commit = true) { clear() }
    }

    private fun newRepository() = SettingsRepository(context, directScope)

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
        val repo = newRepository()
        assertEquals(42L, repo.minSongLengthSecondsFlow.latest())
        assertEquals(setOf("Music/Ignored"), repo.blackListSetFlow.latest())
        assertEquals(setOf("Music/Allowed"), repo.whiteListSetFlow.latest())
        assertEquals(1_209_600L, repo.recentlyAddedFilterSecondFlow.value)
    }

    @Test
    fun minSongLengthDefaultsToResourceValue() {
        val repo = newRepository()
        val expected = context.resources.getInteger(R.integer.filter_default_sec).toLong()
        assertEquals(expected, repo.minSongLengthSecondsFlow.latest())
    }

    @Test
    fun blacklistFollowsFolderFilterAndFallsBackToExtraDisallowedFolders() {
        val repo = newRepository()
        assertEquals(repo.extraDisallowedFolders, repo.blackListSetFlow.latest())
        assertEquals(emptySet<String>(), repo.whiteListSetFlow.latest())

        prefs.edit(commit = true) { putStringSet("folderFilter", setOf("A", "B")) }
        idle()
        assertEquals(setOf("A", "B"), repo.blackListSetFlow.latest())

        prefs.edit(commit = true) { putInt("mediastore_filter", 17) }
        idle()
        assertEquals(17L, repo.minSongLengthSecondsFlow.latest())

        prefs.edit(commit = true) { remove("folderFilter") }
        idle()
        assertEquals(repo.extraDisallowedFolders, repo.blackListSetFlow.latest())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S_V2])
    fun albumCoversPrefAppliesWithoutScopedStorage() {
        prefs.edit(commit = true) { putBoolean("album_covers", false) }
        val repo = newRepository()
        assertEquals(false, repo.shouldUseEnhancedCoverReadingFlow.latest())

        prefs.edit(commit = true) { putBoolean("album_covers", true) }
        idle()
        assertEquals(true, repo.shouldUseEnhancedCoverReadingFlow.latest())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun albumCoversPrefIgnoredWithScopedStorage() {
        prefs.edit(commit = true) { putBoolean("album_covers", false) }
        val repo = newRepository()
        assertNull(repo.shouldUseEnhancedCoverReadingFlow.latest())

        prefs.edit(commit = true) { putBoolean("album_covers", true) }
        idle()
        assertNull(repo.shouldUseEnhancedCoverReadingFlow.latest())
    }

    @Test
    fun migrationRunsOnce() {
        prefs.edit(commit = true) {
            putStringSet("folderFilter", setOf("Custom"))
            putInt("mediastore_filter", 60)
        }
        val repo = newRepository()
        val defaultSec = context.resources.getInteger(R.integer.filter_default_sec)
        assertFalse(prefs.getBoolean("needToAdd_isMusicBlacklist", true))
        assertEquals(setOf("Custom") + repo.extraDisallowedFolders, prefs.getStringSet("folderFilter", null))
        assertEquals(defaultSec, prefs.getInt("mediastore_filter", -1))
        assertEquals(defaultSec.toLong(), repo.minSongLengthSecondsFlow.latest())

        // A second start must not migrate again.
        prefs.edit(commit = true) {
            putStringSet("folderFilter", setOf("Custom"))
            putInt("mediastore_filter", 60)
        }
        newRepository()
        assertEquals(setOf("Custom"), prefs.getStringSet("folderFilter", null))
        assertEquals(60, prefs.getInt("mediastore_filter", -1))
    }

    @Test
    fun migrationDoesNotCreateFolderFilterWhenUnset() {
        newRepository()
        assertFalse(prefs.contains("folderFilter"))
        assertFalse(prefs.getBoolean("needToAdd_isMusicBlacklist", true))
    }
}
