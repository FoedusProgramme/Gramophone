/*
 *     Copyright (C) 2025 The Gramophone authors
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

import androidx.media3.common.MimeTypes
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SpeakerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LrcUtilsTest {

    private fun parse(
        lrcContent: String,
        trim: Boolean? = null,
        multiline: Boolean? = null,
        mustSkip: Boolean? = false
    ): SemanticLyrics? {
        if (trim == null) {
            val a = parse(lrcContent, false, multiline, mustSkip)
            val b = parse(lrcContent, true, multiline, mustSkip)
            assertFalse(
                "trim false and true should result in same type of lyrics",
                a is SemanticLyrics.SyncedLyrics != b is SemanticLyrics.SyncedLyrics
            )
            if (b is SemanticLyrics.SyncedLyrics)
                assertEquals(
                    "trim false and true should result in same list for this string",
                    (a as SemanticLyrics.SyncedLyrics).text,
                    b.text
                )
            else
                assertEquals(
                    "trim false and true should result in same list for this string",
                    a?.unsyncedText,
                    b?.unsyncedText
                )
            return a
        }
        if (multiline == null) {
            val a = parse(lrcContent, trim, false, mustSkip)
            val b = parse(lrcContent, trim, true, mustSkip)
            assertFalse(
                "multiline false and true should result in same type of lyrics (trim=$trim)",
                a is SemanticLyrics.SyncedLyrics != b is SemanticLyrics.SyncedLyrics
            )
            if (b is SemanticLyrics.SyncedLyrics)
                assertEquals(
                    "multiline false and true should result in same list for this string (trim=$trim)",
                    (a as SemanticLyrics.SyncedLyrics).text,
                    b.text
                )
            else
                assertEquals(
                    "multiline false and true should result in same list for this string (trim=$trim)",
                    a?.unsyncedText,
                    b?.unsyncedText
                )
            return a
        }
        val a = LrcUtils.parseLyrics(
            lrcContent,
            MimeTypes.AUDIO_FLAC,
            LrcUtils.LrcParserOptions(trim, multiline, null),
            null
        )
        if (mustSkip != null) {
            if (mustSkip) {
                assertTrue(
                    "excepted skip (trim=$trim multiline=$multiline)",
                    a is SemanticLyrics.UnsyncedLyrics
                )
            } else {
                assertFalse(
                    "excepted no skip (trim=$trim multiline=$multiline)",
                    a is SemanticLyrics.UnsyncedLyrics
                )
            }
        }
        return a
    }

    private fun parseSynced(
        lrcContent: String,
        trim: Boolean? = null,
        multiline: Boolean? = null
    ): List<SemanticLyrics.LyricLine>? {
        return (parse(
            lrcContent,
            trim,
            multiline,
            mustSkip = false
        ) as SemanticLyrics.SyncedLyrics?)?.text
    }

    private fun lyricArrayToString(lrc: List<SemanticLyrics.LyricLine>?): String {
        val str = StringBuilder()
        if (lrc == null) {
            str.appendLine("null")
        } else {
            str.appendLine("listOf(")
            for (i in lrc) {
                str.appendLine(
                    "\tLyricLine(start = ${i.start}uL, text = " +
                        "\"\"\"${i.text}\"\"\", words = ${
                            i.words?.let {
                                "mutableListOf(${
                                    it.joinToString { w ->
                                        "SemanticLyrics.Word(timeRange = " +
                                                "${w.timeRange.first}uL..${w.timeRange.last}uL, charRange = " +
                                                "${w.charRange.first}..${w.charRange.last}, " +
                                                "isRtl = ${w.isRtl})"
                                    }
                                })"
                            } ?: "null"
                        }, speaker = ${
                            i.speaker?.name?.let { "SpeakerEntity.$it" } ?: "null"
                        }, end = ${i.end}uL, isTranslated = ${i.isTranslated}, " +
                        "endIsImplicit = ${i.endIsImplicit}),")
            }
            str.appendLine(")")
        }
        return str.toString()
    }

    @Test
    fun emptyInEmptyOut() {
        val emptyLrc = parse("")
        assertNull(emptyLrc)
    }

    @Test
    fun blankInEmptyOut() {
        val blankLrc = parse("   \t  \n    \u00A0")
        assertNull(blankLrc)
    }

    @Test
    fun testPrintUtility() {
        val lrc = lyricArrayToString(parseSynced(LrcTestData.AS_IT_WAS))
        assertEquals(
            LrcTestData.AS_IT_WAS_PARSED_STR,
            lyricArrayToString(LrcTestData.AS_IT_WAS_PARSED)
        )
        assertEquals(LrcTestData.AS_IT_WAS_PARSED_STR, lrc)
        val lrc2 = lyricArrayToString(parseSynced(LrcTestData.AM_I_DREAMING, trim = false))
        assertEquals(
            LrcTestData.AM_I_DREAMING_PARSED_NO_TRIM_STR,
            lyricArrayToString(LrcTestData.AM_I_DREAMING_PARSED_NO_TRIM)
        )
        assertEquals(LrcTestData.AM_I_DREAMING_PARSED_NO_TRIM_STR, lrc2)
    }

    @Test
    fun testTemplateLrc1() {
        val lrc = parseSynced(LrcTestData.AS_IT_WAS)
        assertNotNull(lrc)
        assertEquals(LrcTestData.AS_IT_WAS_PARSED, lrc)
    }

    /*
     * Test the synthetic newline feature. If this is intentionally broken, it's not a big deal.
     * But don't break it accidentally.
     */
    @Test
    fun testTemplateLrcSyntheticNewlines() {
        val lrc = parseSynced(LrcTestData.AS_IT_WAS.replace("\n", ""))
        assertNotNull(lrc)
        assertEquals(LrcTestData.AS_IT_WAS_PARSED, lrc)
    }

    @Test
    fun testTemplateLrc2() {
        val lrc = parseSynced(LrcTestData.AS_IT_WAS + "\n")
        assertNotNull(lrc)
        assertEquals(LrcTestData.AS_IT_WAS_PARSED, lrc)
    }

    @Test
    fun testTemplateLrcTrimToggle() {
        val a = parseSynced(LrcTestData.AS_IT_WAS_NO_TRIM, trim = false)
        val b = parseSynced(LrcTestData.AS_IT_WAS_NO_TRIM, trim = true)
        assertNotEquals(b, a)
        assertEquals(LrcTestData.AS_IT_WAS_NO_TRIM_PARSED_FALSE, a)
        assertEquals(LrcTestData.AS_IT_WAS_NO_TRIM_PARSED_TRUE, b)
    }

    @Test
    fun testTemplateLrcTranslate2Compressed() {
        val lrc = parseSynced(LrcTestData.DREAM_THREAD)
        assertNotNull(lrc)
        assertEquals(LrcTestData.DREAM_THREAD_PARSED, lrc)
    }

    @Test
    fun testTemplateLrcZeroTimestamps() {
        val lrc = parse(
            LrcTestData.AS_IT_WAS.replace(
                "\\[(\\d{2}):(\\d{2})([.:]\\d+)?]".toRegex(),
                "[00:00.00]"
            ), mustSkip = true
        )
        assertNotNull(lrc)
        assertEquals(
            LrcTestData.AS_IT_WAS_PARSED.map { it.text },
            lrc!!.unsyncedText.map { it.first })
    }

    @Test
    fun testSyntheticNewLineMultiLineParser() {
        //                                                --|-- no newline here
        val lrcS =
            parseSynced("[11:22.33]hello\ngood morning[33:44.55]how are you?", multiline = false)
        assertNotNull(lrcS)
        val lrcM =
            parseSynced("[11:22.33]hello\ngood morning[33:44.55]how are you?", multiline = true)
        assertNotNull(lrcM)
        assertNotEquals(lrcS!!, lrcM!!)
        assertEquals(2, lrcS.size)
        assertEquals(2, lrcM.size)
        assertEquals("hello", lrcS[0].text)
        assertEquals("hello\ngood morning", lrcM[0].text)
        assertEquals("how are you?", lrcS[1].text)
        assertEquals("how are you?", lrcM[1].text)
    }

    @Test
    fun testSimpleMultiLineParser() {
        val lrcS =
            parseSynced("[11:22.33]hello\ngood morning\n[33:44.55]how are you?", multiline = false)
        assertNotNull(lrcS)
        val lrcM =
            parseSynced("[11:22.33]hello\ngood morning\n[33:44.55]how are you?", multiline = true)
        assertNotNull(lrcM)
        assertNotEquals(lrcS!!, lrcM!!)
        assertEquals(2, lrcS.size)
        assertEquals(2, lrcM.size)
        assertEquals("hello", lrcS[0].text)
        assertEquals("hello\ngood morning", lrcM[0].text)
        assertEquals("how are you?", lrcS[1].text)
        assertEquals("how are you?", lrcM[1].text)
    }

    @Test
    fun testLongSyncTimestamp() {
        val lrc = parseSynced("[101:56:78]One two three\n[1234:56:78]Four five six")
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        assertEquals("One two three", lrc[0].text)
        assertEquals(6116780uL, lrc[0].start)
        assertEquals("Four five six", lrc[1].text)
        assertEquals(74096780uL, lrc[1].start)
    }

    @Test
    fun testOffsetMultiLineParser() {
        val lrc = parseSynced(
            "[offset:+3][00:00.004]hello\ngood morning\n[00:00.005]how are you?",
            multiline = true
        )
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        assertEquals("hello\ngood morning", lrc[0].text)
        assertEquals(1uL, lrc[0].start)
        assertEquals("how are you?", lrc[1].text)
        assertEquals(2uL, lrc[1].start)
    }

    @Test
    fun testBogusOffsetMultiLineParser() {
        val lrc = parseSynced(
            "[offset:+200][00:00.004]hello\ngood morning\n[00:00.005]how are you?",
            multiline = true
        )
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        assertEquals("hello\ngood morning", lrc[0].text)
        assertEquals(0uL, lrc[0].start)
        assertEquals("how are you?", lrc[1].text)
        assertEquals(0uL, lrc[1].start)
    }

    @Test
    fun testNegativeOffsetMultiLineParser() {
        val lrc = parseSynced(
            "[offset:-200][00:00.004]hello\ngood morning\n[00:00.005]how are you?",
            multiline = true
        )
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        assertEquals("hello\ngood morning", lrc[0].text)
        assertEquals(204uL, lrc[0].start)
        assertEquals("how are you?", lrc[1].text)
        assertEquals(205uL, lrc[1].start)
    }

    @Test
    fun testDualOffsetMultiLineParser() {
        val lrc = parseSynced(
            "[offset:-200][00:00.004]hello\ngood morning\n[offset:+3][00:00.005]how are you?",
            multiline = true
        )
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        // Order is swapped because second timestamp is smaller thanks to offset
        assertEquals("how are you?", lrc[0].text)
        assertEquals(2uL, lrc[0].start)
        assertEquals("hello\ngood morning", lrc[1].text)
        assertEquals(204uL, lrc[1].start)
    }

    @Test
    fun testEmptyLyricNoTranslation() {
        val lrc =
            parseSynced("[00:00.29]It's hard to breathe but that's alright\n[00:04.45]\n[00:04.45]Hush\n[00:14.23]\n[00:16.25]Shh")
        assertNotNull(lrc)
        assertEquals(5, lrc!!.size)

        assertEquals("It's hard to breathe but that's alright", lrc[0].text)
        assertEquals(290uL, lrc[0].start)
        assertEquals(false, lrc[0].isTranslated)
        assertEquals("", lrc[1].text)
        assertEquals(4450uL, lrc[1].start)
        assertEquals(false, lrc[1].isTranslated)
        assertEquals("Hush", lrc[2].text)
        assertEquals(4450uL, lrc[2].start)
        assertEquals(false, lrc[2].isTranslated)
        assertEquals("", lrc[3].text)
        assertEquals(14230uL, lrc[3].start)
        assertEquals(false, lrc[3].isTranslated)
        assertEquals("Shh", lrc[4].text)
        assertEquals(16250uL, lrc[4].start)
        assertEquals(false, lrc[4].isTranslated)
    }

    @Test
    fun testOnlyWordSyncPoints() {
        val lrc = parseSynced("<00:00.02>a<00:01.00>l\n<00:03.00>b")
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        assertEquals("al", lrc[0].text)
        assertEquals(20uL, lrc[0].start)
        assertEquals("b", lrc[1].text)
        assertEquals(3000uL, lrc[1].start)
    }

    @Test
    fun testOneLineOneWord() {
        val lrc = parseSynced("[00:00.02]<00:00.02>a<00:01.00>")
        assertNotNull(lrc)
        assertEquals(1, lrc!!.size)
        assertEquals(false, lrc[0].isTranslated)
        assertEquals("a", lrc[0].text)
        assertEquals(20uL, lrc[0].start)
        assertNotNull(lrc[0].words)
        assertEquals(1, lrc[0].words!!.size)
        assertEquals(0..<1, lrc[0].words!![0].charRange)
        assertEquals(20uL..<1000uL, lrc[0].words!![0].timeRange)
    }

    @Test
    fun testTemplateLrcRenderBenchmark() {
        val lrc = parseSynced(LrcTestData2.RENDER_BENCHMARK, trim = false)
        assertNotNull(lrc)
        assertEquals(LrcTestData2.RENDER_BENCHMARK_PARSED, lrc)
    }

    @Test
    fun testTemplateLrcTranslationType1() {
        val lrc = parseSynced(LrcTestData.ALL_STAR)
        assertNotNull(lrc)
        assertEquals(LrcTestData.ALL_STAR_PARSED, lrc)
    }

    @Test
    fun testTemplateLrcExtendedAppleTrimToggle() {
        val lrc = parseSynced(LrcTestData.AM_I_DREAMING, trim = false)
        val lrc2 = parseSynced(LrcTestData.AM_I_DREAMING, trim = true)
        assertNotNull(lrc)
        assertEquals(LrcTestData.AM_I_DREAMING_PARSED_NO_TRIM, lrc)
        assertEquals(LrcTestData.AM_I_DREAMING_PARSED_TRIM, lrc2)
    }

    @Test
    fun testCompressedWordScaling() {
        val lrc = parseSynced("[00:00.100][00:10.100]hello<00:00.200>world<00:01.00>lol")
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        assertEquals(100uL, lrc[0].start)
        assertEquals(10100uL, lrc[1].start)
        assertEquals(10099uL, lrc[0].end)
        assertEquals(11270uL, lrc[1].end)
        assertNotNull(lrc[0].words)
        assertEquals(3, lrc[0].words!!.size)
        assertEquals(100uL, lrc[0].words!![0].timeRange.first)
        assertEquals(200uL - 1uL, lrc[0].words!![0].timeRange.last)
        assertEquals(200uL, lrc[0].words!![1].timeRange.first)
        assertEquals(1000uL - 1uL, lrc[0].words!![1].timeRange.last)
        assertEquals(1000uL, lrc[0].words!![2].timeRange.first)
        assertEquals(10099uL, lrc[0].words!![2].timeRange.last)
        assertEquals(10100uL, lrc[1].start)
        assertNotNull(lrc[1].words)
        assertEquals(3, lrc[1].words!!.size)
        assertEquals(10100uL, lrc[1].words!![0].timeRange.first)
        assertEquals(10200uL - 1uL, lrc[1].words!![0].timeRange.last)
        assertEquals(10200uL, lrc[1].words!![1].timeRange.first)
        assertEquals(11000uL - 1uL, lrc[1].words!![1].timeRange.last)
        assertEquals(11000uL, lrc[1].words!![2].timeRange.first)
        assertEquals(11270uL, lrc[1].words!![2].timeRange.last)
    }

    @Test
    fun testCompressedWithSpaces() {
        assertEquals(
            parseSynced("[00:01.00][00:20.01][00:99.00]Can we find a way back?"),
            parseSynced("[00:01.00] [00:20.01] [00:99.00]Can we find a way back?")
        )
    }

    @Test
    fun testBidirectionalWordSplitting() {
        parseSynced("[00:13.00] <00:13.00>یکtwo", trim = false) // make sure its not crashing
        val lrc = parseSynced("[00:13.00] <00:13.00>یکtwo", trim = true)
        assertNotNull(lrc)
        assertEquals(1, lrc!!.size)
        assertNotNull(lrc[0].words)
        assertEquals(2, lrc[0].words!!.size)
        assertEquals(0..<2, lrc[0].words!![0].charRange)
        assertEquals(2..<5, lrc[0].words!![1].charRange)
    }

    @Test
    fun testParserSkippedHello() {
        parse("hello", mustSkip = true)
    }

    @Test
    fun testParserSkipped2() {
        parse("2", mustSkip = true)
    }

    @Test
    fun testParserSkippedDoesNotEatNewlines() {
        assertEquals(listOf("Hello" to null, "" to null, "It's me" to null),
            parse("Hello\n\nIt's me", mustSkip = true)!!.unsyncedText)
        assertEquals(listOf("Hello" to null, "" to null, "It's me" to null, "" to null),
            parse("Hello\n\nIt's me\n", mustSkip = true)!!.unsyncedText)
    }

    @Test
    fun testParserTtmlTemplate() {
        val ttml = parseSynced(LrcTestData2.TTML_DEATH_BED)
        assertEquals(LrcTestData2.TTML_DEATH_BED_PARSED, ttml)
    }

    @Test
    fun testParserTtmlTemplate2() {
        val ttml = parseSynced(LrcTestData2.TTML_SATISIFED)
        assertEquals(LrcTestData2.TTML_SATISFIED_PARSED, ttml)
    }

    @Test
    fun tsZeroIsNotTranslated() {
        val lrc = parseSynced("[00:00.00]hello[00:01.00]bye")
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        assertEquals("hello", lrc[0].text)
        assertEquals("bye", lrc[1].text)
        assert(!lrc[0].isTranslated)
        assert(!lrc[1].isTranslated)
    }

    @Test
    fun voiceInsteadOfVoice1WhenNoVoice2() {
        val lrc = parseSynced("[00:00.00]v1:hello\n[bg:[00:01.00]bye]")
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        assertEquals("hello", lrc[0].text)
        assertEquals("bye", lrc[1].text)
        assertEquals(SpeakerEntity.Voice, lrc[0].speaker)
        assertEquals(SpeakerEntity.VoiceBackground, lrc[1].speaker)
    }

    @Test
    fun voice1WhenThereIsVoice2() {
        val lrc = parseSynced("[00:00.00]v1:hello\n[bg:[00:01.00]bye]\n[00:02.00]v2:hello\n[bg:[00:03.00]bye]")
        assertNotNull(lrc)
        assertEquals(4, lrc!!.size)
        assertEquals("hello", lrc[0].text)
        assertEquals("bye", lrc[1].text)
        assertEquals("hello", lrc[2].text)
        assertEquals("bye", lrc[3].text)
        assertEquals(SpeakerEntity.Voice1, lrc[0].speaker)
        assertEquals(SpeakerEntity.Voice1Background, lrc[1].speaker)
        assertEquals(SpeakerEntity.Voice2, lrc[2].speaker)
        assertEquals(SpeakerEntity.Voice2Background, lrc[3].speaker)
    }

    @Test
    fun explicitEndFromWordRecognized() {
        val lrc = parseSynced("[00:00.00][00:10.00]<00:01.00>hello<00:02.00><00:03.00>")
        assertNotNull(lrc)
        assertEquals(2, lrc!!.size)
        assertEquals("hello", lrc[0].text)
        assertEquals(0uL, lrc[0].start)
        assertNotNull(lrc[0].words)
        assertEquals(1, lrc[0].words!!.size)
        assertEquals(1000uL..1999uL, lrc[0].words!![0].timeRange)
        assertEquals(2999uL, lrc[0].end)
        assertEquals("hello", lrc[1].text)
        assertEquals(10000uL, lrc[1].start)
        assertNotNull(lrc[1].words)
        assertEquals(1, lrc[1].words!!.size)
        assertEquals(11000uL..11999uL, lrc[1].words!![0].timeRange)
        assertEquals(12999uL, lrc[1].end)
    }

    @Test
    fun testBracketWordSync() {
        val lrcStr = "[00:17.12]走[00:17.30]廊[00:17.47]灯[00:17.64]关[00:17.85]上 [00:18.25]书[00:18.46]包[00:18.66]放[00:19.19]"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(1, parsed!!.text.size)
        val line = parsed.text[0]
        assertEquals("走廊灯关上 书包放", line.text)
        assertEquals(17120uL, line.start)
        assertNotNull(line.words)
        val words = line.words!!
        assertEquals(8, words.size)
        assertEquals("走", line.text.substring(words[0].charRange))
        assertEquals(17120uL..17299uL, words[0].timeRange)
        assertEquals("廊", line.text.substring(words[1].charRange))
        assertEquals(17300uL..17469uL, words[1].timeRange)
        assertEquals("放", line.text.substring(words[7].charRange))
        assertEquals(18660uL..19189uL, words[7].timeRange)
        assertEquals(19189uL, line.end)
    }

    @Test
    fun testBracketWordSyncDisabled() {
        val lrcStr = "[00:17.12]走[00:17.30]廊[00:17.47]灯[00:17.64]关[00:17.85]上 [00:18.25]书[00:18.46]包[00:18.66]放[00:19.19]"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = false
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(9, parsed!!.text.size)
        assertEquals("走", parsed.text[0].text)
        assertEquals(17120uL, parsed.text[0].start)
    }

    @Test
    fun testLineLevelClosedWithTimestamp() {
        val lrcStr = "[00:01.10]Hello World[00:06.13]"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(1, parsed!!.text.size)
        val line = parsed.text[0]
        assertEquals("Hello World", line.text)
        assertEquals(1100uL, line.start)
        assertEquals(6130uL, line.end)
        assertFalse(line.endIsImplicit)
        assertNull(line.words)
    }

    @Test
    fun testProducerLineNoWords() {
        val lrcStr = "[00:04.28]词：周杰伦"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(1, parsed!!.text.size)
        val line = parsed.text[0]
        assertEquals("词：周杰伦", line.text)
        assertEquals(4280uL, line.start)
        assertNull(line.words)
    }

    @Test
    fun testBilingualLineEndSync() {
        val lrcStr = "[00:10.00]Hello world[00:15.00]\n[00:10.00]你好世界[00:15.00]"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(2, parsed!!.text.size)
        val line1 = parsed.text[0]
        val line2 = parsed.text[1]
        assertEquals("Hello world", line1.text)
        assertEquals(10000uL, line1.start)
        assertEquals(15000uL, line1.end)
        assertFalse(line1.isTranslated)
        assertNull(line1.words)

        assertEquals("你好世界", line2.text)
        assertEquals(10000uL, line2.start)
        assertEquals(15000uL, line2.end)
        assertTrue(line2.isTranslated)
        assertNull(line2.words)
    }

    @Test
    fun testNormalSingleTag() {
        val lrcStr = "[00:01.00]First line\n[00:05.00]Second line"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(2, parsed!!.text.size)
        assertEquals("First line", parsed.text[0].text)
        assertEquals(1000uL, parsed.text[0].start)
        assertNull(parsed.text[0].words)
        assertTrue(parsed.text[0].endIsImplicit)

        assertEquals("Second line", parsed.text[1].text)
        assertEquals(5000uL, parsed.text[1].start)
        assertNull(parsed.text[1].words)
        assertTrue(parsed.text[1].endIsImplicit)
    }

    @Test
    fun testLineLevelClosedWithTrailingWhitespace() {
        val lrcStr = "[00:04.28]词：周杰伦[00:06.13]   \n[00:08.56]下一句"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(2, parsed!!.text.size)
        val line1 = parsed.text[0]
        assertEquals("词：周杰伦", line1.text)
        assertEquals(4280uL, line1.start)
        assertEquals(6130uL, line1.end)
        // Ensure words is strictly null and NOT an empty list
        assertNull(line1.words)
    }

    @Test
    fun testDegenerateZeroDurationLine() {
        val lrcStr = "[00:00.00]A[00:00.00]\n[00:01.00]Valid line[00:03.00]"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(2, parsed!!.text.size)
        assertEquals("A", parsed.text[0].text)
        assertEquals(0uL, parsed.text[0].start)
        assertEquals("Valid line", parsed.text[1].text)
        assertEquals(1000uL, parsed.text[1].start)
        assertEquals(3000uL, parsed.text[1].end)
    }

    @Test
    fun testCompressedMultiLineClosed() {
        val lrcStr = "[00:01.00][00:03.00]Hello[00:06.00]"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(2, parsed!!.text.size)
        assertEquals(1000uL, parsed.text[0].start)
        assertEquals(6000uL, parsed.text[0].end)
        assertEquals("Hello", parsed.text[0].text)
        assertEquals(3000uL, parsed.text[1].start)
        assertEquals(8000uL, parsed.text[1].end)
        assertEquals("Hello", parsed.text[1].text)
    }

    @Test
    fun testWordSyncLastWordNoEndTag() {
        val lrcStr = "[00:01.00]A[00:02.00]B[00:03.00]C"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(1, parsed!!.text.size)
        val line = parsed.text[0]
        assertEquals("ABC", line.text)
        assertNotNull(line.words)
        val words = line.words!!
        assertEquals(3, words.size)
        assertEquals("A", line.text.substring(words[0].charRange))
        assertEquals(1000uL, words[0].begin)
        assertEquals(1999uL, words[0].endInclusive)
        assertEquals("B", line.text.substring(words[1].charRange))
        assertEquals(2000uL, words[1].begin)
        assertEquals(2999uL, words[1].endInclusive)
        assertEquals("C", line.text.substring(words[2].charRange))
        assertEquals(3000uL, words[2].begin)
    }

    @Test
    fun testTrilingualSameTimestampBlock() {
        val lrcStr = "[00:10.00]Hello[00:15.00]\n[00:10.00]你好[00:15.00]\n[00:10.00]Konnichiwa[00:15.00]"
        val parsed = LrcUtils.parseLyrics(
            lrcStr,
            audioMimeType = null,
            parserOptions = LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = true,
                errorText = null,
                bracketWordSync = true
            ),
            format = LrcUtils.LyricFormat.LRC
        ) as? SemanticLyrics.SyncedLyrics
        assertNotNull(parsed)
        assertEquals(3, parsed!!.text.size)
        assertEquals("Hello", parsed.text[0].text)
        assertFalse(parsed.text[0].isTranslated)
        assertEquals("你好", parsed.text[1].text)
        assertTrue(parsed.text[1].isTranslated)
        assertEquals("Konnichiwa", parsed.text[2].text)
        assertTrue(parsed.text[2].isTranslated)
    }
}