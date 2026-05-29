package org.akanework.gramophone.logic.utils

import android.os.Parcel
import android.os.Parcelable
import android.util.Xml
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Log
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.subrip.SubripParser
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import org.akanework.gramophone.logic.forEachSupport
import org.akanework.gramophone.logic.utils.SemanticLyrics.LyricLine
import org.akanework.gramophone.logic.utils.SemanticLyrics.SyncedLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.UnsyncedLyrics
import org.akanework.gramophone.logic.utils.SemanticLyrics.Word
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

private const val TAG = "SemanticLyrics"

/*
 * Syntactic-semantic lyric parser.
 *   First parse lrc syntax into custom objects, then parse these into usable representation
 *   for playback. This should be more testable and stable than the old parser.
 *
 * Formats we have to consider in this parser are:
 *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
 *  - "compressed LRC" with >1 tag for repeating line ex: [00:11.22][00:15.33] hello i am lyric
 *  - Invalid LRC with all-zero tags [00:00.00] hello i am lyric
 *  - Lyrics that aren't synced and have no tags at all
 *  - Translations, type 1 (ex: pasting first japanese and then english lrc file into one file)
 *     - This implies multiple [offset:] tags are possible
 *  - Translations, type 2 (ex: translated line directly under previous non-translated line)
 *  - The timestamps can variate in the following ways: [00:11] [00:11:22] [00:11.22] [00:11.222] [00:11:222]
 *  - Multiline format: This technically isn't part of any listed guidelines, however is allows for
 *      reading of otherwise discarded lyrics, all the lines between sync point A and B are read as
 *      lyric text of A
 *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - iTunes dual speaker extension (v1: / v2: / [bg: ])
 *  - [offset:] tag in header (ref Wikipedia)
 * We completely ignore all ID3 tags from the header as MediaStore is our source of truth.
 */

// This does not encode the full range of possible information in the TTML format, only what's used
// for actual rendering. This could change in the future, but for now it's like this.
@Parcelize
enum class SpeakerEntity(
    val isVoice2: Boolean = false, // align opposite
    val isGroup: Boolean = false, // center
    val isBackground: Boolean = false, // small
    val isWidthLimited: Boolean = false,
) : Parcelable {
    // Voice is used if there should be no width limit as it's the only person, Voice1 is used if
    // there are more and hence width limit should be applied.
    Voice,
    VoiceBackground(isBackground = true),
    Voice1(isWidthLimited = true),
    Voice1Background(isWidthLimited = true, isBackground = true),
    Voice2(isWidthLimited = true, isVoice2 = true),
    Voice2Background(isWidthLimited = true, isVoice2 = true, isBackground = true),
    Group(isGroup = true),
    GroupBackground(isGroup = true, isBackground = true)
}

/*
 * Syntactic lyric parser. Parses lrc syntax into custom objects.
 *
 * Formats we have to consider in this component are:
 *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
 *  - Invalid LRC with all-zero tags [00:00.00] hello i am lyric
 *  - Lyrics that aren't synced and have no tags at all
 *  - The timestamps can variate in the following ways: [00:11] [00:11:22] [00:11.22] [00:11.222] [00:11:222]
 *  - Multiline format: This technically isn't part of any listed guidelines, however is allows for
 *      reading of otherwise discarded lyrics, all the lines between sync point A and B are read as
 *      lyric text of A
 *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Extended LRC without sync points ex: <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - iTunes dual speaker extension (v1: / v2: / [bg: ])
 *  - Metadata tags in header (ref Wikipedia)
 */
private sealed class SyntacticLrc {
    // all timestamps are in milliseconds ignoring offset
    data class SyncPoint(val timestamp: ULong) : SyntacticLrc()
    data class SpeakerTag(val speaker: SpeakerEntity) : SyntacticLrc()
    data class WordSyncPoint(val timestamp: ULong) : SyntacticLrc()
    data class Metadata(val name: String, val value: String) : SyntacticLrc()
    data class LyricText(val text: String) : SyntacticLrc()
    data class InvalidText(val text: String) : SyntacticLrc()
    open class NewLine() : SyntacticLrc() {
        class SyntheticNewLine : NewLine()
    }

