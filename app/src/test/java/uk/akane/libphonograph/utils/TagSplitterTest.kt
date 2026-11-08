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

package uk.akane.libphonograph.utils

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [TagSplitter].
 *
 * @author SteveZMTstudios
 */
class TagSplitterTest {

    @Test
    fun testBasicSymbolSplitting() {
        val raw = "Artist A; Artist B ， Artist C 、 Artist D"
        val expected = listOf("Artist A", "Artist B", "Artist C", "Artist D")
        val result = TagSplitter.splitArtists(raw)
        assertEquals(expected, result)

        val customRaw = "Artist A, Artist B; Artist C / Artist D & Artist E ， Artist F 、 Artist G"
        val customExpected = listOf(
            "Artist A", "Artist B", "Artist C", "Artist D", "Artist E", "Artist F", "Artist G"
        )
        val customResult = TagSplitter.splitArtists(customRaw, symbols = listOf(",", ";", "/", "&", "，", "、"))
        assertEquals(customExpected, customResult)
    }

    @Test
    fun testSingleEntityBandsProtectedByDefault() {
        val bands = listOf(
            "Above & Beyond",
            "Tyler, The Creator",
            "AC/DC",
            "Earth, Wind & Fire",
            "Crosby, Stills & Nash",
            "Hall & Oates",
            "Simon & Garfunkel"
        )
        for (band in bands) {
            assertEquals(listOf(band), TagSplitter.splitArtists(band))
        }
    }

    @Test
    fun testWordDelimitersWithWhitespace() {
        val raw = "Eminem feat. Rihanna ft. Dr. Dre x Skylar Grey"
        val expected = listOf("Eminem", "Rihanna", "Dr. Dre", "Skylar Grey")
        val result = TagSplitter.splitArtists(raw, words = listOf("feat.", "ft.", "x"))
        assertEquals(expected, result)

        val defaultWordsRaw = "Eminem feat. Rihanna ft. Dr. Dre"
        val defaultWordsExpected = listOf("Eminem", "Rihanna", "Dr. Dre")
        assertEquals(defaultWordsExpected, TagSplitter.splitArtists(defaultWordsRaw))
    }

    @Test
    fun testWordDelimitersNotMistakenlyMatchedInsideNames() {
        // "The xx", "DMX", "Lil Nas X", "Phoenix" should not split the 'x'
        val raw = "The xx; DMX; Lil Nas X; Phoenix"
        val expected = listOf("The xx", "DMX", "Lil Nas X", "Phoenix")
        val result = TagSplitter.splitArtists(raw)
        assertEquals(expected, result)
    }

    @Test
    fun testBackslashEscape() {
        val raw = "Band\\/One / Band Two"
        val expected = listOf("Band/One", "Band Two")
        val result = TagSplitter.splitArtists(raw, symbols = listOf("/"))
        assertEquals(expected, result)

        val acdc = "AC\\/DC / Guns N' Roses"
        val expectedAcdc = listOf("AC/DC", "Guns N' Roses")
        val resultAcdc = TagSplitter.splitArtists(acdc, symbols = listOf("/"))
        assertEquals(expectedAcdc, resultAcdc)

        // Case-insensitive word escape
        val escapedFeat = TagSplitter.splitArtists("Artist A \\FEAT. Artist B")
        assertEquals(listOf("Artist A FEAT. Artist B"), escapedFeat)
        org.junit.Assert.assertFalse(escapedFeat.single().contains("\\"))

        // Exact case preservation on unescape
        val wordsWithX = listOf("feat.", "ft.", "x")
        val escapedLowerX = TagSplitter.splitAndFormatArtists("Xzibit & Malcolm \\x", symbols = listOf("&"), words = wordsWithX)
        assertEquals(listOf("Xzibit", "Malcolm x"), escapedLowerX.artistNames)

        val escapedUpperX = TagSplitter.splitAndFormatArtists("Xzibit & Malcolm \\X", symbols = listOf("&"), words = wordsWithX)
        assertEquals(listOf("Xzibit", "Malcolm X"), escapedUpperX.artistNames)

        // Backslash removed even when symbol is no longer in active configuration
        val symbolsWithoutSlash = TagSplitter.DEFAULT_ARTIST_SYMBOLS - "/"
        val resultNoSlash = TagSplitter.splitArtists("AC\\/DC", symbols = symbolsWithoutSlash)
        assertEquals(listOf("AC/DC"), resultNoSlash)

        // Non-delimiter backslashes are preserved intact
        assertEquals(listOf("AC\\DC"), TagSplitter.splitArtists("AC\\DC"))
        assertEquals(listOf("Back\\slash"), TagSplitter.splitArtists("Back\\slash"))
        assertEquals(listOf("A\\B"), TagSplitter.splitArtists("A\\\\B"))
    }

