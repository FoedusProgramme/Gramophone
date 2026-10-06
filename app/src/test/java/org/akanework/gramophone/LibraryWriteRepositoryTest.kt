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

import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.akanework.gramophone.logic.library.ConsentRequest
import org.akanework.gramophone.logic.library.DeleteResult
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.logic.library.MediaConsentRequester
import org.akanework.gramophone.logic.library.PendingWrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import java.io.File

/**
 * Which writes [LibraryWriteRepository] runs right away, which it queues for consent, and what it
 * does with the consent result. The MediaStore side is a [FakeLibraryWrites], so these tests cover
 * which write runs (or does not), not the write itself.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryWriteRepositoryTest {

    private val requester = MediaConsentRequester()
    private val writes = FakeLibraryWrites()
    // Unconfined runs each launched write synchronously inside the call.
    private val repository = LibraryWriteRepository(
        CoroutineScope(Dispatchers.Unconfined), requester, writes
    )

    private val song = Entry(locations = listOf(Uri.parse("file:///music/a.flac")))
    private val favorites = Uri.parse("content://media/external/audio/playlists/7")
    private val deleteFavorite = PendingWrite.Delete(unfavorite = song.locations)

    /** The next queued consent request; fails instead of hanging if there is none. */
    private suspend fun nextRequest(): ConsentRequest = withTimeout(1000) { requester.next() }

    private suspend fun noRequest() = assertNull(withTimeoutOrNull(50) { requester.next() })

    @Test
    fun cancelledConsentDoesNotWrite() {
        val write = PendingWrite.AddToPlaylist(listOf(song), 7L)
        repository.onConsentResult(write, Activity.RESULT_CANCELED, null)

        assertTrue(writes.performed.isEmpty())
        assertEquals(listOf(write to Activity.RESULT_CANCELED), writes.failed)
    }

    @Test
    fun grantedConsentWrites() {
        val write = PendingWrite.Favorite(listOf(song), favorites, favorite = true)
        repository.onConsentResult(write, Activity.RESULT_OK, null)

        assertEquals(listOf<PendingWrite>(write), writes.performed)
        assertTrue(writes.failed.isEmpty())
    }

    @Test
    fun deleteResultUnfavoritesOnlyWhatWasDeleted() {
        // Cancel is the user's choice: nothing was deleted, so nothing leaves the favorites.
        repository.onConsentResult(deleteFavorite, Activity.RESULT_CANCELED, null)
        assertTrue(writes.performed.isEmpty())
        assertTrue(writes.failed.isEmpty())

        // The system dialog already deleted on OK; only the favorites are left to update.
        repository.onConsentResult(deleteFavorite, Activity.RESULT_OK, null)
        assertEquals(listOf<PendingWrite>(deleteFavorite), writes.performed)
        assertTrue(writes.failed.isEmpty())

        writes.performed.clear()
        repository.onConsentResult(deleteFavorite, Activity.RESULT_FIRST_USER, null)
        assertTrue(writes.performed.isEmpty())
        assertEquals(listOf(deleteFavorite to Activity.RESULT_FIRST_USER), writes.failed)
    }

    @Test
    fun resultWithoutPendingPayloadIsIgnored() {
        repository.onConsentResult(null, Activity.RESULT_OK, null)

        assertTrue(writes.performed.isEmpty())
        assertTrue(writes.failed.isEmpty())
    }

    @Test
    fun writeNeedingConsentIsQueuedNotPerformed() = runBlocking {
        val consent = testIntentSender(3)
        writes.consent = consent
        repository.addToPlaylist(7L, listOf(song))

        assertTrue(writes.performed.isEmpty())
        val request = nextRequest()
        assertSame(consent, request.sender)
        assertEquals(PendingWrite.AddToPlaylist(listOf(song), 7L), request.payload)
    }

    @Test
    fun newPlaylistNeedingConsentIsQueuedWithItsPath() = runBlocking {
        writes.consent = testIntentSender(6)
        repository.addToNewPlaylist(File("/music/new.m3u"), listOf(song))

        assertTrue(writes.performed.isEmpty())
        assertEquals(
            PendingWrite.AddToNewPlaylist(listOf(song), "/music/new.m3u"),
            nextRequest().payload
        )
    }

    @Test
    fun writeWithoutConsentIsPerformedDirectly() = runBlocking {
        repository.markFavorite(listOf(song), favorite = false)

        assertEquals(
            listOf<PendingWrite>(PendingWrite.Favorite(listOf(song), null, favorite = false)),
            writes.performed
        )
        noRequest()
    }

    @Test
    fun favoriteNowWritesWhenNoConsentIsNeeded() = runBlocking {
        assertTrue(repository.markFavoriteNow(listOf(song), favorite = true))

        assertEquals(
            listOf<PendingWrite>(PendingWrite.Favorite(listOf(song), null, favorite = true)),
            writes.performed
        )
    }

    @Test
    fun favoriteNowNeedingConsentWritesNothingAndQueuesNothing() = runBlocking {
        writes.consent = testIntentSender(4)

        assertFalse(repository.markFavoriteNow(listOf(song), favorite = true))

        assertTrue(writes.performed.isEmpty())
        // The caller (the service) asks the user some other way
        noRequest()
    }

    @Test
    fun deleteWithoutConsentWaitsForConfirmation() = runBlocking {
        var deleted = 0
        val result = ItemManipulator.deleteResult(null, deleteFavorite) { deleted++ }
        assertTrue(result is DeleteResult.ConfirmThenRun)
        writes.deleteResult = result

        val returned = repository.deleteSongs(listOf(File("/music/a.flac") to 1L))
        assertSame(result, returned)
        assertEquals(0, deleted)
        // Still a favorite, should the user cancel.
        assertTrue(writes.performed.isEmpty())

        repository.runConfirmed(returned as DeleteResult.ConfirmThenRun)
        assertEquals(1, deleted)
        assertEquals(listOf<PendingWrite>(deleteFavorite), writes.performed)
    }

    @Test
    fun deleteNeedingConsentIsQueued() = runBlocking {
        var deleted = 0
        val consent = testIntentSender(4)
        writes.deleteResult = ItemManipulator.deleteResult(consent, deleteFavorite) { deleted++ }

        val returned = repository.deleteSongs(listOf(File("/music/a.flac") to 1L))
        assertTrue(returned is DeleteResult.NeedsConsent)
        val request = nextRequest()
        assertSame(consent, request.sender)
        // The favorites are updated from the payload once the result comes back.
        assertEquals(deleteFavorite, request.payload)
        assertEquals(0, deleted)
        assertTrue(writes.performed.isEmpty())
    }
}
