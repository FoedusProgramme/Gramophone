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

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.logic.library.LibraryWriteRepository
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
import org.robolectric.shadows.ShadowToast
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry

/**
 * What [DefaultPlayIntentExecutor] asks of the controller and the library writes for each action,
 * and how it resolves the ids it is handed: a bare MediaStore id (audio preview) or the library's
 * "MediaStore:<id>" (search suggestions). The controller is a recording player and the library a
 * fixed id map.
 */
@RunWith(RobolectricTestRunner::class)
class DefaultPlayIntentExecutorTest {

    /** A player that records the calls the executor makes, and does nothing else. */
    private class RecordingPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        val calls = mutableListOf<String>()
        // Not "mediaItems": its setter would clash with Player.setMediaItems.
        var loadedItems: List<MediaItem> = emptyList()
        var loadedPositionMs = C.TIME_UNSET

        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .build()

        override fun handleSetMediaItems(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<*> {
            calls += "setMediaItems"
            loadedItems = mediaItems.toList()
            loadedPositionMs = startPositionMs
            return Futures.immediateVoidFuture()
        }

        override fun handlePrepare(): ListenableFuture<*> {
            calls += "prepare"
            return Futures.immediateVoidFuture()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            calls += if (playWhenReady) "play" else "pause"
            return Futures.immediateVoidFuture()
        }

        override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
            calls += "setShuffleModeEnabled($shuffleModeEnabled)"
            return Futures.immediateVoidFuture()
        }
    }

    private class FakeHost : PlayIntentHost {
        val player = RecordingPlayer()
        var controllerRequests = 0

        override suspend fun awaitController(): Player {
            controllerRequests++
            return player
        }

        override fun navigateTo(key: AppNavKey) = error("unused")
    }

    private val song = MediaItem.Builder().setMediaId("42").build()
    private val writes = FakeLibraryWrites()
    private val host = FakeHost()
    private val executor = DefaultPlayIntentExecutor(
        RuntimeEnvironment.getApplication(),
        flowOf(mapOf(42L to song)),
        LibraryWriteRepository(
            CoroutineScope(Dispatchers.Unconfined), MediaConsentRequester(), writes
        ),
    )

    private fun execute(action: PlayIntentAction) = runBlocking { executor.execute(action, host) }

    private fun calls() = host.player.calls

    private fun searchQuery() = host.player.loadedItems.single().requestMetadata.searchQuery

    @Test
    fun playByIdFoundPlaysFromPosition() {
        execute(PlayIntentAction.PlayById("42", 1234L))

        assertEquals(listOf("setMediaItems", "prepare", "play"), calls())
        assertSame(song, host.player.loadedItems.single())
        assertEquals(1234L, host.player.loadedPositionMs)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun playByIdFromSuggestionResolvesMediaStorePrefix() {
        // Search suggestions send the library's own media id format.
        execute(PlayIntentAction.PlayById("MediaStore:42", 0L))

        assertEquals(listOf("setMediaItems", "prepare", "play"), calls())
        assertSame(song, host.player.loadedItems.single())
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun playByIdMissingMediaStoreIdToasts() {
        execute(PlayIntentAction.PlayById("MediaStore:7", 0L))

        assertTrue(calls().isEmpty())
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
        assertTrue(calls().isEmpty())
        assertEquals(2, ShadowToast.shownToastCount())
        assertTrue(
            ShadowToast.showedToast(
                RuntimeEnvironment.getApplication().getString(R.string.cannot_find_file)
            )
        )
    }

    @Test
    fun markFavoriteWritesWithoutTheController() {
        val entry = Entry(listOf(Uri.parse("file:///music/a.flac")))
        execute(PlayIntentAction.MarkFavorite(entry, favorite = true))

        assertEquals(
            listOf<PendingWrite>(PendingWrite.Favorite(listOf(entry), null, favorite = true)),
            writes.performed
        )
        assertEquals(0, host.controllerRequests)
    }

    @Test
    fun playFromSearchPlaysTheQueryWithItsExtras() {
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE)
        }
        execute(PlayIntentAction.PlayFromSearch("Artist", extras))

        assertEquals(listOf("setMediaItems", "prepare", "play"), calls())
        assertEquals("Artist", searchQuery())
        assertEquals(
            MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE,
            host.player.loadedItems.single().requestMetadata.extras
                ?.getString(MediaStore.EXTRA_MEDIA_FOCUS)
        )
    }

    @Test
    fun shuffleTurnsOnShuffleBeforePlayingTheQuery() {
        execute(PlayIntentAction.Shuffle(""))

        assertEquals(
            listOf("setShuffleModeEnabled(true)", "setMediaItems", "prepare", "play"), calls()
        )
        // An empty query means every song.
        assertEquals("", searchQuery())
        assertNull(host.player.loadedItems.single().requestMetadata.extras)
    }

    @Test
    fun autoplayPlaysWhatIsQueued() {
        execute(PlayIntentAction.Autoplay)

        assertEquals(listOf("prepare", "play"), calls())
    }
}
