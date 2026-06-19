package uk.akane.libphonograph.manipulator

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.util.SparseArray
import android.util.Xml
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.util.keyIterator
import androidx.media3.common.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize
import okio.Path.Companion.toOkioPath
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.utils.flows.IncrementalMap
import org.akanework.gramophone.logic.utils.flows.forKey
import org.nift4.mediastorecompat.MediaStoreCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import uk.akane.libphonograph.toUriCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone
import java.util.TimeZone
import java.util.regex.Pattern

object PlaylistSerializer {
    private val XSPF_EXT_WPL_META = "http://akanework.org/xspf-ext/wpl-roundtrip/meta".toUri() //pfx
    private val XSPF_EXT_WPL_CID = "http://akanework.org/xspf-ext/wpl-roundtrip/media/cid".toUri()
    private val XSPF_EXT_WPL_TID = "http://akanework.org/xspf-ext/wpl-roundtrip/media/tid".toUri()
    private val XSPF_EXT_M3U_GENRE = "http://akanework.org/xspf-ext/m3u-roundtrip/genre".toUri()
    private val XSPF_EXT_M3U_ALBUM = "http://akanework.org/xspf-ext/m3u-roundtrip/album".toUri()
    private val XSPF_EXT_M3U_ARTIST = "http://akanework.org/xspf-ext/m3u-roundtrip/artist".toUri()
    private val XSPF_EXT_M3U_TITLE_KEY = "http://akanework.org/xspf-ext/m3u-roundtrip/titleKey".toUri()
    private val XSPF_EXT_M3U_UNKNOWN = "http://akanework.org/xspf-ext/m3u-roundtrip/unknown".toUri()
    private val XSPF_EXT_M3U_TV = "http://akanework.org/xspf-ext/m3u-roundtrip/tv".toUri() //pfx
    private val XSPF_EXT_GENERATOR = "http://akanework.org/xspf-ext/generator".toUri()

    @Throws(UnsupportedPlaylistFormatException::class)
    fun write(context: Context, outFile: File, out: Uri, songs: Playlist) {
        val format = when (outFile.extension.lowercase()) {
            "m3u", "m3u8" -> PlaylistFormat.M3u
            "xspf" -> PlaylistFormat.Xspf
            "wpl" -> PlaylistFormat.Wpl
            "pls" -> PlaylistFormat.Pls
            else -> throw UnsupportedPlaylistFormatException(outFile.extension)
        }
        write(context, format, outFile, out, songs)
    }

    @Throws(UnsupportedPlaylistFormatException::class)
    fun read(outFile: File): Playlist {
        val format = when (outFile.extension.lowercase()) {
            "m3u", "m3u8" -> PlaylistFormat.M3u
            "xspf" -> PlaylistFormat.Xspf
            "wpl" -> PlaylistFormat.Wpl
            "pls" -> PlaylistFormat.Pls
            else -> throw UnsupportedPlaylistFormatException(outFile.extension)
        }
        return read(format, outFile)
    }

