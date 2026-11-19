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

import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray

/**
 * Deep module for parsing, splitting, and formatting multi-value tags (artists and genres).
 *
 * Handles:
 * - Symbol delimiters (e.g., `;`, `，`, `、`)
 * - Word delimiters with whitespace boundary (e.g., `feat.`, `ft.`)
 * - Backslash escaping (e.g., `AC\/DC` -> `AC/DC` without splitting)
 * - Normalizing symbols to the primary symbol while preserving word delimiters
 * - Trimming and case-insensitive deduplication
 *
 * @author SteveZMTstudios
 */
object TagSplitter {

    const val PREF_MULTI_ARTIST_ENABLED = "multi_artist_enabled"
    const val PREF_MULTI_GENRE_ENABLED = "multi_genre_enabled"
    const val PREF_SPLIT_ARTIST_SYMBOLS = "split_artist_symbols"
    const val PREF_SPLIT_ARTIST_WORDS = "split_artist_words"
    const val PREF_SPLIT_GENRE_SYMBOLS = "split_genre_symbols"

    val DEFAULT_ARTIST_SYMBOLS = listOf(";", "，", "、")
    val DEFAULT_ARTIST_WORDS = listOf("feat.", "ft.")
    val DEFAULT_GENRE_SYMBOLS = listOf(";", "，", "、")

    data class TagSplitConfig(
        val isMultiArtistEnabled: Boolean = false,
        val isMultiGenreEnabled: Boolean = false,
        val artistSymbols: List<String> = DEFAULT_ARTIST_SYMBOLS,
        val artistWords: List<String> = DEFAULT_ARTIST_WORDS,
        val genreSymbols: List<String> = DEFAULT_GENRE_SYMBOLS
    )

    data class ArtistParseResult(
        val artistNames: List<String>,
        val displayArtist: String
    )

    data class GenreParseResult(
        val genreNames: List<String>,
        val displayGenre: String
    )

    fun getTagSplitConfig(prefs: SharedPreferences): TagSplitConfig {
        return TagSplitConfig(
            isMultiArtistEnabled = isMultiArtistEnabled(prefs),
            isMultiGenreEnabled = isMultiGenreEnabled(prefs),
            artistSymbols = getArtistSymbols(prefs),
            artistWords = getArtistWords(prefs),
            genreSymbols = getGenreSymbols(prefs)
        )
    }