    @Test
    fun testSymbolNormalization() {
        val rawSymbols = listOf(",", "ft", "\\", "o", "，", "1", ";", " ", "\t")
        val normalized = TagSplitter.normalizeSymbols(rawSymbols)
        assertEquals(listOf(",", "，", ";"), normalized)
    }

    @Test
    fun testWordNormalization() {
        val rawWords = listOf("feat.", "  ft.  ", "with\\slash", "has space", "feat.")
        val normalized = TagSplitter.normalizeWords(rawWords)
        assertEquals(listOf("feat.", "ft."), normalized)
    }

    @Test
    fun testSyntheticArtistIdStrictlyNegative() {
        val names = listOf("Beyond", "Tyler", "AC", "Earth", "Artist A", "", null)
        for (name in names) {
            val id = MiscUtils.syntheticArtistId(name)
            org.junit.Assert.assertTrue("Synthetic ID for '$name' ($id) must be negative", id < 0L)
        }
    }

    @Test
    fun testDeduplication() {
        val raw = "Queen ; queen ; David Bowie"
        val expected = listOf("Queen", "David Bowie")
        val result = TagSplitter.splitArtists(raw)
        assertEquals(expected, result)
    }

    @Test
    fun testGenreSplitting() {
        val raw = "Rock; Indie、Alternative，Pop"
        val expected = listOf("Rock", "Indie", "Alternative", "Pop")
        val result = TagSplitter.splitGenres(raw)
        assertEquals(expected, result)
    }

    @Test
    fun testFormatArtistsForDisplay() {
        // Symbols should be normalized to primary symbol (first in list, e.g. ", "), words like "feat." preserved
        val raw = "Artist A & Artist B / Artist C feat. Artist D"
        val formatted = TagSplitter.formatArtistsForDisplay(raw, symbols = listOf(",", "/", "&"))
        assertEquals("Artist A, Artist B, Artist C feat. Artist D", formatted)
    }

    @Test
    fun testFormatSymbolSpacingRules() {
        assertEquals(", ", TagSplitter.formatSymbol(","))
        assertEquals("; ", TagSplitter.formatSymbol(";"))
        assertEquals(" / ", TagSplitter.formatSymbol("/"))
        assertEquals(" & ", TagSplitter.formatSymbol("&"))
        assertEquals(" | ", TagSplitter.formatSymbol("|"))
        assertEquals(" + ", TagSplitter.formatSymbol("+"))
        assertEquals("，", TagSplitter.formatSymbol("，"))
        assertEquals("、", TagSplitter.formatSymbol("、"))
        assertEquals("；", TagSplitter.formatSymbol("；"))
    }

    @Test
    fun testFormatArtistsWithDifferentDelimiters() {
        val artists = listOf("Taylor Swift", "Ed Sheeran", "Future")
        assertEquals("Taylor Swift, Ed Sheeran, Future", TagSplitter.formatArtists(artists, listOf(",")))
        assertEquals("Taylor Swift / Ed Sheeran / Future", TagSplitter.formatArtists(artists, listOf("/")))
        assertEquals("Taylor Swift、Ed Sheeran、Future", TagSplitter.formatArtists(artists, listOf("、")))
        assertEquals("Taylor Swift，Ed Sheeran，Future", TagSplitter.formatArtists(artists, listOf("，")))
    }

    @Test
    fun testCjkAndAsciiMixedSplitting() {
        val raw = "周杰伦，林俊杰、陈奕迅; 陶喆"
        val expected = listOf("周杰伦", "林俊杰", "陈奕迅", "陶喆")
        val result = TagSplitter.splitArtists(raw)
        assertEquals(expected, result)
    }

    @Test
    fun testFormatArtistsForDisplayWithCjk() {
        val raw = "周杰伦 / 林俊杰"
        val formattedCjk = TagSplitter.formatArtistsForDisplay(raw, symbols = listOf("、", "/"))
        assertEquals("周杰伦、林俊杰", formattedCjk)

        val formattedAscii = TagSplitter.formatArtistsForDisplay(raw, symbols = listOf(",", "/"))
        assertEquals("周杰伦, 林俊杰", formattedAscii)
    }

    @Test
    fun testSplitAndFormatArtistsConsistency() {
        val raw = "Artist A & Artist B / Artist C feat. Artist D"
        val symbols = listOf(",", "/", "&")
        val words = listOf("feat.", "ft.")
        val combined = TagSplitter.splitAndFormatArtists(raw, symbols, words)
        val separateSplit = TagSplitter.splitArtists(raw, symbols, words)
        val separateDisplay = TagSplitter.formatArtistsForDisplay(raw, symbols, words)

        assertEquals(separateSplit, combined.artistNames)
        assertEquals(separateDisplay, combined.displayArtist)
    }

