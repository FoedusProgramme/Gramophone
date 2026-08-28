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

import android.os.Bundle
import androidx.media3.session.MediaSession
import org.akanework.gramophone.logic.utils.SemanticLyrics

/**
 * Pushes the current lyric line and the whole LRC to the vivo smart car system through the two
 * independent MediaSession channels:
 *
 *  - Channel A (Metadata): `ucar.media.metadata.LYRICS_*` keys are injected on-the-fly into the
 *    current [androidx.media3.common.MediaMetadata.extras] by
 *    `EndedWorkaroundPlayer.getState()` (see [buildMetadataExtras]). Media3's
 *    `LegacyConversions.convertToMediaMetadataCompat` already forwards String/Long entries of
 *    `MediaMetadata.extras` to the top-level `MediaMetadataCompat` keys, so the car launcher can
 *    read them directly.
 *  - Channel B (Extras): `music.media.extras.*` keys are set through
 *    [MediaSession.setSessionExtras] and read by the phone-side "smart car" app
 *    (`com.vivo.smartcar`).
 *
 * Both channels must be updated together to cover all projection scenarios.
 *
 * IMPORTANT: The metadata channel must NEVER be pushed by mutating the player timeline (e.g. via
 * `MediaController.replaceMediaItem`). Replacing the current media item fires
 * `onPositionDiscontinuity`, which triggers another lyric update, which replaces the item again,
 * producing an infinite feedback loop that freezes the main thread and crashes the app. Instead,
 * the metadata is injected in `EndedWorkaroundPlayer.getState()` and refreshed with
 * `invalidateState()` only when the pushed values actually change.
 */
