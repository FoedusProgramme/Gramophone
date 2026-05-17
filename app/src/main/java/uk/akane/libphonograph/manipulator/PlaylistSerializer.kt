package uk.akane.libphonograph.manipulator

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import android.util.SparseArray
import android.util.Xml
import androidx.core.util.keyIterator
import androidx.media3.common.MediaItem
import kotlinx.parcelize.Parcelize
import okio.Path.Companion.toOkioPath
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.getFile
import org.nift4.mediastorecompat.MediaStoreCompat
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern


object PlaylistSerializer {
    @Throws(UnsupportedPlaylistFormatException::class)
    fun write(context: Context, outFile: File, out: Uri, songs: List<Entry>) {
        val format = when (outFile.extension.lowercase()) {
            "m3u", "m3u8" -> PlaylistFormat.M3u
            // "xspf" -> PlaylistFormat.Xspf
            "wpl" -> PlaylistFormat.Wpl
            "pls" -> PlaylistFormat.Pls
            else -> throw UnsupportedPlaylistFormatException(outFile.extension)
        }
        write(context, format, outFile, out, songs)
    }

    @Throws(UnsupportedPlaylistFormatException::class)
    fun read(outFile: File): List<Entry> {
        val format = when (outFile.extension.lowercase()) {
            "m3u", "m3u8" -> PlaylistFormat.M3u
            // "xspf" -> PlaylistFormat.Xspf
            "wpl" -> PlaylistFormat.Wpl
            "pls" -> PlaylistFormat.Pls
            else -> throw UnsupportedPlaylistFormatException(outFile.extension)
        }
        return read(format, outFile)
    }

    private fun read(format: PlaylistFormat, outFile: File): List<Entry> {
        return when (format) {
            PlaylistFormat.M3u -> {
                val lines = outFile.readLines()
                lines.filter { !it.startsWith('#') }.map { Uri.decode(it) }.map {
                    Entry.ofM3u(outFile.resolveSibling(it)
                        .toOkioPath(normalize = true).toFile())
                }
            }
            PlaylistFormat.Wpl -> {
                val parser = Xml.newPullParser()
                parser.setInput(outFile.inputStream(), StandardCharsets.UTF_8.name())
                val items = mutableListOf<Entry>()
                var type: Int
                while (parser.next().also { type = it } != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG) continue
                    if ("smartPlaylist" == parser.name)
                        throw UnsupportedPlaylistFormatException("Windows Smart Playlist")
                    if ("media" == parser.name) {
                        val src = parser.getAttributeValue(null, "src")
                        if (src != null) {
                            val cid = parser.getAttributeValue(null, "cid")
                            val tid = parser.getAttributeValue(null, "tid")
                            items.add(Entry.ofWpl(outFile.resolveSibling(src.replace(
                                '\\', '/')).toOkioPath(normalize = true)
                                .toFile(), cid, tid))
                        }
                    }
                }
                items
            }

            PlaylistFormat.Pls -> {
                val filePattern = Pattern.compile("File(\\d+)=(.+)")
                val titlePattern = Pattern.compile("Title(\\d+)=(.+)")
                val lengthPattern = Pattern.compile("Length(\\d+)=(.+)")
                val files = SparseArray<File>()
                val titles = SparseArray<String>()
                val lengths = SparseArray<Long>()
                outFile.readLines().forEach {
                    val fileMatcher = filePattern.matcher(it)
                    if (fileMatcher.matches()) {
                        files.set(fileMatcher.group(1)!!.toInt(), outFile.resolveSibling(
                            fileMatcher.group(2)!!.replace('\\', '/'))
                            .toOkioPath(normalize = true).toFile())
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
                    entries.add(Entry.ofPls(files.get(it), titles.get(it), lengths.get(it)))
                }
                entries
            }

            PlaylistFormat.Xspf -> TODO()
        }
    }

