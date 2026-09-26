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

    /**
     * @param autoplayPref the "autoplay" preference: start playback when nothing else in the
     *   intent does.
     */
    fun parse(intent: Intent, autoplayPref: Boolean): List<PlayIntentAction> {
        val actions = mutableListOf<PlayIntentAction>()
        val autoPlayId = intent.extras?.getString(PlayIntents.PLAYBACK_AUTO_PLAY_ID)
            ?: (if (intent.action == ACTION_PLAY_MEDIA_FROM_SUGGESTION)
                intent.data?.let {
                    try {
                        ContentUris.parseId(it)
                        "MediaStore:${it.lastPathSegment}"
                    } catch (_: NumberFormatException) {
                        null
                    }
                } else null)
        var willAutoPlayLater = false
        autoPlayId?.let { id ->
            willAutoPlayLater = true
            val pos = intent.extras?.getLong(PlayIntents.PLAYBACK_AUTO_PLAY_POSITION, C.TIME_UNSET)
                ?: C.TIME_UNSET
            actions += PlayIntentAction.PlayById(id, pos)
        }
        IntentCompat.getParcelableExtra(intent, PlayIntents.FAVORITE_ENTRY, Entry::class.java)
            ?.let {
                val state = intent.getBooleanExtra(PlayIntents.FAVORITE_STATE, false)
                actions += PlayIntentAction.MarkFavorite(it, state)
            }
        if (intent.action == Intent.ACTION_VIEW) {
            val id = intent.getStringExtra("playlist")?.toLongOrNull()
            if (id != null) {
                actions += PlayIntentAction.OpenPlaylist(id)
            }
        }
        if (intent.action == Intent.ACTION_SEARCH || intent.action == ACTION_GMS_SEARCH) {
            actions += PlayIntentAction.OpenSearch(intent.getStringExtra(SearchManager.QUERY))
        }
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_SEARCH
            || intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            // https://developer.android.com/media/implement/assistant#declare_legacy_support_for_voice_actions
            // https://android-developers.googleblog.com/2010/09/supporting-new-music-voice-action.html
            // https://developer.android.com/guide/components/intents-common#PlaySearch
            var focus = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
                ?: ContentResolver.ANY_CURSOR_ITEM_TYPE
            // Validate all extras before sending them to service.
            if (focus != ContentResolver.ANY_CURSOR_ITEM_TYPE &&
                focus != MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE &&
                focus != MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE &&
                focus != MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE &&
                focus != MediaStore.Audio.Media.ENTRY_CONTENT_TYPE &&
                focus != (@Suppress("deprecation") MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE)) {
                Log.w(TAG, "unsupported focus " +
                        intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS))
                focus = ContentResolver.ANY_CURSOR_ITEM_TYPE
            }
            val mainQuery: String?
            val subQueries = Bundle()
            subQueries.putString(MediaStore.EXTRA_MEDIA_FOCUS, focus)
            when (focus) {
                MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                    mainQuery = intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                }
                MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
                    mainQuery = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_GENRE, it)
                    }
                }
                MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                    mainQuery = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ARTIST, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_GENRE, it)
                    }
                }
                MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> {
                    mainQuery = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ALBUM, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ARTIST, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_GENRE, it)
                    }
                }
                @Suppress("deprecation") MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> {
                    mainQuery = @Suppress("deprecation")
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_PLAYLIST)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_TITLE, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ALBUM, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ARTIST, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_GENRE, it)
                    }
                }
                else -> mainQuery = intent.getStringExtra(SearchManager.QUERY)
            }
            if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
                if (mainQuery != null) {
                    willAutoPlayLater = true
                    actions += PlayIntentAction.PlayFromSearch(mainQuery, subQueries)
                }
            } else {
                // TODO: support sub queries or at least focus to use a different type of search.
                actions += PlayIntentAction.OpenSearch(mainQuery)
            }
        }
        if (intent.action == ACTION_SHUFFLE) {
            willAutoPlayLater = true
            actions += PlayIntentAction.Shuffle(intent.getStringExtra("item_name") ?: "")
        }
        val autoPlay = intent.extras?.getBoolean(PlayIntents.PLAYBACK_AUTO_START_FOR_FGS, false) == true
                || intent.extras?.getBoolean(IntentCompat.EXTRA_START_PLAYBACK, false) == true
                || autoplayPref
        if (autoPlay && !willAutoPlayLater) {
            actions += PlayIntentAction.Autoplay
        }
        return actions
    }
}