class CarLyricsManager(
    private val mediaSession: MediaSession,
) {
    /** Whether the car lyrics master switch is currently enabled. */
    var enabled: Boolean = false

    private var currentLine: String? = null
    private var wholeLrc: String? = null
    private var status: Long = CarLyricsConstants.LYRICS_STATUS_NO_LYRICS

    // Last values actually pushed, used to detect whether the metadata channel changed so the
    // caller knows when to invalidate the player state (and avoid doing it on every tick).
    private var lastPushedLine: String? = null
    private var lastPushedWhole: String? = null
    private var lastPushedStatus: Long = CarLyricsConstants.LYRICS_STATUS_NO_LYRICS

    /**
     * Sets the loading state. Call this when starting to load lyrics for a new song so the car
     * display shows a loading indicator instead of "-1".
     *
     * @return true if the metadata channel changed and the player state should be invalidated.
     */
    fun setLoading(): Boolean {
        if (status == CarLyricsConstants.LYRICS_STATUS_LOADING && currentLine == null) return false
        currentLine = null
        wholeLrc = null
        status = CarLyricsConstants.LYRICS_STATUS_LOADING
        return push()
    }

    /**
     * Updates the current lyric line (called frequently while playing, e.g. on every
     * position/lyric change).
     *
     * @param currentLine the current lyric line text, or null/blank when there is none.
     * @param lyrics the parsed lyrics of the current song, or null when there are none.
     * @return true if the metadata channel changed and the player state should be invalidated.
     */
    fun updateLyric(
        currentLine: String?,
        lyrics: SemanticLyrics?,
    ): Boolean {
        val line = currentLine?.takeIf { it.isNotBlank() }
        val whole = lyrics?.toLrcString()

        this.currentLine = line
        wholeLrc = whole
        status = if (whole != null) {
            CarLyricsConstants.LYRICS_STATUS_SUCCESS
        } else {
            CarLyricsConstants.LYRICS_STATUS_NO_LYRICS
        }
        return push()
    }

    /**
     * Pushes the current state to both channels. Called whenever the state changes, including
     * when the master switch is toggled.
     *
     * @return true if the metadata channel changed and the player state should be invalidated.
     */
    fun push(): Boolean {
        // The values below must mirror exactly what buildMetadataExtras() injects, so that the
        // "changed" detection matches the actual metadata channel content.
        val lineToPush = if (enabled) currentLine else null
        val wholeToPush = if (enabled) {
            when (status) {
                CarLyricsConstants.LYRICS_STATUS_SUCCESS -> wholeLrc ?: "-1"
                CarLyricsConstants.LYRICS_STATUS_LOADING -> ""
                else -> "-1"
            }
        } else null
        val statusToPush = if (enabled) status else CarLyricsConstants.LYRICS_STATUS_NO_LYRICS
        val changed = lineToPush != lastPushedLine
            || wholeToPush != lastPushedWhole
            || statusToPush != lastPushedStatus
        lastPushedLine = lineToPush
        lastPushedWhole = wholeToPush
        lastPushedStatus = statusToPush

        if (!enabled) {
            // When disabled, clear the extras channel. The metadata channel keys are removed
            // automatically because EndedWorkaroundPlayer.getState() starts from the underlying
            // player state and only injects when enabled.
            mediaSession.setSessionExtras(Bundle())
            return changed
        }

        // ---- Channel B: Extras (phone-side smart car app) ----
        val extras = Bundle().apply {
            putBoolean(CarLyricsConstants.EXTRAS_KEY_LYRIC_ALLOWED, true)
            if (!lineToPush.isNullOrEmpty()) {
                putString(CarLyricsConstants.EXTRAS_KEY_LYRIC, lineToPush)
            }
            putBoolean(CarLyricsConstants.EXTRAS_KEY_NOTICE_CAR, true)
        }
        mediaSession.setSessionExtras(extras)
        return changed
    }

    /**
     * Builds the Channel A metadata extras. Called by `EndedWorkaroundPlayer.getState()` on every
     * state invalidation, so it must be cheap and side-effect free. Returns an empty bundle when
     * disabled so the previously injected keys disappear.
     */
    fun buildMetadataExtras(): Bundle {
        val extras = Bundle()
        if (!enabled) return extras

        // Current line: only set when non-empty.
        val lineToPush = currentLine
        if (!lineToPush.isNullOrEmpty()) {
            extras.putString(CarLyricsConstants.METADATA_KEY_LYRICS_LINE, lineToPush)
        }
        // Whole LRC + status. "-1" for whole LRC means "no lyrics" per the protocol.
        when (status) {
            CarLyricsConstants.LYRICS_STATUS_SUCCESS -> {
                extras.putString(CarLyricsConstants.METADATA_KEY_LYRICS_WHOLE, wholeLrc ?: "-1")
                extras.putLong(CarLyricsConstants.METADATA_KEY_LYRICS_STATUS, status)
            }
            CarLyricsConstants.LYRICS_STATUS_LOADING -> {
                extras.putString(CarLyricsConstants.METADATA_KEY_LYRICS_WHOLE, "")
                extras.putLong(CarLyricsConstants.METADATA_KEY_LYRICS_STATUS, status)
            }
            else -> {
                extras.putString(CarLyricsConstants.METADATA_KEY_LYRICS_WHOLE, "-1")
                extras.putLong(CarLyricsConstants.METADATA_KEY_LYRICS_STATUS, status)
            }
        }
        // CRITICAL: MediaMetadata.equals() deliberately ignores every extras key except the
        // special "lyricInfo" key. Without this key, the very first injected state (e.g. "-1")
        // is broadcast once, but subsequent lyric changes are NOT detected, so the car head unit
        // keeps showing "-1" forever even after real lyrics are loaded. Mirroring the current
        // lyric state into "lyricInfo" makes MediaMetadata.equals() return false on every real
        // change, which triggers onMediaMetadataChanged and pushes the new lyrics to the car.
        extras.putString(
            "lyricInfo",
            "$status|${wholeLrc?.hashCode()}|${lineToPush?.hashCode()}"
        )
        return extras
    }

    /**
     * Converts [SemanticLyrics] to a standard LRC string suitable for the car head unit.
     *
     * We intentionally keep both original and translated lines (instead of filtering translations
     * out) because:
     *  1. The car head unit typically only displays one line at a time (the current line), and we
     *     push the current line separately via LYRICS_LINE. The whole LRC is mainly for the car to
     *     know "there are lyrics" and to have the full text for scrolling/display.
     *  2. Filtering translated lines risks dropping ALL lines for songs where every line happens
     *     to have the same timestamp as its translation — we've seen this cause the whole LRC to
     *     be empty, showing "-1" on the car display.
     *  3. Even if the car displays both, it's harmless — the user gets more info, not less.
     */
    private fun SemanticLyrics.toLrcString(): String? {
        return when (this) {
            is SemanticLyrics.SyncedLyrics -> buildString {
                for (line in text) {
                    if (line.text.isBlank()) continue
                    append('[')
                    append(formatTimestamp(line.start))
                    append(']')
                    append(line.text)
                    append('\n')
                }
            }.takeIf { it.isNotBlank() }

            is SemanticLyrics.UnsyncedLyrics -> buildString {
                for ((text, _) in unsyncedText) {
                    if (text.isNotBlank()) {
                        append(text)
                        append('\n')
                    }
                }
            }.takeIf { it.isNotBlank() }
        }
    }

    private fun formatTimestamp(ms: ULong): String {
        val totalSeconds = ms.toLong() / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms.toLong() % 1000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }
}
