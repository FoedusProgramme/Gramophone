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

import android.app.SearchManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.IntentCompat
import androidx.media3.common.C
import androidx.media3.common.util.Log
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry

/** One thing an intent to [org.akanework.gramophone.ui.MainActivity] asks for. */
sealed interface PlayIntentAction {
    /** Play the song with media id [id] (looked up in the library) from [positionMs]. */
    data class PlayById(val id: String, val positionMs: Long) : PlayIntentAction

    data class MarkFavorite(val entry: Entry, val favorite: Boolean) : PlayIntentAction

    data class OpenPlaylist(val id: Long) : PlayIntentAction

    data class OpenSearch(val query: String?) : PlayIntentAction

    /**
     * Play what the service finds for [query] (may be empty); [extras] carries the validated
     * focus and sub queries as the request metadata's extras.
     */
    class PlayFromSearch(val query: String, val extras: Bundle) : PlayIntentAction

    /** The shuffle shortcut: shuffle what the service finds for [query] (empty = every song). */
    data class Shuffle(val query: String) : PlayIntentAction

    /** Prepare and play whatever is in the queue. */
    data object Autoplay : PlayIntentAction
}

/** Turns an intent into [PlayIntentAction]s, in the order they should run. No side effects. */
object PlayIntentParser {
    private const val TAG = "PlayIntentParser"
    private const val ACTION_PLAY_MEDIA_FROM_SUGGESTION =
        "org.akanework.gramophone.action.PLAY_MEDIA_FROM_SUGGESTION"
    private const val ACTION_SHUFFLE = "org.akanework.gramophone.action.SHUFFLE"
    private const val ACTION_GMS_SEARCH = "com.google.android.gms.actions.SEARCH_ACTION"
    private const val ANY_FOCUS = ContentResolver.ANY_CURSOR_ITEM_TYPE

    /**
     * A media search focus that is passed on to the service: the extra holding its main query
     * (else [SearchManager.QUERY] is used), and the extras passed along as sub queries.
     */
    private class SearchFocus(val mainQueryExtra: String?, val subQueryExtras: List<String>)

    /** Every focus the service understands. Any other focus is searched as [ANY_FOCUS]. */
    @Suppress("deprecation")
    private val searchFocuses = mapOf(
        ANY_FOCUS to SearchFocus(null, emptyList()),
        MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE to
                SearchFocus(MediaStore.EXTRA_MEDIA_GENRE, emptyList()),
        MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE to
                SearchFocus(MediaStore.EXTRA_MEDIA_ARTIST, listOf(MediaStore.EXTRA_MEDIA_GENRE)),
        MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE to SearchFocus(
            MediaStore.EXTRA_MEDIA_ALBUM,
            listOf(MediaStore.EXTRA_MEDIA_ARTIST, MediaStore.EXTRA_MEDIA_GENRE),
        ),
        MediaStore.Audio.Media.ENTRY_CONTENT_TYPE to SearchFocus(
            MediaStore.EXTRA_MEDIA_TITLE,
            listOf(
                MediaStore.EXTRA_MEDIA_ALBUM, MediaStore.EXTRA_MEDIA_ARTIST,
                MediaStore.EXTRA_MEDIA_GENRE,
            ),
        ),
        MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE to SearchFocus(
            MediaStore.EXTRA_MEDIA_PLAYLIST,
            listOf(
                MediaStore.EXTRA_MEDIA_TITLE, MediaStore.EXTRA_MEDIA_ALBUM,
                MediaStore.EXTRA_MEDIA_ARTIST, MediaStore.EXTRA_MEDIA_GENRE,
            ),
        ),
    )