    private fun read(format: PlaylistFormat, outFile: File): Playlist {
        return when (format) {
            PlaylistFormat.M3u -> {
                val extInfRegex = Regex("#EXTINF:(-?\\d+)(?:\\s+([^,]+))?,(.*)")
                val tvKeysRegex = Regex("""([\w-]+)="((?:\\.|[^"\\])*)"""")
                val lines = outFile.readLines()
                var title: String? = null
                var titleKey: String? = null
                var artist: String? = null
                var album: String? = null
                var genre: String? = null
                var tvKeys: List<Pair<String, String>>? = null
                var lastEntry = -1
                var foundEntry = false
                val entries = lines.mapIndexedNotNull { i, it ->
                    if (it.isBlank()) {
                        lastEntry = i
                        foundEntry = true
                        null
                    } else if (!it.startsWith('#')) {
                        val associatedComments = lines.subList(lastEntry + 1, i).toMutableList()
                        var extInfMatch: MatchResult? = null
                        associatedComments.removeAll { input ->
                            if (extInfMatch == null) {
                                extInfMatch = extInfRegex.matchEntire(input)
                                if (extInfMatch != null) {
                                    return@removeAll true
                                }
                            }
                            false
                        }
                        val uriLine = Uri.decode(it)
                        val link = listOf(Entry.parseUri(outFile, uriLine))
                        val durationSeconds = extInfMatch?.groupValues?.get(1)?.toLong()
                        val tvKeys = extInfMatch?.groupValues?.get(2)?.let {
                            tvKeysRegex.findAll(it).map { match ->
                                val key = match.groupValues[1]
                                val value = match.groupValues[2]
                                    .replace("""\"""", "\"")
                                    .replace("""\\""", "\\")
                                key to value
                            }.toList()
                        }
                        val title = extInfMatch?.groupValues?.get(3)
                        lastEntry = i
                        foundEntry = true
                        Entry.ofM3u(link, durationSeconds, tvKeys, title, associatedComments)
                    } else if (!foundEntry) { // might be a header property, or might not
                        when {
                            title == null && it.startsWith("#PLAYLIST:") -> {
                                title = it.substring("#PLAYLIST:".length)
                                titleKey = "PLAYLIST"
                                lastEntry = i
                            }
                            title == null && it.startsWith("#EXTNAME:") -> {
                                title = it.substring("#EXTNAME:".length)
                                titleKey = "EXTNAME"
                                lastEntry = i
                            }
                            album == null && it.startsWith("#EXTALB:") -> {
                                album = it.substring("#EXTALB:".length)
                                lastEntry = i
                            }
                            artist == null && it.startsWith("#EXTART:") -> {
                                artist = it.substring("#EXTART:".length)
                                lastEntry = i
                            }
                            genre == null && it.startsWith("#EXTGENRE:") -> {
                                genre = it.substring("#EXTGENRE:".length)
                                lastEntry = i
                            }
                            tvKeys == null && it.startsWith("#EXTM3U ") -> {
                                val str = it.substring("#EXTM3U ".length)
                                tvKeys = tvKeysRegex.findAll(str).map { match ->
                                    val key = match.groupValues[1]
                                    val value = match.groupValues[2]
                                        .replace("""\"""", "\"")
                                        .replace("""\\""", "\\")
                                    key to value
                                }.toList()
                                lastEntry = i
                            }
                            it.trimEnd() == "#EXTM3U" -> lastEntry = i
                        }
                        null
                    } else
                        null
                }
                Playlist(entries, title = title, titleKey = titleKey, artist = artist,
                    album = album, genre = genre, tvKeys = tvKeys)
            }

            PlaylistFormat.Wpl -> outFile.inputStream().use { ist ->
                val parser = Xml.newPullParser()
                parser.setInput(ist, StandardCharsets.UTF_8.name())
                val items = mutableListOf<Entry>()
                parser.nextTag()
                parser.require(XmlPullParser.START_TAG, null, "smil")
                parser.nextTag()
                parser.require(XmlPullParser.START_TAG, null, "head")
                var title: String? = null
                var author: String? = null
                var category: String? = null
                var genre: String? = null
                var generator: String? = null
                var userName: String? = null
                var totalDuration: Long? = null // seconds
                val metaTags = mutableListOf<Pair<String, String>>()
                while (parser.nextTag() != XmlPullParser.END_TAG) {
                    parser.require(XmlPullParser.START_TAG, null, null)
                    val tag = parser.name
                    when (tag) {
                        "title" -> {
                            title = parser.nextText()
                        }
                        "meta" -> {
                            val key = parser.getAttributeValue(null, "name")
                            val value = parser.getAttributeValue(null, "content")
                            when (key) {
                                "Author" -> author = value
                                "Category" -> category = value
                                "Genre" -> genre = value
                                "Generator" -> generator = value
                                "UserName", "UserName1" -> userName = value
                                "TotalDuration" -> totalDuration = value?.toLong()
                                "ItemCount" -> {}
                                else -> metaTags.add(key to value)
                            }
                        }
                        else -> throw XmlPullParserException("Unexpected <head> tag <$tag>")
                    }
                    parser.nextTag()
                    parser.require(XmlPullParser.END_TAG, null, tag)
                }
                parser.require(XmlPullParser.END_TAG, null, "head")
                parser.nextTag()
                parser.require(XmlPullParser.START_TAG, null, "body")
                parser.nextTag()
                parser.require(XmlPullParser.START_TAG, null, "seq")
                while (parser.nextTag() != XmlPullParser.END_TAG) {
                    if ("smartPlaylist" == parser.name)
                        throw UnsupportedPlaylistFormatException("Windows Smart Playlist")
                    parser.require(XmlPullParser.START_TAG, null, "media")
                    val src = parser.getAttributeValue(null, "src")
                    if (src != null) {
                        val cid = parser.getAttributeValue(null, "cid")
                        val tid = parser.getAttributeValue(null, "tid")
                        items.add(Entry.ofWpl(listOf(Entry.parseUri(outFile, src.replace(
                            '\\', '/'))), cid, tid))
                    }
                    parser.nextTag()
                    parser.require(XmlPullParser.END_TAG, null, "media")
                }
                parser.nextTag()
                parser.require(XmlPullParser.END_TAG, null, "seq")
                parser.nextTag()
                parser.require(XmlPullParser.END_TAG, null, "body")
                parser.nextTag()
                parser.require(XmlPullParser.END_TAG, null, "smil")
                Playlist(items, title = title, author = author, category = category,
                    genre = genre, generator = generator, userName = userName,
                    totalDuration = totalDuration, wplMetaTags = metaTags)
            }

            PlaylistFormat.Pls -> {
                val filePattern = Pattern.compile("File(\\d+)=(.+)")
                val titlePattern = Pattern.compile("Title(\\d+)=(.+)")
                val lengthPattern = Pattern.compile("Length(\\d+)=(.+)")
                val files = SparseArray<String>()
                val titles = SparseArray<String>()
                val lengths = SparseArray<Long>()
                outFile.readLines().forEach {
                    val fileMatcher = filePattern.matcher(it)
                    if (fileMatcher.matches()) {
                        files.set(fileMatcher.group(1)!!.toInt(),
                            fileMatcher.group(2)!!.replace('\\', '/'))
                    } else {
                        val titleMatcher = titlePattern.matcher(it)
                        if (titleMatcher.matches()) {
                            titles.set(titleMatcher.group(1)!!.toInt(), titleMatcher.group(2))
                        } else {
                            val lengthMatcher = lengthPattern.matcher(it)
                            if (lengthMatcher.matches()) {
                                lengths.set(lengthMatcher.group(1)!!.toInt(),
                                    lengthMatcher.group(2)!!.toLongOrNull())
                            }
                        }
                    }
                }
                val entries = mutableListOf<Entry>()
                files.keyIterator().forEach {
                    entries.add(Entry.ofPls(listOf(Entry.parseUri(outFile,
                        files.get(it))), titles.get(it), lengths.get(it)))
                }
                Playlist(entries)
            }

            PlaylistFormat.Xspf -> outFile.inputStream().use { ist ->
                val x0 = "http://xspf.org/ns/0/"
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                parser.setInput(ist, StandardCharsets.UTF_8.name())
                val items = mutableListOf<Entry>()
                parser.nextTag()
                parser.require(XmlPullParser.START_TAG, x0, "playlist")
                var title: String? = null
                var author: String? = null
                var annotation: String? = null
                var info: Uri? = null
                var location: Uri? = null
                var identifier: Uri? = null
                var image: Uri? = null
                var dateCreatedUtc: Long? = null
                var timezoneOffsetSecs: Int? = null // useful so that we can save in same timezone
                var license: Uri? = null
                var attribution: List<Pair<Boolean, String>>? = null
                var genre: String? = null
                var album: String? = null
                var artist: String? = null
                var category: String? = null
                var userName: String? = null
                var generator: String? = null
                var titleKey: String? = null
                val tvKeys = mutableListOf<Pair<String, String>>()
                val wplMetaTags = mutableListOf<Pair<String, String>>()
                val links = mutableListOf<Pair<Uri, Uri>>()
                val metas = mutableListOf<Pair<Uri, String>>()
                val extensions = mutableListOf<ByteArray>()
                while (parser.nextTag() != XmlPullParser.END_TAG) {
                    parser.require(XmlPullParser.START_TAG, x0, null)
                    val tag = parser.name
                    when (tag) {
                        "title" -> {
                            if (title != null)
                                throw XmlPullParserException("Duplicate <title> element")
                            title = parser.nextText()
                        }
                        "creator" -> {
                            if (author != null)
                                throw XmlPullParserException("Duplicate <creator> element")
                            author = parser.nextText()
                        }
                        "annotation" -> {
                            if (annotation != null)
                                throw XmlPullParserException("Duplicate <annotation> element")
                            annotation = parser.nextText()
                        }
                        "info" -> {
                            if (info != null)
                                throw XmlPullParserException("Duplicate <info> element")
                            info = Uri.parse(parser.nextText())
                        }
                        "location" -> {
                            if (location != null)
                                throw XmlPullParserException("Duplicate <location> element")
                            location = Uri.parse(parser.nextText())
                        }
                        "identifier" -> {
                            if (identifier != null)
                                throw XmlPullParserException("Duplicate <identifier> element")
                            identifier = Uri.parse(parser.nextText())
                        }
                        "image" -> {
                            if (image != null)
                                throw XmlPullParserException("Duplicate <image> element")
                            image = Entry.parseUri(outFile, parser.nextText())
                        }
                        "date" -> {
                            val input = parser.nextText().replace(":60", ":59")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val time = DateTimeFormatter.ISO_DATE_TIME.parseBest(
                                    input, ZonedDateTime::from,
                                    OffsetDateTime::from, LocalDateTime::from)
                                dateCreatedUtc = when (time) {
                                    is ZonedDateTime -> time.toEpochSecond()
                                    is OffsetDateTime -> time.toEpochSecond()
                                    is LocalDateTime -> time.atZone(ZoneOffset.systemDefault())
                                        .toEpochSecond()
                                    else -> throw IllegalStateException()
                                }
                                timezoneOffsetSecs = when (time) {
                                    is ZonedDateTime -> time.offset
                                    is OffsetDateTime -> time.offset
                                    is LocalDateTime -> time.atZone(ZoneOffset.systemDefault())
                                        .offset
                                    else -> throw IllegalStateException()
                                }.totalSeconds
                            } else {
                                val isoRegex = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:" +
                                        "\\d{2}:\\d{2})(?:\\.\\d{1,12})?(Z|[+-]\\d{2}:\\d{2})?$")
                                val match = isoRegex.matchEntire(input)!!
                                val dateObj = if (match.groupValues[2] == "") {
                                    val sdf = SimpleDateFormat(
                                        "yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                                    sdf.timeZone = TimeZone.getDefault()
                                    sdf.parse(match.groupValues[1])!!
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        val sdf = SimpleDateFormat(
                                            "yyyy-MM-dd'T'HH:mm:ssX", Locale.US)
                                        sdf.parse(match.groupValues[1] +
                                                match.groupValues[2])!!
                                    } else {
                                        val tz = match.groupValues[2].let { if (it == "Z") "+0000"
                                        else if (it.length == 3) "${it}00" else it
                                            .replace(":", "") }
                                        val sdf = SimpleDateFormat(
                                            "yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                                        sdf.parse(match.groupValues[1] + tz)!!
                                    }
                                }
                                dateCreatedUtc = dateObj.time
                                timezoneOffsetSecs = (@Suppress("deprecation") dateObj
                                    .timezoneOffset) * 60
                            }
                        }
                        "license" -> {
                            if (license != null)
                                throw XmlPullParserException("Duplicate <license> element")
                            license = Uri.parse(parser.nextText())
                        }
                        "attribution" -> {
                            if (attribution != null)
                                throw XmlPullParserException("Duplicate <attribution> element")
                            attribution = mutableListOf()
                            while (parser.nextTag() != XmlPullParser.END_TAG) {
                                parser.require(XmlPullParser.START_TAG, x0,
                                    null)
                                val tag = parser.name
                                if (tag != "identifier" && tag != "location")
                                    throw XmlPullParserException("Bad tag <$tag> in <attribution>")
                                attribution.add((tag == "location") to parser.nextText())
                                parser.require(XmlPullParser.END_TAG, x0,
                                    null)
                            }
                        }
                        "link" -> {
                            links.add(parser.getAttributeValue(null,
                                "rel").toUri() to parser.nextText().toUri())
                        }
                        "meta" -> {
                            val key = parser.getAttributeValue(null, "rel").toUri()
                            val value = parser.nextText()
                            when (key) {
                                XSPF_EXT_GENERATOR -> generator = value
                                XSPF_EXT_M3U_GENRE -> genre = value
                                XSPF_EXT_M3U_ALBUM -> album = value
                                XSPF_EXT_M3U_ARTIST -> artist = value
                                XSPF_EXT_M3U_TITLE_KEY -> titleKey = value
                                else if key.toString().startsWith(XSPF_EXT_M3U_TV
                                    .toString()) -> {
                                    tvKeys.add(key.toString().substring(XSPF_EXT_M3U_TV
                                        .toString().length) to value)
                                }
                                else if key.toString().startsWith(XSPF_EXT_WPL_META
                                    .toString()) -> {
                                    when (val wplKey = key.lastPathSegment!!) {
                                        "Category" -> category = value
                                        "UserName", "UserName1" -> userName = value
                                        else -> wplMetaTags.add(wplKey to value)
                                    }
                                }
                                else -> metas.add(key to value)
                            }
                        }
                        "extension" -> {
                            val application = parser.getAttributeValue(null,
                                "application").toUri()
                            when (application) {
                                else -> extensions += parser.tagDeepToUtf8()
                            }
                        }
                        "trackList" -> {
                            while (parser.nextTag() != XmlPullParser.END_TAG) {
                                parser.require(XmlPullParser.START_TAG, x0,
                                    "track")
                                val locations = mutableListOf<Uri>()
                                val identifiers = mutableListOf<Uri>()
                                var title: String? = null
                                var author: String? = null
                                var annotation: String? = null
                                var info: Uri? = null
                                var image: Uri? = null
                                var album: String? = null
                                var contentId: String? = null
                                var trackingId: String? = null
                                var trackNum: UInt? = null
                                var durationMs: UInt? = null
                                var associatedComments: List<String>? = null
                                val tvKeys = mutableListOf<Pair<String, String>>()
                                val links = mutableListOf<Pair<Uri, Uri>>()
                                val metas = mutableListOf<Pair<Uri, String>>()
                                val extensions = mutableListOf<ByteArray>()
                                while (parser.nextTag() != XmlPullParser.END_TAG) {
                                    parser.require(XmlPullParser.START_TAG,
                                        x0, null)
                                    val tag = parser.name
                                    when (tag) {
                                        "location" -> {
                                            locations.add(Entry.parseUri(outFile,
                                                parser.nextText()))
                                        }
                                        "identifier" -> {
                                            identifiers.add(parser.nextText().toUri())
                                        }
                                        "title" -> {
                                            if (title != null)
                                                throw XmlPullParserException("Duplicate <title> " +
                                                        "element")
                                            title = parser.nextText()
                                        }
                                        "creator" -> {
                                            if (author != null)
                                                throw XmlPullParserException("Duplicate <creator> " +
                                                        "element")
                                            author = parser.nextText()
                                        }
                                        "annotation" -> {
                                            if (annotation != null)
                                                throw XmlPullParserException("Duplicate " +
                                                        "<annotation> element")
                                            annotation = parser.nextText()
                                        }
                                        "info" -> {
                                            if (info != null)
                                                throw XmlPullParserException("Duplicate " +
                                                        "<info> element")
                                            info = Uri.parse(parser.nextText())
                                        }
                                        "image" -> {
                                            if (image != null)
                                                throw XmlPullParserException("Duplicate <image>" +
                                                        " element")
                                            image = Entry.parseUri(outFile, parser.nextText())
                                        }
                                        "album" -> {
                                            if (album != null)
                                                throw XmlPullParserException("Duplicate <album> " +
                                                        "element")
                                            album = parser.nextText()
                                        }
                                        "trackNum" -> {
                                            if (trackNum != null)
                                                throw XmlPullParserException("Duplicate" +
                                                        " <trackNum> element")
                                            trackNum = parser.nextText().toUInt()
                                        }
                                        "duration" -> {
                                            if (durationMs != null)
                                                throw XmlPullParserException("Duplicate" +
                                                        " <duration> element")
                                            durationMs = parser.nextText().toUInt()
                                        }
                                        "link" -> {
                                            links.add(parser.getAttributeValue(null,
                                                "rel").toUri() to parser.nextText().toUri())
                                        }
                                        "meta" -> {
                                            val key = parser.getAttributeValue(null,
                                                "rel").toUri()
                                            val value = parser.nextText()
                                            when (key) {
                                                XSPF_EXT_WPL_CID -> contentId = value
                                                XSPF_EXT_WPL_TID -> trackingId = value
                                                XSPF_EXT_M3U_UNKNOWN -> associatedComments =
                                                    value.lines()
                                                else if key.toString().startsWith(
                                                    XSPF_EXT_M3U_TV.toString()) -> {
                                                   tvKeys.add(key.toString().substring(startIndex =
                                                       XSPF_EXT_M3U_TV.toString().length) to value)
                                                }
                                                else -> metas.add(key to value)
                                            }
                                        }
                                        "extension" -> {
                                            val application = parser.getAttributeValue(
                                                null, "application").toUri()
                                            when (application) {
                                                else -> extensions += parser.tagDeepToUtf8()
                                            }
                                        }
                                        else -> throw XmlPullParserException("Unexpected " +
                                                "<track> tag <$tag>")
                                    }
                                    parser.require(XmlPullParser.END_TAG, x0,
                                        tag)
                                }
                                parser.require(XmlPullParser.END_TAG, x0,
                                    "track")
                                items.add(Entry(locations, identifiers = identifiers,
                                    title = title, artist = author, annotation = annotation,
                                    info = info, image = image, album = album, trackNum = trackNum,
                                    durationMs = durationMs?.toULong(), links = links,
                                    metas = metas, contentId = contentId, trackingId = trackingId,
                                    extensions = extensions, tvKeys = tvKeys,
                                    associatedComments = associatedComments))
                            }
                        }
                        else -> throw XmlPullParserException("Unexpected <playlist> tag <$tag>")
                    }
                    parser.require(XmlPullParser.END_TAG, x0, tag)
                }
                parser.require(XmlPullParser.END_TAG, x0, "playlist")
                Playlist(items, title = title, author = author, annotation = annotation,
                    info = info, location = location, identifier = identifier, image = image,
                    dateCreatedUtc = dateCreatedUtc, timezoneOffsetSecs = timezoneOffsetSecs,
                    license = license, attribution = attribution, links = links, metas = metas,
                    wplMetaTags = wplMetaTags, genre = genre, category = category, album = album,
                    userName = userName, generator = generator, extensions = extensions,
                    artist = artist, titleKey = titleKey, tvKeys = tvKeys)
            }
        }
    }