    @Test
    fun testSplitAndFormatGenresConsistency() {
        val raw = "Rock / Pop; Jazz"
        val symbols = listOf(",", ";", "/")
        val combined = TagSplitter.splitAndFormatGenres(raw, symbols)
        val separateSplit = TagSplitter.splitGenres(raw, symbols)
        val separateDisplay = TagSplitter.formatGenresForDisplay(raw, symbols)

        assertEquals(separateSplit, combined.genreNames)
        assertEquals(separateDisplay, combined.displayGenre)
    }

    @Test
    fun testNullAndEmpty() {
        assertEquals(emptyList<String>(), TagSplitter.splitArtists(null))
        assertEquals(emptyList<String>(), TagSplitter.splitArtists("   "))
        assertEquals("", TagSplitter.formatArtistsForDisplay(null))
        assertEquals("", TagSplitter.formatArtistsForDisplay("   "))
        assertEquals("", TagSplitter.formatArtists(emptyList()))
        val emptyCombined = TagSplitter.splitAndFormatArtists(null)
        assertEquals(emptyList<String>(), emptyCombined.artistNames)
        assertEquals("", emptyCombined.displayArtist)
    }

    @Test
    fun testReviewerExplicitCriteria() {
        // 1. Escaped delimiter should not be split: "A\/B" -> ["A/B"]
        val escaped = TagSplitter.splitArtists("A\\/B", symbols = listOf("/"))
        assertEquals(listOf("A/B"), escaped)

        // 2. CJK delimiter should format without extra spaces: "A；B" -> "A；B"
        val cjk = TagSplitter.formatArtistsForDisplay("A；B", symbols = listOf("；"))
        assertEquals("A；B", cjk)

        // 3. Word delimiter requires whitespace on both sides:
        // "A feat.B" -> not split
        assertEquals(listOf("A feat.B"), TagSplitter.splitArtists("A feat.B", words = listOf("feat.")))
        // "A feat. B" -> split
        assertEquals(listOf("A", "B"), TagSplitter.splitArtists("A feat. B", words = listOf("feat.")))

        // 4. Case-insensitive deduplication: "a; A" -> ["a"]
        assertEquals(listOf("a"), TagSplitter.splitArtists("a; A"))
    }

