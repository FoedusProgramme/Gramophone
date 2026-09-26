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

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.actions.PlaylistDialogs.rename
import org.akanework.gramophone.ui.components.compose.AppDialog
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.PlaylistSerializer
import java.io.File

/** The playlist create and rename dialogs. */
object PlaylistDialogs {
    private const val TAG = "PlaylistDialogs"

    fun create(activity: MainActivity) {
        val context: Context = activity
        playlistNameDialog(activity, R.string.create_playlist, "",
            { ItemManipulator.getDefaultPlaylistFile(it) }) { path ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val uri = ItemManipulator.createPlaylist(context, path)
                    ItemManipulator.setPlaylistContent(
                        context, uri, PlaylistSerializer.Playlist.create(), true
                    )
                } catch (e: Exception) {
                    Log.e(TAG, Log.getThrowableString(e)!!)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context, context.getString(
                                R.string.create_failed_playlist,
                                e.javaClass.name + ": " + e.message
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
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
            val uri = ContentUris.withAppendedId(
                @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id
            )
            val data = Bundle().apply {
                putLong("Id", id)
                putString("Path", path.absolutePath)
            }
            CoroutineScope(Dispatchers.Default).launch {
                val token = MediaStoreCompat.needRequestEfficientMove(
                    activity, uri, path.parent ?: ""
                )
                if (token != null) {
                    val pendingIntent = MediaStoreCompat.createWriteRequest(activity, listOf(token))
                    withContext(Dispatchers.Main) {
                        activity.requestPlaylistRename(pendingIntent.intentSender, data)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        continueRename(activity, Activity.RESULT_OK, data)
                    }
                }
            }
        }
    }

    /** Second half of [rename], after the MediaStore write permission came back. */
    fun continueRename(context: Context, resultCode: Int, data: Bundle) {
        if (resultCode == Activity.RESULT_OK) {
            val uri = ContentUris.withAppendedId(
                @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                data.getLong("Id")
            )
            val path = data.getString("Path")!!
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    MediaStoreCompat.efficientMove(context, uri, path)
                } catch (e: Exception) {
                    Log.e(TAG, Log.getThrowableString(e)!!)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context, context.getString(
                                R.string.rename_failed_playlist, e.javaClass.name + ": " + e.message
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            Toast.makeText(
                context, context.getString(R.string.rename_failed_playlist, "$resultCode"),
                Toast.LENGTH_LONG
            ).show()
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