    companion object {
        // also eats space if present
        val timeMarksRegex = "\\[(\\d+):(\\d{2})([.:]\\d+)?]".toRegex()
        val timeMarksAfterWsRegex = "([ \t]+)\\[(\\d+):(\\d{2})([.:]\\d+)?]".toRegex()
        val timeWordMarksRegex = "<(\\d+):(\\d{2})([.:]\\d+)?>".toRegex()
        val metadataRegex = "\\[([a-zA-Z#]+):([^]]*)]".toRegex()

        private fun parseTime(match: MatchResult): ULong {
            val minute = match.groupValues[1].toULong()
            val milliseconds = ((match.groupValues[2] + match.groupValues[3]
                .replace(':', '.')).toDouble() * 1000L).toULong()
            return minute * 60u * 1000u + milliseconds
        }

        fun parseLrc(text: String, multiLineEnabled: Boolean): List<SyntacticLrc>? {
            if (text.isBlank()) return null
            var pos = 0
            val out = mutableListOf<SyntacticLrc>()
            var isBgSpeaker = false
            while (pos < text.length) {
                var pendingBgNewLine = false
                if (isBgSpeaker && text[pos] == ']') {
                    pos++
                    isBgSpeaker = false
                    pendingBgNewLine = true
                }
                if (pos < text.length && pos + 1 < text.length && text.regionMatches(
                        pos,
                        "\r\n",
                        0,
                        2
                    )
                ) {
                    out.add(NewLine())
                    pos += 2
                    continue
                }
                if (pos < text.length && (text[pos] == '\n' || text[pos] == '\r')) {
                    out.add(NewLine())
                    pos++
                    continue
                }
                if (pendingBgNewLine) {
                    out.add(NewLine.SyntheticNewLine())
                    continue
                }
                val tmMatch = timeMarksRegex.matchAt(text, pos)
                if (tmMatch != null) {
                    // Insert synthetic newlines at places where we'd expect one. This won't ever
                    // work with word lyrics without normal sync points at all for obvious reasons,
                    // but hey, we tried. Can't do much about it.
                    // If you want to write something that looks like a timestamp into your lyrics,
                    // you'll probably have to delete the following three lines.
                    val lastOrNull = out.lastOrNull()
                    if (!(lastOrNull is NewLine? || lastOrNull is SyncPoint
                                || (lastOrNull is SpeakerTag && lastOrNull.speaker.isBackground)))
                        out.add(NewLine.SyntheticNewLine())
                    out.add(SyncPoint(parseTime(tmMatch)))
                    pos += tmMatch.value.length
                    continue
                }
                // Skip spaces in between of compressed lyric sync points. They really are
                // completely useless information we can and should discard.
                val tmwMatch = timeMarksAfterWsRegex.matchAt(text, pos)
                if (out.lastOrNull() is SyncPoint && pos + 7 < text.length && tmwMatch != null) {
                    pos += tmwMatch.groupValues[1].length
                    continue
                }
                // Speaker points can only appear directly after a sync point
                if (out.lastOrNull() is SyncPoint) {
                    if (pos + 2 < text.length && text.regionMatches(pos, "v1:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice1))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, "v2:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice2))
                        pos += 3
                        continue
                    }
                    if (pos + 2 < text.length && text.regionMatches(pos, "v3:", 0, 3)) {
                        out.add(SpeakerTag(SpeakerEntity.Group))
                        pos += 3
                        continue
                    }
                    if (pos + 3 < text.length && text.regionMatches(pos, " v1:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice1))
                        pos += 4
                        continue
                    }
                    if (pos + 3 < text.length && text.regionMatches(pos, " v2:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Voice2))
                        pos += 4
                        continue
                    }
                    if (pos + 3 < text.length && text.regionMatches(pos, " v3:", 0, 4)) {
                        out.add(SpeakerTag(SpeakerEntity.Group))
                        pos += 4
                        continue
                    }
                }
                // Metadata (or the bg speaker, which looks like metadata) can only appear in the
                // beginning of a file or after newlines
                if (pos + 3 < text.length && text.regionMatches(pos, "[bg:", 0, 4)) {
                    // Insert synthetic newlines at places where we'd expect one.
                    // If you want to write [bg: into your lyrics, you'll probably have to add the
                    // conditional surrounding this comment into the
                    // if (out.isEmpty() || out.last() is NewLine) below.
                    if (out.isNotEmpty() && out.last() !is NewLine)
                        out.add(NewLine.SyntheticNewLine())
                    val lastSpeaker = if (out.isNotEmpty()) out.subList(0, out.size - 1)
                        .indexOfLast { it is NewLine }.let { if (it < 0) null else it }?.let {
                            (out.subList(it, out.size - 1).findLast { it is SpeakerTag }
                                    as SpeakerTag?)?.speaker
                        } else null
                    // TODO revisit this heuristic. iTunes bg lines are a child of the last main
                    //  line and there is no "opposite" flag for background lines, but reality does
                    //  not work that way. can main lines be empty in iTunes' lyrics?
                    out.add(
                        SpeakerTag(
                            when {
                                lastSpeaker?.isGroup == true -> SpeakerEntity.GroupBackground
                                lastSpeaker?.isVoice2 == true -> SpeakerEntity.Voice2Background
                                else -> SpeakerEntity.Voice1Background
                            }
                        )
                    )
                    pos += 4
                    isBgSpeaker = true
                    continue
                }
                if (out.isEmpty() || out.last() is NewLine) {
                    val mmMatch = metadataRegex.matchAt(text, pos)
                    if (mmMatch != null) {
                        out.add(Metadata(mmMatch.groupValues[1], mmMatch.groupValues[2]))
                        pos += mmMatch.value.length
                        continue
                    }
                }
                // Word marks can be in any lyric text, and in some cases there are no sync points
                // but only word marks in a lrc file.
                val wmMatch = timeWordMarksRegex.matchAt(text, pos)
                if (wmMatch != null) {
                    out.add(WordSyncPoint(parseTime(wmMatch)))
                    pos += wmMatch.value.length
                    continue
                }
                val firstUnsafeCharPos = (text.substring(pos).indexOfFirst {
                    it == '[' ||
                            it == '<' || it == '\r' || it == '\n' || (isBgSpeaker && it == ']')
                } + pos)
                    .let { if (it == pos - 1) text.length else it }
                    .let { if (it == pos) it + 1 else it }
                val subText = text.substring(pos, firstUnsafeCharPos)
                val last = out.lastOrNull()
                // Only count lyric text as lyric text if there is at least one kind of timestamp
                // associated.
                if (out.indexOfLast { it is NewLine } <
                    out.indexOfLast { it is SyncPoint || it is WordSyncPoint }) {
                    if (last is LyricText) {
                        out[out.size - 1] = LyricText(last.text + subText)
                    } else {
                        out.add(LyricText(subText))
                    }
                } else {
                    if (last is InvalidText) {
                        out[out.size - 1] = InvalidText(last.text + subText)
                    } else {
                        out.add(InvalidText(subText))
                    }
                }
                pos = firstUnsafeCharPos
            }
            if (out.lastOrNull() is SyncPoint)
                out.add(InvalidText(""))
            if (out.isNotEmpty() && out.last() !is NewLine)
                out.add(NewLine.SyntheticNewLine())
            return out.let {
                // If there isn't a single sync point with timestamp over zero, that is probably not
                // a valid .lrc file.
                if (it.find {
                        it is SyncPoint && it.timestamp > 0u
                                || it is WordSyncPoint && it.timestamp > 0u
                    } == null)
                // Recover only text information to make the most out of this damaged file.
                    it.flatMap {
                        when (it) {
                            is InvalidText -> listOf(it)
                            is SpeakerTag -> listOf(it)
                            is LyricText -> listOf(InvalidText(it.text))
                            else -> listOf()
                        }
                    }
                else it
            }.let {
                if (multiLineEnabled) {
                    val a = AtomicReference<String?>(null)
                    it.flatMap {
                        val aa = a.get()
                        when {
                            it is LyricText -> {
                                if (aa == null)
                                    a.set(it.text)
                                else
                                    a.set(aa + it.text)
                                listOf()
                            }
                            // make sure InvalidText that can't be lyric text isn't saved as lyric
                            it is InvalidText && aa != null -> {
                                a.set(aa + it.text)
                                listOf()
                            }

                            it is NewLine && aa != null -> {
                                a.set(aa + "\n")
                                listOf()
                            }

                            aa != null -> {
                                a.set(null)
                                var aaa: String = aa
                                var i = 0
                                while (aaa.last() == '\n') {
                                    i++
                                    aaa = aaa.dropLast(1)
                                }
                                listOf(LyricText(aaa)).let {
                                    var aaaa: List<SyntacticLrc> = it
                                    while (i-- > 0)
                                        aaaa = aaaa + listOf(NewLine())
                                    aaaa
                                } + it
                            }

                            else -> listOf(it)
                        }
                    }.let {
                        if (a.get() != null)
                            it + if (a.get()!!.last() == '\n')
                                listOf(LyricText(a.get()!!.dropLast(1)), NewLine())
                            else
                                listOf(LyricText(a.get()!!))
                        else it
                    }
                } else it
            }
        }
    }
}

private fun splitBidirectionalWords(syncedLyrics: SyncedLyrics) {
    syncedLyrics.text.forEach { line ->
        if (line.words.isNullOrEmpty()) return@forEach
        val bidirectionalBarriers = findBidirectionalBarriers(line.text)
        var lastWasRtl = false
        bidirectionalBarriers.forEach { barrier ->
            val evilWordIndex =
                if (barrier.first == -1) -1 else line.words.indexOfFirst {
                    it.charRange.contains(barrier.first) && it.charRange.first != barrier.first
                }
            if (evilWordIndex == -1) {
                // Propagate the new direction (if there is a barrier after that, direction will
                // be corrected after it).
                val wordIndex = if (barrier.first == -1) 0 else
                    line.words.indexOfFirst { it.charRange.first == barrier.first }
                line.words.forEachSupport(skipFirst = wordIndex) {
                    it.isRtl = barrier.second
                }
                lastWasRtl = barrier.second
                return@forEach
            }
            val evilWord = line.words[evilWordIndex]
            // Estimate how long this word will take based on character to time ratio. To avoid
            // this estimation, add a word sync point to bidirectional barriers :)
            val barrierTime = min(evilWord.timeRange.first + ((line.words.map {
                it.timeRange.count() / it.charRange.count().toFloat()
            }.average().let { if (it.isNaN()) 100.0 else it } * (barrier.first -
                    evilWord.charRange.first))).toULong(), evilWord.timeRange.last - 1uL)
            val firstPart = Word(
                charRange = evilWord.charRange.first..<barrier.first,
                timeRange = evilWord.timeRange.first..<barrierTime, isRtl = lastWasRtl
            )
            val secondPart = Word(
                charRange = barrier.first..evilWord.charRange.last,
                timeRange = barrierTime..evilWord.timeRange.last, isRtl = barrier.second
            )
            line.words[evilWordIndex] = firstPart
            line.words.add(evilWordIndex + 1, secondPart)
            lastWasRtl = barrier.second
        }
    }
}

private val ltr =
    arrayOf(
        Character.DIRECTIONALITY_LEFT_TO_RIGHT,
        Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
        Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
    )
private val rtl =
    arrayOf(
        Character.DIRECTIONALITY_RIGHT_TO_LEFT,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
        Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE
    )

fun findBidirectionalBarriers(text: CharSequence): List<Pair<Int, Boolean>> {
    val barriers = mutableListOf<Pair<Int, Boolean>>()
    if (text.isEmpty()) return barriers
    var previousDirection = text.find {
        val dir = Character.getDirectionality(it)
        dir in ltr || dir in rtl
    }?.let { Character.getDirectionality(it) in rtl } == true
    barriers.add(Pair(-1, previousDirection))
    for (i in 0 until text.length) {
        val currentDirection = Character.getDirectionality(text[i])
        val isRtl = currentDirection in rtl
        if (currentDirection !in ltr && !isRtl)
            continue
        if (previousDirection != isRtl)
            barriers.add(Pair(i, isRtl))
        previousDirection = isRtl
    }
    return barriers
}


/*
 * Syntactic lyric parser. Parse custom objects into usable representation for playback.
 *
 * Formats we have to consider in this component are:
 *  - Simple LRC files (ref Wikipedia) ex: [00:11.22] hello i am lyric
 *  - "compressed LRC" with >1 tag for repeating line ex: [00:11.22][00:15.33] hello i am lyric
 *  - Lyrics that aren't synced and have no tags at all
 *  - Translations, type 1 (ex: pasting first japanese and then english lrc file into one file)
 *     - This implies multiple [offset:] tags are possible
 *  - Translations, type 2 (ex: translated line directly under previous non-translated line)
 *  - Extended LRC (ref Wikipedia) ex: [00:11.22] <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - Extended LRC without sync points ex: <00:11.22> hello <00:12.85> i am <00:13.23> lyric
 *  - iTunes dual speaker extension (v1: / v2: / [bg: ])
 *  - [offset:] tag in header (ref Wikipedia)
 * We completely ignore all ID3 tags from the header as MediaStore is our source of truth.
 */
sealed class SemanticLyrics : Parcelable {
    abstract val unsyncedText: List<Pair<String, SpeakerEntity?>>

    @Parcelize
    data class UnsyncedLyrics(override val unsyncedText: List<Pair<String, SpeakerEntity?>>) :
        SemanticLyrics()

