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

package org.akanework.gramophone.logic.car

/**
 * Constants for the vivo smart car (JoviInCar / uCar) lyrics projection protocol.
 *
 * Two independent channels must be implemented simultaneously:
 *  - Channel A (Metadata): the car launcher reads `ucar.media.metadata.*` keys directly from
 *    the MediaSession metadata.
 *  - Channel B (Extras): the phone-side "smart car" app reads `music.media.extras.*` keys from
 *    the MediaSession extras and forwards them to the car head unit.
 *
 * See the vivo "车载投屏歌词适配开发指南" for details.
 */
object CarLyricsConstants {
    // ---- Channel A: Metadata (car launcher direct) ----
    const val METADATA_KEY_LYRICS_LINE = "ucar.media.metadata.LYRICS_LINE"
    const val METADATA_KEY_LYRICS_WHOLE = "ucar.media.metadata.LYRICS_WHOLE"
    const val METADATA_KEY_LYRICS_STATUS = "ucar.media.metadata.LYRICS_STATUS"

    // ---- Channel B: Extras (phone-side smart car app forwarding) ----
    const val EXTRAS_KEY_LYRIC = "music.media.extras.LYRIC"
    const val EXTRAS_KEY_LYRIC_ALLOWED = "music.media.extras.LYRIC_IS_ALLOWED"
    const val EXTRAS_KEY_NOTICE_CAR = "music.media.extras.NOTICE_CAR"

    // ---- Lyrics status enum (MediaConstants$LyricsState) ----
    const val LYRICS_STATUS_SUCCESS = 0L // has lyrics
    const val LYRICS_STATUS_NO_LYRICS = 1L // no lyrics
    const val LYRICS_STATUS_LOADING = 2L // loading
    const val LYRICS_STATUS_FAIL = 3L // load failed

    /** Preference key for the car lyrics master switch. */
    const val PREF_CAR_LYRICS_ENABLED = "car_lyrics_enabled"
}