    @Test
    fun testResolveAlbumArtistMajorityFallback() {
        // 8 songs with albumArtist "A" and 2 songs with albumArtist "B" (compilation album)
        val songs = (1..8).map {
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumArtist("A").build()
            ).build()
        } + (1..2).map {
            MediaItem.Builder().setMediaMetadata(
                MediaMetadata.Builder().setAlbumArtist("B").build()
            ).build()
        }
        val config = TagSplitter.TagSplitConfig(isMultiArtistEnabled = true)
        val resolved = MiscUtils.resolveAlbumArtist(songs, config)
        org.junit.Assert.assertNotNull(resolved)
        assertEquals("A", resolved!!.albumArtist)
        assertEquals(listOf("A"), resolved.primaryAlbumArtists)
    }

    @Test
    fun testTwoPassArtistAggregationMatchesRealMediaStoreId() {
        // Track 1: Solo track by "Artist A" (MediaStore assigned real ID 101L)
        val song1 = MediaItem.Builder()
            .setMediaId("solo_1")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Solo Song")
                    .setArtist("Artist A")
                    .build()
            ).build()

        // Track 2: Collaboration track "Artist A; Artist B"
        val song2 = MediaItem.Builder()
            .setMediaId("collab_2")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Collab Song")
                    .setArtist("Artist A; Artist B")
                    .build()
            ).build()

        val songs = listOf(song1, song2)
        val config = TagSplitter.TagSplitConfig(isMultiArtistEnabled = true)

        // Pass 1: Build cache from scanned solo track (canonical lowercase key "artist a" -> 101L)
        val cache = MiscUtils.buildArtistCache(mapOf("Artist A" to 101L))
        assertEquals(101L, cache[MiscUtils.canonicalArtistKey("Artist A")])

        // Pass 2: Aggregate artists
        val artistMap = MiscUtils.aggregateArtists(songs, cache, config)

        // "Artist A" in collab song matches 101L real ID; both song1 and song2 are in artistMap[101L]
        org.junit.Assert.assertTrue("Artist A must resolve to real MediaStore ID 101L", artistMap.containsKey(101L))
        val artistA = artistMap[101L]!!
        assertEquals("Artist A", artistA.title)
        assertEquals(2, artistA.songList.size)
        assertEquals(setOf("solo_1", "collab_2"), artistA.songList.map { it.mediaId }.toSet())

        // "Artist B" has no solo track in library, so it gets a deterministic negative synthetic ID
        val syntheticB = MiscUtils.syntheticArtistId("Artist B")
        org.junit.Assert.assertTrue(syntheticB < 0L)
        org.junit.Assert.assertTrue("Artist B must resolve to synthetic ID", artistMap.containsKey(syntheticB))
        val artistB = artistMap[syntheticB]!!
        assertEquals("Artist B", artistB.title)
        assertEquals(listOf("collab_2"), artistB.songList.map { it.mediaId })

        // Exactly 2 artists exist in the library: "Artist A" (101L) and "Artist B" (synthetic)
        assertEquals(2, artistMap.size)
    }

    @Test
    fun testArtistAggregationCaseInsensitivityAndDeduplication() {
        // Track 1: Solo track by "Queen" with real ID 55L
        val song1 = MediaItem.Builder()
            .setMediaId("queen_solo")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Bohemian Rhapsody")
                    .setArtist("Queen")
                    .build()
            ).build()

        // Track 2: Collaboration with lowercase "queen"
        val song2 = MediaItem.Builder()
            .setMediaId("queen_collab")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Under Pressure")
                    .setArtist("queen; David Bowie")
                    .build()
            ).build()

        val songs = listOf(song1, song2)
        val config = TagSplitter.TagSplitConfig(isMultiArtistEnabled = true)

        // Pass 1: Build cache with "Queen" -> 55L (canonicalized to "queen")
        val cache = MiscUtils.buildArtistCache(mapOf("Queen" to 55L))
        val artistMap = MiscUtils.aggregateArtists(songs, cache, config)

        // "queen" in Track 2 must match "Queen" (55L) despite case difference
        org.junit.Assert.assertTrue(artistMap.containsKey(55L))
        val queen = artistMap[55L]!!
        assertEquals(2, queen.songList.size)
        assertEquals(setOf("queen_solo", "queen_collab"), queen.songList.map { it.mediaId }.toSet())

        // No separate synthetic entry for "queen"
        val syntheticQueen = MiscUtils.syntheticArtistId("queen")
        org.junit.Assert.assertFalse("Lowercase queen must not create duplicate artist entry", artistMap.containsKey(syntheticQueen))
        assertEquals(2, artistMap.size) // 55L (Queen) + synthetic (David Bowie)
    }

    @Test
    fun testSyntheticArtistIdCaseInsensitiveStability() {
        // Two collaboration tracks containing different casings of an artist without any solo track
        val song1 = MediaItem.Builder().setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Song 1")
                .setArtist("Indie Band; Other")
                .build()
        ).build()
        val song2 = MediaItem.Builder().setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Song 2")
                .setArtist("indie band; Another")
                .build()
        ).build()

        val config = TagSplitter.TagSplitConfig(isMultiArtistEnabled = true)
        val artistMap = MiscUtils.aggregateArtists(listOf(song1, song2), emptyMap(), config)

        // Both casings produce the exact same synthetic ID
        val syntheticId = MiscUtils.syntheticArtistId("Indie Band")
        assertEquals(syntheticId, MiscUtils.syntheticArtistId("indie band"))
        org.junit.Assert.assertTrue(artistMap.containsKey(syntheticId))
        assertEquals(2, artistMap[syntheticId]!!.songList.size)
    }

    @Test
    fun testTwoPassOrderingFalsifiability() {
        // Falsifiability test: If Pass 2 runs without Pass 1 (cache is empty),
        // "Artist A" in the collab song CANNOT match 101L and instead falls back to synthetic ID.
        val song1 = MediaItem.Builder().setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Solo")
                .setArtist("Artist A")
                .build()
        ).build()
        val song2 = MediaItem.Builder().setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Collab")
                .setArtist("Artist A; Artist B")
                .build()
        ).build()

        val config = TagSplitter.TagSplitConfig(isMultiArtistEnabled = true)

        // When Pass 1 cache is present, 101L receives both song1 and song2
        val properCache = MiscUtils.buildArtistCache(mapOf("Artist A" to 101L))
        val properMap = MiscUtils.aggregateArtists(listOf(song1, song2), properCache, config)
        org.junit.Assert.assertTrue(properMap.containsKey(101L))
        assertEquals(2, properMap[101L]!!.songList.size)

        // BUT without Pass 1 cache, 101L does NOT exist
        val brokenMap = MiscUtils.aggregateArtists(listOf(song1, song2), emptyMap(), config)
        org.junit.Assert.assertFalse("Without cache, real ID 101L cannot be resolved", brokenMap.containsKey(101L))

        // And both songs' "Artist A" fell back to the same synthetic ID
        val syntheticA = MiscUtils.syntheticArtistId("Artist A")
        org.junit.Assert.assertTrue(brokenMap.containsKey(syntheticA))
        assertEquals(2, brokenMap[syntheticA]!!.songList.size)
    }
}
