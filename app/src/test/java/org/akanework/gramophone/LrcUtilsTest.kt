package org.akanework.gramophone

import androidx.media3.common.MimeTypes
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.LyricLine
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

	private fun parse(lrcContent: String, trim: Boolean? = null, multiline: Boolean? = null, mustSkip: Boolean? = false): SemanticLyrics? {
		if (trim == null) {
			val a = parse(lrcContent, false, multiline, mustSkip)
			val b = parse(lrcContent, true, multiline, mustSkip)
			assertFalse("trim false and true should result in same type of lyrics", a is SemanticLyrics.SyncedLyrics != b is SemanticLyrics.SyncedLyrics)
			if (b is SemanticLyrics.SyncedLyrics)
				assertEquals("trim false and true should result in same list for this string", (a as SemanticLyrics.SyncedLyrics).text, b.text)
			else
				assertEquals("trim false and true should result in same list for this string", a?.unsyncedText, b?.unsyncedText)
			return a
		}
		if (multiline == null) {
			val a = parse(lrcContent, trim, false, mustSkip)
			val b = parse(lrcContent, trim, true, mustSkip)
			assertFalse("multiline false and true should result in same type of lyrics (trim=$trim)", a is SemanticLyrics.SyncedLyrics != b is SemanticLyrics.SyncedLyrics)
			if (b is SemanticLyrics.SyncedLyrics)
				assertEquals("multiline false and true should result in same list for this string (trim=$trim)", (a as SemanticLyrics.SyncedLyrics).text, b.text)
			else
				assertEquals("multiline false and true should result in same list for this string (trim=$trim)", a?.unsyncedText, b?.unsyncedText)
			return a
		}
		val a = LrcUtils.parseLyrics(lrcContent, MimeTypes.AUDIO_FLAC, LrcUtils.LrcParserOptions(trim, multiline, null), null)
		if (mustSkip != null) {
			if (mustSkip) {
				assertTrue("excepted skip (trim=$trim multiline=$multiline)", a is SemanticLyrics.UnsyncedLyrics)
			} else {
				assertFalse("excepted no skip (trim=$trim multiline=$multiline)", a is SemanticLyrics.UnsyncedLyrics)
			}
		}
		return a
	}

	private fun parseSynced(lrcContent: String, trim: Boolean? = null, multiline: Boolean? = null): List<SemanticLyrics.LyricLine>? {
		return (parse(lrcContent, trim, multiline, mustSkip = false) as SemanticLyrics.SyncedLyrics?)?.text
	}

	private fun lyricArrayToString(lrc: List<SemanticLyrics.LyricLine>?): String {
		val str = StringBuilder()
		if (lrc == null) {
			str.appendLine("null")
		} else {
			str.appendLine("listOf(")
			for (i in lrc) {
				str.appendLine("\tLyricLine(start = ${i.start}uL, text = " +
						"\"\"\"${i.text}\"\"\", words = ${i.words?.let { "mutableListOf(${
							it.joinToString { "SemanticLyrics.Word(timeRange = " +
									"${it.timeRange.first}uL..${it.timeRange.last}uL, charRange = " +
									"${it.charRange.first}..${it.charRange.last}, " +
									"isRtl = ${it.isRtl})" }})"
						} ?: "null"}, speaker = ${i.speaker?.name?.let { "SpeakerEntity.$it" } ?:
						"null"}, end = ${i.end}uL, isTranslated = ${i.isTranslated}, " +
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
		assertEquals(LrcTestData.AS_IT_WAS_PARSED_STR, lyricArrayToString(LrcTestData.AS_IT_WAS_PARSED))
		assertEquals(LrcTestData.AS_IT_WAS_PARSED_STR, lrc)
		val lrc2 = lyricArrayToString(parseSynced(LrcTestData.AM_I_DREAMING, trim = false))
		assertEquals(LrcTestData.AM_I_DREAMING_PARSED_NO_TRIM_STR, lyricArrayToString(LrcTestData.AM_I_DREAMING_PARSED_NO_TRIM))
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
		val lrc = parse(LrcTestData.AS_IT_WAS.replace("\\[(\\d{2}):(\\d{2})([.:]\\d+)?]".toRegex(), "[00:00.00]"), mustSkip = true)
		assertNotNull(lrc)
		assertEquals(LrcTestData.AS_IT_WAS_PARSED.map { it.text }, lrc!!.unsyncedText.map { it.first })
	}

	@Test
	fun testSyntheticNewLineMultiLineParser() {
		//                                                --|-- no newline here
		val lrcS = parseSynced("[11:22.33]hello\ngood morning[33:44.55]how are you?", multiline = false)
		assertNotNull(lrcS)
		val lrcM = parseSynced("[11:22.33]hello\ngood morning[33:44.55]how are you?", multiline = true)
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
		val lrcS = parseSynced("[11:22.33]hello\ngood morning\n[33:44.55]how are you?", multiline = false)
		assertNotNull(lrcS)
		val lrcM = parseSynced("[11:22.33]hello\ngood morning\n[33:44.55]how are you?", multiline = true)
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
		val lrc = parseSynced("[offset:+3][00:00.004]hello\ngood morning\n[00:00.005]how are you?", multiline = true)
		assertNotNull(lrc)
		assertEquals(2, lrc!!.size)
		assertEquals("hello\ngood morning", lrc[0].text)
		assertEquals(1uL, lrc[0].start)
		assertEquals("how are you?", lrc[1].text)
		assertEquals(2uL, lrc[1].start)
	}

	@Test
	fun testBogusOffsetMultiLineParser() {
		val lrc = parseSynced("[offset:+200][00:00.004]hello\ngood morning\n[00:00.005]how are you?", multiline = true)
		assertNotNull(lrc)
		assertEquals(2, lrc!!.size)
		assertEquals("hello\ngood morning", lrc[0].text)
		assertEquals(0uL, lrc[0].start)
		assertEquals("how are you?", lrc[1].text)
		assertEquals(0uL, lrc[1].start)
	}

	@Test
	fun testNegativeOffsetMultiLineParser() {
		val lrc = parseSynced("[offset:-200][00:00.004]hello\ngood morning\n[00:00.005]how are you?", multiline = true)
		assertNotNull(lrc)
		assertEquals(2, lrc!!.size)
		assertEquals("hello\ngood morning", lrc[0].text)
		assertEquals(204uL, lrc[0].start)
		assertEquals("how are you?", lrc[1].text)
		assertEquals(205uL, lrc[1].start)
	}

	@Test
	fun testDualOffsetMultiLineParser() {
		val lrc = parseSynced("[offset:-200][00:00.004]hello\ngood morning\n[offset:+3][00:00.005]how are you?", multiline = true)
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
        val lrc = parseSynced("[00:00.29]It's hard to breathe but that's alright\n[00:04.45]\n[00:04.45]Hush\n[00:14.23]\n[00:16.25]Shh")
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
	fun testTemplateLrcWalaoke() {
		val lrc = parseSynced(LrcTestData.WALAOKE_TEST, trim = true)
		assertNotNull(lrc)
		assertEquals(LrcTestData.WALAOKE_TEST_PARSED, lrc)
	}

	@Test
	fun testCompressedWordScaling() {
		val lrc = parseSynced("[00:00.100][00:10.100]hello<00:00.200>world<00:01.00>lol")
		assertNotNull(lrc)
		assertEquals(2, lrc!!.size)
		assertEquals(100uL, lrc[0].start)
		assertEquals(10100uL, lrc[1].start)
		assertNotNull(lrc[0].words)
		assertEquals(3, lrc[0].words!!.size)
		assertEquals(100uL, lrc[0].words!![0].timeRange.start)
		assertEquals(200uL - 1uL, lrc[0].words!![0].timeRange.last)
		assertEquals(200uL, lrc[0].words!![1].timeRange.start)
		assertEquals(1000uL - 1uL, lrc[0].words!![1].timeRange.last)
		assertEquals(1000uL, lrc[0].words!![2].timeRange.start)
		assertEquals(1270uL, lrc[0].words!![2].timeRange.last)
		assertEquals(10100uL, lrc[1].start)
		assertNotNull(lrc[1].words)
		assertEquals(3, lrc[1].words!!.size)
		assertEquals(10100uL, lrc[1].words!![0].timeRange.start)
		assertEquals(10200uL - 1uL, lrc[1].words!![0].timeRange.last)
		assertEquals(10200uL, lrc[1].words!![1].timeRange.start)
		assertEquals(11000uL - 1uL, lrc[1].words!![1].timeRange.last)
		assertEquals(11000uL, lrc[1].words!![2].timeRange.start)
		assertEquals(11270uL, lrc[1].words!![2].timeRange.last)
	}

	@Test
	fun testCompressedWithSpaces() {
		assertEquals(parseSynced("[00:01.00][00:20.01][00:99.00]Can we find a way back?"),
			parseSynced("[00:01.00] [00:20.01] [00:99.00]Can we find a way back?"))
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
	fun testParserTtmlTemplate() {
		val ttml = parseSynced(LrcTestData.TTML_DEATH_BED)
		assertEquals(LrcTestData.TTML_DEATH_BED_PARSED, ttml)
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
}