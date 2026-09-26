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
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.logic.library.DeleteResult
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.logic.library.LibraryWrites
import org.akanework.gramophone.logic.library.MediaConsentRequester
import org.akanework.gramophone.logic.library.PendingWrite
import org.akanework.gramophone.ui.intent.DefaultPlayIntentExecutor
import org.akanework.gramophone.ui.intent.PlayIntentAction
import org.akanework.gramophone.ui.intent.PlayIntentHost
import org.akanework.gramophone.ui.nav.AppNavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.lang.reflect.Proxy

/**
 * How [DefaultPlayIntentExecutor] resolves the ids it is handed: a bare MediaStore id (audio
 * preview) or the library's "MediaStore:<id>" (search suggestions), and what happens when the id
 * isn't in the library. The controller is a recording proxy and the library a fixed id map.
 */
@Config(application = Application::class)
@RunWith(RobolectricTestRunner::class)
class DefaultPlayIntentExecutorTest {

    /** One recorded [Player] call: method name and arguments. */
    private data class Call(val name: String, val args: List<Any?>)

    private class FakeHost : PlayIntentHost {
        val calls = mutableListOf<Call>()
        var controllerRequests = 0

        private val player = Proxy.newProxyInstance(
            Player::class.java.classLoader, arrayOf(Player::class.java)
        ) { _, method, args ->
            calls += Call(method.name, args?.toList() ?: emptyList())
            null // every method used here returns void
        } as Player

        override suspend fun awaitController(): Player {
            controllerRequests++
            return player
        }

        override fun navigateTo(key: AppNavKey) = error("unused")
    }

    private class FakeWrites : LibraryWrites {
        override suspend fun favoritesUri(): Uri? = null
        override suspend fun consentFor(write: PendingWrite): IntentSender? = null
        override suspend fun perform(write: PendingWrite) {}
        override suspend fun reportFailure(write: PendingWrite, resultCode: Int, data: Intent?) {}
        override suspend fun createPlaylist(file: File) {}
        override suspend fun deleteSongs(list: List<Pair<File, Long>>): DeleteResult = error("unused")
        override suspend fun deletePlaylist(id: Long): DeleteResult = error("unused")
    }

    private val song = MediaItem.Builder().setMediaId("42").build()
    private val writes = FakeWrites()
    private val host = FakeHost()
    private val executor = DefaultPlayIntentExecutor(
        RuntimeEnvironment.getApplication(),
        flowOf(mapOf(42L to song)),
        LibraryWriteRepository(CoroutineScope(Dispatchers.Unconfined), MediaConsentRequester(), writes),
    )

    private fun execute(action: PlayIntentAction) = runBlocking { executor.execute(action, host) }

    private fun callNames() = host.calls.map { it.name }

    @Test
    fun playByIdFoundPlaysFromPosition() {
        execute(PlayIntentAction.PlayById("42", 1234L))

        assertEquals(listOf("setMediaItem", "prepare", "play"), callNames())
        assertSame(song, host.calls[0].args[0])
        assertEquals(1234L, host.calls[0].args[1])
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun playByIdFromSuggestionResolvesMediaStorePrefix() {
        // Search suggestions send the library's own media id format.
        execute(PlayIntentAction.PlayById("MediaStore:42", 0L))

        assertEquals(listOf("setMediaItem", "prepare", "play"), callNames())
        assertSame(song, host.calls[0].args[0])
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun playByIdMissingMediaStoreIdToasts() {
        execute(PlayIntentAction.PlayById("MediaStore:7", 0L))

        assertTrue(host.calls.isEmpty())
        assertTrue(
            ShadowToast.showedToast(
                RuntimeEnvironment.getApplication().getString(R.string.cannot_find_file)
            )
        )
    }

    @Test
    fun playByIdMissingToastsAndDoesNotPlay() {
        execute(PlayIntentAction.PlayById("7", 0L))
        execute(PlayIntentAction.PlayById("not a number", 0L))

        assertEquals(0, host.controllerRequests)
        assertTrue(host.calls.isEmpty())
        assertEquals(2, ShadowToast.shownToastCount())
        assertTrue(
            ShadowToast.showedToast(
                RuntimeEnvironment.getApplication().getString(R.string.cannot_find_file)
            )
        )
    }
}
