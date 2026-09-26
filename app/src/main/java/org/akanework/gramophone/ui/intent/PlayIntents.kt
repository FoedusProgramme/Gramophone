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

package org.akanework.gramophone.ui.intent

/**
 * Extras understood by [org.akanework.gramophone.ui.MainActivity]. The string values are an
 * external contract (the playback service's notifications, the media button receiver, other apps
 * and pending intents that outlive an update), so never change them.
 */
object PlayIntents {
    /** Boolean: start playback (sent when the service could not start in the foreground). */
    const val PLAYBACK_AUTO_START_FOR_FGS = "AutoStartFgs"
    /** String: media id of the song to play. */
    const val PLAYBACK_AUTO_PLAY_ID = "AutoStartId"
    /** Long: position (ms) to start [PLAYBACK_AUTO_PLAY_ID] at. */
    const val PLAYBACK_AUTO_PLAY_POSITION = "AutoStartPos"
    /** Parcelable playlist entry to add to or remove from favorites. */
    const val FAVORITE_ENTRY = "FavoriteEntry"
    /** Boolean: whether [FAVORITE_ENTRY] becomes a favorite. */
    const val FAVORITE_STATE = "FavoriteState"
}
