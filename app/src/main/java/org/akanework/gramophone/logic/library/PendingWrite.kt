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

package org.akanework.gramophone.logic.library

import android.content.IntentSender
import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry

/**
 * A library write waiting for the user's MediaStore consent. It fully describes what to do once
 * the consent comes back, so the result can be handled even after the process was recreated.
 */
sealed interface PendingWrite : Parcelable {

    /** Append [songs] to the playlist at [uri], or create the playlist file [name] if uri is null. */
    @Parcelize
    data class AddToPlaylist(val songs: List<Entry>, val uri: Uri?, val name: String?) : PendingWrite

    /** Add [songs] to (or remove from) the favorites playlist at [uri], creating it if null. */
    @Parcelize
    data class Favorite(val songs: List<Entry>, val uri: Uri?, val favorite: Boolean) : PendingWrite

    /** The system delete dialog already deletes; only errors are left to report. */
    @Parcelize
    data object Delete : PendingWrite

    /** Move the playlist with MediaStore [id] to [path]. */
    @Parcelize
    data class Rename(val id: Long, val path: String) : PendingWrite
}

/** Outcome of preparing a delete. */
sealed interface DeleteResult {
    /** The system has to ask the user; [sender] shows its dialog. */
    class NeedsConsent(val sender: IntentSender, val payload: PendingWrite) : DeleteResult

    /** No system dialog needed: ask in the app first, and call [run] once the user confirmed. */
    class ConfirmThenRun(val run: suspend () -> Unit) : DeleteResult

    class Failed(val error: Throwable) : DeleteResult
}
