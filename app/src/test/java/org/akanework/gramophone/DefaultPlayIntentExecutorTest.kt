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
import android.os.Bundle
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
import org.akanework.gramophone.ui.nav.PlaylistKey
import org.akanework.gramophone.ui.nav.SearchKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import java.io.File
import java.lang.reflect.Proxy

/**
 * Each [PlayIntentAction] branch of [DefaultPlayIntentExecutor]: which controller calls it makes,
 * where it navigates, and what it writes. The controller is a recording proxy, the library a fixed
 * id map and the MediaStore side of [LibraryWriteRepository] a fake.
 */
@Config(application = Application::class)
@RunWith(RobolectricTestRunner::class)
class DefaultPlayIntentExecutorTest {

    /** One recorded [Player] call: method name and arguments. */
    private data class Call(val name: String, val args: List<Any?>)

    private class FakeHost : PlayIntentHost {
        val calls = mutableListOf<Call>()
        val navigated = mutableListOf<AppNavKey>()
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

        override fun navigateTo(key: AppNavKey) {
            navigated += key
        }
    }

    private class FakeWrites : LibraryWrites {
        val performed = mutableListOf<PendingWrite>()

        override suspend fun favoritesUri(): Uri? = FAVORITES
        override suspend fun consentFor(write: PendingWrite): IntentSender? = null
        override suspend fun perform(write: PendingWrite) {
            performed += write
        }
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
        // Unconfined runs the launched write synchronously inside markFavorite.
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

    @Test
    fun markFavoriteWritesToFavorites() {
        val entry = Entry(locations = listOf(Uri.parse("file:///music/a.flac")))
        execute(PlayIntentAction.MarkFavorite(entry, favorite = false))

        val write = writes.performed.single() as PendingWrite.Favorite
        assertEquals(listOf(entry), write.songs)
        assertEquals(FAVORITES, write.uri)
        assertFalse(write.favorite)
        assertEquals(0, host.controllerRequests)
    }

    @Test
    fun openPlaylistNavigates() {
        execute(PlayIntentAction.OpenPlaylist(9L))

        val key = host.navigated.single() as PlaylistKey
        assertEquals(9L, key.id)
        assertNull(key.className)
        assertEquals(0, host.controllerRequests)
    }

    @Test
    fun openSearchNavigates() {
        execute(PlayIntentAction.OpenSearch("jazz"))
        execute(PlayIntentAction.OpenSearch(null))

        assertEquals(listOf("jazz", null), host.navigated.map { (it as SearchKey).query })
        assertEquals(0, host.controllerRequests)
    }

    @Test
    fun playFromSearchPlaysSearchQueryWithExtras() {
        val extras = Bundle().apply { putString("android.intent.extra.artist", "Someone") }
        execute(PlayIntentAction.PlayFromSearch("query", extras))

        assertEquals(listOf("setMediaItem", "prepare", "play"), callNames())
        val request = (host.calls[0].args.single() as MediaItem).requestMetadata
        assertEquals("query", request.searchQuery)
        assertEquals("Someone", request.extras?.getString("android.intent.extra.artist"))
    }

    @Test
    fun shuffleTurnsOnShuffleAndPlaysSearchQuery() {
        execute(PlayIntentAction.Shuffle(""))

        assertEquals(
            listOf("setShuffleModeEnabled", "setMediaItem", "prepare", "play"), callNames()
        )
        assertEquals(listOf<Any?>(true), host.calls[0].args)
        val request = (host.calls[1].args.single() as MediaItem).requestMetadata
        assertEquals("", request.searchQuery)
        assertNull(request.extras)
    }

    @Test
    fun autoplayPreparesAndPlays() {
        execute(PlayIntentAction.Autoplay)

        assertEquals(listOf("prepare", "play"), callNames())
    }

    private companion object {
        val FAVORITES: Uri = Uri.parse("content://media/external/audio/playlists/1")
    }
}