    @Parcelize
    data class SyncedLyrics(val text: List<LyricLine>) : SemanticLyrics() {
        override val unsyncedText
            get() = text.map { it.text to it.speaker }
    }

    @Parcelize
    data class LyricLine(
        val text: String,
        val start: ULong,
        var end: ULong,
        var endIsImplicit: Boolean,
        val words: MutableList<Word>?,
        var speaker: SpeakerEntity?,
        var isTranslated: Boolean
    ) : Parcelable {
        val isClickable: Boolean
            get() = text.isNotBlank()
        val timeRange: ULongRange
            get() = start..end
    }

    @Parcelize
    data class Word(
        var begin: ULong,
        var endInclusive: ULong?,
        var charRange: @WriteWith<IntRangeParceler>() IntRange,
        var isRtl: Boolean
    ) : Parcelable {
        constructor(timeRange: ULongRange, charRange: IntRange, isRtl: Boolean)
                : this(timeRange.first, timeRange.last, charRange, isRtl)

        val timeRange
            get() = begin..endInclusive!!
    }

    object IntRangeParceler : Parceler<IntRange> {
        override fun create(parcel: Parcel) = parcel.readInt()..parcel.readInt()

        override fun IntRange.write(parcel: Parcel, flags: Int) {
            parcel.writeInt(first)
            parcel.writeInt(last)
        }
    }
}

fun parseLrc(lyricText: String, trimEnabled: Boolean, multiLineEnabled: Boolean): SemanticLyrics? {
    val lyricSyntax = SyntacticLrc.parseLrc(lyricText, multiLineEnabled)
        ?: return null
    if (lyricSyntax.find { it is SyntacticLrc.SyncPoint || it is SyntacticLrc.WordSyncPoint } == null) {
        var lastSpeakerTag: SpeakerEntity? = null
        val out = mutableListOf<Pair<String, SpeakerEntity?>>()
        for (element in lyricSyntax) {
            when (element) {
                is SyntacticLrc.SpeakerTag -> {
                    lastSpeakerTag = element.speaker
                }

                is SyntacticLrc.InvalidText -> {
                    out += element.text to lastSpeakerTag
                    lastSpeakerTag = null
                }

                else -> throw IllegalStateException("unexpected type ${element.javaClass.name}")
            }
        }
        while (out.firstOrNull()?.first?.isBlank() == true)
            out.removeAt(0)
        //while (out.lastOrNull()?.first?.isBlank() == true)
        //    out.removeAt(out.lastIndex) TODO this breaks unit tests, but blank lines are useless
        return UnsyncedLyrics(out)
    }
    // Synced lyrics processing state machine starts here
    val out = mutableListOf<LyricLine>()
    var offset = 0L
    var lastSyncPoint: ULong? = null
    var lastWordSyncPoint: ULong? = null
    var speaker: SpeakerEntity? = null
    var hadVoice2 = false
    var hadLyricSinceWordSync = true
    var hadWordSyncSinceNewLine = false
    val currentLine = mutableListOf<Pair<ULong, String?>>()
    var syncPointStreak = 0
    val compressed = mutableListOf<ULong>()
    for (element in lyricSyntax) {
        if (element is SyntacticLrc.SyncPoint)
            syncPointStreak++
        else
            syncPointStreak = 0
        when (element) {
            is SyntacticLrc.Metadata if element.name == "offset" -> {
                // positive offset means lyric played earlier in lrc, hence multiply with -1
                offset = element.value.toLong() * -1
            }

            is SyntacticLrc.SyncPoint -> {
                val ts = (element.timestamp.toLong() + offset).coerceAtLeast(0).toULong()
                if (syncPointStreak > 1) {
                    compressed.add(ts)
                } else {
                    if (compressed.isNotEmpty())
                        throw IllegalStateException("while parsing lrc, $compressed not empty but syncPointStreak is 1; lrc file: $lyricText")
                    lastSyncPoint = ts
                }
            }

            is SyntacticLrc.SpeakerTag -> {
                speaker = element.speaker
                if (element.speaker.isVoice2) {
                    hadVoice2 = true
                }
            }

            is SyntacticLrc.WordSyncPoint -> {
                if (!hadLyricSinceWordSync && lastWordSyncPoint != null)
                // add a dummy word for preserving end timing of previous word
                    currentLine.add(Pair(lastWordSyncPoint, null))
                lastWordSyncPoint = (element.timestamp.toLong() + offset).coerceAtLeast(0).toULong()
                if (lastSyncPoint == null)
                    lastSyncPoint = lastWordSyncPoint
                hadLyricSinceWordSync = false
                hadWordSyncSinceNewLine = true
            }

            is SyntacticLrc.LyricText -> {
                hadLyricSinceWordSync = true
                currentLine.add(Pair(lastWordSyncPoint ?: lastSyncPoint!!, element.text))
            }

            is SyntacticLrc.NewLine -> {
                val words = if (currentLine.size > 1 || hadWordSyncSinceNewLine) {
                    val wout = mutableListOf<Word>()
                    var idx = 0
                    for (i in currentLine.indices) {
                        val current = currentLine[i]
                        if (current.second == null)
                            continue // skip dummy words that only exist to provide time
                        val oIdx = idx
                        idx += current.second!!.length
                        // Make sure we do NOT include whitespace as part of the word. Whitespaces
                        // do not have a strong bi-di flag assigned and hence ICU4J/AndroidBidi will
                        // set bi-di transition point before whitespace. Rendering relies on being
                        // able to change edge treatment with trailing flag in Layout which only
                        // works on bi-di transition points. Additionally, excluding whitespace
                        // allows us to scale gradient properly based on asking ourselves if the
                        // next char is even rendered (or whitespace).
                        val textWithoutStartWhitespace = current.second!!.trimStart()
                        val startWhitespaceLength =
                            current.second!!.length - textWithoutStartWhitespace.length
                        val textWithoutWhitespaces = textWithoutStartWhitespace.trimEnd()
                        val endWhitespaceLength =
                            textWithoutStartWhitespace.length - textWithoutWhitespaces.length
                        val startIndex = oIdx + startWhitespaceLength
                        val endIndex = idx - endWhitespaceLength
                        if (startIndex == endIndex)
                            continue // word contained only whitespace
                        val endInclusive = if (i + 1 < currentLine.size) {
                            // If we have a next word (with sync point), use its sync
                            // point minus 1ms as end point of this word
                            currentLine[i + 1].first - 1uL
                        } else if (lastWordSyncPoint != null &&
                            lastWordSyncPoint > current.first
                        ) {
                            // If we have a dedicated sync point just for the last word,
                            // use it. Similar to dummy words but for the last word only
                            lastWordSyncPoint - 1uL // minus 1ms for consistency
                        } else null /* filled later */
                        if (endInclusive == null || endInclusive > current.first)
                        // isRtl is filled in later in splitBidirectionalWords
                            wout.add(
                                Word(
                                    current.first, endInclusive,
                                    startIndex..<endIndex,
                                    isRtl = false
                                )
                            )
                    }
                    wout
                } else null
                if (currentLine.isNotEmpty() || lastWordSyncPoint != null || lastSyncPoint != null) {
                    var text = currentLine.joinToString("") { it.second ?: "" }
                    if (trimEnabled) {
                        val orig = text
                        text = orig.trimStart()
                        val startDiff = orig.length - text.length
                        text = text.trimEnd()
                        val iter = words?.listIterator()
                        iter?.forEach {
                            if (it.charRange.last.toLong() - startDiff < 0
                                || it.charRange.first.toLong() - startDiff >= text.length
                            )
                                iter.remove()
                            else
                                it.charRange = (it.charRange.first - startDiff)
                                    .coerceAtLeast(0)..(it.charRange.last - startDiff)
                                    .coerceAtMost(text.length - 1)
                        }
                    }
                    val start = if (currentLine.isNotEmpty()) lastSyncPoint
                        ?: currentLine.first().first
                    else lastWordSyncPoint ?: lastSyncPoint!!
                    // if we had trailing word sync point after the last word was already ended,
                    // it's an explicit line end ts, so use it. otherwise, use the last word's sync
                    // point as end time. otherwise we will fill it later based on next (temporal,
                    // not file order) line.
                    val lastWordSyncForEnd = lastWordSyncPoint?.let { it - 1uL }
                    val lastWordBegin = words?.lastOrNull()?.begin
                    val end = if (lastWordSyncForEnd != null && lastWordBegin != null &&
                        lastWordBegin < lastWordSyncForEnd
                    ) lastWordSyncForEnd else null
                    out.add(
                        LyricLine(
                            text, start, end ?: 0uL /* filled later */,
                            end == null, words, speaker, false /* filled later */
                        )
                    )
                    compressed.forEach {
                        val diff = it - start
                        out.add(
                            out.last().copy(
                                start = it,
                            end = end?.plus(diff) ?: 0uL /* filled later */,
                            words = words?.map {
                                it.copy(
                                    begin = it.begin + diff,
                                    endInclusive = it.endInclusive?.plus(diff)
                                )
                            }?.toMutableList()
                        )
                        )
                    }
                }
                compressed.clear()
                currentLine.clear()
                lastSyncPoint = null
                lastWordSyncPoint = null
                hadWordSyncSinceNewLine = false
                speaker = null
                hadLyricSinceWordSync = true
            }

            else -> {}
        }
    }
    out.sortBy { it.start }
    var previousLyric: LyricLine? = null
    out.forEach { lyric ->
        if (!hadVoice2) { // If there is no v2: tag, we should avoid width limit.
            lyric.speaker = when (lyric.speaker) {
                SpeakerEntity.Voice1 -> SpeakerEntity.Voice
                SpeakerEntity.Voice1Background -> SpeakerEntity.VoiceBackground
                else -> lyric.speaker
            }
        }
        val mainEnd = if (lyric.start == previousLyric?.start) out.firstOrNull {
            it.start == lyric.start && !it.endIsImplicit
        }?.end else null
        val wordWithoutEnd = lyric.words?.lastOrNull()
        if (wordWithoutEnd != null && wordWithoutEnd.endInclusive == null) {
            wordWithoutEnd.endInclusive = mainEnd?.takeIf { it > wordWithoutEnd.begin }
                ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                    ?.takeIf { it > wordWithoutEnd.begin }
                        ?: run {
                    // Estimate how long this word will take based on character
                    // to time ratio. To avoid this estimation, add a last word
                    // sync point to the line after the text :)
                    wordWithoutEnd.begin + (lyric.words.subList(0, lyric.words.size - 1)
                        .map { it.timeRange.count() / it.charRange.count().toFloat() }
                        .average().let { if (it.isNaN()) 100.0 else it } *
                            lyric.text.substring(wordWithoutEnd.charRange).length).toULong()
                }
        }
        if (lyric.endIsImplicit) {
            if (mainEnd != null) {
                lyric.end = mainEnd
                lyric.endIsImplicit = false
            } else {
                lyric.end = wordWithoutEnd?.endInclusive
                    ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                            ?: Long.MAX_VALUE.toULong()
                lyric.endIsImplicit = wordWithoutEnd == null // TODO: should this just stay true?
            }
        }
        lyric.isTranslated = lyric.start == previousLyric?.start &&
                (previousLyric.text.isNotBlank() || lyric.text.isBlank())
        previousLyric = lyric
    }
    while (out.firstOrNull()?.text?.isBlank() == true)
        out.removeAt(0)
    //while (out.lastOrNull()?.text?.isBlank() == true)
    //    out.removeAt(out.lastIndex) TODO this breaks unit tests, but blank lines are useless
    return SyncedLyrics(out).also { splitBidirectionalWords(it) }
}

// Class heavily based on MIT-licensed https://github.com/yoheimuta/ExoPlayerMusic/blob/77cfb989b59f6906b1170c9b2d565f9b8447db41/app/src/main/java/com/github/yoheimuta/amplayer/playback/UsltFrameDecoder.kt
// See http://id3.org/id3v2.4.0-frames
object UsltFrameDecoder {
    sealed class Result {
        data class Uslt(val language: String, val description: String, val text: String) : Result()
        data class Sylt(
            val language: String,
            val contentType: Int,
            val description: String,
            val text: List<Line>
        ) : Result() {
            data class Line(val timestamp: UInt, val text: String)
        }
    }

