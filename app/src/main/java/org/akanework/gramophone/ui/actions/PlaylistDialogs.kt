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

import android.content.ContentUris
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.AppDialog
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** The playlist create, rename and add-to-playlist dialogs. */
object PlaylistDialogs {
    fun create(env: AppActionEnv) {
        playlistNameDialog(env, R.string.create_playlist, "",
            { ItemManipulator.getDefaultPlaylistFile(it) }) { path ->
            env.writes.createPlaylist(path)
        }
    }

    fun rename(env: AppActionEnv, item: Playlist) {
        val id = item.id
        if (id == null) {
            Toast.makeText(
                env.context, env.getString(R.string.rename_failed_playlist, "$item"),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        playlistNameDialog(
            env,
            R.string.rename_playlist,
            item.title ?: "",
            { name ->
                item.path!!.resolveSibling(
                    if (item.path.extension != "") "$name.${item.path.extension}" else name
                )
            }
        ) { path ->
            env.writes.renamePlaylist(id, path)
        }
    }

    /**
     * Asks which playlist [item] goes to, or a name for a new one. Shows a progress dialog if the
     * playlists take more than 300 ms to load.
     */
    fun addToPlaylist(env: AppActionEnv, item: MediaItem) {
        val song = Entry.ofMediaItem(item)
        if (song == null) {
            Toast.makeText(
                env.context,
                env.getString(R.string.edit_playlist_failed, "song == null"),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // On the application scope, like the Activity's lifecycleScope before: the chooser still
        // shows (on root-level dialogs) if the screen that asked goes away meanwhile.
        env.appScope.launch(Dispatchers.Default) {
            val job = async(start = CoroutineStart.UNDISPATCHED) {
                env.reader.playlistListFlow.first().filter { it.title != null }
            }
            val maybeValue = withTimeoutOrNull(300.milliseconds) {
                job.await()
            }
            val playlists = maybeValue ?: run {
                launch(Dispatchers.Main) {
                    withContext(NonCancellable) {
                        val progress = AppDialog.Progress(env.getString(R.string.loading_playlists))
                        env.dialogs.show(progress)
                        job.invokeOnCompletion {
                            launch(Dispatchers.Main, start = CoroutineStart.ATOMIC) {
                                withContext(NonCancellable) {
                                    env.dialogs.dismissIf(progress)
                                }
                            }
                        }
                    }
                }
                job.await()
            }
            launch(Dispatchers.Main) {
                val names = playlists.map {
                    if (it is Favorite) env.getString(R.string.playlist_favourite) else
                        it.title ?: it.path?.absolutePath ?: it.id.toString()
                } + env.getString(R.string.create_playlist)
                env.dialogs.show(AppDialog.Choice(
                    title = env.getString(R.string.add_to_playlist),
                    icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                    items = names,
                ) { chosen ->
                    if (playlists.size == chosen) {
                        playlistNameDialog(env, R.string.create_playlist, "",
                            { ItemManipulator.getDefaultPlaylistFile(it) }) { name ->
                            env.writes.addToPlaylist(null, name, listOf(song))
                        }
                        return@Choice
                    }
                    val pl = playlists[chosen]
                    env.writes.addToPlaylist(
                        ContentUris.withAppendedId(
                            @Suppress("deprecation") MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                            pl.id!!
                        ), null, listOf(song)
                    )
                })
            }
        }
    }

    fun playlistNameDialog(
        env: AppActionEnv,
        title: Int,
        initialValue: String,
        nameToFile: (String) -> File,
        then: (File) -> Unit
    ) {
        env.dialogs.show(AppDialog.TextInput(
            title = env.getString(title),
            initial = initialValue,
            hint = env.getString(R.string.playlist_name),
            validate = { name ->
                val hasForbidden = name.any { it in "/\\:*?\"<>|" || it.code <= 0x1F || it.code == 0x7F }
                when {
                    hasForbidden -> env.getString(R.string.forbidden_symbol_error)
                    name.isBlank() -> null
                    withContext(Dispatchers.IO) { nameToFile(name).exists() } ->
                        env.getString(R.string.another_with_name)
                    else -> null
                }
            },
            onConfirm = { name -> then(nameToFile(name)) },
        ))
    }
}
