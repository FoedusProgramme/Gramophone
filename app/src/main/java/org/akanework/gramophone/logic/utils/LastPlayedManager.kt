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

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.media3.common.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import uk.akane.libphonograph.items.EXTRA_ADD_DATE
import uk.akane.libphonograph.items.EXTRA_ALBUM_ID
import uk.akane.libphonograph.items.EXTRA_ALBUM_YEAR
import uk.akane.libphonograph.items.EXTRA_ARTIST_ID
import uk.akane.libphonograph.items.EXTRA_AUTHOR
import uk.akane.libphonograph.items.EXTRA_CD_TRACK_NUMBER
import uk.akane.libphonograph.items.EXTRA_MODIFIED_DATE
import uk.akane.libphonograph.items.addDate
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.albumYear
import uk.akane.libphonograph.items.artistId
import uk.akane.libphonograph.items.author
import uk.akane.libphonograph.items.cdTrackNumber
import uk.akane.libphonograph.items.modifiedDate
import java.nio.charset.StandardCharsets

class LastPlayedManager(
    context: Context,
    private val controller: EndedWorkaroundPlayer
) {

    companion object {
        private const val TAG = "LastPlayedManager"
    }

    var allowSavingState = true
    private var job: Job? = null
    private val prefs by lazy { context.getSharedPreferences("LastPlayedManager", 0) }

    private fun dumpPlaylist(): MediaItemsWithStartPosition {
        val items = mutableListOf<MediaItem>()
        for (i in 0 until controller.mediaItemCount) {
            items.add(controller.getMediaItemAt(i))
        }
        return MediaItemsWithStartPosition(
            items, controller.currentMediaItemIndex, controller.currentPosition
        )
    }

    fun eraseShuffleOrder() {
        prefs.edit(commit = true) {
            putString("shuffle_persist", null)
        }
    }

    fun save() {
        if (!allowSavingState) {
            Log.i(TAG, "skipped save")
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "dumping playlist...")
        }
        val data = dumpPlaylist()
        val repeatMode = controller.repeatMode
        val shuffleModeEnabled = controller.shuffleModeEnabled
        val playbackParameters = controller.playbackParameters
        val persistent = if (controller.shuffleModeEnabled)
            CircularShuffleOrder.Persistent(controller.exoPlayer.shuffleOrder as CircularShuffleOrder)
        else null
        val ended = controller.playbackState == Player.STATE_ENDED
        job?.cancel()
        job = CoroutineScope(Dispatchers.Default).launch {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG, "saving playlist (${data.mediaItems.size} items, repeat $repeatMode, " +
                            "shuffle $shuffleModeEnabled, ended $ended)..."
                )
            }
            val lastPlayed = PrefsListUtils.dump(
                data.mediaItems.map {
                    val b = SafeDelimitedStringConcat(":")
                    // add new entries at the bottom and remember they are null for upgrade path
                    b.writeStringUnsafe("ver_" + 1)
                    b.writeStringSafe(it.mediaId)
                    b.writeUri(it.localConfiguration?.uri)
                    b.writeStringSafe(it.localConfiguration?.mimeType)
                    b.writeStringSafe(it.mediaMetadata.title)
                    b.writeStringSafe(it.mediaMetadata.artist)
                    b.writeStringSafe(it.mediaMetadata.albumTitle)
                    b.writeStringSafe(it.mediaMetadata.albumArtist)
                    b.writeUri(it.mediaMetadata.artworkUri)
                    b.writeInt(it.mediaMetadata.trackNumber)
                    b.writeInt(it.mediaMetadata.discNumber)
                    b.writeInt(it.mediaMetadata.recordingYear)
                    b.writeInt(it.mediaMetadata.releaseYear)
                    b.writeBool(it.mediaMetadata.isBrowsable)
                    b.writeBool(it.mediaMetadata.isPlayable)
                    b.writeLong(it.mediaMetadata.addDate)
                    b.writeStringSafe(it.mediaMetadata.writer)
                    b.writeStringSafe(it.mediaMetadata.compilation)
                    b.writeStringSafe(it.mediaMetadata.composer)
                    b.writeStringSafe(it.mediaMetadata.genre)
                    b.writeInt(it.mediaMetadata.recordingDay)
                    b.writeInt(it.mediaMetadata.recordingMonth)
                    b.writeLong(it.mediaMetadata.artistId)
                    b.writeLong(it.mediaMetadata.albumId)
                    b.writeStringSafe(it.mediaMetadata.author)
                    b.writeLong(it.mediaMetadata.durationMs)
                    b.writeLong(it.mediaMetadata.modifiedDate)
                    b.writeStringSafe(it.mediaMetadata.cdTrackNumber)
                    b.writeLong(it.mediaMetadata.albumYear)
                    b.toString()
                })
            prefs.edit {
                putStringSet("last_played_lst", lastPlayed.first)
                putString("last_played_grp", lastPlayed.second)
                putInt("last_played_idx", data.startIndex)
                putLong("last_played_pos", data.startPositionMs)
                putInt("repeat_mode", repeatMode)
                putBoolean("shuffle", shuffleModeEnabled)
                putString("shuffle_persist", persistent?.toString())
                putBoolean("ended", ended)
                putFloat("speed", playbackParameters.speed)
                putFloat("pitch", playbackParameters.pitch)
                apply()
            }
        }
    }

    suspend fun restore(callback: (MediaItemsWithStartPosition?, CircularShuffleOrder.Persistent) -> Unit) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "decoding playlist...")
        }
        withContext(Dispatchers.Default) {
            val seed = try {
                CircularShuffleOrder.Persistent.deserialize(
                    prefs.getString(
                        "shuffle_persist",
                        null
                    )
                )
            } catch (e: Exception) {
                eraseShuffleOrder()
                throw e
            }
            try {
                val lastPlayedLst = prefs.getStringSet("last_played_lst", null)?.toSet()
                val lastPlayedGrp = prefs.getString("last_played_grp", null)
                val lastPlayedIdx = prefs.getInt("last_played_idx", 0)
                val lastPlayedPos = prefs.getLong("last_played_pos", 0)
                if (lastPlayedGrp == null || lastPlayedLst == null) {
                    runCallback(callback, seed) { null }
                    return@withContext
                }
                val repeatMode = prefs.getInt("repeat_mode", Player.REPEAT_MODE_OFF)
                val shuffleModeEnabled = prefs.getBoolean("shuffle", false)
                val ended = prefs.getBoolean("ended", false)
                val playbackParameters = PlaybackParameters(
                    prefs.getFloat("speed", 1f),
                    prefs.getFloat("pitch", 1f)
                )
                val data = MediaItemsWithStartPosition(
                    PrefsListUtils.parse(lastPlayedLst, lastPlayedGrp)
                        .map {
                            val b = SafeDelimitedStringDecat(":", it)
                            // add new entries at the bottom and remember they are null for upgrade path
                            val versionStr = b.readStringUnsafe()
                            val version = versionStr.let {
                                if (it?.startsWith("ver_") == true)
                                    it.substring("ver_".length).toInt()
                                else 0
                            }
                            val mediaId = if (version == 0) {
                                "MediaStore:$versionStr" // used to be mediaId
                            } else b.readStringSafe()
                            var uri = b.readUri()
                            if (version == 0 && uri?.toString()?.let {
                                it.startsWith("/storage/") || it.startsWith("/mnt/")
                                        || (@SuppressLint("SdCardPath")
                                it.startsWith("/sdcard/")) } == true)
                                uri = uri.buildUpon().scheme("file").build()
                            val mimeType = b.readStringSafe()
                            val title = b.readStringSafe()
                            val artist = b.readStringSafe()
                            val album = b.readStringSafe()
                            val albumArtist = b.readStringSafe()
                            val imgUri = b.readUri()
                            val trackNumber = b.readInt()
                            val discNumber = b.readInt()
                            val recordingYear = b.readInt()
                            val releaseYear = b.readInt()
                            val isBrowsable = b.readBool()
                            val isPlayable = b.readBool()
                            val addDate = b.readLong()
                            val writer = b.readStringSafe()
                            val compilation = b.readStringSafe()
                            val composer = b.readStringSafe()
                            val genre = b.readStringSafe()
                            val recordingDay = b.readInt()
                            val recordingMonth = b.readInt()
                            val artistId = b.readLong()
                            val albumId = b.readLong()
                            if (version < 1)
                                b.skip() // used to be GenreId
                            val author = b.readStringSafe()
                            if (version < 1)
                                b.skip() // used to be CdTrackNumber
                            val duration = b.readLong()
                            if (version < 1)
                                b.skip() // used to be Path
                            val modifiedDate = b.readLong()
                            val cdTrackNumber = b.readStringSafe()
                            val albumYear = b.readLong()
                            MediaItem.Builder()
                                .setUri(uri)
                                .setMediaId(mediaId!!)
                                .setMimeType(mimeType)
                                .setMediaMetadata(
                                    MediaMetadata
                                        .Builder()
                                        .setTitle(title)
                                        .setArtist(artist)
                                        .setWriter(writer)
                                        .setComposer(composer)
                                        .setGenre(genre)
                                        .setCompilation(compilation)
                                        .setRecordingDay(recordingDay)
                                        .setRecordingMonth(recordingMonth)
                                        .setAlbumTitle(album)
                                        .setAlbumArtist(albumArtist)
                                        .setArtworkUri(imgUri)
                                        .setTrackNumber(trackNumber)
                                        .setDiscNumber(discNumber)
                                        .setRecordingYear(recordingYear)
                                        .setReleaseYear(releaseYear)
                                        .setDurationMs(duration)
                                        .setIsBrowsable(isBrowsable)
                                        .setIsPlayable(isPlayable)
                                        .setExtras(Bundle().apply {
                                            if (addDate != null) {
                                                putLong(EXTRA_ADD_DATE, addDate)
                                            }
                                            if (artistId != null) {
                                                putLong(EXTRA_ARTIST_ID, artistId)
                                            }
                                            if (albumId != null) {
                                                putLong(EXTRA_ALBUM_ID, albumId)
                                            }
                                            if (albumYear != null) {
                                                putLong(EXTRA_ALBUM_YEAR, albumYear)
                                            }
                                            putString(EXTRA_CD_TRACK_NUMBER, cdTrackNumber)
                                            putString(EXTRA_AUTHOR, author)
                                            if (modifiedDate != null) {
                                                putLong(EXTRA_MODIFIED_DATE, modifiedDate)
                                            }
                                        })
                                        .build()
                                )
                                .build()
                        },
                    lastPlayedIdx,
                    lastPlayedPos
                )
                runCallback(callback, seed) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "restoring playlist (${data.mediaItems.size} items, repeat $repeatMode, " +
                                    "shuffle $shuffleModeEnabled, ended $ended)..."
                        )
                    }
                    controller.isEnded = ended
                    controller.repeatMode = repeatMode
                    controller.shuffleModeEnabled = shuffleModeEnabled
                    controller.playbackParameters = playbackParameters
                    data
                }
                return@withContext
            } catch (e: Exception) {
                try {
                    this@LastPlayedManager.eraseShuffleOrder()
                } catch (_: Exception) {
                }
                Log.e(TAG, Log.getThrowableString(e)!!)
                runCallback(callback, seed) { null }
                return@withContext
            }
        }
    }
}

