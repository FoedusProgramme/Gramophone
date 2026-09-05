/*
 *     Copyright (C) 2026 Akane Foundation
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
import android.graphics.Bitmap
import android.net.Uri

data class CardWidgetPlaybackState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val isFavorite: Boolean = false,
    val isShuffle: Boolean = false,
    val artworkUri: Uri? = null,
    val artworkBitmap: Bitmap? = null
)

data class CardWidgetActions(
    val openAppPi: PendingIntent,
    val favoritePi: PendingIntent,
    val prevPi: PendingIntent,
    val playPausePi: PendingIntent,
    val nextPi: PendingIntent,
    val shufflePi: PendingIntent
)
