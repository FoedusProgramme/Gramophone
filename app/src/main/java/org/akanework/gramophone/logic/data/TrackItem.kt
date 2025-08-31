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

package org.akanework.gramophone.logic.data

import android.net.Uri
import android.os.Bundle

/**
 * Simple replacement for Media3's MediaItem to reduce dependencies
 */
data class TrackItem(
    val mediaId: String,
    val uri: Uri,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Long = 0L,
    val artworkUri: Uri? = null,
    val extras: Bundle? = null
) {
    companion object {
        fun fromUri(uri: Uri, mediaId: String = uri.toString()): TrackItem {
            return TrackItem(
                mediaId = mediaId,
                uri = uri
            )
        }
    }
}

/**
 * Compatibility constants to replace Media3 constants
 */
object TrackConstants {
    const val REPEAT_MODE_OFF = 0
    const val REPEAT_MODE_ONE = 1
    const val REPEAT_MODE_ALL = 2
    
    const val SHUFFLE_MODE_DISABLED = false
    const val SHUFFLE_MODE_ENABLED = true
    
    const val STATE_IDLE = 1
    const val STATE_BUFFERING = 2
    const val STATE_READY = 3
    const val STATE_ENDED = 4
    
    const val PLAYBACK_STATE_IDLE = 0
    const val PLAYBACK_STATE_BUFFERING = 1
    const val PLAYBACK_STATE_READY = 2
    const val PLAYBACK_STATE_ENDED = 3
}