    private fun write(context: Context, format: PlaylistFormat, outFile: File, uri: Uri, playlist: Playlist) {
        val parent = outFile.parentFile
            ?: throw NullPointerException("parentFile of playlist is null")
        val os = if (uri.scheme == ContentResolver.SCHEME_FILE)
            outFile.outputStream()
        else MediaStoreCompat.openOutputStream(context, uri, "wt")!!
        os.use { _ ->
            val songs = playlist.entries
            when (format) {
                PlaylistFormat.M3u -> {
                    val out = StringBuilder("#EXTM3U\r\n")
                    if (playlist.title != null)
                        out.append("#${playlist.titleKey ?: "PLAYLIST"}:${playlist.title}\r\n")
                    if (playlist.album != null)
                        out.append("#EXTALB:${playlist.album}\r\n")
                    if (playlist.artist != null)
                        out.append("#EXTART:${playlist.artist}\r\n")
                    if (playlist.genre != null)
                        out.append("#EXTGENRE:${playlist.genre}\r\n")
                    songs.filter { it.locations.isNotEmpty() }.forEach {
                        it.associatedComments?.forEach { comment -> out.append("$comment\r\n") }
                        if (it.title != null || it.durationMs != null ||
                            !it.tvKeys.isNullOrEmpty()
                        ) {
                            val tvKeys = if (!it.tvKeys.isNullOrEmpty())
                                it.tvKeys.joinToString(" ", prefix = " ") { (k, v) ->
                                    """$k="${
                                        v.replace("\\", "\\\\")
                                            .replace("\"", "\\\"")
                                    }""""
                                } else ""
                            out.append(
                                "#EXTINF:${it.durationMs ?: -1}$tvKeys," +
                                        "${it.title ?: it.nameWithoutExtension}\r\n"
                            )
                        }
                        out.append("${it.toRelativeString(parent)}\r\n")
                    }
                    os.use { it.writer().use { writer -> writer.write(out.toString()) } }
                }

                PlaylistFormat.Wpl -> {
                    val doc = Xml.newSerializer()
                    // will output in DOS line endings
                    doc.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
                    doc.setOutput(os, StandardCharsets.UTF_8.name())
                    doc.startDocument(null, true)
                    doc.startTag(null, "smil")
                    doc.startTag(null, "head")

                    if (playlist.title != null) {
                        doc.startTag(null, "title")
                        doc.text(playlist.title)
                        doc.endTag(null, "title")
                    }

                    doc.startTag(null, "meta")
                    doc.attribute(null, "name", "Generator")
                    doc.attribute(
                        null, "content", "Gramophone " +
                                "${BuildConfig.MY_VERSION_NAME}/${BuildConfig.RELEASE_TYPE}"
                    )
                    doc.endTag(null, "meta")
                    if (songs.find { it.durationMs == null } == null) {
                        doc.startTag(null, "meta")
                        doc.attribute(null, "name", "TotalDuration")
                        doc.attribute(
                            null, "content", songs
                            .sumOf { it.durationMs!! }.div(1000u).toString()
                        )
                        doc.endTag(null, "meta")
                    }
                    if (playlist.author != null) {
                        doc.startTag(null, "meta")
                        doc.attribute(null, "name", "Author")
                        doc.attribute(null, "content", playlist.author)
                        doc.endTag(null, "meta")
                    }
                    if (playlist.category != null) {
                        doc.startTag(null, "meta")
                        doc.attribute(null, "name", "Category")
                        doc.attribute(null, "content", playlist.category)
                        doc.endTag(null, "meta")
                    }
                    if (playlist.genre != null) {
                        doc.startTag(null, "meta")
                        doc.attribute(null, "name", "Genre")
                        doc.attribute(null, "content", playlist.genre)
                        doc.endTag(null, "meta")
                    }
                    if (playlist.userName != null) {
                        doc.startTag(null, "meta")
                        doc.attribute(null, "name", "UserName")
                        doc.attribute(null, "content", playlist.userName)
                        doc.endTag(null, "meta")
                    }
                    doc.startTag(null, "meta")
                    doc.attribute(null, "name", "ItemCount")
                    doc.attribute(null, "content", songs.size.toString())
                    doc.endTag(null, "meta")
                    if (playlist.wplMetaTags != null) {
                        for (tag in playlist.wplMetaTags) {
                            doc.startTag(null, "meta")
                            doc.attribute(null, "name", tag.first)
                            doc.attribute(null, "content", tag.second)
                            doc.endTag(null, "meta")
                        }
                    }

                    doc.endTag(null, "head")
                    doc.startTag(null, "body")
                    doc.startTag(null, "seq")
                    for (item in songs) {
                        if (item.locations.isNotEmpty()) {
                            doc.startTag(null, "media")
                            doc.attribute(
                                null, "src",
                                item.toRelativeString(parent)
                            )
                            if (item.contentId != null) {
                                doc.attribute(null, "cid", item.contentId)
                            }
                            if (item.trackingId != null) {
                                doc.attribute(null, "tid", item.trackingId)
                            }
                            doc.endTag(null, "media")
                        }
                    }
                    doc.endTag(null, "seq")
                    doc.endTag(null, "body")
                    doc.endTag(null, "smil")
                    doc.endDocument()
                }

                PlaylistFormat.Pls -> {
                    val songs = songs.filter { it.locations.isNotEmpty() }
                    val out = "[playlist]\r\n" + songs.mapIndexed { i, it ->
                        "File${i + 1}=${it.toRelativeString(parent)}\r\n" +
                                "Title${i + 1}=${it.title ?: it.nameWithoutExtension}\r\n" +
                                "Length${i + 1}=${it.durationMs ?: -1}"
                    }.joinToString("\r\n").trim() +
                            "\r\nNumberOfEntries=${songs.size}\r\nVersion=2\r\n"
                    os.use { it.writer().use { writer -> writer.write(out) } }
                }

                PlaylistFormat.Xspf -> {
                    val x0 = "http://xspf.org/ns/0/"
                    val doc = Xml.newSerializer()
                    doc.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
                    doc.setOutput(os, StandardCharsets.UTF_8.name())
                    doc.startDocument("utf-8", true)
                    doc.setPrefix("", x0)
                    doc.startTag(x0, "playlist")
                    doc.attribute(null, "version", "1")

                    if (playlist.title != null) {
                        doc.startTag(x0, "title")
                        doc.text(playlist.title)
                        doc.endTag(x0, "title")
                    }
                    if (playlist.author != null) {
                        doc.startTag(x0, "creator")
                        doc.text(playlist.author)
                        doc.endTag(x0, "creator")
                    }
                    if (playlist.annotation != null) {
                        doc.startTag(x0, "annotation")
                        doc.text(playlist.annotation)
                        doc.endTag(x0, "annotation")
                    }
                    if (playlist.info != null) {
                        doc.startTag(x0, "info")
                        doc.text(playlist.info.toString())
                        doc.endTag(x0, "info")
                    }
                    if (playlist.location != null) {
                        doc.startTag(x0, "location")
                        doc.text(playlist.location.toString())
                        doc.endTag(x0, "location")
                    }
                    if (playlist.identifier != null) {
                        doc.startTag(x0, "identifier")
                        doc.text(playlist.identifier.toString())
                        doc.endTag(x0, "identifier")
                    }
                    if (playlist.image != null) {
                        doc.startTag(x0, "image")
                        if (playlist.image.scheme == "file") {
                            doc.text(
                                Uri.Builder().path(
                                    playlist.image.toFile()
                                        .toRelativeString(parent)
                                ).build().toString()
                            )
                        } else {
                            doc.text(playlist.image.toString())
                        }
                        doc.endTag(x0, "image")
                    }
                    if (playlist.dateCreatedUtc != null) {
                        doc.startTag(x0, "date")
                        if ((playlist.timezoneOffsetSecs ?: 0) == 0) {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                            doc.text(sdf.format(Date(playlist.dateCreatedUtc * 1000)))
                        } else {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                            sdf.timeZone = SimpleTimeZone(
                                playlist.timezoneOffsetSecs!! * 1000, "")
                            val t = sdf.format(Date(playlist.dateCreatedUtc * 1000))
                            doc.text("${t.substring(0, t.length - 2)}:" +
                                    t.substring(t.length - 2, t.length))
                        }
                        doc.endTag(x0, "date")
                    }
                    if (playlist.license != null) {
                        doc.startTag(x0, "license")
                        doc.text(playlist.license.toString())
                        doc.endTag(x0, "license")
                    }
                    if (playlist.attribution != null) {
                        doc.startTag(x0, "attribution")
                        for (attribution in playlist.attribution) {
                            if (attribution.first) {
                                doc.startTag(x0, "location")
                            } else {
                                doc.startTag(x0, "identifier")
                            }
                            doc.text(attribution.second)
                            if (attribution.first) {
                                doc.endTag(x0, "location")
                            } else {
                                doc.endTag(x0, "identifier")
                            }
                        }
                        doc.endTag(x0, "attribution")
                    }
                    if (playlist.links != null) {
                        for (link in playlist.links) {
                            doc.startTag(x0, "link")
                            doc.attribute(null, "rel", link.first.toString())
                            doc.text(link.second.toString())
                            doc.endTag(x0, "link")
                        }
                    }
                    if (playlist.metas != null) {
                        for (meta in playlist.metas) {
                            doc.startTag(x0, "meta")
                            doc.attribute(null, "rel", meta.first.toString())
                            doc.text(meta.second)
                            doc.endTag(x0, "meta")
                        }
                    }
                    if (playlist.wplMetaTags != null) {
                        for (meta in playlist.wplMetaTags) {
                            doc.startTag(x0, "meta")
                            doc.attribute(
                                null, "rel",
                                "$XSPF_EXT_WPL_META/${meta.first}"
                            )
                            doc.text(meta.second)
                            doc.endTag(x0, "meta")
                        }
                    }
                    if (playlist.category != null) {
                        doc.startTag(x0, "meta")
                        doc.attribute(null, "rel", "$XSPF_EXT_WPL_META/Category")
                        doc.text(playlist.category)
                        doc.endTag(x0, "meta")
                    }
                    if (playlist.userName != null) {
                        doc.startTag(x0, "meta")
                        doc.attribute(null, "rel", "$XSPF_EXT_WPL_META/UserName")
                        doc.text(playlist.userName)
                        doc.endTag(x0, "meta")
                    }
                    if (playlist.genre != null) {
                        doc.startTag(x0, "meta")
                        doc.attribute(null, "rel", "$XSPF_EXT_M3U_GENRE")
                        doc.text(playlist.genre)
                        doc.endTag(x0, "meta")
                    }
                    if (playlist.album != null) {
                        doc.startTag(x0, "meta")
                        doc.attribute(null, "rel", "$XSPF_EXT_M3U_ALBUM")
                        doc.text(playlist.album)
                        doc.endTag(x0, "meta")
                    }
                    if (playlist.artist != null) {
                        doc.startTag(x0, "meta")
                        doc.attribute(null, "rel", "$XSPF_EXT_M3U_ARTIST")
                        doc.text(playlist.artist)
                        doc.endTag(x0, "meta")
                    }
                    if (playlist.titleKey != null) {
                        doc.startTag(x0, "meta")
                        doc.attribute(null, "rel", "$XSPF_EXT_M3U_TITLE_KEY")
                        doc.text(playlist.titleKey)
                        doc.endTag(x0, "meta")
                    }
                    if (playlist.tvKeys != null) {
                        for (meta in playlist.tvKeys) {
                            doc.startTag(x0, "meta")
                            doc.attribute(
                                null, "rel",
                                "$XSPF_EXT_M3U_TV/${meta.first}"
                            )
                            doc.text(meta.second)
                            doc.endTag(x0, "meta")
                        }
                    }
                    doc.startTag(x0, "meta")
                    doc.attribute(null, "rel", "$XSPF_EXT_GENERATOR")
                    doc.text("Gramophone ${BuildConfig.MY_VERSION_NAME}/${BuildConfig.RELEASE_TYPE}")
                    doc.endTag(x0, "meta")

                    if (playlist.extensions != null) {
                        doc.flush()
                        for (extension in playlist.extensions) {
                            os.write(extension)
                        }
                    }
                    doc.startTag(x0, "trackList")
                    for (entry in playlist.entries) {
                        doc.startTag(x0, "track")
                        for (location in entry.locations) {
                            doc.startTag(x0, "location")
                            if (location.scheme == "file") {
                                doc.text(
                                    Uri.Builder().path(
                                        location.toFile()
                                            .toRelativeString(parent)
                                    ).build().toString()
                                )
                            } else {
                                doc.text(location.toString())
                            }
                            doc.endTag(x0, "location")
                        }
                        if (entry.identifiers != null) {
                            for (identifier in entry.identifiers) {
                                doc.startTag(x0, "identifier")
                                doc.text(identifier.toString())
                                doc.endTag(x0, "identifier")
                            }
                        }
                        if (entry.title != null) {
                            doc.startTag(x0, "title")
                            doc.text(entry.title)
                            doc.endTag(x0, "title")
                        }
                        if (entry.artist != null) {
                            doc.startTag(x0, "creator")
                            doc.text(entry.artist)
                            doc.endTag(x0, "creator")
                        }
                        if (entry.annotation != null) {
                            doc.startTag(x0, "annotation")
                            doc.text(entry.annotation)
                            doc.endTag(x0, "annotation")
                        }
                        if (entry.info != null) {
                            doc.startTag(x0, "info")
                            doc.text(entry.info.toString())
                            doc.endTag(x0, "info")
                        }
                        if (entry.image != null) {
                            doc.startTag(x0, "image")
                            if (entry.image.scheme == "file") {
                                doc.text(
                                    Uri.Builder().path(
                                        entry.image.toFile()
                                            .toRelativeString(parent)
                                    ).build().toString()
                                )
                            } else {
                                doc.text(entry.image.toString())
                            }
                            doc.endTag(x0, "image")
                        }
                        if (entry.album != null) {
                            doc.startTag(x0, "album")
                            doc.text(entry.album)
                            doc.endTag(x0, "album")
                        }
                        if (entry.trackNum != null) {
                            doc.startTag(x0, "trackNum")
                            doc.text(entry.trackNum.toString())
                            doc.endTag(x0, "trackNum")
                        }
                        if (entry.durationMs != null) {
                            doc.startTag(x0, "duration")
                            doc.text(entry.durationMs.toString())
                            doc.endTag(x0, "duration")
                        }
                        if (entry.links != null) {
                            for (link in entry.links) {
                                doc.startTag(x0, "link")
                                doc.attribute(null, "rel", link.first.toString())
                                doc.text(link.second.toString())
                                doc.endTag(x0, "link")
                            }
                        }
                        if (entry.metas != null) {
                            for (meta in entry.metas) {
                                doc.startTag(x0, "meta")
                                doc.attribute(null, "rel", meta.first.toString())
                                doc.text(meta.second)
                                doc.endTag(x0, "meta")
                            }
                        }
                        if (!entry.associatedComments.isNullOrEmpty()) {
                            doc.startTag(x0, "meta")
                            doc.attribute(null, "rel", "$XSPF_EXT_M3U_UNKNOWN")
                            doc.text(entry.associatedComments.joinToString("\r\n"))
                            doc.endTag(x0, "meta")
                        }
                        if (entry.tvKeys != null) {
                            for (meta in entry.tvKeys) {
                                doc.startTag(x0, "meta")
                                doc.attribute(
                                    null, "rel",
                                    "$XSPF_EXT_M3U_TV/${meta.first}"
                                )
                                doc.text(meta.second)
                                doc.endTag(x0, "meta")
                            }
                        }
                        if (entry.contentId != null) {
                            doc.startTag(x0, "meta")
                            doc.attribute(null, "rel", "$XSPF_EXT_WPL_CID")
                            doc.text(entry.contentId)
                            doc.endTag(x0, "meta")
                        }
                        if (entry.trackingId != null) {
                            doc.startTag(x0, "meta")
                            doc.attribute(null, "rel", "$XSPF_EXT_WPL_TID")
                            doc.text(entry.trackingId)
                            doc.endTag(x0, "meta")
                        }
                        if (entry.extensions != null) {
                            doc.flush()
                            for (extension in entry.extensions) {
                                os.write(extension)
                            }
                        }
                        doc.endTag(x0, "track")
                    }
                    doc.endTag(x0, "trackList")

                    doc.endTag(x0, "playlist")
                    doc.endDocument()
                }
            }
        }
    }

