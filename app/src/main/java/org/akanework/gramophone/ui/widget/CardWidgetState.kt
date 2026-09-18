/*
 *     Copyright (C) 2026 SteveZMTstudios
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

package org.akanework.gramophone.ui.widget

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.Player

data class CardWidgetPlaybackState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val isFavorite: Boolean = false,
    val isShuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val artworkUri: Uri? = null,
    val hdArtworkUri: Uri? = null,
    val artworkBitmap: Bitmap? = null
) {
    val hasTrack: Boolean
        get() = title.isNotEmpty() || artist.isNotEmpty() || artworkUri != null || hdArtworkUri != null

    val bestArtworkUri: Uri?
        get() = hdArtworkUri ?: artworkUri
}

data class CardWidgetActions(
    val openAppPi: PendingIntent,
    val favoritePi: PendingIntent,
    val prevPi: PendingIntent,
    val playPausePi: PendingIntent,
    val nextPi: PendingIntent,
    val repeatPi: PendingIntent? = null,
    val shufflePi: PendingIntent? = null
)

object CardWidgetStore {
    private const val PREFS_NAME = "GramophoneCardWidget"
    private const val KEY_TITLE = "widget_last_title"
    private const val KEY_ARTIST = "widget_last_artist"
    private const val KEY_ARTWORK_URI = "widget_last_artwork_uri"
    private const val KEY_HD_ARTWORK_URI = "widget_last_hd_artwork_uri"
    private const val KEY_FAVORITE = "widget_last_favorite"
    private const val KEY_SHUFFLE = "widget_last_shuffle"
    private const val KEY_REPEAT_MODE = "widget_last_repeat_mode"

    fun savePlaybackState(context: Context, state: CardWidgetPlaybackState) {
        if (!state.hasTrack) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_TITLE, state.title)
            putString(KEY_ARTIST, state.artist)
            putString(KEY_ARTWORK_URI, state.artworkUri?.toString())
            putString(KEY_HD_ARTWORK_URI, state.hdArtworkUri?.toString())
            putBoolean(KEY_FAVORITE, state.isFavorite)
            putBoolean(KEY_SHUFFLE, state.isShuffle)
            putInt(KEY_REPEAT_MODE, state.repeatMode)
        }
    }

    fun loadPlaybackState(context: Context): CardWidgetPlaybackState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null).orEmpty()
        val artist = prefs.getString(KEY_ARTIST, null).orEmpty()
        val uriStr = prefs.getString(KEY_ARTWORK_URI, null)
        val hdUriStr = prefs.getString(KEY_HD_ARTWORK_URI, null)
        val isFavorite = prefs.getBoolean(KEY_FAVORITE, false)
        val isShuffle = prefs.getBoolean(KEY_SHUFFLE, false)
        val repeatMode = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF)
        return CardWidgetPlaybackState(
            title = title,
            artist = artist,
            isPlaying = false,
            isFavorite = isFavorite,
            isShuffle = isShuffle,
            repeatMode = repeatMode,
            artworkUri = uriStr?.toUri(),
            hdArtworkUri = hdUriStr?.toUri(),
            artworkBitmap = null
        )
    }
}