    /**
     * An intent launched from Recents only keeps its navigation: on API 23-30 Back finishes the
     * task's root activity, and reopening the task from Recents starts it again with the intent
     * it was first launched with, which must not play, shuffle or (un)favorite a second time. The
     * "autoplay" preference still applies, as to any launch.
     *
     * @param autoplayPref the "autoplay" preference: start playback when nothing else in the
     *   intent does.
     */
    fun parse(intent: Intent, autoplayPref: Boolean): List<PlayIntentAction> {
        val fromHistory = (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        val actions = listOfNotNull(
            playById(intent),
            markFavorite(intent),
            openPlaylist(intent),
            openSearch(intent),
            mediaSearch(intent),
            shuffle(intent),
        ).filter { !fromHistory || it.isNavigation }
        val autoplay = !fromHistory &&
                (intent.getBooleanExtra(PlayIntents.PLAYBACK_AUTO_START_FOR_FGS, false)
                        || intent.getBooleanExtra(IntentCompat.EXTRA_START_PLAYBACK, false))
                || autoplayPref
        return if (autoplay && actions.none { it.startsPlayback }) {
            actions + PlayIntentAction.Autoplay
        } else actions
    }

    private val PlayIntentAction.startsPlayback: Boolean
        get() = this is PlayIntentAction.PlayById || this is PlayIntentAction.PlayFromSearch
                || this is PlayIntentAction.Shuffle

    private val PlayIntentAction.isNavigation: Boolean
        get() = this is PlayIntentAction.OpenPlaylist || this is PlayIntentAction.OpenSearch

    /** A song id from the extras, or else from a search suggestion's content uri. */
    private fun playById(intent: Intent): PlayIntentAction.PlayById? {
        val id = intent.getStringExtra(PlayIntents.PLAYBACK_AUTO_PLAY_ID)
            ?: (if (intent.action == ACTION_PLAY_MEDIA_FROM_SUGGESTION)
                intent.data?.let {
                    try {
                        ContentUris.parseId(it)
                        "MediaStore:${it.lastPathSegment}"
                    } catch (_: NumberFormatException) {
                        null
                    }
                } else null)
            ?: return null
        val position = intent.getLongExtra(PlayIntents.PLAYBACK_AUTO_PLAY_POSITION, C.TIME_UNSET)
        return PlayIntentAction.PlayById(id, position)
    }

    private fun markFavorite(intent: Intent): PlayIntentAction.MarkFavorite? {
        val entry = IntentCompat.getParcelableExtra(
            intent, PlayIntents.FAVORITE_ENTRY, Entry::class.java
        ) ?: return null
        val state = intent.getBooleanExtra(PlayIntents.FAVORITE_STATE, false)
        return PlayIntentAction.MarkFavorite(entry, state)
    }

    private fun openPlaylist(intent: Intent): PlayIntentAction.OpenPlaylist? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val id = intent.getStringExtra("playlist")?.toLongOrNull() ?: return null
        return PlayIntentAction.OpenPlaylist(id)
    }

    private fun openSearch(intent: Intent): PlayIntentAction.OpenSearch? {
        if (intent.action != Intent.ACTION_SEARCH && intent.action != ACTION_GMS_SEARCH) return null
        return PlayIntentAction.OpenSearch(intent.getStringExtra(SearchManager.QUERY))
    }

    /**
     * The legacy media search intents: play what the service finds, or only open the search
     * page for [MediaStore.INTENT_ACTION_MEDIA_SEARCH].
     */
    private fun mediaSearch(intent: Intent): PlayIntentAction? {
        val play = when (intent.action) {
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> true
            MediaStore.INTENT_ACTION_MEDIA_SEARCH -> false
            else -> return null
        }
        // https://developer.android.com/media/implement/assistant#declare_legacy_support_for_voice_actions
        // https://android-developers.googleblog.com/2010/09/supporting-new-music-voice-action.html
        // https://developer.android.com/guide/components/intents-common#PlaySearch
        val requestedFocus = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
        // Validate all extras before sending them to service.
        var focus = requestedFocus ?: ANY_FOCUS
        val spec = searchFocuses[focus] ?: run {
            Log.w(TAG, "unsupported focus $requestedFocus")
            focus = ANY_FOCUS
            searchFocuses.getValue(ANY_FOCUS)
        }
        val mainQuery = spec.mainQueryExtra?.let(intent::getStringExtra)
            ?: intent.getStringExtra(SearchManager.QUERY)
        if (!play) {
            // TODO: support sub queries or at least focus to use a different type of search.
            return PlayIntentAction.OpenSearch(mainQuery)
        }
        if (mainQuery == null) return null
        val subQueries = Bundle()
        subQueries.putString(MediaStore.EXTRA_MEDIA_FOCUS, focus)
        for (extra in spec.subQueryExtras) {
            intent.getStringExtra(extra)?.let { subQueries.putString(extra, it) }
        }
        return PlayIntentAction.PlayFromSearch(mainQuery, subQueries)
    }

    private fun shuffle(intent: Intent): PlayIntentAction.Shuffle? {
        if (intent.action != ACTION_SHUFFLE) return null
        return PlayIntentAction.Shuffle(intent.getStringExtra("item_name") ?: "")
    }
}