    // Serialize the entire current tag to a ByteArray in UTF-8. Current event must be START_TAG,
    // and when returned the event is END_TAG.
    private fun XmlPullParser.tagDeepToUtf8(): ByteArray {
        val scratchOutputStream = ByteArrayOutputStream()
        val xmlSerializer = Xml.newSerializer()
        xmlSerializer.setOutput(scratchOutputStream, StandardCharsets.UTF_8.name())
        val startDepth = depth
        while (depth >= startDepth) {
            when (eventType) {
                XmlPullParser.START_DOCUMENT -> xmlSerializer.startDocument(null, false)
                XmlPullParser.END_DOCUMENT -> xmlSerializer.endDocument()
                XmlPullParser.START_TAG -> {
                    xmlSerializer.startTag(namespace, name)
                    val nsStart = getNamespaceCount(depth - 1)
                    val nsEnd = getNamespaceCount(depth)
                    for (i in nsStart..<nsEnd) {
                        xmlSerializer.setPrefix(
                            getNamespacePrefix(i),
                            getNamespaceUri(i))
                    }
                    for (i in 0..<attributeCount) {
                        xmlSerializer.attribute(
                            getAttributeNamespace(i),
                            getAttributeName(i),
                            getAttributeValue(i)
                        )
                    }
                }

                XmlPullParser.END_TAG -> xmlSerializer.endTag(namespace, name)
                XmlPullParser.TEXT -> xmlSerializer.text(text)
                XmlPullParser.CDSECT -> xmlSerializer.cdsect(text)
                XmlPullParser.ENTITY_REF -> xmlSerializer.entityRef(text)
                XmlPullParser.IGNORABLE_WHITESPACE -> xmlSerializer.ignorableWhitespace(text)
                XmlPullParser.PROCESSING_INSTRUCTION -> xmlSerializer.processingInstruction(text)
                XmlPullParser.COMMENT -> xmlSerializer.comment(text)
                XmlPullParser.DOCDECL -> xmlSerializer.docdecl(text)
                else -> {}
            }
            nextToken()
        }
        xmlSerializer.flush()
        return scratchOutputStream.toByteArray()
    }

