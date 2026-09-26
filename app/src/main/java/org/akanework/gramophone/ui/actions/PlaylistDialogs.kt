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

package org.akanework.gramophone.ui.actions

import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.compose.AppDialog
import org.koin.android.ext.android.get
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.manipulator.ItemManipulator
import java.io.File

/** The playlist create and rename dialogs. */
object PlaylistDialogs {
    fun create(activity: MainActivity) {
        playlistNameDialog(activity, R.string.create_playlist, "",
            { ItemManipulator.getDefaultPlaylistFile(it) }) { path ->
            activity.get<LibraryWriteRepository>().createPlaylist(path)
        }
    }

    fun rename(activity: MainActivity, item: Playlist) {
        val id = item.id
        if (id == null) {
            Toast.makeText(
                activity, activity.getString(R.string.rename_failed_playlist, "$item"),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        playlistNameDialog(
            activity,
            R.string.rename_playlist,
            item.title ?: "",
            { name ->
                item.path!!.resolveSibling(
                    if (item.path.extension != "") "$name.${item.path.extension}" else name
                )
            }
        ) { path ->
            activity.get<LibraryWriteRepository>().renamePlaylist(id, path)
        }
    }

    fun playlistNameDialog(
        activity: MainActivity,
        title: Int,
        initialValue: String,
        nameToFile: (String) -> File,
        then: (File) -> Unit
    ) {
        activity.dialogs.show(AppDialog.TextInput(
            title = activity.getString(title),
            initial = initialValue,
            hint = activity.getString(R.string.playlist_name),
            validate = { name ->
                val hasForbidden = name.any { it in "/\\:*?\"<>|" || it.code <= 0x1F || it.code == 0x7F }
                when {
                    hasForbidden -> activity.getString(R.string.forbidden_symbol_error)
                    name.isBlank() -> null
                    withContext(Dispatchers.IO) { nameToFile(name).exists() } ->
                        activity.getString(R.string.another_with_name)
                    else -> null
                }
            },
            onConfirm = { name -> then(nameToFile(name)) },
        ))
    }
}