    fun isMultiArtistEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_MULTI_ARTIST_ENABLED, false)
    }

    fun isMultiGenreEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(PREF_MULTI_GENRE_ENABLED, false)
    }

    fun getArtistSymbols(prefs: SharedPreferences): List<String> {
        val parsed = parseStringList(prefs.getString(PREF_SPLIT_ARTIST_SYMBOLS, null))
        return if (parsed.isNullOrEmpty()) DEFAULT_ARTIST_SYMBOLS else normalizeSymbols(parsed).ifEmpty { DEFAULT_ARTIST_SYMBOLS }
    }

    fun setArtistSymbols(prefs: SharedPreferences, list: List<String>) {
        prefs.edit { putString(PREF_SPLIT_ARTIST_SYMBOLS, serializeStringList(normalizeSymbols(list))) }
    }

    fun getArtistWords(prefs: SharedPreferences): List<String> {
        val parsed = parseStringList(prefs.getString(PREF_SPLIT_ARTIST_WORDS, null))
        return if (parsed.isNullOrEmpty()) DEFAULT_ARTIST_WORDS else normalizeWords(parsed).ifEmpty { DEFAULT_ARTIST_WORDS }
    }

    fun setArtistWords(prefs: SharedPreferences, list: List<String>) {
        prefs.edit { putString(PREF_SPLIT_ARTIST_WORDS, serializeStringList(normalizeWords(list))) }
    }

    fun getGenreSymbols(prefs: SharedPreferences): List<String> {
        val parsed = parseStringList(prefs.getString(PREF_SPLIT_GENRE_SYMBOLS, null))
        return if (parsed.isNullOrEmpty()) DEFAULT_GENRE_SYMBOLS else normalizeSymbols(parsed).ifEmpty { DEFAULT_GENRE_SYMBOLS }
    }

    fun setGenreSymbols(prefs: SharedPreferences, list: List<String>) {
        prefs.edit { putString(PREF_SPLIT_GENRE_SYMBOLS, serializeStringList(normalizeSymbols(list))) }
    }

    internal fun normalizeSymbols(list: List<String>): List<String> {
        return list.asSequence()
            .map { it.trim() }
            .filter { it.length == 1 && it != "\\" && !it.isBlank() && !Character.isLetterOrDigit(it[0]) }
            .distinct()
            .toList()
    }

    internal fun normalizeWords(list: List<String>): List<String> {
        return list.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains('\\') && !it.contains(' ') }
            .distinct()
            .toList()
    }

    private fun serializeStringList(list: List<String>): String {
        val jsonArray = JSONArray()
        for (item in list) {
            jsonArray.put(item)
        }
        return jsonArray.toString()
    }

    private fun parseStringList(jsonStr: String?): List<String>? {
        if (jsonStr == null) return null
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Splits and formats an artist string in a single tokenization pass.
     */
    fun splitAndFormatArtists(
        raw: String?,
        symbols: List<String> = DEFAULT_ARTIST_SYMBOLS,
        words: List<String> = DEFAULT_ARTIST_WORDS
    ): ArtistParseResult {
        if (raw.isNullOrBlank()) return ArtistParseResult(emptyList(), "")
        val tokens = tokenize(raw, symbols, words)
        if (tokens.isEmpty()) return ArtistParseResult(emptyList(), "")

        val primarySymbol = symbols.firstOrNull() ?: ","
        val formattedPrimarySymbol = formatSymbol(primarySymbol)

        val seen = mutableSetOf<String>()
        val artistList = mutableListOf<String>()
        val result = StringBuilder()
        var lastDelimiter: String? = null

        for (token in tokens) {
            when (token) {
                is Token.Delimiter -> {
                    lastDelimiter = if (token.isWord) {
                        " ${token.text} "
                    } else {
                        formattedPrimarySymbol
                    }
                }
                is Token.Value -> {
                    val name = token.text.trim()
                    if (name.isNotEmpty()) {
                        val lower = name.lowercase()
                        if (seen.add(lower)) {
                            artistList.add(name)
                            if (result.isNotEmpty() && lastDelimiter != null) {
                                result.append(lastDelimiter)
                            }
                            result.append(name)
                        }
                    }
                    lastDelimiter = null
                }
            }
        }

        val display = if (result.isNotEmpty()) result.toString() else raw.replace("\\", "")
        return ArtistParseResult(artistList, display)
    }

    /**
     * Splits an artist string into a list of distinct artist names.
     */
    fun splitArtists(
        raw: String?,
        symbols: List<String> = DEFAULT_ARTIST_SYMBOLS,
        words: List<String> = DEFAULT_ARTIST_WORDS
    ): List<String> {
        return splitAndFormatArtists(raw, symbols, words).artistNames
    }

    fun splitArtists(raw: String?, config: TagSplitConfig): List<String> {
        return splitArtists(raw, config.artistSymbols, config.artistWords)
    }

    /**
     * Splits and formats a genre string in a single pass.
     */
    fun splitAndFormatGenres(
        raw: String?,
        symbols: List<String> = DEFAULT_GENRE_SYMBOLS
    ): GenreParseResult {
        if (raw.isNullOrBlank()) return GenreParseResult(emptyList(), "")
        val genres = splitInternal(raw, symbols, emptyList())
        if (genres.isEmpty()) return GenreParseResult(emptyList(), "")
        val primary = formatSymbol(symbols.firstOrNull() ?: ",")
        val display = genres.joinToString(primary)
        return GenreParseResult(genres, display)
    }

    /**
     * Splits a genre string into a list of distinct genre names.
     */
    fun splitGenres(
        raw: String?,
        symbols: List<String> = DEFAULT_GENRE_SYMBOLS
    ): List<String> {
        return splitAndFormatGenres(raw, symbols).genreNames
    }

    fun splitGenres(raw: String?, config: TagSplitConfig): List<String> {
        return splitGenres(raw, config.genreSymbols)
    }

    fun formatArtists(
        artists: List<String>,
        symbols: List<String> = DEFAULT_ARTIST_SYMBOLS
    ): String {
        if (artists.isEmpty()) return ""
        val primarySymbol = symbols.firstOrNull() ?: return artists.joinToString(" ")
        val formattedDelimiter = formatSymbol(primarySymbol)
        return artists.joinToString(formattedDelimiter)
    }

    fun formatArtists(
        artists: List<String>,
        config: TagSplitConfig
    ): String = formatArtists(artists, config.artistSymbols)

    /**
     * Cleans and normalizes the artist display string.
     */
    fun formatArtistsForDisplay(
        raw: String?,
        symbols: List<String> = DEFAULT_ARTIST_SYMBOLS,
        words: List<String> = DEFAULT_ARTIST_WORDS
    ): String {
        return splitAndFormatArtists(raw, symbols, words).displayArtist
    }

    /**
     * Cleans and normalizes the genre display string:
     * - Symbol delimiters are standardized to the primary (first) symbol.
     */
    fun formatGenresForDisplay(
        raw: String?,
        symbols: List<String> = DEFAULT_GENRE_SYMBOLS
    ): String {
        return splitAndFormatGenres(raw, symbols).displayGenre
    }

    internal fun formatSymbol(symbol: String): String {
        val trimmed = symbol.trim()
        if (trimmed.isEmpty()) return " "
        // CJK punctuation: no surrounding spaces
        val isCjkPunctuation = trimmed.all { ch ->
            ch in '\u3000'..'\u303F' || // CJK Symbols and Punctuation (e.g. 、)
            ch in '\uFF00'..'\uFFEF'    // Halfwidth and Fullwidth Forms (e.g. ，；)
        }
        if (isCjkPunctuation) return trimmed
        return when (trimmed) {
            ",", ";" -> "$trimmed "
            else -> " $trimmed "
        }
    }

    private sealed class Token {
        data class Value(val text: String) : Token()
        data class Delimiter(val text: String, val isWord: Boolean) : Token()
    }

    private fun splitInternal(
        raw: String,
        symbols: List<String>,
        words: List<String>
    ): List<String> {
        val tokens = tokenize(raw, symbols, words)
        val list = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        for (token in tokens) {
            if (token is Token.Value) {
                val cleaned = token.text.trim()
                if (cleaned.isNotEmpty()) {
                    val lower = cleaned.lowercase()
                    if (seen.add(lower)) {
                        list.add(cleaned)
                    }
                }
            }
        }
        return list
    }

    private fun tokenize(
        input: String,
        symbols: List<String>,
        words: List<String>
    ): List<Token> {
        // Step 1: Replace escaped delimiters with safe placeholders
        var processed = input
        val protectedPlaceholders = mutableMapOf<String, String>()
        var placeholderIndex = 0

        // Handle explicit backslash escapes: \symbol or \word (case-insensitive for words)
        val sortedWords = words.filter { it.isNotBlank() }.sortedByDescending { it.length }
        if (sortedWords.isNotEmpty()) {
            val wordsPattern = sortedWords.joinToString("|") { Regex.escape(it) }
            val escapedWordRegex = Regex("(?i)\\\\($wordsPattern)")
            processed = escapedWordRegex.replace(processed) { matchResult ->
                val ph = "\u0000ESCAPED_${placeholderIndex++}\u0000"
                protectedPlaceholders[ph] = matchResult.groupValues[1]
                ph
            }
        }

        val sortedSymbols = symbols.filter { it.isNotBlank() }.sortedByDescending { it.length }
        if (sortedSymbols.isNotEmpty()) {
            val symbolsPattern = sortedSymbols.joinToString("|") { Regex.escape(it) }
            val escapedSymbolRegex = Regex("\\\\($symbolsPattern)")
            processed = escapedSymbolRegex.replace(processed) { matchResult ->
                val ph = "\u0000ESCAPED_${placeholderIndex++}\u0000"
                protectedPlaceholders[ph] = matchResult.groupValues[1]
                ph
            }
        }

        // Step 2: Build regex for matching delimiters
        // Words require surrounding whitespace/boundary: (?i)\s+(?:feat\.|ft\.|x)\s+
        // Symbols can have optional surrounding whitespace: \s*(?:/|,|&|;)\s*
        val patterns = mutableListOf<Pair<Regex, Boolean>>() // Pair(Regex, isWord)

        if (sortedWords.isNotEmpty()) {
            val wordsGroup = sortedWords.joinToString("|") { Regex.escape(it) }
            // Must have whitespace around the word delimiter
            val wordRegex = Regex("(?i)(?<=\\s)(?:$wordsGroup)(?=\\s)")
            patterns.add(Pair(wordRegex, true))
        }

        if (sortedSymbols.isNotEmpty()) {
            val symbolsGroup = sortedSymbols.joinToString("|") { Regex.escape(it) }
            val symbolRegex = Regex("(?:$symbolsGroup)")
            patterns.add(Pair(symbolRegex, false))
        }

        if (patterns.isEmpty()) {
            val restored = restorePlaceholders(processed, protectedPlaceholders)
            return listOf(Token.Value(restored))
        }

        // Find all matches with their index in processed string
        data class MatchInfo(val start: Int, val end: Int, val text: String, val isWord: Boolean)
        val allMatches = mutableListOf<MatchInfo>()

        for ((regex, isWord) in patterns) {
            for (match in regex.findAll(processed)) {
                allMatches.add(MatchInfo(match.range.first, match.range.last + 1, match.value, isWord))
            }
        }

        // Sort matches by start position, resolving overlaps by taking earliest/longest
        allMatches.sortBy { it.start }
        val nonOverlapping = mutableListOf<MatchInfo>()
        var lastEnd = 0
        for (m in allMatches) {
            if (m.start >= lastEnd) {
                nonOverlapping.add(m)
                lastEnd = m.end
            }
        }

        // Step 3: Build tokens
        val tokens = mutableListOf<Token>()
        var cursor = 0
        for (m in nonOverlapping) {
            if (m.start > cursor) {
                val valuePart = processed.substring(cursor, m.start)
                val restored = restorePlaceholders(valuePart, protectedPlaceholders).trim()
                if (restored.isNotEmpty()) {
                    tokens.add(Token.Value(restored))
                }
            }
            tokens.add(Token.Delimiter(m.text, m.isWord))
            cursor = m.end
        }
        if (cursor < processed.length) {
            val valuePart = processed.substring(cursor)
            val restored = restorePlaceholders(valuePart, protectedPlaceholders).trim()
            if (restored.isNotEmpty()) {
                tokens.add(Token.Value(restored))
            }
        }

        return tokens
    }

    private fun restorePlaceholders(
        text: String,
        placeholders: Map<String, String>
    ): String {
        var restored = text
        for ((ph, original) in placeholders) {
            restored = restored.replace(ph, original)
        }
        // Unescape: clean any remaining backslash before non-alphanumeric characters (\X -> X)
        return restored.replace(Regex("""\\([^\p{L}\p{N}])"""), "$1")
    }
}