    private enum class PlaylistFormat {
        M3u,
        Xspf,
        Wpl,
        Pls,
    }

    @Parcelize
    data class Playlist(
        val entries: List<Entry>,
        val title: String? = null,
        val author: String? = null, // who made the playlist
        val artist: String? = null, // if the playlist is album, artist of album
        val category: String? = null,
        val album: String? = null,
        val genre: String? = null,
        val generator: String? = null,
        val userName: String? = null,
        val totalDuration: Long? = null, // seconds
        val wplMetaTags: List<Pair<String, String>>? = null,
        val annotation: String? = null,
        val info: Uri? = null,
        val location: Uri? = null,
        val identifier: Uri? = null,
        val image: Uri? = null,
        val dateCreatedUtc: Long? = null,
        val timezoneOffsetSecs: Int? = null, // useful so that we can save in same timezone
        val license: Uri? = null,
        val attribution: List<Pair<Boolean, String>>? = null,
        val links: List<Pair<Uri, Uri>>? = null,
        val metas: List<Pair<Uri, String>>? = null,
        val extensions: List<ByteArray>? = null,
        val titleKey: String? = null,
        val tvKeys: List<Pair<String, String>>? = null,
    ) : Parcelable {
        companion object {
            fun create() = Playlist(emptyList(),
                dateCreatedUtc = System.currentTimeMillis() / 1000L,
                timezoneOffsetSecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        (Instant.now().atZone(ZoneOffset.systemDefault()).offset.totalSeconds)
                else (@Suppress("deprecation") Date().timezoneOffset * 60))
        }
    }

