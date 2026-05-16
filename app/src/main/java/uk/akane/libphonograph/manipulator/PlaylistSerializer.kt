package uk.akane.libphonograph.manipulator

import android.content.Context
import android.net.Uri
import okio.Path.Companion.toOkioPath
import org.nift4.mediastorecompat.MediaStoreCompat
import java.io.File

object PlaylistSerializer {
    @Throws(UnsupportedPlaylistFormatException::class)
    fun write(context: Context, outFile: File, out: Uri, songs: List<File>) {
        val format = when (outFile.extension.lowercase()) {
            "m3u", "m3u8" -> PlaylistFormat.M3u
            // "xspf" -> PlaylistFormat.Xspf
            // "wpl" -> PlaylistFormat.Wpl
            // "pls" -> PlaylistFormat.Pls
            else -> throw UnsupportedPlaylistFormatException(outFile.extension)
        }
        write(context, format, outFile, out, songs)
    }

    @Throws(UnsupportedPlaylistFormatException::class)
    fun read(outFile: File): List<File> {
        val format = when (outFile.extension.lowercase()) {
            "m3u", "m3u8" -> PlaylistFormat.M3u
            // "xspf" -> PlaylistFormat.Xspf
            // "wpl" -> PlaylistFormat.Wpl
            // "pls" -> PlaylistFormat.Pls
            else -> throw UnsupportedPlaylistFormatException(outFile.extension)
        }
        return read(format, outFile)
    }

    private fun read(format: PlaylistFormat, outFile: File): List<File> {
        return when (format) {
            PlaylistFormat.M3u -> {
                val lines = outFile.readLines()
                lines.filter { !it.startsWith('#') }.map { Uri.decode(it) }.map {
                    outFile.resolveSibling(it).toOkioPath(normalize = true).toFile()
                }
            }

            PlaylistFormat.Xspf -> TODO()
            PlaylistFormat.Wpl -> TODO()
            PlaylistFormat.Pls -> TODO()
        }
    }

    private fun write(context: Context, format: PlaylistFormat, outFile: File, uri: Uri, songs: List<File>) {
        when (format) {
            PlaylistFormat.M3u -> {
                val parent = outFile.parentFile
                    ?: throw NullPointerException("parentFile of playlist is null")
                val out =
                    "#EXTM3U\n" + songs.joinToString("\n") { it.relativeTo(parent).toString() }
                        .trim()
                MediaStoreCompat.openOutputStream(context, uri, "wt")!!.use {
                    it.writer().use { writer -> writer.write(out) }
                }
            }

            PlaylistFormat.Xspf -> TODO()
            PlaylistFormat.Wpl -> TODO()
            PlaylistFormat.Pls -> TODO()
        }
    }

    private enum class PlaylistFormat {
        M3u,
        Xspf,
        Wpl,
        Pls,
    }

    class UnsupportedPlaylistFormatException(extension: String) : Exception(extension)
}