    private const val ID3_TEXT_ENCODING_ISO_8859_1 = 0
    private const val ID3_TEXT_ENCODING_UTF_16 = 1
    private const val ID3_TEXT_ENCODING_UTF_16BE = 2
    private const val ID3_TEXT_ENCODING_UTF_8 = 3

    fun decode(id3Data: ParsableByteArray): Result.Uslt? {
        if (id3Data.limit() < 4) {
            // Frame is malformed.
            return null
        }

        val encoding = id3Data.readUnsignedByte()
        val charset = getCharsetName(encoding)
        val lang = ByteArray(3)
        id3Data.readBytes(lang, 0, 3) // language
        val language = decodeStringIfValid(lang, 0, 3, Charset.forName("ISO-8859-1"))
        if (delimiterLength(encoding) == 1 &&
            (id3Data.peekUnsignedByte() == 1 || id3Data.peekUnsignedByte() == 2)
        ) {
            return null // this got to be SYLT
        }
        val rest = ByteArray(id3Data.limit() - 4)
        id3Data.readBytes(rest, 0, id3Data.limit() - 4)

        val descriptionEndIndex = indexOfEos(rest, 0, encoding)
        val description = decodeStringIfValid(rest, 0, descriptionEndIndex, charset)
        val textStartIndex = descriptionEndIndex + delimiterLength(encoding)
        val textEndIndex = indexOfEos(rest, textStartIndex, encoding)
        val text = decodeStringIfValid(rest, textStartIndex, textEndIndex, charset)
        return Result.Uslt(language, description, text)
    }

    fun decodeSylt(sampleRate: Int, id3Data: ParsableByteArray): Result.Sylt? {
        if (id3Data.limit() < 1) {
            // Frame is malformed.
            return null
        }
        val encoding = id3Data.readUnsignedByte()
        if (id3Data.limit() < 8 + 2 * delimiterLength(encoding)) {
            // Frame is malformed.
            return null
        }
        val charset = getCharsetName(encoding)
        val lang = ByteArray(3)
        id3Data.readBytes(lang, 0, 3) // language
        val language = decodeStringIfValid(lang, 0, 3, Charset.forName("ISO-8859-1"))
        val timestampFormat = id3Data.readUnsignedByte()
        val contentType = id3Data.readUnsignedByte()
        val rest = ByteArray(id3Data.limit() - 6)
        id3Data.readBytes(rest, 0, id3Data.limit() - 6)

        val descriptionEndIndex = indexOfEos(rest, 0, encoding)
        val description = decodeStringIfValid(rest, 0, descriptionEndIndex, charset)
        var processed = descriptionEndIndex + delimiterLength(encoding)
        val syltLines = mutableListOf<Result.Sylt.Line>()
        while (rest.size - processed > 1) {
            val textEndIndex = indexOfEos(rest, processed, encoding)
            val text = decodeStringIfValid(rest, processed, textEndIndex, charset)
            processed = textEndIndex + delimiterLength(encoding) + 4
            if (processed > rest.size) {
                // Frame is malformed. Maybe this is a USLT frame?
                return null
            }
            val timestamp = decodeTimestamp(rest, processed - 4, timestampFormat, sampleRate)
            syltLines.add(Result.Sylt.Line(timestamp, text))
        }
        return Result.Sylt(language, contentType, description, syltLines)
    }

    private fun getCharsetName(encodingByte: Int): Charset {
        val name = when (encodingByte) {
            ID3_TEXT_ENCODING_UTF_16 -> "UTF-16"
            ID3_TEXT_ENCODING_UTF_16BE -> "UTF-16BE"
            ID3_TEXT_ENCODING_UTF_8 -> "UTF-8"
            ID3_TEXT_ENCODING_ISO_8859_1 -> "ISO-8859-1"
            else -> throw IllegalArgumentException("unsupported charset $encodingByte")
        }
        return Charset.forName(name)
    }

    // this is copied from ExoPlayer's Id3Decoder
    private fun indexOfEos(data: ByteArray, fromIndex: Int, encoding: Int): Int {
        var terminationPos = indexOfZeroByte(data, fromIndex)

        // For single byte encoding charsets, we're done.
        if (encoding == ID3_TEXT_ENCODING_ISO_8859_1 || encoding == ID3_TEXT_ENCODING_UTF_8) {
            return terminationPos
        }

        // Otherwise ensure an even index and look for a second zero byte.
        while (terminationPos < data.size - 1) {
            if (terminationPos % 2 == 0 && data[terminationPos + 1] == 0.toByte()) {
                return terminationPos
            }
            terminationPos = indexOfZeroByte(data, terminationPos + 1)
        }

        return data.size
    }

    // this is copied from ExoPlayer's Id3Decoder
    private fun indexOfZeroByte(data: ByteArray, fromIndex: Int): Int {
        for (i in fromIndex until data.size) {
            if (data[i] == 0.toByte()) {
                return i
            }
        }
        return data.size
    }

    // this is copied from ExoPlayer's Id3Decoder
    private fun delimiterLength(encodingByte: Int): Int {
        return if (encodingByte == ID3_TEXT_ENCODING_ISO_8859_1 || encodingByte == ID3_TEXT_ENCODING_UTF_8)
            1
        else
            2
    }

