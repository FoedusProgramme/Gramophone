/*
 *     Copyright (C) 2024 Akane Foundation
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

package org.akanework.gramophone.logic.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.VisibleForTesting
import androidx.media3.common.Metadata
import androidx.media3.common.util.Log
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import org.akanework.gramophone.logic.queryWithPending
import org.akanework.gramophone.logic.utils.SemanticLyrics.SyncedLyrics
import org.nift4.mediastorecompat.MediaStoreCompat
import java.io.File
import java.nio.charset.Charset

object LrcUtils {

    private const val TAG = "LrcUtils"

    enum class LyricFormat {
        LRC,
        TTML,
        SRT
    }

    data class LrcParserOptions(
        val trim: Boolean,
        val multiLine: Boolean,
        val errorText: String?,
        val bracketWordSync: Boolean = false
    )

    @VisibleForTesting
    fun parseLyrics(
        lyrics: String,
        audioMimeType: String?,
        parserOptions: LrcParserOptions,
        format: LyricFormat?
    ): SemanticLyrics? {
        for (i in listOf({
            if (format == null || format == LyricFormat.TTML)
                parseTtml(audioMimeType, lyrics)
            else null
        }, {
            if (format == null || format == LyricFormat.SRT)
                parseSrt(lyrics, parserOptions.trim)
            else null
        }, {
            if (format == null || format == LyricFormat.LRC)
                parseLrc(lyrics, parserOptions.trim, parserOptions.multiLine, parserOptions.bracketWordSync)
            else null
        })) {
            return try {
                val ret = i() ?: continue
                if (ret is SyncedLyrics && Flags.HIDE_SAME_TRANSLATIONS)
                    ret.copy(text = ret.text.filterIndexed { i, it ->
                        !it.isTranslated || it.text != ret.text.subList(0, i)
                            .last { !it.isTranslated }.text
                    })
                else
                    ret
            } catch (e: Exception) {
                if (parserOptions.errorText == null)
                    throw e
                Log.e(TAG, Log.getThrowableString(e)!!)
                Log.e(TAG, "The lyrics are:\n$lyrics")
                SemanticLyrics.UnsyncedLyrics(
                    listOf(
                        parserOptions.errorText + "\n\n${
                            Log.getThrowableString(
                                e
                            )!!
                        }\n\n$lyrics" to null
                    )
                )
            }
        }
        return null
    }

    // returns best lyrics first
    fun extractAndParseLyrics(
        sampleRate: Int,
        audioMimeType: String?,
        metadata: Metadata,
        parserOptions: LrcParserOptions
    ): List<SemanticLyrics> {
        val out = mutableListOf<SemanticLyrics>()
        for (i in 0..<metadata.length()) {
            val meta = metadata.get(i)
            if (meta is BinaryFrame && (meta.id == "SYLT" || meta.id == "SLT")) {
                val syltData = try {
                    UsltFrameDecoder.decodeSylt(sampleRate, ParsableByteArray(meta.data))
                } catch (e: Exception) {
                    if (parserOptions.errorText == null)
                        throw e
                    Log.e(TAG, Log.getThrowableString(e)!!)
                    Log.e(TAG, "The lyrics are:\n${meta.data.toHexString()}")
                    out.add(SemanticLyrics.UnsyncedLyrics(
                        listOf(
                            parserOptions.errorText + "\n\n${
                                Log.getThrowableString(
                                    e
                                )!!
                            }" to null
                        )
                    ))
                    continue
                }
                if (syltData != null) {
                    if (syltData.contentType == 1 || syltData.contentType == 2) {
                        out.add(try {
                            val ret = syltData.toSyncedLyrics(parserOptions.trim)
                            if (Flags.HIDE_SAME_TRANSLATIONS)
                                ret.copy(text = ret.text.filterIndexed { i, it ->
                                    !it.isTranslated || it.text != ret.text.subList(0, i)
                                        .last { !it.isTranslated }.text
                                })
                            else
                                ret
                        } catch (e: Exception) {
                            if (parserOptions.errorText == null)
                                throw e
                            Log.e(TAG, Log.getThrowableString(e)!!)
                            Log.e(TAG, "The lyrics are:\n$syltData")
                            SemanticLyrics.UnsyncedLyrics(
                                listOf(
                                    parserOptions.errorText + "\n\n${
                                        Log.getThrowableString(
                                            e
                                        )!!
                                    }\n\n$syltData" to null
                                )
                            )
                        })
                    }
                    continue
                }
            }
            val plainTextData =
                if (meta is VorbisComment && meta.key == "LYRICS") // vorbis comments
                    meta.value
                else if (meta is BinaryFrame && (meta.id == "USLT" || meta.id == "ULT"
                            || meta.id == "SLT" /* out-of-spec */
                            || meta.id == "SYLT" /* out-of-spec */)
                ) // ID3
                    UsltFrameDecoder.decode(ParsableByteArray(meta.data))?.text
                else if (meta is TextInformationFrame && meta.id == "USLT") // mp4
                    meta.values.joinToString("\n")
                else null
            if (plainTextData != null) {
                parseLyrics(plainTextData, audioMimeType, parserOptions, null)?.let {
                    out.add(it)
                    continue
                }
            }
        }
        out.sortBy {
            if (it !is SyncedLyrics) {
                return@sortBy -10
            }
            val hasWords = it.text.find { it.words != null } != null
            val hasTl = it.text.find { it.isTranslated } != null
            (if (hasWords) 10 else 0) + (if (hasTl) 1 else 0)
        }
        return out
    }

    fun loadAndParseLyricsFile(
        context: Context,
        musicFile: File?,
        audioMimeType: String?,
        parserOptions: LrcParserOptions
    ): SemanticLyrics? {
        if (!Flags.MEDIASTORE_IO) {
            return loadTextFile(
                musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".ttml") },
                parserOptions.errorText
            )?.let { parseLyrics(it, audioMimeType, parserOptions, LyricFormat.TTML) }
                ?: loadTextFile(
                    musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".srt") },
                    parserOptions.errorText
                )?.let { parseLyrics(it, audioMimeType, parserOptions, LyricFormat.SRT) }
                ?: loadTextFile(
                    musicFile?.let { File(it.parentFile, it.nameWithoutExtension + ".lrc") },
                    parserOptions.errorText
                )?.let { parseLyrics(it, audioMimeType, parserOptions, LyricFormat.LRC) }
        }
        if (musicFile == null) return null
        val extensions = listOf("ttml", "srt", "lrc")
        val extensionsEnum = listOf(LyricFormat.TTML, LyricFormat.SRT, LyricFormat.LRC)
        val loaded = hashMapOf<LyricFormat, Long>()
        context.contentResolver.queryWithPending(MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA),
            "${MediaStore.Files.FileColumns.DATA} IN (" + extensions.joinToString { "?" }
                    + ")", extensions.map { musicFile.resolveSibling(
                musicFile.nameWithoutExtension + ".$it").path }.toTypedArray(),
            null).use { c ->
            if (c != null && c.moveToFirst()) {
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                do {
                    loaded[extensionsEnum[extensions.indexOf(File(c.getString(
                        dataColumn)!!).extension)]] = c.getLong(idColumn)
                } while (c.moveToNext())
            }
        }
        extensionsEnum.forEach { format ->
            val id = loaded[format] ?: return@forEach
            loadTextFile(context, ContentUris.withAppendedId(
                MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI, id),
                parserOptions.errorText
            )?.let {
                return parseLyrics(it, audioMimeType, parserOptions, format)
            }
        }
        return null
    }

    private fun loadTextFile(context: Context, lrcFile: Uri, errorText: String?): String? {
        return try {
            context.contentResolver.openInputStream(lrcFile)!!.use {
                it.readBytes().toString(Charset.defaultCharset())
            }
        } catch (e: Exception) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            return errorText
        }
    }

    private fun loadTextFile(lrcFile: File?, errorText: String?): String? {
        return try {
            if (lrcFile?.exists() == true)
                lrcFile.readBytes().toString(Charset.defaultCharset())
            else null
        } catch (e: Exception) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            return errorText
        }
    }
}