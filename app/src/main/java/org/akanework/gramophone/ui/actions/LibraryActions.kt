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

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.ShareCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.requireMediaStoreId
import org.akanework.gramophone.logic.setMediaItemsSeamlessly
import org.akanework.gramophone.logic.setMediaItemsWithTitle
import org.akanework.gramophone.ui.MainActivity
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
import uk.akane.libphonograph.manipulator.ItemManipulator

/** The hosting [MainActivity] behind whatever context wrapper Compose hands out. */
fun Context.findMainActivity(): MainActivity {
    var c: Context = this
    while (c !is MainActivity) {
        c = (c as? ContextWrapper)?.baseContext
            ?: throw IllegalStateException("not hosted by MainActivity")
    }
    return c
}

/** Item and header actions of the library lists. */
object LibraryActions {
    fun playSong(activity: MainActivity, songs: List<MediaItem>, position: Int, title: String) {
        val mediaController = activity.getPlayer() ?: return
        // If the currently playing song is also the clicked song, then we continue playing the
        // song and open full player, but we still replace the list. This is intended to copy
        // UX of Chinese players that open full player when clicking song, and we don't want
        // this UX to break if list is different for some reason.
        val currentItem = mediaController.currentMediaItem
        mediaController.setMediaItemsSeamlessly(songs, position, title)
        mediaController.prepare()
        mediaController.play()
        if (currentItem?.mediaId == songs[position].mediaId) {
            activity.playerSheet.open()
        }
    }

    fun playAll(activity: MainActivity, songs: List<MediaItem>, title: String) {
        activity.getPlayer()?.apply {
            setMediaItemsWithTitle(
                songs, title = title, shuffleEnabled = false, repeatMode = Player.REPEAT_MODE_OFF,
            )
            if (songs.isNotEmpty()) {
                prepare()
                play()
            }
        }
    }

    fun shuffleAll(activity: MainActivity, songs: List<MediaItem>, title: String) {
        ShortcutManagerCompat.reportShortcutUsed(activity, "shuffle_all")
        activity.getPlayer()?.apply {
            setMediaItemsWithTitle(songs, title = title, shuffleEnabled = true)
            if (songs.isNotEmpty()) {
                prepare()
                play()
            }
        }
    }

    fun playAllAlbums(activity: MainActivity, albums: List<Album>, title: String) {
        activity.getPlayer()?.apply {
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

    fun shuffleAllAlbums(activity: MainActivity, albums: List<Album>, title: String) {
        ShortcutManagerCompat.reportShortcutUsed(activity, "shuffle_all")
        activity.getPlayer()?.apply {
            albums.takeIf { it.isNotEmpty() }?.also { list ->
                setMediaItemsWithTitle(
                    list.shuffled().flatMap { it.songList },
                    title = activity.getString(R.string.shuffled, title),
                    shuffleEnabled = false, repeatMode = Player.REPEAT_MODE_OFF,
                )
                prepare()
                play()
            } ?: setMediaItems(listOf())
        }
    }

    fun playNext(activity: MainActivity, items: List<MediaItem>) {
        val mediaController = activity.getPlayer() ?: return
        mediaController.addMediaItems(mediaController.currentMediaItemIndex + 1, items)
    }

    fun addToQueue(activity: MainActivity, items: List<MediaItem>) {
        activity.getPlayer()?.addMediaItems(items)
    }

    fun openAlbum(activity: MainActivity, id: Long?) = activity.navigateTo(AlbumKey(id))
    fun openGenre(activity: MainActivity, id: Long?) = activity.navigateTo(GenreKey(id))
    fun openDate(activity: MainActivity, id: Long?) = activity.navigateTo(DateKey(id))
    fun openArtist(activity: MainActivity, id: Long?, albumArtist: Boolean) =
        activity.navigateTo(ArtistKey(id, albumArtist))

    fun openPlaylist(activity: MainActivity, item: Playlist) =
        activity.navigateTo(PlaylistKey(item.id, item.javaClass.name))

    fun goToAlbum(activity: MainActivity, item: MediaItem) =
        openAlbum(activity, item.mediaMetadata.albumId)

    fun goToArtist(activity: MainActivity, item: MediaItem) =
        openArtist(activity, item.mediaMetadata.artistId, albumArtist = false)

    fun showDetails(activity: MainActivity, item: MediaItem) =
        activity.navigateTo(SongDetailKey(item.mediaId))

    fun deleteSongs(
        activity: MainActivity, songs: List<MediaItem>, @StringRes message: Int, name: CharSequence?
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            val res = ItemManipulator.deleteSongs(
                activity, songs.map { it.getFile()!! to it.requireMediaStoreId() }
            )
            if (res != null) {
                withContext(Dispatchers.Main) {
                    activity.dialogs.show(AppDialog.Confirm(
                        title = activity.getString(R.string.delete),
                        message = activity.getString(message, name),
                        confirmText = activity.getString(R.string.delete),
                        onConfirm = { res.invoke() },
                    ))
                }
            }
        }
    }

    fun deletePlaylist(activity: MainActivity, item: Playlist) {
        val id = item.id
        if (id == null) {
            Toast.makeText(
                activity, activity.getString(R.string.delete_failed_playlist, "item.id == null"),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        CoroutineScope(Dispatchers.Default).launch {
            val res = ItemManipulator.deletePlaylist(activity, id)
            if (res != null) {
                withContext(Dispatchers.Main) {
                    activity.dialogs.show(AppDialog.Confirm(
                        title = activity.getString(R.string.delete),
                        message = activity.getString(
                            R.string.delete_really,
                            if (item is Favorite) activity.getString(R.string.playlist_favourite)
                            else item.title
                        ),
                        confirmText = activity.getString(R.string.delete),
                        onConfirm = { res.invoke() },
                    ))
                }
            }
        }
    }

    fun renamePlaylist(activity: MainActivity, item: Playlist) {
        PlaylistDialogs.rename(activity, item)
    }

    fun share(activity: MainActivity, item: MediaItem) {
        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri ?: return
        val mimeType = item.localConfiguration?.mimeType ?: "audio/*"
        try {
            ShareCompat.IntentBuilder(activity)
                .setType(mimeType)
                .setStream(uri)
                .setChooserTitle("Share audio file")
                .startChooser()
        } catch (e: Exception) {
            Toast.makeText(activity, "Unable to share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