    // this is copied from ExoPlayer's Id3Decoder
    private fun decodeStringIfValid(
        data: ByteArray,
        from: Int,
        to: Int,
        charset: Charset
    ): String {
        return if (to <= from || to > data.size) {
            ""
        } else String(data, from, to - from, charset)
    }

    private fun decodeTimestamp(data: ByteArray, pos: Int, format: Int, sampleRate: Int): UInt {
        val dec =
            ((((data[pos].toUInt() shl 24) and 0xff000000U) or ((data[pos + 1].toUInt() shl 16)
                    and 0xff0000U)) or ((data[pos + 2].toUInt() shl 8) and 0xff00U)) or (data[pos + 3].toUInt() and 0xffU)
        return when (format) {
            1 -> mpegFramePositionToMs(sampleRate, dec.toLong()).toUInt()
            2 -> dec
            else -> throw IllegalArgumentException("bad id3 timestamp format $format")
        }
    }

    private fun mpegFramePositionToMs(sampleRate: Int, pos: Long): Long {
        val samplesPerFrame = when (sampleRate) {
            32000, 44100, 48000 -> 1152 // MPEG-1
            16000, 22050, 24000 -> 576 // MPEG-2
            8000, 11025, 12000 -> 576 // MPEG-2.5
            else -> throw IllegalArgumentException("bad mpeg sample rate $sampleRate")
        }
        return (pos * samplesPerFrame * 1000L) / sampleRate
    }
}

// TODO: this could use some unit tests
fun UsltFrameDecoder.Result.Sylt.toSyncedLyrics(trimEnabled: Boolean): SyncedLyrics {
    val out = mutableListOf<LyricLine>()
    var i = 0
    while (i < text.size) {
        var j = i + 1
        // ID3 spec: SYLT whitespace is in next elem, not this one, and that also includes
        // newlines.
        while (j < text.size &&
            !text[j].text.trimStart { it == '\t' || it == ' ' || it == '\r' }.startsWith("\n")
        ) {
            j++ // find the next line start, so that j points to last element (exclusive)
        }
        var idx = 0
        val wout = mutableListOf<Word>()
        for (k in i..<j) {
            val it = text[k]
            val next = if (k + 1 < j) text[k + 1] else null
            val oIdx = idx
            idx += it.text.length
            // Make sure we do NOT include whitespace as part of the word. Whitespaces
            // do not have a strong bi-di flag assigned and hence ICU4J/AndroidBidi will
            // set bi-di transition point before whitespace. Rendering relies on being
            // able to change edge treatment with trailing flag in Layout which only
            // works on bi-di transition points. Additionally, excluding whitespace
            // allows us to scale gradient properly based on asking ourselves if the
            // next char is even rendered (or whitespace).
            val textWithoutStartWhitespace = it.text.trimStart()
            val startWhitespaceLength =
                it.text.length - textWithoutStartWhitespace.length
            val textWithoutWhitespaces = textWithoutStartWhitespace.trimEnd()
            val endWhitespaceLength =
                textWithoutStartWhitespace.length - textWithoutWhitespaces.length
            val startIndex = oIdx + startWhitespaceLength
            val endIndex = idx - endWhitespaceLength
            if (startIndex == endIndex)
                continue // word contained only whitespace
            val endInclusive = if (next != null && next.timestamp > 0uL) {
                // If we have a next word (with sync point), use its sync
                // point minus 1ms as end point of this word
                next.timestamp - 1uL
            } else null
            if (endInclusive == null || endInclusive > it.timestamp)
            // isRtl is filled in later in splitBidirectionalWords
                wout.add(
                    Word(
                        it.timestamp.toULong(), endInclusive,
                        startIndex..<endIndex,
                        isRtl = false
                    )
                )
        }
        var string = text.subList(i, j).joinToString("") { it.text }
        val nli1 = string.indexOf('\n')
        if (nli1 != -1 && string.take(nli1)
                .trimStart { it == '\t' || it == ' ' || it == '\r' }.isEmpty()
        ) {
            // remove last line's trailing whitespace (and eat newline)
            string = string.substring(nli1 + 1)
        }
        if (j < text.size) {
            // get our own trailing whitespace from next line (excl newline)
            var nli = text[j].text.indexOf('\n')
            if (nli == -1) {
                throw IllegalStateException("nli == -1, can't happen")
            }
            if (nli > 0 && text[j].text[nli - 1] == '\r')
                nli-- // don't split CRLF in half
            string += text[j].text.substring(0, nli)
        }
        if (trimEnabled) {
            val orig = string
            string = orig.trimStart()
            val startDiff = orig.length - string.length
            string = string.trimEnd()
            val iter = wout.listIterator()
            iter.forEach {
                if (it.charRange.last.toLong() - startDiff < 0
                    || it.charRange.first.toLong() - startDiff >= string.length
                )
                    iter.remove()
                else
                    it.charRange = (it.charRange.first - startDiff)
                        .coerceAtLeast(0)..(it.charRange.last - startDiff)
                        .coerceAtMost(string.length - 1)
            }
        }
        // if we had trailing sync point only with new line, it's explicit line end ts, use it.
        // if we have >1 segment, use it's last sync point (possibly estimated) as end time,
        // otherwise we will fill it later based on next line.
        val explicitEnd = if (i < j - 1 && text[j - 1].text.isBlank())
            text[j - 1].timestamp.toULong() - 1uL
        else if (wout.size > 1) wout.last().endInclusive else null
        out.add(
            LyricLine(
                string, text[i].timestamp.toULong(), explicitEnd ?: 0uL
                /* filled later */, explicitEnd == null,
                if (wout.size > 1) wout else null, null,
                false /* filled later */
            )
        )
        i = j
    }
    out.sortBy { it.start }
    var previousTimestamp = ULong.MAX_VALUE
    out.forEach { lyric ->
        val mainEnd = if (lyric.start == previousTimestamp) out.firstOrNull {
            it.start == lyric.start && !it.endIsImplicit
        }?.end else null
        val wordWithoutEnd = lyric.words?.lastOrNull()
        if (wordWithoutEnd != null && wordWithoutEnd.endInclusive == null) {
            wordWithoutEnd.endInclusive = mainEnd?.takeIf { it > wordWithoutEnd.begin }
                ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                    ?.takeIf { it > wordWithoutEnd.begin }
                        ?: run {
                    // Estimate how long this word will take based on character
                    // to time ratio. To avoid this estimation, add a last word
                    // sync point to the line after the text :)
                    wordWithoutEnd.begin + (lyric.words.subList(0, lyric.words.size - 1)
                        .map { it.timeRange.count() / it.charRange.count().toFloat() }
                        .average().let { if (it.isNaN()) 100.0 else it } *
                            lyric.text.substring(wordWithoutEnd.charRange).length).toULong()
                }
        }
        if (lyric.endIsImplicit) {
            if (mainEnd != null) {
                lyric.end = mainEnd
                lyric.endIsImplicit = false
            } else {
                lyric.end = wordWithoutEnd?.endInclusive
                    ?: out.find { it.start > lyric.start }?.start?.minus(1uL)
                            ?: Long.MAX_VALUE.toULong()
                lyric.endIsImplicit = wordWithoutEnd == null // TODO: should this just stay true?
            }
        }
        lyric.isTranslated = lyric.start == previousTimestamp
        previousTimestamp = lyric.start
    }
    while (out.firstOrNull()?.text?.isBlank() == true)
        out.removeAt(0)
    //while (out.lastOrNull()?.text?.isBlank() == true)
    //    out.removeAt(out.lastIndex) TODO this breaks (lrc's) unit tests, but blank lines are useless
    return SyncedLyrics(out).also { splitBidirectionalWords(it) }
}

private const val tt = "http://www.w3.org/ns/ttml"
private const val ttm = "http://www.w3.org/ns/ttml#metadata"
private const val ttp = "http://www.w3.org/ns/ttml#parameter"
private const val itunes = "http://itunes.apple.com/lyric-ttml-extensions"
private const val itunesInternal = "http://music.apple.com/lyric-ttml-internal"
private fun XmlPullParser.skipToEndOfTag() {
    if (eventType != XmlPullParser.START_TAG)
        throw XmlPullParserException("expected start tag in skipToEndOfTag()")
    while (next() != XmlPullParser.END_TAG) {
        // we have a child tag!
        if (eventType == XmlPullParser.START_TAG)
            skipToEndOfTag()
        else if (eventType != XmlPullParser.TEXT)
            throw XmlPullParserException("expected start tag or text in skipToEndOfTag()")
        // else: we have some text, boring
    }
}

private fun XmlPullParser.nextAndThrowIfNotEnd() {
    if (next() != XmlPullParser.END_TAG)
        throw XmlPullParserException("expected end tag in nextAndThrowIfNotEnd()")
}

private fun XmlPullParser.nextAndThrowIfNotText() {
    if (next() != XmlPullParser.TEXT)
        throw XmlPullParserException("expected end tag in nextAndThrowIfNotText()")
}

private class TtmlTimeTracker(private val parser: XmlPullParser, private val isApple: Boolean) {
    private val effectiveFrameRate: Float
    private val subFrameRate: Int
    private val tickRate: Int