    /**
     * A playlist entry, which is composed of a path to a file and optional additional metadata used
     * to resolve the song if the file path doesn't exist (the song could've been moved).
     */
    @Parcelize
    data class Entry(
        // file:// Uris are converted to absolute in memory but should always be relative on disk
        val locations: List<Uri>,
        val title: String? = null, // pls, m3u, xspf
        val tvKeys: List<Pair<String, String>>? = null, // m3u
        val artist: String? = null, // xspf
        val album: String? = null, // xspf
        val associatedComments: List<String>? = null, // m3u
        val contentId: String? = null, // wpl
        val trackingId: String? = null, // wpl
        val identifiers: List<Uri>? = null, // xspf
        val annotation: String? = null, // xspf
        val info: Uri? = null, // xspf
        val image: Uri? = null, // xspf
        val trackNum: UInt? = null, // xspf
        val durationMs: ULong? = null, // xspf, pls, m3u
        val links: List<Pair<Uri, Uri>>? = null, // xspf
        val metas: List<Pair<Uri, String>>? = null, // xspf
        val extensions: List<ByteArray>? = null, // xspf
    ) : Parcelable {
        companion object {
            private val uriRegex = Regex("[A-Za-z]{2,}[A-Za-z0-9+.-]*:.+")
            fun parseUri(outFile: File, line: String) =
                if (uriRegex.matchEntire(line) != null) {
                    val uri = line.toUri()
                    if (uri.scheme == "file")
                        outFile.resolveSibling(uri.toFile()).toOkioPath(normalize = true)
                            .toFile().toUriCompat()
                    else
                        uri
                } else
                    outFile.resolveSibling(Uri.decode(line))
                        .toOkioPath(normalize = true).toFile().toUriCompat()
            fun ofAbstract(file: File) = Entry(listOf(file.toUriCompat()))
            fun ofM3u(
                file: List<Uri>,
                durationSeconds: Long?,
                tvKeys: List<Pair<String, String>>?,
                title: String?,
                associatedComments: List<String>?,
            ) = Entry(file, title = title, durationMs = durationSeconds
                ?.takeIf { it != -1L }?.times(1000L)?.toULong(), tvKeys = tvKeys,
                associatedComments = associatedComments)
            fun ofPls(file: List<Uri>, title: String?, length: Long?) =
                Entry(file, title = title, durationMs =
                    length.takeIf { it != -1L }?.times(1000L)?.toULong())
            // https://learn.microsoft.com/en-us/previous-versions/windows/desktop/wmp/media-element
            fun ofWpl(file: List<Uri>, contentId: String?, trackingId: String?) =
                Entry(file, contentId = contentId, trackingId = trackingId)
            fun ofMediaItem(song: MediaItem) = song.getFile()?.let {
                Entry(listOf(it.toUriCompat())) }?.copyFromMediaItem(song)
        }
        val preferredLocation
            get() = locations.first()
        val nameWithoutExtension
            get() = preferredLocation.lastPathSegment?.substringBeforeLast('.') ?: ""
        fun toRelativeString(base: File) = if (preferredLocation.scheme == "file")
            preferredLocation.toFile().toRelativeString(base)
        else preferredLocation.toString()
        fun updateFromMediaItem(pathMap: Map<String, MediaItem>?) =
            resolveMediaItem(pathMap)?.let { copyFromMediaItem(it) } ?: this
        // TODO(ASAP): add basic xspf (maybe for extm3u or pls too?) content resolving
        fun resolveMediaItem(pathMap: Map<String, MediaItem>?) =
            locations.filter { it.scheme == "file" }.firstNotNullOfOrNull { link ->
                pathMap!![link.toFile().absolutePath]
            }
        // TODO: support multiple location
        fun resolveMediaItem2(pathMapFlow: Flow<IncrementalMap<String, MediaItem>>) =
            locations.find { it.scheme == "file" }?.let { link ->
                pathMapFlow.forKey(link.toFile().absolutePath)
            }
        fun copyFromMediaItem(song: MediaItem) = copy(
            locations = if (song.getFile()?.toUriCompat()?.let { f -> locations.find { it == f } !=
                    null } == false) run {
                val keepAlternatives = locations.filter {
                    it.scheme != "file" // TODO(ASAP) filter windows path or bad prefix for
                    //  android or sd card that isn't in recent volumes of mediastore
                }
                listOf(song.getFile()!!.toUriCompat()) + keepAlternatives
            } else locations,
            title = song.mediaMetadata.title?.toString(),
            durationMs = song.mediaMetadata.durationMs?.toULong(),
            artist = song.mediaMetadata.artist?.toString(),
            album = song.mediaMetadata.albumTitle?.toString(),
            trackNum = song.mediaMetadata.trackNumber?.toUInt()
            // TODO(ASAP) write album art jpg path (if external instead of embedded) to "image" field
        )

        fun fuzzyEquals(other: Entry): Boolean {
            return locations.find { other.locations.contains(it) } != null
        }
    }
    class UnsupportedPlaylistFormatException(extension: String) : Exception(extension)
}