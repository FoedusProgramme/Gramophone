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

package org.akanework.gramophone

import android.app.Application
import android.app.SearchManager
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.IntentCompat
import androidx.media3.common.C
import org.akanework.gramophone.ui.intent.PlayIntentAction
import org.akanework.gramophone.ui.intent.PlayIntentAction.Autoplay
import org.akanework.gramophone.ui.intent.PlayIntentAction.MarkFavorite
import org.akanework.gramophone.ui.intent.PlayIntentAction.OpenPlaylist
import org.akanework.gramophone.ui.intent.PlayIntentAction.OpenSearch
import org.akanework.gramophone.ui.intent.PlayIntentAction.PlayById
import org.akanework.gramophone.ui.intent.PlayIntentAction.PlayFromSearch
import org.akanework.gramophone.ui.intent.PlayIntentAction.Shuffle
import org.akanework.gramophone.ui.intent.PlayIntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry

/**
 * Locks in what the old `MainActivity.doPlayFromIntent` did for each kind of intent: which
 * actions come out, in which order, and when the fallback autoplay is added.
 */
@Config(application = Application::class)
@RunWith(RobolectricTestRunner::class)
class PlayIntentParserTest {

    private fun parse(intent: Intent, autoplay: Boolean = false) =
        PlayIntentParser.parse(intent, autoplay)

    @Suppress("DEPRECATION")
    private val playlistsType = MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE

    private fun Bundle.asMap(): Map<String, Any?> =
        keySet().associateWith { @Suppress("DEPRECATION") get(it) }