    init {
        val frameRate = parser.getAttributeValue(ttp, "frameRate")?.toInt() ?: 30
        val frameRateMultiplier = parser.getAttributeValue(ttp, "frameRateMultiplier")
            ?.split(" ")?.let { parts ->
                parts[0].toInt() / parts[1].toInt().toFloat()
            } ?: 1f
        effectiveFrameRate = frameRate * frameRateMultiplier
        subFrameRate = parser.getAttributeValue(ttp, "subFrameRate")?.toInt() ?: 1
        tickRate = parser.getAttributeValue(ttp, "tickRate")?.toInt() ?: 1
    }

    // of course someone didn't conform to spec again :D - business as usual
    private val appleTimeRegex = Regex("^(?:([0-9]+):)?(?:([0-9]+):)?([0-9]+(?:\\.[0-9]+)?)?$")
    private val clockTimeRegex =
        Regex("^([0-9]{2,}):([0-9]{2}):([0-9]{2})(?:(\\.[0-9]+)|:([0-9]{2})(?:\\.([0-9]+))?)?$")
    private val offsetTimeRegex = Regex("^([0-9]+(?:\\.[0-9]+)?)(h|m|s|ms|f|t)$")
    var audioOffset: Long? = null
    fun parseTimestampMs(input: String?, offset: Long, negative: Boolean): Long? {
        if (input?.isEmpty() != false) return null
        val multiplier = if (negative && input.startsWith('-')) -1 else 1
        val input = if (multiplier == -1) input.substring(1) else input
        if (isApple) {
            val appleMatch = appleTimeRegex.matchEntire(input)
            if (appleMatch != null) {
                val hours = if (appleMatch.groupValues[2].isNotEmpty())
                    appleMatch.groupValues[1].toDoubleOrNull() ?: 0.0 else 0.0
                val minutes = if (appleMatch.groupValues[2].isNotEmpty())
                    appleMatch.groupValues[2].toDoubleOrNull() ?: 0.0 else
                    appleMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val seconds = appleMatch.groupValues[3].toDouble()
                // Apple has no idea how a TTML file works. So omit frame offset just for their broken files
                return ((hours * 3600000 + minutes * 60000 + seconds * 1000).toLong() + (audioOffset
                    ?: 0L)) * multiplier
            }
        } else {
            val clockMatch = clockTimeRegex.matchEntire(input)
            if (clockMatch != null) {
                val hours = clockMatch.groupValues[1].toDouble()
                val minutes = clockMatch.groupValues[2].toDouble()
                val seconds = (clockMatch.groupValues[3] + clockMatch.groupValues[4]).toDouble()
                val frameSecs = clockMatch.groupValues[5].toDoubleOrNull()
                    ?.div(effectiveFrameRate) ?: 0.0
                val subFrameSecs = clockMatch.groupValues[6].toDoubleOrNull()
                    ?.div(subFrameRate)?.div(effectiveFrameRate) ?: 0.0
                return ((hours * 3600000 + minutes * 60000 + (seconds + frameSecs +
                        subFrameSecs) * 1000).toLong() + offset + (audioOffset ?: 0L)) * multiplier
            }
        }
        val offsetMatch = offsetTimeRegex.matchEntire(input)
        if (offsetMatch != null) {
            var time = offsetMatch.groupValues[1].toDouble()
            when (offsetMatch.groupValues[2]) {
                "h" -> time *= 3600000.0
                "m" -> time *= 60000.0
                "s" -> time *= 1000.0
                "ms" -> {}
                "f" -> time /= effectiveFrameRate / 1000.0
                "t" -> time /= tickRate / 1000.0
            }
            if (isApple) {
                // Apple has no idea how a TTML file works. So omit frame offset just for their broken files
                return (time.toLong() + (audioOffset ?: 0L)) * multiplier
            }
            return (time.toLong() + offset + (audioOffset ?: 0L)) * multiplier
        }
        throw XmlPullParserException("can't understand this TTML timestamp: $input")
    }

    private fun parseRange(offset: ULong): ULongRange? {
        var begin = parseTimestampMs(
            parser.getAttributeValue("", "begin"),
            offset.toLong(),
            false
        )?.toULong()
        val dur = parseTimestampMs(parser.getAttributeValue("", "dur"), 0L, false)?.toULong()
        var end =
            parseTimestampMs(parser.getAttributeValue("", "end"), offset.toLong(), false)?.toULong()
        if (begin == null && end == null || end == null && dur == null
            || begin == null && dur == null
        )
            return null
        if (begin == null && dur != null)
            begin = (end ?: 0uL) - dur
        else if (end == null && dur != null)
            end = begin!! + dur
        return begin!!..end!!
    }

    private class TtmlLevel(val time: ULongRange?, val level: Int, var seq: ULong?)

    private val stack = mutableListOf<TtmlLevel>()
    fun beginBlock() {
        val isSeq = parser.getAttributeValue("", "timeContainer").let {
            when (it) {
                "par", null -> false
                "seq" -> true
                else -> throw XmlPullParserException("unknown timeContainer value $it")
            }
        }
        val last = stack.lastOrNull()
        val range = parseRange(last?.seq ?: last?.time?.first ?: 0uL)
        val frange = range ?: last?.time
        stack.add(
            TtmlLevel(
                frange,
                (last?.level ?: 0) + if (range != null) 1 else 0,
                if (isSeq) frange?.first else null
            )
        )
    }

    fun getTime(): ULongRange? {
        return stack.lastOrNull()?.time
    }

    fun getLevel(): Int {
        return stack.lastOrNull()?.level ?: 0
    }

    fun endBlock() {
        val removed = stack.removeAt(stack.size - 1)
        stack.lastOrNull()?.let {
            it.seq = if (it.seq != null) removed.time?.last else null
        }
    }
}

private class TtmlParserState(
    private val parser: XmlPullParser,
    private val timer: TtmlTimeTracker
) {
    data class Text(val text: String, val time: ULongRange?, val role: String?)
    data class P(
        val texts: List<Text>, val time: ULongRange?, val agent: String?,
        val songPart: String?, val key: String?, val role: String?, val translated: Boolean = false
    )

    private var texts: MutableList<Text>? = null
    val paragraphs = mutableListOf<P>()

    fun parse(
        time: ULongRange? = null,
        level: Int = 0,
        plevel: Int = 0,
        agent: String? = null,
        songPart: String? = null,
        key: String? = null,
        role: String? = null
    ) {
        var time = time
        var agent = agent
        var songPart = songPart
        var key = key
        var role = role
        var level = level
        var plevel = plevel
        if (parser.eventType == XmlPullParser.TEXT) {
            if (parser.text.isBlank() && parser.text.contains("\n"))
                return // shrug
            if (texts == null) {
                if (parser.text.isNotBlank())
                    throw IllegalStateException("found TEXT \"${parser.text}\" but text isn't allowed here (forgot <p>?)")
                return
            }
            if (level < plevel)
                time = null
            texts!!.add(Text(parser.text, time, role))
            return
        }
        if (parser.eventType != XmlPullParser.START_TAG)
            throw IllegalStateException("expected START_TAG or TEXT, found ${parser.eventType}!")
        if (parser.name != "span")
            parser.getAttributeValue(ttm, "agent")?.let { agent = it }
        parser.getAttributeValue(ttm, "role")?.let { role = it }
        timer.beginBlock()
        time = timer.getTime()
        level = timer.getLevel()
        var isP = false
        when (parser.name) {
            "div" -> {
                // not even having consistent attribute naming is truly beautiful
                parser.getAttributeValue(itunes, "song-part")?.let { songPart = it }
                parser.getAttributeValue(itunesInternal, "songPart")?.let { songPart = it }
            }

            "p" -> {
                parser.getAttributeValue(itunesInternal, "key")?.let { key = it }
                texts = mutableListOf()
                isP = true
                plevel = level
            }

            "body", "span" -> {}
            else -> throw IllegalStateException("unknown tag ${parser.name}, wanted body/span/div/p")
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                plevel = level + 1
            }
            parse(time, level, plevel, agent, songPart, key, role)
        }
        timer.endBlock()
        if (isP) {
            while (texts!!.isNotEmpty() && texts!![0].text.isBlank())
                texts!!.removeAt(0)
            while (texts!!.isNotEmpty() && texts!![texts!!.size - 1].text.isBlank())
                texts!!.removeAt(texts!!.size - 1)
            paragraphs.add(P(texts!!, time, agent, songPart, key, role))
            texts = null
        }
        if (parser.eventType != XmlPullParser.END_TAG)
            throw IllegalStateException("expected END_TAG, found ${parser.eventType}!")
    }
}