private suspend inline fun runCallback(
    crossinline callback: (
        MediaItemsWithStartPosition?,
        CircularShuffleOrder.Persistent
    ) -> Unit,
    seed: CircularShuffleOrder.Persistent,
    noinline parameter: () -> MediaItemsWithStartPosition?
) {
    withContext(Dispatchers.Main) { callback(parameter(), seed) }
}

private class SafeDelimitedStringConcat(private val delimiter: String) {
    private val b = StringBuilder()
    private var hadFirst = false

    private fun append(s: String?) {
        if (s?.contains(delimiter, false) == true) {
            throw IllegalArgumentException("argument must not contain delimiter")
        }
        if (hadFirst) {
            b.append(delimiter)
        } else {
            hadFirst = true
        }
        s?.let { b.append(it) }
    }

    override fun toString(): String {
        return b.toString()
    }

    fun writeStringUnsafe(s: CharSequence?) = append(s?.toString())
    fun writeBase64(b: ByteArray?) = append(b?.let { Base64.encodeToString(it, Base64.DEFAULT) })
    fun writeStringSafe(s: CharSequence?) =
        writeBase64(s?.toString()?.toByteArray(StandardCharsets.UTF_8))
    fun writeInt(i: Int?) = append(i?.toString())
    fun writeLong(i: Long?) = append(i?.toString())
    fun writeBool(b: Boolean?) = append(b?.toString())
    fun writeUri(u: Uri?) = writeStringSafe(u?.toString())
    fun skip() = append(null)
}

