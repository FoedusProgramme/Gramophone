/*
 *     Copyright (C) 2025 nift4
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

package org.nift4.gramophone.hificore

import android.annotation.SuppressLint
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import androidx.annotation.RequiresApi
import androidx.media3.common.util.Log

object AudioTrackHiddenApi {
    private const val TAG = "AudioTrackHiddenApi"
    private const val TRACE_TAG = "GpNativeTrace"
    var libLoaded = false
        private set

    init {
        if (canLoadLib()) {
            if (!AdaptiveDynamicRangeCompression.libLoaded) {
                try {
                    Log.d(TRACE_TAG, "Loading libhificore.so")
                    System.loadLibrary("hificore")
                    libLoaded = true
                    Log.d(TRACE_TAG, "Done loading libhificore.so")
                } catch (e: Throwable) {
                    Log.e(TAG, Log.getThrowableString(e)!!)
                }
            } else libLoaded = true
        }
    }

    /**
     * A device may be banned from loading `libhificore.so` because it has crashed with a
     * memory-related problem in Play Console. In order to rule out this being related to hacks in
     * `libhificore.so`, these devices are banned from loading it. If further crashes appear, the
     * ban can be lifted again because it's not our fault. If there are no further crashes, it may
     * indicate an incompatibility of `libhificore.so` with the device's firmware.
     */
    fun canLoadLib(): Boolean {
        return !(Build.VERSION.SDK_INT == 33 && Build.BRAND == "TECNO" &&
                Build.PRODUCT.startsWith("BG6-")) && // Tecno SPARK Go 2024
            !(Build.VERSION.SDK_INT == 34 && Build.BRAND == "samsung" &&
                    Build.DEVICE == "dm1q") // Samsung Galaxy S23
    }

    @SuppressLint("PrivateApi")
    private fun getAudioTrackPtr(audioTrack: AudioTrack): Long {
        if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
            throw IllegalArgumentException("cannot get pointer for released AudioTrack")
        val cls = audioTrack.javaClass
        val field = cls.getDeclaredField("mNativeTrackInJavaObj")
        field.isAccessible = true
        return field.get(audioTrack) as Long
    }

    fun getHalSampleRate(audioTrack: AudioTrack): UInt? {
        if (!libLoaded)
            return null
        if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
            throw IllegalArgumentException("cannot get hal sample rate for released AudioTrack")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // getHalSampleRate() exists since below commit which first appeared in Android U
            // https://cs.android.com/android/_/android/platform/frameworks/av/+/310037a32d56e361d5b5156b74f8846f92bc245e
            val ret = try {
                getHalSampleRateInternal(getAudioTrackPtr(audioTrack))
            } catch (e: Throwable) {
                Log.e(TAG, Log.getThrowableString(e)!!)
                null
            }
            if (ret != null && ret != 0)
                return ret.toUInt()
            return null
        }
        val output = getOutput(audioTrack) ?: return null
        return AudioSystemHiddenApi.getSampleRate(output)
    }
    private external fun getHalSampleRateInternal(@Suppress("unused") audioTrackPtr: Long): Int

    fun getHalChannelCount(audioTrack: AudioTrack): Int? {
        if (!libLoaded)
            return null
        if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
            throw IllegalArgumentException("cannot get hal channel count for released AudioTrack")
        // before U, caller should query channel mask from audio_port/audio_port_v7
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) null else
        // getHalChannelCount() exists since below commit which first appeared in Android U
        // https://cs.android.com/android/_/android/platform/frameworks/av/+/310037a32d56e361d5b5156b74f8846f92bc245e
            try {
                Log.d(TRACE_TAG, "calling native getHalChannelCountInternal/getAudioTrackPtr")
                getHalChannelCountInternal(getAudioTrackPtr(audioTrack))
            } catch (e: Throwable) {
                Log.e(TAG, Log.getThrowableString(e)!!)
                null
            }.also { Log.d(TRACE_TAG, "native getHalChannelCountInternal/getAudioTrackPtr is done: $it") }
    }
    private external fun getHalChannelCountInternal(@Suppress("unused") audioTrackPtr: Long): Int

    fun getHalFormat(audioTrack: AudioTrack): UInt? {
        if (!libLoaded)
            return null
        if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
            throw IllegalArgumentException("cannot get hal format for released AudioTrack")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // getHalFormat() exists since below commit which first appeared in Android U
            // https://cs.android.com/android/_/android/platform/frameworks/av/+/310037a32d56e361d5b5156b74f8846f92bc245e
            val ret = try {
                Log.d(TRACE_TAG, "calling native getHalFormatInternal/getAudioTrackPtr")
                getHalFormatInternal(getAudioTrackPtr(audioTrack))
            } catch (e: Throwable) {
                Log.e(TAG, Log.getThrowableString(e)!!)
                null
            }.also { Log.d(TRACE_TAG, "native getHalChannelCountInternal/getAudioTrackPtr is done: $it") }
            if (ret != null && ret != 0)
                return ret.toUInt()
            return null
        }
        val output = getOutput(audioTrack) ?: return null
        return AudioSystemHiddenApi.getFormat(output)
    }
    private external fun getHalFormatInternal(@Suppress("unused") audioTrackPtr: Long): Int

    fun findAfTrackFlags(dump: String?, latency: Int?, track: AudioTrack, grantedFlags: Int?): Int? {
        if (!libLoaded)
            return null
        // First exposure to client process was below commit, which first appeared in U QPR2.
        // https://cs.android.com/android/_/android/platform/frameworks/av/+/94ed47c6b6ca5a69b90238f6ae97af2ce7df9be0
        // Offload is added here only since V:
        // https://cs.android.com/android/_/android/platform/frameworks/av/+/2b51523207689cfa3047e0ca45451f3068a545c0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            return null
        try {
            val dump = dump ?: throw NullPointerException("af track dump is null, check prior logs")
            val latency = latency ?: throw NullPointerException("af track latency is null, check prior logs")
            val theLine = dump.split('\n').first { it.contains("AF SampleRate") }
            val theLine2 = dump.split('\n').first { it.contains("format(0x") }
            val regex = Regex(".*AF latency \\(([0-9]+)\\) AF frame count\\(([0-9]+)\\) AF SampleRate\\(([0-9]+)\\).*")
            val regex2 = Regex(".*format\\(0x([0-9a-f]+)\\), .*")
            val match = regex.matchEntire(theLine) ?: throw NullPointerException("failed match of $theLine")
            val match2 = regex2.matchEntire(theLine2) ?: throw NullPointerException("failed match2 of $theLine2")
            val afLatency = match.groupValues.getOrNull(1)?.toIntOrNull()
                ?: throw NullPointerException("failed parsing afLatency in: $theLine")
            val afFrameCount = match.groupValues.getOrNull(2)?.toLongOrNull()
                ?: throw NullPointerException("failed parsing afFrameCount in: $theLine")
            val afSampleRate = match.groupValues.getOrNull(3)?.toIntOrNull()
                ?: throw NullPointerException("failed parsing afSampleRate in: $theLine")
            val format = match2.groupValues.getOrNull(1)?.toUIntOrNull(radix = 16)?.toInt()
                ?: throw NullPointerException("failed parsing format in: $theLine2")
            val ptr = getAudioTrackPtr(track)
            Log.d(TRACE_TAG, "calling native findAfTrackFlagsInternal")
            return findAfTrackFlagsInternal(ptr, afLatency, afFrameCount, afSampleRate, latency, format).let {
                if (it == Int.MAX_VALUE || it == Int.MIN_VALUE)
                    null // something went wrong, this was logged to logcat
                else if (grantedFlags != null && (it or grantedFlags) != it) {
                    // should never happen
                    Log.e(TAG, "af track flags($it) are nonsense, |$grantedFlags = ${it or grantedFlags}")
                    null
                } else it
            }.also { Log.d(TRACE_TAG, "native findAfTrackFlagsInternal is done: $it") }
        } catch (e: Throwable) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            return null
        }
    }
    @Suppress("unused") // for parameters
    private external fun findAfTrackFlagsInternal(pointer: Long, afLatency: Int, afFrameCount: Long,
                                                  afSampleRate: Int, latency: Int, format: Int): Int

    fun getOutput(audioTrack: AudioTrack): Int? {
        if (!libLoaded)
            return null
        if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
            throw IllegalArgumentException("cannot get hal output for released AudioTrack")
        Log.d(TRACE_TAG, "calling native getOutputInternal/getAudioTrackPtr")
        return try {
            getOutputInternal(getAudioTrackPtr(audioTrack))
        } catch (e: Throwable) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            null
        }.also { Log.d(TRACE_TAG, "native getOutputInternal/getAudioTrackPtr is done: $it") }
    }
    private external fun getOutputInternal(@Suppress("unused") audioTrackPtr: Long): Int

    fun getGrantedFlags(audioTrack: AudioTrack): Int? {
        if (!libLoaded)
            return null
        if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
            throw IllegalArgumentException("cannot get hal output for released AudioTrack")
        Log.d(TRACE_TAG, "calling native getOutputInternal/getAudioTrackPtr")
        return try {
            getFlagsInternal(audioTrack, getAudioTrackPtr(audioTrack)).let {
                if (it == Int.MAX_VALUE || it == Int.MIN_VALUE)
                    null // something went wrong, native side logged reason to logcat
                else it
            }
        } catch (e: Throwable) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            null
        }.also { Log.d(TRACE_TAG, "native getOutputInternal/getAudioTrackPtr is done: $it") }
    }
    @Suppress("unused") // for parameters
    /*private*/ external fun getFlagsInternal(audioTrack: AudioTrack?, audioTrackPtr: Long): Int

    fun dump(audioTrack: AudioTrack): String? {
        if (!libLoaded)
            return null
        if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED)
            throw IllegalArgumentException("cannot dump released AudioTrack")
        Log.d(TRACE_TAG, "calling native dump/getAudioTrackPtr")
        return try {
            dumpInternal(getAudioTrackPtr(audioTrack))
        } catch (e: Throwable) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            null
        }.also { Log.d(TRACE_TAG, "native dump/getAudioTrackPtr is done: $it") }
    }
    /*private*/ external fun dumpInternal(@Suppress("unused") audioTrackPtr: Long): String

    private val idRegex = Regex(".*id\\((.*)\\) .*")
    fun getPortIdFromDump(dump: String?): Int? {
        if (dump == null)
            return null
        // ID is in dump output since below commit which first appeared in Q.
        // https://cs.android.com/android/_/android/platform/frameworks/av/+/fb8ede2a020e741cb892ee024fcfba7e689183f2
        // https://cs.android.com/android/_/android/platform/frameworks/av/+/973db02ac18fa1de9ce6221f47b01af1bdc4bec2
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            return null
        val dt = dump.trim().split('\n').map { it.trim() }
        if (dt.size < 2) {
            Log.e(
                TAG,
                "getIdFromDump() failure: not enough lines, DUMP:\n$dump"
            )
            return null
        }
        if (dt[0] != "AudioTrack::dump") {
            Log.e(
                TAG,
                "getIdFromDump() failure: L0 isn't AudioTrack::dump, DUMP:\n$dump"
            )
            return null
        }
        if (!dt[1].contains("id(")) {
            Log.e(
                TAG,
                "getIdFromDump() failure: L1 didn't contain id(, DUMP:\n$dump"
            )
            return null
        }
        val idText = idRegex.matchEntire(dt[1])?.groupValues[1]
        if (idText == null) {
            Log.e(
                TAG,
                "getIdFromDump() failure: L1 didn't match regex, DUMP:\n$dump"
            )
            return null
        }
        idText.toIntOrNull()?.let { return it }
        Log.e(
            TAG,
            "getIdFromDump() failure: $idText didn't convert to int from base 10, DUMP:\n$dump"
        )
        return null
    }

    private val notificationRegex = Regex(".*notif. frame count\\((.*)\\), req\\. notif\\. frame .*")
    fun getNotificationFramesActFromDump(dump: String?): Int? {
        if (dump == null)
            return null
        // ID is in dump output since below commit which first appeared in P.
        // https://cs.android.com/android/_/android/platform/frameworks/av/+/d114b624ea2ec5c51779b74132a60b4a46f6cdba
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            return null
        val dt = dump.trim().split('\n').map { it.trim() }
        if (dt.size < 7) {
            Log.e(
                TAG,
                "getNotificationFramesActFromDump() failure: not enough lines, DUMP:\n$dump"
            )
            return null
        }
        if (dt[0] != "AudioTrack::dump") {
            Log.e(
                TAG,
                "getNotificationFramesActFromDump() failure: L0 isn't AudioTrack::dump, DUMP:\n$dump"
            )
            return null
        }
        if (!dt[6].contains("notif. frame count(")) {
            Log.e(
                TAG,
                "getNotificationFramesActFromDump() failure: L6 didn't contain notif. frame count(, DUMP:\n$dump"
            )
            return null
        }
        val notificationText = notificationRegex.matchEntire(dt[6])?.groupValues[1]
        if (notificationText == null) {
            Log.e(
                TAG,
                "getNotificationFramesActFromDump() failure: L6 didn't match regex, DUMP:\n$dump"
            )
            return null
        }
        notificationText.toIntOrNull()?.let { return it }
        Log.e(
            TAG,
            "getNotificationFramesActFromDump() failure: $notificationText didn't convert to int, DUMP:\n$dump"
        )
        return null
    }

    private val latencyRegex = Regex(".*\\), latency \\((.*)\\).*")
    fun getLatencyFromDump(dump: String?): Int? {
        if (dump == null)
            return null
        // use AudioTrack.getLatency() on N+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            return null
        val dt = dump.trim().split('\n').map { it.trim() }
        if (dt.size < 5) {
            Log.e(
                TAG,
                "getLatencyFromDump() failure: not enough lines, DUMP:\n$dump"
            )
            return null
        }
        if (dt[0] != "AudioTrack::dump") {
            Log.e(
                TAG,
                "getLatencyFromDump() failure: L0 isn't AudioTrack::dump, DUMP:\n$dump"
            )
            return null
        }
        if (!dt[4].contains("latency (")) {
            Log.e(
                TAG,
                "getLatencyFromDump() failure: L4 didn't contain latency (, DUMP:\n$dump"
            )
            return null
        }
        val text = latencyRegex.matchEntire(dt[4])?.groupValues[1]
        if (text == null) {
            Log.e(
                TAG,
                "getLatencyFromDump() failure: L4 didn't match regex, DUMP:\n$dump"
            )
            return null
        }
        text.toIntOrNull()?.let { return it }
        Log.e(
            TAG,
            "getLatencyFromDump() failure: $text didn't convert to int, DUMP:\n$dump"
        )
        return null
    }

    private val frameCountRegex = Regex(".*\\), frame count \\((.*)\\).*")
    fun getFrameCountFromDump(dump: String?): Long? {
        if (dump == null)
            return null
        val dt = dump.trim().split('\n').map { it.trim() }
        if (dt.size < 3) {
            Log.e(
                TAG,
                "getFrameCountFromDump() failure: not enough lines, DUMP:\n$dump"
            )
            return null
        }
        if (dt[0] != "AudioTrack::dump") {
            Log.e(
                TAG,
                "getFrameCountFromDump() failure: L0 isn't AudioTrack::dump, DUMP:\n$dump"
            )
            return null
        }
        if (!dt[2].contains("latency (")) {
            Log.e(
                TAG,
                "getFrameCountFromDump() failure: L2 didn't contain latency (, DUMP:\n$dump"
            )
            return null
        }
        val text = frameCountRegex.matchEntire(dt[2])?.groupValues[1]
        if (text == null) {
            Log.e(
                TAG,
                "getFrameCountFromDump() failure: L2 didn't match regex, DUMP:\n$dump"
            )
            return null
        }
        text.toLongOrNull()?.let { return it }
        Log.e(
            TAG,
            "getFrameCountFromDump() failure: $text didn't convert to int, DUMP:\n$dump"
        )
        return null
    }

    private val stateRegex = Regex(".*state\\((.*)\\), latency.*")
    fun getStateFromDump(dump: String?): Int? {
        if (dump == null)
            return null
        val dt = dump.trim().split('\n').map { it.trim() }
        if (dt.size < 5) {
            Log.e(
                TAG,
                "getStateFromDump() failure: not enough lines, DUMP:\n$dump"
            )
            return null
        }
        if (dt[0] != "AudioTrack::dump") {
            Log.e(
                TAG,
                "getStateFromDump() failure: L0 isn't AudioTrack::dump, DUMP:\n$dump"
            )
            return null
        }
        if (!dt[4].contains("state(")) {
            Log.e(
                TAG,
                "getStateFromDump() failure: L4 didn't contain state(, DUMP:\n$dump"
            )
            return null
        }
        val text = stateRegex.matchEntire(dt[2])?.groupValues[1]
        if (text == null) {
            Log.e(
                TAG,
                "getStateFromDump() failure: L4 didn't match regex, DUMP:\n$dump"
            )
            return null
        }
        text.toIntOrNull()?.let { return it }
        Log.e(
            TAG,
            "getStateFromDump() failure: $text didn't convert to int, DUMP:\n$dump"
        )
        return null
    }
}