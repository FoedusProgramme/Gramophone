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
import androidx.annotation.StringRes
import androidx.core.app.ShareCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.library.DeleteResult
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.logic.requireMediaStoreId
import org.akanework.gramophone.logic.setMediaItemsSeamlessly
import org.akanework.gramophone.logic.setMediaItemsWithTitle
import org.akanework.gramophone.ui.components.compose.AppDialog
import org.akanework.gramophone.ui.nav.AlbumKey
import org.akanework.gramophone.ui.nav.ArtistKey
import org.akanework.gramophone.ui.nav.DateKey
import org.akanework.gramophone.ui.nav.GenreKey
import org.akanework.gramophone.ui.nav.PlaylistKey
import org.akanework.gramophone.ui.nav.SongDetailKey
import uk.akane.libphonograph.dynamicitem.Favorite
import uk.akane.libphonograph.items.Album
import uk.akane.libphonograph.items.Playlist
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId

/** Id of the "shuffle all" launcher shortcut (res/xml/shortcuts.xml). */
internal const val SHORTCUT_SHUFFLE_ALL = "shuffle_all"

/** Item and header actions of the library lists. */
object LibraryActions {
    fun playSong(env: AppActionEnv, songs: List<MediaItem>, position: Int, title: String) {
        val mediaController = env.player ?: return
        // If the currently playing song is also the clicked song, then we continue playing the
        // song and open full player, but we still replace the list. This is intended to copy
        // UX of Chinese players that open full player when clicking song, and we don't want
        // this UX to break if list is different for some reason.
        val currentItem = mediaController.currentMediaItem
        mediaController.setMediaItemsSeamlessly(songs, position, title)
        mediaController.prepare()
        mediaController.play()
        if (currentItem?.mediaId == songs[position].mediaId) {
            env.playerSheet.open()
        }
    }

    fun playAll(env: AppActionEnv, songs: List<MediaItem>, title: String) {
        env.player?.apply {
            setMediaItemsWithTitle(
                songs, title = title, shuffleEnabled = false, repeatMode = Player.REPEAT_MODE_OFF,
            )
            if (songs.isNotEmpty()) {
                prepare()
                play()
            }
        }
    }

    fun shuffleAll(env: AppActionEnv, songs: List<MediaItem>, title: String) {
        ShortcutManagerCompat.reportShortcutUsed(env.context, SHORTCUT_SHUFFLE_ALL)
        env.player?.apply {
            setMediaItemsWithTitle(songs, title = title, shuffleEnabled = true)
            if (songs.isNotEmpty()) {
                prepare()
                play()
            }
        }
    }

    fun playAllAlbums(env: AppActionEnv, albums: List<Album>, title: String) {
        env.player?.apply {
            albums.takeIf { it.isNotEmpty() }?.also { list ->
                setMediaItemsWithTitle(
                    list.flatMap { it.songList },
                    title = title, shuffleEnabled = false, repeatMode = Player.REPEAT_MODE_OFF,
                )
                prepare()
                play()
            } ?: setMediaItems(listOf())
        }
    }

    fun shuffleAllAlbums(env: AppActionEnv, albums: List<Album>, title: String) {
        ShortcutManagerCompat.reportShortcutUsed(env.context, SHORTCUT_SHUFFLE_ALL)
        env.player?.apply {
            albums.takeIf { it.isNotEmpty() }?.also { list ->
                setMediaItemsWithTitle(
                    list.shuffled().flatMap { it.songList },
                    title = env.getString(R.string.shuffled, title),
                    shuffleEnabled = false, repeatMode = Player.REPEAT_MODE_OFF,
                )
                prepare()
                play()
            } ?: setMediaItems(listOf())
        }
    }

    fun playNext(env: AppActionEnv, items: List<MediaItem>) {
        val mediaController = env.player ?: return
        mediaController.addMediaItems(mediaController.currentMediaItemIndex + 1, items)
    }

    fun addToQueue(env: AppActionEnv, items: List<MediaItem>) {
        env.player?.addMediaItems(items)
    }

    fun openAlbum(env: AppActionEnv, id: Long?) = env.navigate(AlbumKey(id))
    fun openGenre(env: AppActionEnv, id: Long?) = env.navigate(GenreKey(id))
    fun openDate(env: AppActionEnv, id: Long?) = env.navigate(DateKey(id))
    fun openArtist(env: AppActionEnv, id: Long?, albumArtist: Boolean) =
        env.navigate(ArtistKey(id, albumArtist))

    fun openPlaylist(env: AppActionEnv, item: Playlist) =
        env.navigate(PlaylistKey(item.id, item.javaClass.name))

    fun goToAlbum(env: AppActionEnv, item: MediaItem) =
        openAlbum(env, item.mediaMetadata.albumId)

    fun goToArtist(env: AppActionEnv, item: MediaItem) =
        openArtist(env, item.mediaMetadata.artistId, albumArtist = false)

    fun showDetails(env: AppActionEnv, item: MediaItem) =
        env.navigate(SongDetailKey(item.mediaId))

    fun deleteSongs(
        env: AppActionEnv, songs: List<MediaItem>, @StringRes message: Int, name: CharSequence?
    ) {
        val writes = env.writes
        // On the application scope, so leaving the screen doesn't cancel the delete.
        writes.deleteSongs(songs.map { it.getFile()!! to it.requireMediaStoreId() }) { result ->
            confirmDelete(env, writes, result, env.getString(message, name))
        }
    }

    fun deletePlaylist(env: AppActionEnv, item: Playlist) {
        val id = item.id
        if (id == null) {
            Toast.makeText(
                env.context, env.getString(R.string.delete_failed_playlist, "item.id == null"),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val writes = env.writes
        writes.deletePlaylist(id) { result ->
            confirmDelete(env, writes, result, env.getString(
                R.string.delete_really,
                if (item is Favorite) env.getString(R.string.playlist_favourite)
                else item.title
            ))
        }
    }

    /** Asks in the app before a delete the system would not ask about itself. */
    private fun confirmDelete(
        env: AppActionEnv, writes: LibraryWriteRepository, result: DeleteResult,
        message: String,
    ) {
        when (result) {
            // Already queued for the system dialog, which asks the user itself.
            is DeleteResult.NeedsConsent -> Unit
            is DeleteResult.ConfirmThenRun -> env.dialogs.show(AppDialog.Confirm(
                title = env.getString(R.string.delete),
                message = message,
                confirmText = env.getString(R.string.delete),
                onConfirm = { writes.runConfirmed(result) },
            ))
            is DeleteResult.Failed -> Toast.makeText(
                env.context,
                env.getString(R.string.delete_failed, result.error.message ?: result.error.toString()),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun renamePlaylist(env: AppActionEnv, item: Playlist) {
        PlaylistDialogs.rename(env, item)
    }

    fun share(env: AppActionEnv, item: MediaItem) {
        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri ?: return
        val mimeType = item.localConfiguration?.mimeType ?: "audio/*"
        try {
            ShareCompat.IntentBuilder(env.context)
                .setType(mimeType)
                .setStream(uri)
                .setChooserTitle("Share audio file")
                .startChooser()
        } catch (e: Exception) {
            Toast.makeText(env.context, "Unable to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
