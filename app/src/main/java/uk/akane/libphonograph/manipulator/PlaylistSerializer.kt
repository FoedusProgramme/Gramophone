package uk.akane.libphonograph.manipulator

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.media3.common.util.Log
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

object PlaylistSerializer {
    private const val TAG = "PlaylistSerializer"

    @Throws(UnsupportedPlaylistFormatException::class)
    fun write(context: Context, outFile: File, songs: List<File>) {
        val format = when (outFile.extension.lowercase()) {
            "m3u", "m3u8" -> PlaylistFormat.M3u
            // "xspf" -> PlaylistFormat.Xspf
            // "wpl" -> PlaylistFormat.Wpl
            // "pls" -> PlaylistFormat.Pls
            else -> throw UnsupportedPlaylistFormatException(outFile.extension)
        }
        write(context, format, outFile, songs)
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
					outFile.resolveSibling(it).toOkioPath(normalize = true).toFile() }
            }
            PlaylistFormat.Xspf -> TODO()
            PlaylistFormat.Wpl -> TODO()
            PlaylistFormat.Pls -> TODO()
        }
    }

    private fun write(context: Context, format: PlaylistFormat, outFile: File, songs: List<File>) {
        when (format) {
            PlaylistFormat.M3u -> {
                val parent = outFile.parentFile ?: throw NullPointerException("parentFile of playlist is null")
                val out = "#EXTM3U\n" + songs.joinToString("\n") { it.relativeTo(parent).toString() }.trim()
                outFile.writeText(out)
            }
            PlaylistFormat.Xspf -> TODO()
            PlaylistFormat.Wpl -> TODO()
            PlaylistFormat.Pls -> TODO()
        }
        MediaScannerConnection.scanFile(context, arrayOf(outFile.toString()), null) { path, uri ->
            if (uri == null) {
                Log.e(TAG, "failed to scan playlist $path")
            }
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