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
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.akanework.gramophone.logic.library.DeleteResult
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.logic.library.LibraryWrites
import org.akanework.gramophone.logic.library.MediaConsentRequester
import org.akanework.gramophone.logic.library.PendingWrite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import java.io.File

/**
 * The consent queue and the result dispatch. The MediaStore side ([LibraryWrites]) is faked: the
 * real one needs a MediaStore provider Robolectric does not have, so these tests cover which write
 * runs (or does not) for each result, not the write itself.
 */
@Config(application = Application::class)
@RunWith(RobolectricTestRunner::class)
class MediaConsentRequesterTest {

    private class FakeWrites : LibraryWrites {
        var consent: IntentSender? = null
        var deleteResult: DeleteResult? = null
        val performed = mutableListOf<PendingWrite>()
        val failed = mutableListOf<Pair<PendingWrite, Int>>()

        override suspend fun favoritesUri(): Uri? = null
        override suspend fun consentFor(write: PendingWrite) = consent
        override suspend fun perform(write: PendingWrite) {
            performed += write
        }
        override suspend fun reportFailure(write: PendingWrite, resultCode: Int, data: Intent?) {
            failed += write to resultCode
        }
        override suspend fun createPlaylist(file: File) {}
        override suspend fun deleteSongs(list: List<Pair<File, Long>>) = deleteResult!!
        override suspend fun deletePlaylist(id: Long) = deleteResult!!
    }

    private lateinit var requester: MediaConsentRequester
    private lateinit var writes: FakeWrites
    private lateinit var repository: LibraryWriteRepository

    private val song = Entry(locations = listOf(Uri.parse("file:///music/a.flac")))
    private val playlist = Uri.parse("content://media/external/audio/playlists/7")

    private fun sender(requestCode: Int): IntentSender = PendingIntent.getActivity(
        RuntimeEnvironment.getApplication(), requestCode, Intent(), PendingIntent.FLAG_IMMUTABLE
    ).intentSender

    @Before
    fun setUp() {
        requester = MediaConsentRequester()
        writes = FakeWrites()
        // Unconfined runs each launched write synchronously inside the call.
        repository = LibraryWriteRepository(
            CoroutineScope(Dispatchers.Unconfined), requester, writes
        )
    }

    @Test
    fun cancelledConsentDoesNotWrite() {
        val write = PendingWrite.AddToPlaylist(listOf(song), playlist, null)
        repository.onConsentResult(write, Activity.RESULT_CANCELED, null)

        assertTrue(writes.performed.isEmpty())
        assertEquals(listOf(write to Activity.RESULT_CANCELED), writes.failed)
    }

    @Test
    fun grantedConsentWrites() {
        val write = PendingWrite.Favorite(listOf(song), playlist, favorite = true)
        repository.onConsentResult(write, Activity.RESULT_OK, null)

        assertEquals(listOf<PendingWrite>(write), writes.performed)
        assertTrue(writes.failed.isEmpty())
    }

    @Test
    fun deleteResultOnlyReportsErrors() {
        // The system dialog already deleted on OK; cancel is the user's choice.
        repository.onConsentResult(PendingWrite.Delete, Activity.RESULT_OK, null)
        repository.onConsentResult(PendingWrite.Delete, Activity.RESULT_CANCELED, null)
        assertTrue(writes.performed.isEmpty())
        assertTrue(writes.failed.isEmpty())

        repository.onConsentResult(PendingWrite.Delete, Activity.RESULT_FIRST_USER, null)
        assertEquals(listOf(PendingWrite.Delete to Activity.RESULT_FIRST_USER), writes.failed)
    }

    @Test
    fun resultWithoutPendingPayloadIsIgnored() {
        repository.onConsentResult(null, Activity.RESULT_OK, null)

        assertTrue(writes.performed.isEmpty())
        assertTrue(writes.failed.isEmpty())
    }

    @Test
    fun writeNeedingConsentIsQueuedNotPerformed() = runBlocking {
        val consent = sender(3)
        writes.consent = consent
        repository.addToPlaylist(playlist, null, listOf(song))

        assertTrue(writes.performed.isEmpty())
        val request = requester.next()
        assertSame(consent, request.sender)
        assertEquals(PendingWrite.AddToPlaylist(listOf(song), playlist, null), request.payload)
    }

    @Test
    fun writeWithoutConsentIsPerformedDirectly() = runBlocking {
        repository.markFavorite(listOf(song), favorite = false)

        assertEquals(
            listOf<PendingWrite>(PendingWrite.Favorite(listOf(song), null, favorite = false)),
            writes.performed
        )
        assertNull(withTimeoutOrNull(50) { requester.next() })
    }

    @Test
    fun deleteWithoutConsentWaitsForConfirmation() = runBlocking {
        var deleted = 0
        val result = ItemManipulator.deleteResult(consent = null) { deleted++ }
        assertTrue(result is DeleteResult.ConfirmThenRun)
        writes.deleteResult = result

        val returned = repository.deleteSongs(listOf(File("/music/a.flac") to 1L))
        assertSame(result, returned)
        assertEquals(0, deleted)

        repository.runConfirmed(returned as DeleteResult.ConfirmThenRun)
        assertEquals(1, deleted)
    }

    @Test
    fun deleteNeedingConsentIsQueued() = runBlocking {
        var deleted = 0
        val consent = sender(4)
        writes.deleteResult = ItemManipulator.deleteResult(consent) { deleted++ }

        val returned = repository.deletePlaylist(7L)
        assertTrue(returned is DeleteResult.NeedsConsent)
        val request = requester.next()
        assertSame(consent, request.sender)
        assertEquals(PendingWrite.Delete, request.payload)
        assertEquals(0, deleted)
    }
}