fun parseTtml(audioMimeType: String?, lyricText: String): SemanticLyrics? {
    val formattedLyricText = lyricText
        .replace(Regex("&(?!#?[a-zA-Z0-9]+;)"), "&amp;")
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.setInput(StringReader(formattedLyricText))
    try {
        parser.nextTag()
        parser.require(XmlPullParser.START_TAG, tt, "tt")
    } catch (_: XmlPullParserException) {
        return null // not ttml
    }
    // val lang = parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
    val timing = parser.getAttributeValue(itunesInternal, "timing")
    var hasItunesNamespace = timing != null
    if (!hasItunesNamespace) {
        for (i in 0..<parser.getNamespaceCount(parser.depth)) {
            if (parser.getNamespaceUri(i) == itunes || parser.getNamespaceUri(i) == itunesInternal) {
                hasItunesNamespace = true
                break
            }
        }
    }
    val peopleToType = hashMapOf<String, String>()
    val people = hashMapOf<String, MutableList<String>>()
    val itunesTranslations = hashMapOf<String, HashMap<String, out List<Pair<String?, String>>>>()
    val timer = TtmlTimeTracker(parser, hasItunesNamespace)
    parser.nextTag()
    if (parser.eventType == XmlPullParser.END_TAG && parser.namespace == tt && parser.name == "tt") {
        return null // a valid ttml file could have neither <head> or <body>, but it would be a weird one
    }
    parser.require(XmlPullParser.START_TAG, tt, null)
    if (parser.name != "body") { // <head> is optional
        parser.require(XmlPullParser.START_TAG, tt, "head")
        // TODO parse and reject based on https://www.w3.org/TR/2018/REC-ttml2-20181108/#feature-profile-version-2 to be compliant
        while (parser.nextTag() != XmlPullParser.END_TAG) {
            if (parser.name == "metadata") {
                while (parser.nextTag() != XmlPullParser.END_TAG) {
                    if (parser.namespace == ttm && parser.name == "agent") {
                        val id =
                            parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "id")
                        val type = parser.getAttributeValue("", "type")
                        people.getOrPut(type) { mutableListOf() }.add(id)
                        peopleToType[id] = type
                        while (parser.nextTag() != XmlPullParser.END_TAG) {
                            if (parser.namespace == ttm && parser.name == "name") {
                                // val type = parser.getAttributeValue("", "type")
                                parser.nextAndThrowIfNotText()
                                // val name = parser.text
                                parser.nextAndThrowIfNotEnd()
                            } else {
                                throw XmlPullParserException(
                                    "expected <ttm:name>, got " +
                                            "<${(parser.prefix?.plus(":") ?: "") + parser.name}> " +
                                            "in <ttm:agent> in <metadata>"
                                )
                            }
                        }
                    } else if (parser.name == "iTunesMetadata") {
                        while (parser.nextTag() != XmlPullParser.END_TAG) {
                            when (parser.name) {
                                "songwriters" -> {
                                    while (parser.nextTag() != XmlPullParser.END_TAG) {
                                        if (parser.name == "songwriter") {
                                            parser.nextAndThrowIfNotText()
                                            // val songwriter = parser.text
                                            parser.nextAndThrowIfNotEnd()
                                        } else {
                                            throw XmlPullParserException(
                                                "expected <songwriter>, got " +
                                                        "<${(parser.prefix?.plus(":") ?: "") + parser.name}> " +
                                                        "in <songwriters> in <iTunesMetadata>"
                                            )
                                        }
                                    }
                                }

                                "audio" -> {
                                    val role = parser.getAttributeValue(null, "role")
                                    if (when (role) {
                                            "spatial" -> audioMimeType == MimeTypes.AUDIO_AC3 ||
                                                    audioMimeType == MimeTypes.AUDIO_E_AC3 ||
                                                    audioMimeType == MimeTypes.AUDIO_AC4
                                            // ext- are Gramophone extensions
                                            "ext-not-spatial" -> !(audioMimeType == MimeTypes.AUDIO_AC3 ||
                                                    audioMimeType == MimeTypes.AUDIO_E_AC3 ||
                                                    audioMimeType == MimeTypes.AUDIO_AC4)

                                            "ext-always" -> true
                                            else -> throw XmlPullParserException(
                                                "unsupported offset " +
                                                        "role $role, can't decide whether to apply offset"
                                            )
                                        }
                                    ) {
                                        val parsed = timer.parseTimestampMs(
                                            parser.getAttributeValue(
                                                null,
                                                "lyricOffset"
                                            ), 0L, true
                                        )
                                        if (timer.audioOffset != null)
                                            timer.audioOffset = timer.audioOffset!! + (parsed ?: 0L)
                                        else
                                            timer.audioOffset = parsed
                                    }
                                    parser.nextAndThrowIfNotEnd()
                                }

                                "translations" -> {
                                    while (parser.nextTag() != XmlPullParser.END_TAG) {
                                        if (parser.name == "translation") {
                                            val type = parser.getAttributeValue(null, "type")
                                            if (type != "subtitle") {
                                                throw XmlPullParserException("unsupported translation type $type")
                                            }
                                            val lang = parser.getAttributeValue(
                                                "http://www.w3.org/XML/1998/namespace",
                                                "lang"
                                            )
                                            val out = hashMapOf<String, ArrayList<Pair<String?, String>>>()
                                            while (parser.nextTag() != XmlPullParser.END_TAG) {
                                                if (parser.name == "text") {
                                                    val `for` =
                                                        parser.getAttributeValue(null, "for")
                                                            ?: throw XmlPullParserException("missing attribute for at $parser")
                                                    val roleStack = mutableListOf<String>()
                                                    while (true) {
                                                        when (parser.next()) {
                                                            XmlPullParser.TEXT -> {
                                                                out.getOrPut(`for`) {
                                                                    ArrayList() }.add(roleStack
                                                                    .lastOrNull() to parser.text)
                                                            }

                                                            XmlPullParser.START_TAG if
                                                            parser.name == "span" -> {
                                                                val role = parser.getAttributeValue(
                                                                    ttm, "role")
                                                                roleStack.add(role)
                                                            }

                                                            XmlPullParser.END_TAG if
                                                            parser.name == "span" &&
                                                                    roleStack.isNotEmpty() -> {
                                                                roleStack.removeAt(
                                                                    roleStack.lastIndex)
                                                            }

                                                            XmlPullParser.END_TAG if
                                                            parser.name == "text" &&
                                                                    roleStack.isEmpty() -> {
                                                                break
                                                            }

                                                            else ->
                                                                throw XmlPullParserException(
                                                                    "Wrong event type " +
                                                                            "${parser.eventType} " +
                                                                            "in $parser")
                                                        }
                                                    }
                                                } else {
                                                    throw XmlPullParserException(
                                                        "expected <text>, got " +
                                                                "<${(parser.prefix?.plus(":") ?: "") + parser.name}> " +
                                                                "in <translation> in <translations> in <iTunesMetadata>"
                                                    )
                                                }
                                            }
                                            itunesTranslations[lang] = out
                                        } else {
                                            throw XmlPullParserException(
                                                "expected <translation>, got " +
                                                        "<${(parser.prefix?.plus(":") ?: "") + parser.name}> " +
                                                        "in <translations> in <iTunesMetadata>"
                                            )
                                        }
                                    }
                                }

                                else -> parser.skipToEndOfTag()
                            } // there are some others
                        }
                    } else parser.skipToEndOfTag()
                }
            } else // probably <styling> or <layout>
                parser.skipToEndOfTag()
        }
        parser.require(XmlPullParser.END_TAG, tt, "head")
        parser.nextTag()
        if (parser.eventType == XmlPullParser.END_TAG && parser.namespace == tt && parser.name == "tt") {
            return null // a valid ttml file could have no <body>, but it would be a weird one
        }
    }
    parser.require(XmlPullParser.START_TAG, tt, "body")
    val state = TtmlParserState(parser, timer)
    state.parse()
    val paragraphs = state.paragraphs.flatMap {
        /* x-bg can be anywhere in a line, let's split it out into
         * separate lines for now, that looks better */
        if (it.texts.isEmpty()) return@flatMap listOf(it)
        val out = mutableListOf<TtmlParserState.P>()
        var idx = it.texts.indexOfFirst { i -> i.role != it.texts[0].role }
        var cur = 0
        do {
            if (cur == 0 && idx == -1 && !(it.texts.firstOrNull()?.text?.startsWith('(') == true
                        && it.texts.lastOrNull()?.text?.endsWith(')') == true &&
                        (it.texts.firstOrNull()?.role ?: it.role) == "x-bg")
            )
                out.add(it.copy(role = it.texts.firstOrNull()?.role ?: it.role))
            else {
                // trim start...
                while ((cur < idx || (idx == -1 && cur < it.texts.size)) && it.texts[cur].text.isBlank())
                    cur++
                // ...and end
                var iIdx = idx
                while (cur < iIdx && iIdx > 0 && it.texts[iIdx - 1].text.isBlank())
                    iIdx--
                if (cur < iIdx || (iIdx == -1 && cur < it.texts.size)) {
                    val t =
                        it.texts.subList(cur, iIdx.let { i -> if (i == -1) it.texts.size else i })
                            .toMutableList()
                    if (t.firstOrNull()?.text?.startsWith('(') == true
                        && t.lastOrNull()?.text?.endsWith(')') == true
                        && (t.firstOrNull()?.role ?: it.role) == "x-bg"
                    ) {
                        t[0] = t.first().copy(text = t.first().text.substring(1))
                        t[t.size - 1] = t.last()
                            .copy(text = t.last().text.substring(0, t.last().text.length - 1))
                    }
                    out.add(
                        it.copy(
                            texts = t,
                            time = t.lastOrNull()?.time?.last?.let { other ->
                                t.firstOrNull()?.time?.first?.rangeTo(other)
                            } ?: it.time, role = t.firstOrNull()?.role ?: it.role))
                }
            }
            cur = idx
            if (cur != -1)
                idx = it.texts.subList(cur, it.texts.size)
                    .indexOfFirst { i -> i.role != it.texts[cur].role }
            if (idx != -1) idx += cur
        } while (cur != -1)
        out
    }.toMutableList()
    itunesTranslations.forEach { lang ->
        lang.value.forEach { line ->
            val indices = paragraphs.flatMapIndexed { i, it -> if (it.key == line.key)
                listOf(i) else emptyList() }
            if (indices.size == line.value.size) {
                var offset = 0
                line.value.forEachIndexed { occurrenceIndex, roleAndText ->
                    val untranslated = paragraphs[indices[occurrenceIndex] + offset]
                    if (untranslated.role != roleAndText.first)
                        throw XmlPullParserException("translation role is different: " +
                                "$untranslated vs $roleAndText (idx $occurrenceIndex)")
                    paragraphs.add(indices[occurrenceIndex] + ++offset,
                        untranslated.copy(texts = listOf(TtmlParserState.Text(
                            roleAndText.second.let {
                                if (roleAndText.first == "x-bg" && it.startsWith('(')
                                    && it.endsWith(')'))
                                    it.substring(1, it.length - 1) else it
                            }, time = null, role = null)), translated = true))
                }
            } else if (indices.size > 1 && line.value.size == 1 &&
                line.value.first().first == null) { // if the translation has no roles
                val idx = indices.findLast { paragraphs[it].role == null } ?: indices.last()
                paragraphs.add(idx + 1, paragraphs[idx].copy(texts = listOf(
                    TtmlParserState.Text(line.value.first().second, time = null, role = null)),
                    translated = true))
            } else {
                throw XmlPullParserException("translation count is different: " +
                        "$indices vs ${line.value}")
            }
        }
    }
    // start left if person is first, and right if sample is first.
    val pToSide = if (Flags.TTML_AGENT_SMART_SIDES) {
        val pToSide = hashMapOf<Int, Boolean /* true = right */>()
        var agent = paragraphs.firstOrNull()?.agent
        var side = paragraphs.firstOrNull()?.let { peopleToType[it.agent] == "other" } == true
        paragraphs.forEachIndexed { i, it ->
            if (peopleToType[it.agent] != "group") {
                if (agent != it.agent) {
                    agent = it.agent
                    side = !side
                }
                pToSide[i] = side
            }
        }
        val countLeft = pToSide.count { !it.value }
        val countRight = pToSide.count { it.value }
        if (countRight * 100 >= (countLeft + countRight) * 85) {
            pToSide.keys.toList().forEach {
                pToSide[it] = !pToSide[it]!!
            }
        }
        pToSide
    } else null
    val hasAtLeastTwoPeople = people["person"]?.let { it.size > 1 } == true
    if (paragraphs.find { it.time != null } == null) {
        return UnsyncedLyrics(paragraphs.mapIndexed { j, it ->
            val text = it.texts.joinToString("") { it.text }
            val isBg = it.role == "x-bg"
            val isGroup = peopleToType[it.agent] == "group"
            val isOther = peopleToType[it.agent] == "other"
            // first person goes left, second right, third left, fourth right, and so on.
            // and the same goes for "other" except that we start on the right here.
            val isVoice2 = if (pToSide != null) !isGroup && pToSide[j]!! else
                (it.agent != null && (people[peopleToType[it.agent]] ?: throw NullPointerException(
                    "expected to find ${it.agent} (${peopleToType[it.agent]}) in $people"
                )).indexOf(it.agent) % 2 == (if (isOther) 0 else 1))
            val speaker = when {
                isGroup && isBg -> SpeakerEntity.GroupBackground
                isGroup -> SpeakerEntity.Group
                isVoice2 && isBg -> SpeakerEntity.Voice2Background
                isVoice2 -> SpeakerEntity.Voice2
                (hasAtLeastTwoPeople || isOther) && isBg -> SpeakerEntity.Voice1Background
                (hasAtLeastTwoPeople || isOther) -> SpeakerEntity.Voice1
                isBg -> SpeakerEntity.VoiceBackground
                else -> SpeakerEntity.Voice
            }
            Pair(text, speaker)
        })
    }
    return SyncedLyrics(paragraphs.mapIndexed { j, it ->
        val text = StringBuilder()
        val words = mutableListOf<IntRange>()
        for (i in it.texts) {
            val start = text.length
            text.append(i.text)
            words += start..<text.length
        }
        // Do not try to use words if timing is set to Line, as those words may actually be x-bg
        // background lines. We can't assume a x-bg background line with only one word is actually
        // line synced either as that would break lines with word sync that only consist of one
        // word. We really have to fall back to header information here.
        val theWords = if (timing != "Line") it.texts.mapIndexed { i, it -> it to words[i] }
            .filter { it.first.time != null }
            .map { Word(it.first.time!!, it.second, false) }
            .takeIf { it.isNotEmpty() }
            ?.toMutableList() else null
        val isBg = it.role == "x-bg"
        val isGroup = peopleToType[it.agent] == "group"
        val isOther = peopleToType[it.agent] == "other"
        // first person goes left, second right, third left, fourth right, and so on.
        // and the same goes for "other" except that we start on the right here.
        val isVoice2 = if (pToSide != null) !isGroup && pToSide[j]!! else
            (it.agent != null && (people[peopleToType[it.agent]] ?: throw NullPointerException(
                "expected to find ${it.agent} (${peopleToType[it.agent]}) in $people"
            )).indexOf(it.agent) % 2 == (if (isOther) 0 else 1))
        val speaker = when {
            isGroup && isBg -> SpeakerEntity.GroupBackground
            isGroup -> SpeakerEntity.Group
            isVoice2 && isBg -> SpeakerEntity.Voice2Background
            isVoice2 -> SpeakerEntity.Voice2
            hasAtLeastTwoPeople && isBg -> SpeakerEntity.Voice1Background
            hasAtLeastTwoPeople -> SpeakerEntity.Voice1
            isBg -> SpeakerEntity.VoiceBackground
            else -> SpeakerEntity.Voice
        }
        if (it.time == null) {
            throw IllegalArgumentException("it.time == null but some other P has non-null time")
        }
        val next = paragraphs.getOrNull(j + 1)?.time?.first
        LyricLine(
            text.toString(), it.time.first, it.time.last, theWords == null
                    && next != null && (it.time.last == next || it.time.last == next - 1uL),
            theWords, speaker, it.translated
        )
    }).also { splitBidirectionalWords(it) }
}

fun parseSrt(lyricText: String, trimEnabled: Boolean): SemanticLyrics? {
    if (!lyricText.startsWith("1\n") && !lyricText.startsWith("1\r")) return null // invalid SubRip
    val cues = mutableListOf<CuesWithTiming>()
    val parser = SubripParser()
    try {
        parser.parse(
            lyricText.toByteArray(),
            SubtitleParser.OutputOptions.allCues()
        ) { cues.add(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to parse something which looks like SRT: ${Log.getThrowableString(e)}")
        return null
    }
    var lastTs: ULong? = null
    return SyncedLyrics(cues.map {
        val ts = (it.startTimeUs / 1000).toULong()
        val l = lastTs == ts
        lastTs = ts
        LyricLine(
            it.cues[0].text!!.toString().let {
                if (trimEnabled)
                    it.trim()
                else it
            }, ts, (it.endTimeUs / 1000).toULong(), false, null,
            null, l
        )
    })
}