    private fun playFromSearch(focus: String?, vararg extras: Pair<String, String>) =
        Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            focus?.let { putExtra(MediaStore.EXTRA_MEDIA_FOCUS, it) }
            extras.forEach { (k, v) -> putExtra(k, v) }
        }

    private fun single(actions: List<PlayIntentAction>): PlayFromSearch {
        assertEquals(1, actions.size)
        return actions[0] as PlayFromSearch
    }

    @Test
    fun emptyIntentDoesNothing() {
        assertEquals(emptyList<PlayIntentAction>(), parse(Intent()))
        assertEquals(emptyList<PlayIntentAction>(), parse(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun autoPlayIdWithPosition() {
        val intent = Intent().putExtra("AutoStartId", "42").putExtra("AutoStartPos", 1234L)
        assertEquals(listOf(PlayById("42", 1234L)), parse(intent))
    }

    @Test
    fun autoPlayIdDefaultsToTimeUnset() {
        val intent = Intent().putExtra("AutoStartId", "42")
        assertEquals(listOf(PlayById("42", C.TIME_UNSET)), parse(intent))
    }

    @Test
    fun suggestionWithContentUri() {
        val intent = Intent("org.akanework.gramophone.action.PLAY_MEDIA_FROM_SUGGESTION")
            .setData(Uri.parse("content://media/external/audio/media/77"))
        assertEquals(listOf(PlayById("MediaStore:77", C.TIME_UNSET)), parse(intent))
    }

    @Test
    fun suggestionWithNonNumericSegmentDoesNotPlay() {
        val intent = Intent("org.akanework.gramophone.action.PLAY_MEDIA_FROM_SUGGESTION")
            .setData(Uri.parse("content://media/external/audio/media/abc"))
        assertEquals(emptyList<PlayIntentAction>(), parse(intent))
        // ...and so the fallback autoplay may still kick in.
        assertEquals(listOf(Autoplay), parse(intent, autoplay = true))
    }

    @Test
    fun suggestionWithoutDataDoesNotPlay() {
        val intent = Intent("org.akanework.gramophone.action.PLAY_MEDIA_FROM_SUGGESTION")
        assertEquals(emptyList<PlayIntentAction>(), parse(intent))
    }

    @Test
    fun autoPlayIdExtraWinsOverSuggestionUri() {
        val intent = Intent("org.akanework.gramophone.action.PLAY_MEDIA_FROM_SUGGESTION")
            .setData(Uri.parse("content://media/external/audio/media/77"))
            .putExtra("AutoStartId", "5")
        assertEquals(listOf(PlayById("5", C.TIME_UNSET)), parse(intent))
    }

    @Test
    fun autoPlayIdOnlyWorksForSuggestionAction() {
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(Uri.parse("content://media/external/audio/media/77"))
        assertEquals(emptyList<PlayIntentAction>(), parse(intent))
    }

    @Test
    fun favoriteEntry() {
        val entry = Entry(listOf(Uri.parse("file:///sdcard/Music/a.mp3")), title = "a")
        val on = Intent().putExtra("FavoriteEntry", entry).putExtra("FavoriteState", true)
        assertEquals(listOf(MarkFavorite(entry, true)), parse(on))
        // State defaults to false.
        val off = Intent().putExtra("FavoriteEntry", entry)
        assertEquals(listOf(MarkFavorite(entry, false)), parse(off))
        // Favorite doesn't schedule playback, so the fallback still applies.
        assertEquals(listOf(MarkFavorite(entry, true), Autoplay), parse(on, autoplay = true))
    }

    @Test
    fun viewPlaylist() {
        val intent = Intent(Intent.ACTION_VIEW).putExtra("playlist", "12")
        assertEquals(listOf(OpenPlaylist(12L)), parse(intent))
    }

    @Test
    fun viewPlaylistIgnoresBadIdAndOtherActions() {
        assertEquals(emptyList<PlayIntentAction>(),
            parse(Intent(Intent.ACTION_VIEW).putExtra("playlist", "x")))
        assertEquals(emptyList<PlayIntentAction>(), parse(Intent(Intent.ACTION_VIEW)))
        assertEquals(emptyList<PlayIntentAction>(),
            parse(Intent(Intent.ACTION_MAIN).putExtra("playlist", "12")))
        // Navigation doesn't schedule playback.
        assertEquals(listOf(OpenPlaylist(12L), Autoplay),
            parse(Intent(Intent.ACTION_VIEW).putExtra("playlist", "12"), autoplay = true))
    }

    @Test
    fun searchActions() {
        assertEquals(listOf(OpenSearch("abc")),
            parse(Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "abc")))
        assertEquals(listOf(OpenSearch("def")),
            parse(Intent("com.google.android.gms.actions.SEARCH_ACTION")
                .putExtra(SearchManager.QUERY, "def")))
        assertEquals(listOf(OpenSearch(null)), parse(Intent(Intent.ACTION_SEARCH)))
    }

    @Test
    fun mediaSearchOnlyOpensSearch() {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE)
            .putExtra(MediaStore.EXTRA_MEDIA_ARTIST, "Artist")
            .putExtra(SearchManager.QUERY, "query")
        assertEquals(listOf(OpenSearch("Artist")), parse(intent))
        assertEquals(listOf(OpenSearch("Artist"), Autoplay), parse(intent, autoplay = true))
        assertEquals(listOf(OpenSearch(null)),
            parse(Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH)))
    }

    @Test
    fun playFromSearchAnyFocus() {
        val action = single(parse(playFromSearch(null, SearchManager.QUERY to "q",
            MediaStore.EXTRA_MEDIA_ARTIST to "ignored")))
        assertEquals("q", action.query)
        assertEquals(mapOf(MediaStore.EXTRA_MEDIA_FOCUS to ContentResolver.ANY_CURSOR_ITEM_TYPE),
            action.extras.asMap())
    }

    @Test
    fun playFromSearchUnsupportedFocusFallsBackToAny() {
        val action = single(parse(playFromSearch("vnd.android.cursor.item/bogus",
            SearchManager.QUERY to "q", MediaStore.EXTRA_MEDIA_TITLE to "ignored")))
        assertEquals("q", action.query)
        assertEquals(mapOf(MediaStore.EXTRA_MEDIA_FOCUS to ContentResolver.ANY_CURSOR_ITEM_TYPE),
            action.extras.asMap())
    }

    @Test
    fun playFromSearchGenreFocus() {
        val focus = MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE
        val action = single(parse(playFromSearch(focus, MediaStore.EXTRA_MEDIA_GENRE to "Rock",
            SearchManager.QUERY to "q", MediaStore.EXTRA_MEDIA_ARTIST to "ignored")))
        assertEquals("Rock", action.query)
        assertEquals(mapOf(MediaStore.EXTRA_MEDIA_FOCUS to focus), action.extras.asMap())
        // Main query falls back to QUERY.
        assertEquals("q", single(parse(playFromSearch(focus, SearchManager.QUERY to "q"))).query)
    }

    @Test
    fun playFromSearchArtistFocus() {
        val focus = MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE
        val action = single(parse(playFromSearch(focus, MediaStore.EXTRA_MEDIA_ARTIST to "Ar",
            MediaStore.EXTRA_MEDIA_GENRE to "Ge", MediaStore.EXTRA_MEDIA_ALBUM to "ignored")))
        assertEquals("Ar", action.query)
        assertEquals(mapOf(MediaStore.EXTRA_MEDIA_FOCUS to focus,
            MediaStore.EXTRA_MEDIA_GENRE to "Ge"), action.extras.asMap())
    }

    @Test
    fun playFromSearchAlbumFocus() {
        val focus = MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE
        val action = single(parse(playFromSearch(focus, MediaStore.EXTRA_MEDIA_ALBUM to "Al",
            MediaStore.EXTRA_MEDIA_ARTIST to "Ar", MediaStore.EXTRA_MEDIA_GENRE to "Ge",
            MediaStore.EXTRA_MEDIA_TITLE to "ignored")))
        assertEquals("Al", action.query)
        assertEquals(mapOf(MediaStore.EXTRA_MEDIA_FOCUS to focus,
            MediaStore.EXTRA_MEDIA_ARTIST to "Ar", MediaStore.EXTRA_MEDIA_GENRE to "Ge"),
            action.extras.asMap())
    }

    @Test
    fun playFromSearchSongFocus() {
        val focus = MediaStore.Audio.Media.ENTRY_CONTENT_TYPE
        val action = single(parse(playFromSearch(focus, MediaStore.EXTRA_MEDIA_TITLE to "Ti",
            MediaStore.EXTRA_MEDIA_ALBUM to "Al", MediaStore.EXTRA_MEDIA_ARTIST to "Ar",
            MediaStore.EXTRA_MEDIA_GENRE to "Ge")))
        assertEquals("Ti", action.query)
        assertEquals(mapOf(MediaStore.EXTRA_MEDIA_FOCUS to focus,
            MediaStore.EXTRA_MEDIA_ALBUM to "Al", MediaStore.EXTRA_MEDIA_ARTIST to "Ar",
            MediaStore.EXTRA_MEDIA_GENRE to "Ge"), action.extras.asMap())
    }

    @Suppress("DEPRECATION")
    @Test
    fun playFromSearchPlaylistFocus() {
        val action = single(parse(playFromSearch(playlistsType,
            MediaStore.EXTRA_MEDIA_PLAYLIST to "Pl", MediaStore.EXTRA_MEDIA_TITLE to "Ti",
            MediaStore.EXTRA_MEDIA_ALBUM to "Al", MediaStore.EXTRA_MEDIA_ARTIST to "Ar",
            MediaStore.EXTRA_MEDIA_GENRE to "Ge")))
        assertEquals("Pl", action.query)
        assertEquals(mapOf(MediaStore.EXTRA_MEDIA_FOCUS to playlistsType,
            MediaStore.EXTRA_MEDIA_TITLE to "Ti", MediaStore.EXTRA_MEDIA_ALBUM to "Al",
            MediaStore.EXTRA_MEDIA_ARTIST to "Ar", MediaStore.EXTRA_MEDIA_GENRE to "Ge"),
            action.extras.asMap())
    }

    @Test
    fun playFromSearchEmptyQueryStillPlays() {
        val action = single(parse(playFromSearch(null, SearchManager.QUERY to "")))
        assertEquals("", action.query)
    }

    @Test
    fun playFromSearchWithoutQueryDoesNotPlay() {
        assertEquals(emptyList<PlayIntentAction>(), parse(playFromSearch(null)))
        // No query means nothing is scheduled, so the fallback applies.
        assertEquals(listOf(Autoplay), parse(playFromSearch(null), autoplay = true))
    }

    @Test
    fun playFromSearchSuppressesFallback() {
        val intent = playFromSearch(null, SearchManager.QUERY to "q")
            .putExtra("AutoStartFgs", true)
        val actions = parse(intent, autoplay = true)
        assertEquals(1, actions.size)
        assertTrue(actions[0] is PlayFromSearch)
    }

    @Test
    fun shuffleShortcut() {
        assertEquals(listOf(Shuffle("")),
            parse(Intent("org.akanework.gramophone.action.SHUFFLE")))
        assertEquals(listOf(Shuffle("jazz")),
            parse(Intent("org.akanework.gramophone.action.SHUFFLE").putExtra("item_name", "jazz")))
        assertEquals(listOf(Shuffle("")),
            parse(Intent("org.akanework.gramophone.action.SHUFFLE"), autoplay = true))
    }

    @Test
    fun fallbackAutoplaySources() {
        assertEquals(listOf(Autoplay), parse(Intent().putExtra("AutoStartFgs", true)))
        assertEquals(listOf(Autoplay),
            parse(Intent().putExtra(IntentCompat.EXTRA_START_PLAYBACK, true)))
        assertEquals(listOf(Autoplay), parse(Intent(Intent.ACTION_MAIN), autoplay = true))
        assertEquals(emptyList<PlayIntentAction>(), parse(Intent().putExtra("AutoStartFgs", false)))
    }

    @Test
    fun scheduledPlaybackSuppressesFallback() {
        val intent = Intent().putExtra("AutoStartId", "1").putExtra("AutoStartFgs", true)
        assertEquals(listOf(PlayById("1", C.TIME_UNSET)), parse(intent, autoplay = true))
    }

    @Test
    fun actionsKeepTheOldOrder() {
        val entry = Entry(listOf(Uri.parse("file:///sdcard/Music/a.mp3")))
        val intent = Intent(Intent.ACTION_VIEW)
            .putExtra("AutoStartId", "3")
            .putExtra("FavoriteEntry", entry)
            .putExtra("FavoriteState", true)
            .putExtra("playlist", "9")
        assertEquals(listOf(PlayById("3", C.TIME_UNSET), MarkFavorite(entry, true),
            OpenPlaylist(9L)), parse(intent, autoplay = true))
    }
}
