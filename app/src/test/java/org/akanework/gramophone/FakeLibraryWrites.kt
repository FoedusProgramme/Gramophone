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

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import org.akanework.gramophone.logic.library.DeleteResult
import org.akanework.gramophone.logic.library.LibraryWrites
import org.akanework.gramophone.logic.library.PendingWrite
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The MediaStore side of the library writes, faked: the real one needs a MediaStore provider
 * Robolectric does not have. Records which writes run or fail; [consent] and [deleteResult] are
 * what the test wants the MediaStore to answer.
 */
internal class FakeLibraryWrites : LibraryWrites {
    /** The consent dialog every write needs, or null to write right away. */
    var consent: IntentSender? = null
    /** What preparing any delete returns. */
    var deleteResult: DeleteResult? = null
    val performed = mutableListOf<PendingWrite>()
    val failed = mutableListOf<Pair<PendingWrite, Int>>()

    override suspend fun favoritesUri(): Uri? = null
    override suspend fun consentFor(write: PendingWrite) = consent
    override suspend fun perform(write: PendingWrite, quiet: Boolean): Boolean {
        performed += write
        return true
    }
    override suspend fun reportFailure(write: PendingWrite, resultCode: Int, data: Intent?) {
        failed += write to resultCode
    }
    override suspend fun createPlaylist(file: File) {}
    override suspend fun deleteSongs(list: List<Pair<File, Long>>) = deleteResult!!
    override suspend fun deletePlaylist(id: Long) = deleteResult!!
}

/** A distinct consent dialog sender per [requestCode]. Needs Robolectric. */
internal fun testIntentSender(requestCode: Int): IntentSender = PendingIntent.getActivity(
    RuntimeEnvironment.getApplication(), requestCode, Intent(), PendingIntent.FLAG_IMMUTABLE
).intentSender