private class SafeDelimitedStringDecat(delimiter: String, str: String) {
    private val items = str.split(delimiter)
    private var pos = 0

    private fun read(): String? {
        if (pos == items.size) return null
        return items[pos++].ifEmpty { null }
    }

    fun readStringUnsafe(): String? = read()
    fun readBase64(): ByteArray? = read()?.let { Base64.decode(it, Base64.DEFAULT) }
    fun readStringSafe(): String? = readBase64()?.toString(StandardCharsets.UTF_8)
    fun readInt(): Int? = read()?.toInt()
    fun readLong(): Long? = read()?.toLong()
    fun readBool(): Boolean? = read()?.toBooleanStrict()
    fun readUri(): Uri? = readStringSafe()?.toUri()
    fun skip() {
        read()
    }
}

private object PrefsListUtils {
    fun parse(stringSet: Set<String>, groupStr: String): List<String> {
        if (groupStr.isEmpty()) return emptyList()
        val groups = groupStr.split(",")
        return groups.map { hc ->
            stringSet.firstOrNull { it.hashCode().toString() == hc } ?:
                throw NoSuchElementException("tried to find \"$hc\" (from \"$groupStr\") in: " +
                        stringSet.joinToString { it.hashCode().toString() })
        }
    }

    fun dump(inList: List<String>): Pair<Set<String>, String> {
        val list = inList.map { it.trim() }
        return Pair(list.toSet(), list.joinToString(",") { it.hashCode().toString() })
    }
}