    private fun write(context: Context, format: PlaylistFormat, outFile: File, uri: Uri, songs: List<Entry>) {
        val parent = outFile.parentFile
            ?: throw NullPointerException("parentFile of playlist is null")
        val os = MediaStoreCompat.openOutputStream(context, uri, "wt")!!
        when (format) {
            PlaylistFormat.M3u -> {
                val out = "#EXTM3U\n" + songs.joinToString("\n") {
                    it.file.relativeTo(parent).toString() }.trim()
                os.use { it.writer().use { writer -> writer.write(out) } }
            }

            PlaylistFormat.Wpl -> {
                val doc = Xml.newSerializer()
                doc.setOutput(os, StandardCharsets.UTF_8.name())
                doc.startDocument(null, true)
                doc.startTag(null, "smil")
                doc.startTag(null, "head")

                doc.startTag(null, "title")
                doc.text(outFile.nameWithoutExtension)
                doc.endTag(null, "title")

                doc.startTag(null, "meta")
                doc.attribute(null, "name", "Generator")
                doc.attribute(null, "content", "Gramophone " +
                        "${BuildConfig.MY_VERSION_NAME}/${BuildConfig.RELEASE_TYPE}")
                doc.endTag(null, "meta")

                doc.startTag(null, "meta")
                doc.attribute(null, "name", "ItemCount")
                doc.attribute(null, "content", songs.size.toString())
                doc.endTag(null, "meta")

                doc.endTag(null, "head")
                doc.startTag(null, "body")
                doc.startTag(null, "seq")
                for (item in songs) {
                    doc.startTag(null, "media")
                    doc.attribute(null, "src", item.file.relativeTo(parent).toString())
                    doc.endTag(null, "media")
                }
                doc.endTag(null, "seq")
                doc.endTag(null, "body")
                doc.endTag(null, "smil")
                doc.endDocument()
            }

            PlaylistFormat.Pls -> {
                val out = "[playlist]\n" + songs.mapIndexed { i, it ->
                    "File${i + 1}=${it.file.toRelativeString(parent)}\n" +
                            "Title${i + 1}=${it.title ?: it.file.nameWithoutExtension}\n" +
                            "Length${i + 1}=${it.durationSeconds ?: -1}"
                }.joinToString("\n").trim() +
                        "\nNumberOfEntries=${songs.size}\nVersion=2\n"
                os.use { it.writer().use { writer -> writer.write(out) } }
            }

            PlaylistFormat.Xspf -> TODO()
        }
    }

    private enum class PlaylistFormat {
        M3u,
        Xspf,
        Wpl,
        Pls,
    }

    /**
     * A playlist entry, which is composed of a path to a file and optional additional metadata used
     * to resolve the song if the file path doesn't exist (the song could've been moved).
     */
    @Parcelize @ConsistentCopyVisibility
    data class Entry private constructor(
        val file: File,
        val title: String? = null, // pls
        val durationSeconds: Long? = null, // pls
        val contentId: String? = null, // wpl
        val trackingId: String? = null, // wpl
    ) : Parcelable {
        companion object {
            fun ofAbstract(file: File) = Entry(file)
            fun ofM3u(file: File) = Entry(file)
            fun ofPls(file: File, title: String?, length: Long?) =
                Entry(file, title = title, durationSeconds = length.takeIf { it != -1L })
            // https://learn.microsoft.com/en-us/previous-versions/windows/desktop/wmp/media-element
            fun ofWpl(file: File, contentId: String?, trackingId: String?) =
                Entry(file, contentId = contentId, trackingId = trackingId)
            fun ofMediaItem(song: MediaItem) = song.getFile()?.let { Entry(it) }
                ?.copyFromMediaItem(song)
        }
        fun resolveMediaItem(pathMap: Map<String, MediaItem>?) =
            pathMap!![file.absolutePath]
        fun copyFromMediaItem(song: MediaItem) = copy(
            title = song.mediaMetadata.title?.toString(),
            durationSeconds = song.mediaMetadata.durationMs?.div(1000L),
        )
    }
    class UnsupportedPlaylistFormatException(extension: String) : Exception(extension)
}