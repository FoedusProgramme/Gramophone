/*
 *     Copyright (C) 2011 The Android Open Source Project
 *                   2025 nift4
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
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMetadataReadMap
import android.media.AudioPresentation
import android.media.AudioRouting
import android.media.AudioTrack
import android.media.VolumeShaper
import android.media.metrics.LogSessionId
import android.os.Build
import android.os.Parcel
import android.os.PersistableBundle
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.media3.common.util.Log
import java.nio.ByteBuffer

/*
 * Exposes most of the API surface of AudioTrack.cpp, with one minor exceptions:
 * - setCallerName/getCallerName because I want to avoid offset hardcoding, and it's only used for metrics
 * All native method calls are wrapped to avoid Throwables from being thrown - only Exceptions will be thrown by
 * this class or its methods. However, you should always be prepared to handle such an exception, as everything can
 * fail.
 * TODO: tone down the magic numbers a bit.
 */
@Suppress("unused")
class NativeTrack(context: Context, attributes: AudioAttributes, streamType: Int, sampleRate: Int,
                  format: UInt, channelMask: UInt, frameCount: Int?, trackFlags: Int,
                  sessionId: Int, maxRequiredSpeed: Float, selectedDeviceId: Int?, bitRate: Int, durationUs: Long,
                  hasVideo: Boolean, smallBuf: Boolean, isStreaming: Boolean, offloadBufferSize: Int,
                  notificationFrames: Int, doNotReconnect: Boolean, transferMode: TransferMode, contentId: Int?,
                  syncId: Int?, encapsulationMode: Int, sharedMem: ByteBuffer?) {
    companion object {
        private const val TAG = "NativeTrack.kt"
        const val ENCAPSULATION_MODE_NONE = 0 // AudioTrack.ENCAPSULATION_MODE_NONE
        const val ENCAPSULATION_MODE_ELEMENTARY_STREAM = 1 // AudioTrack.ENCAPSULATION_MODE_ELEMENTARY_STREAM
        const val ENCAPSULATION_MODE_HANDLE = 2 // AudioTrack.ENCAPSULATION_MODE_HANDLE

        enum class TransferMode(val id: Int) {
            Callback(1), // onMoreData() called by track
            Obtain(2), // user calls obtainBuffer() and releaseBuffer()
            Sync(3), // user calls write()
            Shared(4), // shared memory ctor parameter
            @RequiresApi(Build.VERSION_CODES.Q)
            SyncWithCallback(5) // user calls write(), track calls onCanWriteMoreData()
        }

        data class DirectPlaybackSupport(val normalOffload: Boolean, val gaplessOffload: Boolean,
                                         val directBitstream: Boolean) {
            companion object {
                val NONE = DirectPlaybackSupport(
	                normalOffload = false,
	                gaplessOffload = false,
	                directBitstream = false
                )
                val OFFLOAD = DirectPlaybackSupport(
	                normalOffload = true,
	                gaplessOffload = false,
	                directBitstream = false
                )
                val GAPLESS_OFFLOAD = DirectPlaybackSupport(
	                normalOffload = false,
	                gaplessOffload = true,
	                directBitstream = false
                )
                val DIRECT = DirectPlaybackSupport(
	                normalOffload = false,
	                gaplessOffload = false,
	                directBitstream = true
                )
            }
            val offload
                get() = normalOffload || gaplessOffload
            val directOrOffload
                get() = directBitstream || offload
        }

        fun getDirectPlaybackSupport(context: Context, sampleRate: Int, encoding: UInt, platformEncoding: Int?,
                                     channelMask: UInt, platformChannelMask: Int?): DirectPlaybackSupport {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = platformEncoding?.let { platformChannelMask?.let {
                buildAudioFormat(sampleRate, platformEncoding, platformChannelMask) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && format != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!(@Suppress("deprecation")
                        AudioTrack.isDirectPlaybackSupported(format, attributes))) {
                        // No direct or offload port exists... but let's try inactive routes.
                        val type = @Suppress("deprecation")
                            AudioManager.getPlaybackOffloadSupport(format, attributes)
                        if (type != AudioManager.PLAYBACK_OFFLOAD_NOT_SUPPORTED) {
                            // TODO: also none, but explain that offload is available on diff routes
                            return DirectPlaybackSupport.NONE
                        }
                        return DirectPlaybackSupport.NONE // if there's nothing suitable, give up
                    }
                    // Data point: either direct or offload port must exist.
                    val am = context.getSystemService<AudioManager>()!!
                    val profiles = am.getDirectProfilesForAttributes(attributes).toMutableList()
                    return if (profiles.isNotEmpty()) {
                        // Data point: there is no non-offloadable effect.
                        profiles.removeIf { it.format != format.encoding ||
                                !it.channelMasks.contains(format.channelMask) ||
                                !it.sampleRates.contains(format.sampleRate) }
                        if (profiles.isEmpty()) {
                            Log.w(TAG, "missing matching profile for" +
                                    "$format: ${am.getDirectProfilesForAttributes(attributes)}")
                        }
                        val offloadType = @Suppress("deprecation")
                            AudioManager.getPlaybackOffloadSupport(format, attributes)
                        if (offloadType != AudioManager.PLAYBACK_OFFLOAD_NOT_SUPPORTED) {
                            // Best case, as we can with confidence say what we have.
                            val hasGaplessOffloadCurrently = offloadType ==
                                    AudioManager.PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED
                            val hasDirect = (AudioManager.getDirectPlaybackSupport(format, attributes)
                                    and AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED) != 0
                            DirectPlaybackSupport(!hasGaplessOffloadCurrently,
                                hasGaplessOffloadCurrently, hasDirect)
                        } else
                        // Either offload is prevented by master mono or props, or it doesn't exist.
                        // TODO: use AudioSystem.getMasterMono to report that offload is not working
                        if (profiles.size > 1) {
                            // While possible, odds are that there is a direct port instead of two
                            // offload ports.
                            DirectPlaybackSupport.DIRECT
                        } else DirectPlaybackSupport.DIRECT // TODO: low confidence flag
                    } else {
                        // Data point: there's a non-offloadable effect present. But the port could
                        // still be unimpacted because it's direct.
                        DirectPlaybackSupport.DIRECT // TODO: low confidence flag
                    }
                } else {
                    // be careful: both of these methods consider inactive routes
                    return when (getPlaybackOffloadSupportPlatformCompat(format, attributes)) {
                        AudioManager.PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED -> return DirectPlaybackSupport.GAPLESS_OFFLOAD
                        AudioManager.PLAYBACK_OFFLOAD_SUPPORTED -> return DirectPlaybackSupport.OFFLOAD
                        else -> {
                            // isDirectPlaybackSupported does not care whether offload is possible,
                            // and will happily return true if offload profile is found and pretend
                            // it's direct. but we can't detect it.
                            if (@Suppress("deprecation")
                                AudioTrack.isDirectPlaybackSupported(format, attributes))
                                DirectPlaybackSupport.DIRECT // TODO: low confidence flag
                            else DirectPlaybackSupport.NONE
                        }
                    }
                }
            }
            val bitWidth = bitsPerSampleForFormat(encoding)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // TODO implement native getDirectProfilesForAttributes
                return DirectPlaybackSupport.NONE // TODO implement native getDirectPlaybackSupport
            }
            // TODO before T, inactive routes were considered in isDirectPlaybackSupported according to AOSP doc.
            //  does that apply to getPlaybackOffloadSupport and isOffloadSupported too?
            val bitrate = if (bitWidth != 0) {
                bitWidth * Integer.bitCount(channelMask.toInt()) * sampleRate
            } else 128 // arbitrary guess for compressed formats
            val durationUs = 2100L /* 3.5min * 60 */ * 1000 * 1000 // must be >60s
            if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                        && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1)
                || !formatIsRawPcm(encoding) || bitWidth < 24
            ) {
                // this cannot be trusted on N/O with 24+ bit PCM formats due to format confusion bug
                // be careful: this considers inactive routes too
                // TODO verify if this works on Q/R/S
                when (try {
                    isOffloadSupported(sampleRate, encoding.toInt(), channelMask.toInt(), 0, bitWidth, 0)
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getThrowableString(t)!!)
                    0
                }) {
                    2 -> return DirectPlaybackSupport.GAPLESS_OFFLOAD
                    1 -> return DirectPlaybackSupport.OFFLOAD
                    0 -> {}
                    else -> throw IllegalStateException()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // TODO implement native isDirectOutputSupported
                    // TODO low confidence flag
                    return DirectPlaybackSupport.DIRECT
                }
            } else {
                // check for offload on N/O with 24+ bit PCM formats by opening track...
                // safeguard against bad direct track recycling on O by opening new session every time
                val sessionId = context.getSystemService<AudioManager>()!!.generateAudioSessionId()
                try {
                    val track = NativeTrack(
                        context, attributes, AudioManager.STREAM_MUSIC, sampleRate, encoding,
                        channelMask, null, 0x11, sessionId, 1.0f, null, bitrate, durationUs,
                        hasVideo = false,
                        smallBuf = false,
                        isStreaming = false,
                        offloadBufferSize = 0,
                        notificationFrames = 0,
                        doNotReconnect = true,
                        transferMode = TransferMode.Sync,
                        contentId = null,
                        syncId = null,
                        encapsulationMode = ENCAPSULATION_MODE_NONE,
                        sharedMem = null
                    )
                    val port = AudioSystemHiddenApi.getMixPortForThread(track.getOutput())
                    val flags = track.flags()
                    track.release()
                    if (port == null) {
                        Log.w(TAG, "port is null")
                        return DirectPlaybackSupport.NONE
                    }
                    if (port.format != encoding) {
                        Log.e(
                            TAG,
                            "port ${port.name} was found, but is format ${port.format} instead of $encoding"
                        )
                        return DirectPlaybackSupport.NONE
                    }
                    if ((flags and 0x11) == 0x11) {
                        return DirectPlaybackSupport.OFFLOAD
                    }
                    if ((flags and 0x11) == 0x1) {
                        return DirectPlaybackSupport.DIRECT
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getThrowableString(t)!!) // TODO don't stacktrace when set fails due to unsupported format
                }
            }
            // check for direct output below Q by opening track...
            val sessionId = context.getSystemService<AudioManager>()!!.generateAudioSessionId()
            try {
                val track = NativeTrack(
                    context, attributes, AudioManager.STREAM_MUSIC, sampleRate, encoding,
                    channelMask, null, 0x1, sessionId, 1.0f, null, bitrate, durationUs,
                    hasVideo = false,
                    smallBuf = false,
                    isStreaming = false,
                    offloadBufferSize = 0,
                    notificationFrames = 0,
                    doNotReconnect = true,
                    transferMode = TransferMode.Sync,
                    contentId = null,
                    syncId = null,
                    encapsulationMode = ENCAPSULATION_MODE_NONE,
                    sharedMem = null
                )
                val port = AudioSystemHiddenApi.getMixPortForThread(track.getOutput())
                val flags = track.flags()
                track.release()
                if (port == null) {
                    Log.w(TAG, "port is null")
                    return DirectPlaybackSupport.NONE
                }
                if (port.format != encoding) {
                    Log.e(
                        TAG,
                        "port ${port.name} was found, but is format ${port.format} instead of $encoding"
                    )
                    return DirectPlaybackSupport.NONE
                }
                if ((flags and 0x11) == 0x11) {
                    return DirectPlaybackSupport.OFFLOAD
                }
                if ((flags and 0x11) == 0x1) {
                    return DirectPlaybackSupport.DIRECT
                }
            } catch (t: Throwable) {
                Log.e(TAG, Log.getThrowableString(t)!!) // TODO don't stacktrace when set fails due to unsupported format
            }
            return DirectPlaybackSupport.NONE
        }
        /*private external fun getDirectPlaybackSupport(usage: Int, contentType: Int, attrFlags: Int,
                                                      sampleRate: Int, format: Int, channelMask: Int,
                                                      bitRate: Int, bitWidth: Int, offloadBufferSize: Int) TODO*/
        // TODO implement native getDirectProfilesForAttributes
        // TODO implement native isDirectOutputSupported
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun getPlaybackOffloadSupportPlatformCompat(format: AudioFormat, attributes: AudioAttributes): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (@Suppress("deprecation") AudioManager.getPlaybackOffloadSupport(
                    format, attributes))
            } else {
                @SuppressLint("InlinedApi") if (
                    AudioManager.isOffloadedPlaybackSupported(format, attributes))
                    AudioManager.PLAYBACK_OFFLOAD_SUPPORTED
                else AudioManager.PLAYBACK_OFFLOAD_NOT_SUPPORTED
            }
        }
        fun getMinBufferSize(sampleRateInHz: Int, channelConfig: Int, audioFormat: UInt): Int {
            val minFrameCount = getMinFrameCount(-1, sampleRateInHz)
            val bps = bitsPerSampleForFormat(audioFormat)
            if (bps == 0) // compressed
                return minFrameCount
            return minFrameCount * Integer.bitCount(channelConfig) * (bps / 8)
        }
        fun getMinFrameCount(streamType: Int, sampleRateInHz: Int): Int {
            prepareForLib()
            return try {
                getMinFrameCountInternal(streamType, sampleRateInHz)
            } catch (t: Throwable) {
                throw NativeTrackException("failed to get min frame count ($streamType, $sampleRateInHz)", t)
            }
        }
        private external fun getMinFrameCountInternal(streamType: Int, sampleRateInHz: Int): Int
        private fun prepareForLib() {
            if (!AudioTrackHiddenApi.canLoadLib())
                throw NativeTrackException("this device is banned")
            if (!AudioTrackHiddenApi.libLoaded)
                throw NativeTrackException("lib isn't loaded but device isn't banned")
            if (!try {
                    initDlsym()
                } catch (t: Throwable) {
                    throw NativeTrackException("initDlsym() failed", t)
                })
                throw NativeTrackException("initDlsym() returned false")
        }

        fun bitsPerSampleForFormat(format: UInt): Int {
            val cafOffloadMain = when {
                Build.VERSION.SDK_INT >= 25 -> null
                Build.VERSION.SDK_INT >= 23 -> 0x1A000000U
                else -> 0x1C000000U
            }
            val normalized = if (cafOffloadMain != null && (format and 0xff000000U) == cafOffloadMain) {
                format and (0xff000000U.inv())
            } else format
            return when (normalized) {
                0x1U, 0x0D000000U -> 16
                0x2U -> 8
                0x3U, 0x4U, 0x5U -> 32
                0x6U -> 24
                else -> 0
            }
        }
        fun formatIsRawPcm(format: UInt) = (format and 0xff000000U /* AUDIO_FORMAT_MAIN_MASK */) == 0U
        private fun buildAudioFormat(sampleRate: Int, encoding: Int, channelMask: Int): AudioFormat? {
            val formatBuilder = AudioFormat.Builder()
            try {
                formatBuilder.setSampleRate(sampleRate)
            } catch (_: IllegalArgumentException) {
                formatBuilder.setSampleRate(48000)
                try {
                    @SuppressLint("PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi")
                    val field = formatBuilder.javaClass.getDeclaredField("mSampleRate")
                    field.isAccessible = true
                    field.set(formatBuilder, sampleRate)
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getThrowableString(t)!!)
                    return null
                }
            }
            try {
                formatBuilder.setEncoding(encoding)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, Log.getThrowableString(e)!!)
                return null
            }
            return formatBuilder.setChannelMask(channelMask).build()
        }
        private external fun isOffloadSupported(sampleRate: Int, format: Int, channelMask: Int, bitRate: Int,
                                                bitWidth: Int, offloadBufferSize: Int): Int
        private external fun initDlsym(): Boolean
        fun forTest(context: Context): NativeTrack {
            return NativeTrack(
                context,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                0,
                sampleRate = 13370,
                0x1U, // pcm 16bit
                channelMask = 3U,
                frameCount = null,
                trackFlags = 1,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
                maxRequiredSpeed = 1.0f,
                selectedDeviceId = null,
                bitRate = 0,
                durationUs = 0,
                hasVideo = false,
                smallBuf = false,
                isStreaming = false,
                offloadBufferSize = 0,
                notificationFrames = 0,
                doNotReconnect = false,
                transferMode = TransferMode.Sync,
                contentId = null,
                syncId = null,
                encapsulationMode = ENCAPSULATION_MODE_NONE,
                sharedMem = null
            )
        }
    }
    private val cachedFormat: UInt
    private val cachedChannelMask: UInt
    private val transferMode: TransferMode
    private var sessionId: Int
    private var cachedBuffer: ByteBuffer?
    val ptr: Long
    @Volatile var myState: State
    // proxy limitations: a lot of fields not initialized (mSampleRate, mAudioFormat, mOffloaded, ...) which can
    // cause some internal checks in various methods to fail; stream event and playback position callbacks both
    // are no-op; we MUST call play(), pause(), stop() and don't use the native methods ourselves for this to work;
    // we must also not cache playing/paused/stopped/volume ourselves because it may change under our feet.
    // however, it does:
    // - register (and overwrite) any codec format listeners on native side ; good for us because we can't register
    //   one in a normal way due to dependence on volatile offsets. i.e. with proxy we get codec format listeners!
    // - register player base (which we really should have on N+ to be a nice citizen and have stuff like ducking)
    // - register (and overwrite) routing callback, which is meh but we can just use the java one, it don't hurt
    // - allow for volume shapers! these would be near-impossible using the native API because it's all inline.
    // it's a bit fiddly, but we get all possibilities of a native AudioTrack and a Java one - combined.
    // reminder: do not call write() or any other standard APIs as we break a lot of assumptions. + proxy is not
    //  always available (i.e. L/M), hence native methods are preferable where we can.
    private val proxy: AudioTrack?
    private val codecListener: AudioTrack.OnCodecFormatChangedListener?
    private val routingListener: AudioRouting.OnRoutingChangedListener?
    private val audioManager: AudioManager
    init {
        if (sharedMem?.isDirect == false)
            throw IllegalArgumentException("shared memory specified but isn't direct")
        if (sharedMem == null && transferMode == TransferMode.Shared)
            throw IllegalArgumentException("transfer mode is Shared but sharedMem is null")
        if (sharedMem != null && transferMode != TransferMode.Shared)
            throw IllegalArgumentException("transfer mode is not Shared but sharedMem is specified")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            transferMode == @Suppress("NewApi") TransferMode.SyncWithCallback)
            throw IllegalArgumentException("SyncWithCallback not supported on this android version")
        if (frameCount != null && frameCount == 0)
            throw IllegalArgumentException("frameCount cannot be zero (did you mean to use null?)")
        if (selectedDeviceId != null && selectedDeviceId == 0)
            throw IllegalArgumentException("selectedDeviceId cannot be zero (did you mean to use null?)")
        if (syncId != null && syncId < 1)
            throw IllegalArgumentException("syncId must be positive (did you mean to use null?)")
        if (contentId != null && contentId < 0)
            throw IllegalArgumentException("contentId cannot be negative (did you mean to use null?)")
        if (contentId == 0 && syncId == null)
            throw IllegalArgumentException("CONTENT_ID_NONE with no syncId (did you mean to use null?)")
        prepareForLib()
        audioManager = context.getSystemService<AudioManager>()!!
        ptr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ats = context.attributionSource
            val parcel = Parcel.obtain()
            try {
                ats.writeToParcel(parcel, 0)
                try {
                    create(parcel)
                } catch (t: Throwable) {
                    throw NativeTrackException("create() threw exception", t)
                }
            } finally {
                parcel.recycle()
            }
        } else try {
            create(null)
        } catch (t: Throwable) {
            throw NativeTrackException("create() threw exception", t)
        }
        if (ptr == 0L) {
            throw NativeTrackException("create() returned NULL")
        }
        this.sessionId = if (sessionId == AudioManager.AUDIO_SESSION_ID_GENERATE)
            context.getSystemService<AudioManager>()!!.generateAudioSessionId()
        else sessionId
        val usage = attributes.usage
        val contentType = attributes.contentType
        val hasOutputFlagDeepBufferSet = (trackFlags and 0x8) != 0
        val attrFlags = attributes.flags or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2
                    && attributes.isContentSpatialized) 0x4000 else 0) or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 && attributes.spatializationBehavior
                    == AudioAttributes.SPATIALIZATION_BEHAVIOR_NEVER) 0x8000 else 0) or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && attributes.areHapticChannelsMuted()) 0x800 else 0) or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    when (attributes.allowedCapturePolicy) {
                        AudioAttributes.ALLOW_CAPTURE_BY_NONE -> 0x1400
                        AudioAttributes.ALLOW_CAPTURE_BY_SYSTEM -> 0x400
                        else -> 0x0
                    } else 0) or (if (hasOutputFlagDeepBufferSet) 0x200 else 0x0)
        val bitWidth = bitsPerSampleForFormat(format)
        // java streamType is compatible with native streamType
        val ret = try {
            set(ptr = ptr, streamType = streamType, sampleRate = sampleRate, format = format.toInt(),
                channelMask = channelMask.toInt(), frameCount = frameCount ?: 0, trackFlags = trackFlags,
                sessionId = this.sessionId, maxRequiredSpeed = maxRequiredSpeed,
                selectedDeviceId = selectedDeviceId ?: 0, bitRate = bitRate, durationUs = durationUs,
                hasVideo = hasVideo, smallBuf = smallBuf, isStreaming = isStreaming, bitWidth = bitWidth,
                offloadBufferSize = offloadBufferSize, usage = usage, contentType = contentType,
                attrFlags = attrFlags, notificationFrames = notificationFrames, doNotReconnect = doNotReconnect,
                transferMode = transferMode.id, contentId = contentId ?: 0, syncId = syncId ?: 0,
                encapsulationMode = encapsulationMode, sharedMem = sharedMem)
        } catch (t: Throwable) {
            try {
                dtor(ptr)
            } catch (t2: Throwable) {
                throw NativeTrackException("dtor() threw exception after set() threw exception: " +
                        Log.getThrowableString(t2)!!, t)
            }
            throw NativeTrackException("set() threw exception", t)
        }
        cachedFormat = format
        cachedChannelMask = channelMask
        cachedBuffer = sharedMem
        this.transferMode = transferMode
        if (ret != 0) {
            try {
                dtor(ptr)
            } catch (t: Throwable) {
                throw NativeTrackException("dtor() threw exception after set() failed with code $ret", t)
            }
            throw NativeTrackException("set() failed with code $ret")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            proxy = try {
                getProxy(ptr, this.sessionId)
            } catch (t: Throwable) {
                try {
                    dtor(ptr)
                } catch (t2: Throwable) {
                    throw NativeTrackException("dtor() threw exception after getProxy() threw exception: " +
                            Log.getThrowableString(t2)!!, t)
                }
                throw NativeTrackException("getProxy() threw exception", t)
            }
            if (proxy == null) {
                try {
                    dtor(ptr)
                } catch (t: Throwable) {
                    throw NativeTrackException("dtor() threw exception after getProxy() returned null, " +
                            "check prior logs", t)
                }
                throw NativeTrackException("getProxy() returned null, check prior logs")
            }
            routingListener = AudioRouting.OnRoutingChangedListener { this@NativeTrack.onRoutingChanged() }
            proxy.addOnRoutingChangedListener(routingListener, null)
        } else {
            proxy = null
            routingListener = null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            codecListener = AudioTrack.OnCodecFormatChangedListener { audioTrack, info ->
                this@NativeTrack.onCodecFormatChanged(info)
            }
            proxy!!.addOnCodecFormatChangedListener({ r -> r.run() }, codecListener)
        } else codecListener = null
        myState = State.ALIVE
    }
    private external fun create(parcel: Parcel?): Long
    /*
     * CAUTION: Until including Android 7.1, direct outputs could be reused even with different session IDs.
     *          If another app is using a direct (or offload) stream, we might end up with no audio (there can
     *          only ever be one client). However, this problem is isolated to MediaPlayer using compressed offload
     *          (or another app like us doing that), and us using hidden API to offload in the same format, sample
     *          rate and channel mask. Audio focus sadly isn't enough as the track needs to be released to avoid
     *          this bug, so either avoid other media player apps or using compressed offload on these versions.
     *
     * CAUTION: From Android 7.0 until Android 8.1, direct outputs with PCM modes int24, int32 or float32 were all
     *          treated as compatible. To avoid track creation failures caused by ourselves, we should always
     *          release active tracks on any direct output before attempting to switch formats - otherwise it may
     *          try to reuse the track despite the different format. But if we're unlucky, on Android 7.x only, we
     *          may get a busy output with a different format anyway because another app has an active direct PCM
     *          track - which will result in set() failing. There's another case where set() could fail: on N/O,
     *          when we request float32, APM may select int32 instead (because both have the same bit width) - if
     *          we request int32, we may get float32; and with some bad luck, if we request a 32-bit format, we may
     *          even get int24 (if no 32 bit format is supported) - or when requesting int24, we may get a 32-bit
     *          format (if int24 is not supported). After APM gives us that output, AF will fail creating the track
     *          causing set() to fail. In that case, we have to try again with another format.
     */
    private external fun set(ptr: Long, streamType: Int, sampleRate: Int, format: Int, channelMask: Int,
                             frameCount: Int, trackFlags: Int, sessionId: Int, maxRequiredSpeed: Float,
                             selectedDeviceId: Int, bitRate: Int, durationUs: Long, hasVideo: Boolean,
                             smallBuf: Boolean, isStreaming: Boolean, bitWidth: Int, offloadBufferSize: Int,
                             usage: Int, contentType: Int, attrFlags: Int, notificationFrames: Int,
                             doNotReconnect: Boolean, transferMode: Int, contentId: Int, syncId: Int,
                             encapsulationMode: Int, sharedMem: ByteBuffer? /* direct */): Int
    private external fun getRealPtr(ptr: Long): Long
    private external fun notificationFramesActFromOffset(ptr: Long): Int
    private external fun dtor(ptr: Long)
    @RequiresApi(Build.VERSION_CODES.N)
    private external fun getProxy(ptr: Long, sessionId: Int): AudioTrack?

    fun release() {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState already")
        myState = State.RELEASED
        cachedBuffer = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && codecListener != null) {
            proxy!!.removeOnCodecFormatChangedListener(codecListener)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            proxy!!.removeOnRoutingChangedListener(routingListener)
            proxy.release() // this doesn't free native obj because we hold extra strong ref, cleared in dtor()
        }
        dtor(ptr)
    }

    fun dump(): String {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            AudioTrackHiddenApi.dumpInternal(getRealPtr(ptr))
        } catch (t: Throwable) {
            throw NativeTrackException("failed to dump", t)
        }
    }

    fun state(): Int {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        return AudioTrackHiddenApi.getStateFromDump(dump())
            ?: throw IllegalStateException("state failed, check prior logs")
    }

    fun isPlaying(): Boolean {
        val state = state()
        return state == 0 || state == 5
    }

    fun frameCount(): Long {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return AudioTrackHiddenApi.getFrameCountFromDump(dump())
            ?: throw IllegalStateException("frameCount failed, check prior logs")
    }

    fun format(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return cachedFormat
    }

    fun sessionId() = sessionId

    /**
     * The accuracy of this method depends on the Android version:
     * Android 8.0 or later / Non-CAF Android 7.1: all flags are adjusted to match output capabilities
     * CAF Android 7.0 / 7.1: system only adjusts fast flag, we adjust direct flag through a trick
     * Non-CAF Android 7.0: system only adjusts fast flag
     * (because Audio HALs from Android 7.x time don't support using compressed formats in for anything except
     * passthrough or offload, we can assume that if we request offload, we get offload, passthrough, or a creation
     * failure. this last disambiguation must be done by end user based on mix port name.)
     * Android 5.x / 6.x: system only adjusts fast, direct and offload flag
     */
    fun flags(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            AudioTrackHiddenApi.getFlagsInternal(proxy, getRealPtr(ptr))
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get flags", t)
        }.let {
            if (it == Int.MAX_VALUE || it == Int.MIN_VALUE)
                throw NativeTrackException("something went wrong while getting flags, check prior logs")
            else it
        }
    }

    fun notificationPeriodInFrames(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            AudioTrackHiddenApi.getNotificationFramesActFromDump(dump())
                ?: throw IllegalStateException("notificationFramesAct failed, check prior logs")
        else try {
            notificationFramesActFromOffset(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get notificationFramesAct", t)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun policyPortId(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return AudioTrackHiddenApi.getPortIdFromDump(dump())
            ?: throw IllegalStateException("getPortId failed, check prior logs")
    }

    fun channelMask(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return cachedChannelMask
    }

    fun latency(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            try {
                (AudioTrack::class.java.getMethod("getLatency").invoke(proxy) as Int).toUInt()
            } catch (t: Throwable) {
                throw NativeTrackException("getLatency failed", t)
            }
        else
            (AudioTrackHiddenApi.getLatencyFromDump(dump())
                ?: throw NativeTrackException("getLatencyFromDump failed, see prior logs")).toUInt()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun getUnderrunCount(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return proxy!!.underrunCount
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun getBufferSizeInFrames(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return proxy!!.bufferSizeInFrames
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun getBufferDurationInUs(): ULong {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val ret = try {
            getBufferDurationInUsInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get buffer duration us", t)
        }
        if (ret == -32L) {
            myState = State.DEAD_OBJECT
            throw NativeTrackException("getBufferDurationInUs() failed, track died")
        }
        if (ret < 0) {
            throw NativeTrackException("getBufferDurationInUs() failed: $ret")
        }
        return ret.toULong()
    }
    private external fun getBufferDurationInUsInternal(ptr: Long): Long

    @RequiresApi(Build.VERSION_CODES.N)
    fun setBufferSizeInFrames(size: Int) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        proxy!!.bufferSizeInFrames = size
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun getStartThresholdInFrames(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return proxy!!.startThresholdInFrames
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setStartThresholdInFrames(size: Int) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        proxy!!.startThresholdInFrames = size
    }

    fun sharedBuffer(): ByteBuffer {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        if (transferMode != TransferMode.Shared)
            throw IllegalStateException("transfer mode isn't shared, sharedBuffer() can't be called")
        return cachedBuffer!!
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun getMetrics(): PersistableBundle {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return proxy!!.metrics
    }

    fun start() {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        if (proxy != null) {
            proxy.play()
        } else {
            val ret = try {
                startInternal(ptr)
            } catch (t: Throwable) {
                throw NativeTrackException("failed to play", t)
            }
            if (ret == -32) {
                myState = State.DEAD_OBJECT
                throw NativeTrackException("start() failed, track died")
            }
            if (ret != 0) {
                throw NativeTrackException("start() failed: $ret")
            }
        }
    }
    private external fun startInternal(ptr: Long): Int

    fun stop() {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        if (proxy != null) {
            proxy.stop()
        } else {
            try {
                stopInternal(ptr)
            } catch (t: Throwable) {
                throw NativeTrackException("failed to stop", t)
            }
        }
    }
    private external fun stopInternal(ptr: Long)

    fun stopped(): Boolean {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            stoppedInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to check if stopped", t)
        }
    }
    private external fun stoppedInternal(ptr: Long): Boolean

    fun flush() {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        try {
            flushInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to flush", t)
        }
    }
    private external fun flushInternal(ptr: Long)

    fun pause() {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        if (proxy != null) {
            proxy.pause()
        } else {
            try {
                pauseInternal(ptr)
            } catch (t: Throwable) {
                throw NativeTrackException("failed to pause", t)
            }
        }
    }
    private external fun pauseInternal(ptr: Long)

    @RequiresApi(Build.VERSION_CODES.S_V2)
    fun pauseAndWait(timeoutMs: ULong): Boolean {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            pauseAndWaitInternal(ptr, timeoutMs.toLong())
        } catch (t: Throwable) {
            throw NativeTrackException("failed to pause", t)
        }
        // no-op as far as track is concerned, but java object and system should be notified about the pause.
        proxy?.pause()
        return ret
    }
    private external fun pauseAndWaitInternal(ptr: Long, timeoutMs: Long): Boolean

    fun setVolume(volume: Float) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        if (proxy != null) {
            proxy.setVolume(volume)
        } else {
            val ret = try {
                setVolumeInternal(ptr, volume)
            } catch (t: Throwable) {
                throw NativeTrackException("failed to set volume to $volume", t)
            }
            if (ret != 0) {
                throw NativeTrackException("setVolume($volume) failed: $ret")
            }
        }
    }
    private external fun setVolumeInternal(ptr: Long, volume: Float): Int

    fun setAuxEffectSendLevel(level: Float) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        if (proxy != null) {
            proxy.setAuxEffectSendLevel(level)
        } else {
            val ret = try {
                setAuxEffectSendLevelInternal(ptr, level)
            } catch (t: Throwable) {
                throw NativeTrackException("failed to set aux effect send level to $level", t)
            }
            if (ret != 0) {
                throw NativeTrackException("setAuxEffectSendLevel($level) failed: $ret")
            }
        }
    }
    private external fun setAuxEffectSendLevelInternal(ptr: Long, level: Float): Int

    fun getAuxEffectSendLevel(): Float {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            getAuxEffectSendLevelInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get aux effect send level", t)
        }
    }
    private external fun getAuxEffectSendLevelInternal(ptr: Long): Float

    fun setSampleRate(rate: UInt) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            setSampleRateInternal(ptr, rate.toInt())
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set sample rate to $rate", t)
        }
        if (ret != 0) {
            throw NativeTrackException("setSampleRate($rate) failed: $ret")
        }
    }
    private external fun setSampleRateInternal(ptr: Long, rate: Int): Int

    fun getSampleRate(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            getSampleRateInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get sample rate", t)
        }.toUInt()
    }
    private external fun getSampleRateInternal(ptr: Long): Int

    @RequiresApi(Build.VERSION_CODES.M)
    fun getOriginalSampleRate(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            getOriginalSampleRateInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get sample rate", t)
        }.toUInt()
    }
    @RequiresApi(Build.VERSION_CODES.M)
    private external fun getOriginalSampleRateInternal(ptr: Long): Int

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // TODO qpr
    fun getHalSampleRate(): UInt {
        TODO()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // TODO qpr
    fun getHalChannelCount(): UInt {
        TODO()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // TODO qpr
    fun getHalFormat(): UInt {
        TODO()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun setPlaybackRate(rate: PlaybackRate) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            setPlaybackRateInternal(ptr, rate.speed, rate.pitch, if (rate.stretchForVoice) 1 else 0, when (rate.fallback) {
	            StretchFallbackMode.AUDIO_TIMESTRETCH_FALLBACK_CUT_REPEAT -> -1
	            StretchFallbackMode.AUDIO_TIMESTRETCH_FALLBACK_DEFAULT -> 0
	            StretchFallbackMode.AUDIO_TIMESTRETCH_FALLBACK_MUTE -> 1
	            StretchFallbackMode.AUDIO_TIMESTRETCH_FALLBACK_FAIL -> 2
            })
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set playback rate to $rate", t)
        }
        if (ret != 0) {
            throw NativeTrackException("setPlaybackRate($rate) failed: $ret")
        }
    }
    @RequiresApi(Build.VERSION_CODES.M)
    private external fun setPlaybackRateInternal(ptr: Long, speed: Float, pitch: Float, stretchMode: Int, fallback: Int): Int

    enum class StretchFallbackMode {
        AUDIO_TIMESTRETCH_FALLBACK_CUT_REPEAT,
        AUDIO_TIMESTRETCH_FALLBACK_DEFAULT,
        AUDIO_TIMESTRETCH_FALLBACK_MUTE,
        AUDIO_TIMESTRETCH_FALLBACK_FAIL
    }
    data class PlaybackRate(val speed: Float, val pitch: Float, val stretchForVoice: Boolean, val fallback: StretchFallbackMode)
    @RequiresApi(Build.VERSION_CODES.M)
    fun getPlaybackRate(): PlaybackRate {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val speedPitch = FloatArray(2)
        val ret = getPlaybackRateInternal(ptr, speedPitch)
        val stretchForVoice = (ret shr 32).toInt()
        val fallbackMode = ret.toInt()
        return PlaybackRate(speedPitch[0], speedPitch[1],
            stretchForVoice == 1, when (fallbackMode) {
                -1 -> StretchFallbackMode.AUDIO_TIMESTRETCH_FALLBACK_CUT_REPEAT
                0 -> StretchFallbackMode.AUDIO_TIMESTRETCH_FALLBACK_DEFAULT
                1 -> StretchFallbackMode.AUDIO_TIMESTRETCH_FALLBACK_MUTE
                2 -> StretchFallbackMode.AUDIO_TIMESTRETCH_FALLBACK_FAIL
                else -> throw IllegalArgumentException("timestretch $ret")
            })
    }
    @RequiresApi(Build.VERSION_CODES.M)
    private external fun getPlaybackRateInternal(ptr: Long, speedPitch: FloatArray): Long

    @RequiresApi(Build.VERSION_CODES.S)
    fun setDualMonoMode(dualMonoMode: Int) {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        proxy!!.setDualMonoMode(dualMonoMode)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun getDualMonoMode(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return proxy!!.dualMonoMode
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun setAudioDescriptionMixLevel(level: Float) {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        proxy!!.audioDescriptionMixLeveldB = level
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun getAudioDescriptionMixLevel(): Float {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return proxy!!.audioDescriptionMixLeveldB
    }

    fun setLoop(loopStart: UInt, loopEnd: UInt, loopCount: Int) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            setLoopInternal(ptr, loopStart.toInt(), loopEnd.toInt(), loopCount)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set loop to $loopStart/$loopEnd/$loopCount", t)
        }
        if (ret != 0) {
            throw NativeTrackException("setLoop($loopStart, $loopEnd, $loopCount) failed: $ret")
        }
    }
    private external fun setLoopInternal(ptr: Long, loopStart: Int, loopEnd: Int, loopCount: Int): Int

    fun setMarkerPosition(markerPosition: UInt) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            setMarkerPositionInternal(ptr, markerPosition.toInt())
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set marker pos to $markerPosition", t)
        }
        if (ret != 0) {
            throw NativeTrackException("setMarkerPosition($markerPosition) failed: $ret")
        }
    }
    private external fun setMarkerPositionInternal(ptr: Long, pos: Int): Int

    fun getMarkerPosition(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val comboRet = try {
            getMarkerPositionInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get marker pos", t)
        }.toULong()
        val ret = ((comboRet and 0xffffffff00000000UL) shr 32).toInt()
        val data = (comboRet and 0x00000000ffffffffUL).toUInt()
        if (ret != 0) {
            throw NativeTrackException("getMarkerPosition() failed: $ret (data=$data)")
        }
        return data
    }
    private external fun getMarkerPositionInternal(ptr: Long): Long

    fun setPositionUpdatePeriod(pos: UInt) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            setPositionUpdatePeriodInternal(ptr, pos.toInt())
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set pos update period to $pos", t)
        }
        if (ret != 0) {
            throw NativeTrackException("setPositionUpdatePeriod($pos) failed: $ret")
        }
    }
    private external fun setPositionUpdatePeriodInternal(ptr: Long, pos: Int): Int

    fun getPositionUpdatePeriod(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val comboRet = try {
            getPositionUpdatePeriodInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get pos update period", t)
        }.toULong()
        val ret = ((comboRet and 0xffffffff00000000UL) shr 32).toInt()
        val data = (comboRet and 0x00000000ffffffffUL).toUInt()
        if (ret != 0) {
            throw NativeTrackException("getPositionUpdatePeriod() failed: $ret (data=$data)")
        }
        return data
    }
    private external fun getPositionUpdatePeriodInternal(ptr: Long): Long

    fun setPosition(pos: UInt) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            setPositionInternal(ptr, pos.toInt())
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set pos to $pos", t)
        }
        if (ret != 0) {
            throw NativeTrackException("setPosition($pos) failed: $ret")
        }
    }
    private external fun setPositionInternal(ptr: Long, pos: Int): Int

    fun getPosition(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val comboRet = try {
            getPositionInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get pos", t)
        }.toULong()
        val ret = ((comboRet and 0xffffffff00000000UL) shr 32).toInt()
        val data = (comboRet and 0x00000000ffffffffUL).toUInt()
        if (ret != 0) {
            throw NativeTrackException("getPosition() failed: $ret (data=$data)")
        }
        return data
    }
    private external fun getPositionInternal(ptr: Long): Long

    fun getBufferPosition(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val comboRet = try {
            getBufferPositionInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get buffer pos", t)
        }.toULong()
        val ret = ((comboRet and 0xffffffff00000000UL) shr 32).toInt()
        val data = (comboRet and 0x00000000ffffffffUL).toUInt()
        if (ret != 0) {
            throw NativeTrackException("getBufferPosition() failed: $ret (data=$data)")
        }
        return data
    }
    private external fun getBufferPositionInternal(ptr: Long): Long

    fun reload() {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            reloadInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to reload", t)
        }
        if (ret != 0) {
            throw NativeTrackException("reload() failed: $ret")
        }
    }
    private external fun reloadInternal(ptr: Long): Int

    fun getOutput(): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            getOutputInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get output", t)
        }
    }
    private external fun getOutputInternal(ptr: Long): Int

    @RequiresApi(Build.VERSION_CODES.M)
    fun setSelectedDevice(audioDeviceInfo: AudioDeviceInfo?): Boolean {
        if (audioDeviceInfo != null && !audioDeviceInfo.isSink)
            return false
        val id = audioDeviceInfo?.id ?: 0 /* AUDIO_PORT_HANDLE_NONE */
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            setSelectedDeviceInternal(ptr, id)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set selected device", t)
        }
        if (ret == -32) {
            myState = State.DEAD_OBJECT
            throw NativeTrackException("setSelectedDevice($id) failed, track died")
        }
        if (ret != 0) {
            Log.w(TAG, "setSelectedDevice($id) failed: $ret")
            return false
        }
        return true
    }
    private external fun setSelectedDeviceInternal(ptr: Long, id: Int): Int

    @RequiresApi(Build.VERSION_CODES.M)
    fun getSelectedDevice(): AudioDeviceInfo? {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val id = try {
            getSelectedDeviceInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set selected device", t)
        }
        if (id == 0) return null
        // this is somewhat racy, we can loose a device between these two calls, but shrug
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).find { it.id == id }
        return device
    }
    private external fun getSelectedDeviceInternal(ptr: Long): Int

    @RequiresApi(Build.VERSION_CODES.M)
    fun getRoutedDevices(): List<AudioDeviceInfo> {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ids = try {
            getRoutedDevicesInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set selected device", t)
        }
        // this is somewhat racy, we can loose a device between these two calls, but shrug
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return ids.map { id -> devices.find { it.id == id } }.filterNotNull()
    }
    private external fun getRoutedDevicesInternal(ptr: Long): IntArray

    fun attachAuxEffect(effectId: Int) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            attachAuxEffectInternal(ptr, effectId)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to attach aux effect $effectId", t)
        }
        if (ret == -32) {
            myState = State.DEAD_OBJECT
            throw NativeTrackException("attachAuxEffect($effectId) failed, track died")
        }
        if (ret != 0) {
            throw NativeTrackException("attachAuxEffect($effectId) failed: $ret")
        }
    }
    private external fun attachAuxEffectInternal(ptr: Long, effectId: Int): Int

    fun obtainBufferWithNonContig(requestedFrames: Long, waitCount: Int): Pair<ByteBuffer, Long> {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val nc = LongArray(1)
        val ret = try {
            obtainBufferInternal(ptr, frameSize(), waitCount, nc, requestedFrames)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to obtain buffer of $requestedFrames frames", t)
        }
        if (ret == null) {
            throw NativeTrackException("failed to obtain buffer of $requestedFrames frames, check prior logs")
        }
        return ret to nc[0]
    }
    fun obtainBuffer(requestedFrames: Long, waitCount: Int): ByteBuffer {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            obtainBufferInternal(ptr, frameSize(), waitCount, null, requestedFrames)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to obtain buffer of $requestedFrames frames", t)
        }
        if (ret == null) {
            throw NativeTrackException("failed to obtain buffer of $requestedFrames frames, check prior logs")
        }
        return ret
    }
    private external fun obtainBufferInternal(ptr: Long, frameSize: Int, waitCount: Int, nonContig: LongArray?,
                                              requestedFrameCount: Long): ByteBuffer?

    /** set limit to amount of written bytes, and don't call any method on buf after giving it to this method */
    fun releaseBuffer(buf: ByteBuffer) {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        try {
             releaseBufferInternal(ptr, frameSize(), buf, buf.limit())
        } catch (t: Throwable) {
            throw NativeTrackException("failed to release buffer $buf", t)
        }
    }
    private external fun releaseBufferInternal(ptr: Long, frameSize: Int, buf: ByteBuffer, limit: Int)

    fun write(buf: ByteBuffer, offset: Int?, size: Int?, blocking: Boolean): Long {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        if (!buf.isDirect) {
            return write(buf.array(), buf.arrayOffset() + (offset ?: buf.position()),
                size ?: (buf.limit() - (offset ?: buf.position())), blocking)
        }
        // TODO replicate blockUntilOffloadDrain()
        val ret = try {
            writeInternal(ptr, buf, offset ?: buf.position(),
                size ?: (buf.limit() - (offset ?: buf.position())), blocking)
        } catch (t: Throwable) {
            throw NativeTrackException("write($buf / $blocking) failed", t)
        }
        if (ret == -32L) {
            myState = State.DEAD_OBJECT
            throw NativeTrackException("write($buf / $blocking) failed, track died")
        }
        if (ret < 0) {
            throw NativeTrackException("write($buf / $blocking) failed: $ret")
        }
        return ret
    }
    fun write(buf: ByteArray, offset: Int, size: Int?, blocking: Boolean): Long {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        // TODO replicate blockUntilOffloadDrain()
        val ret = try {
            writeInternal(ptr, buf, offset, size ?: buf.size, blocking)
        } catch (t: Throwable) {
            throw NativeTrackException("write(${buf.size} / $blocking) failed", t)
        }
        if (ret == -32L) {
            myState = State.DEAD_OBJECT
            throw NativeTrackException("write(${buf.size} / $blocking) failed, track died")
        }
        if (ret < 0) {
            throw NativeTrackException("write(${buf.size} / $blocking) failed: $ret")
        }
        return ret
    }
    fun write(buf: FloatArray, offset: Int, size: Int?, blocking: Boolean): Long {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        // TODO assert format is float
        // TODO replicate blockUntilOffloadDrain()
        val ret = try {
            writeInternal(ptr, buf, offset, size ?: buf.size, blocking)
        } catch (t: Throwable) {
            throw NativeTrackException("write(${buf.size} / $blocking) failed", t)
        }
        if (ret == -32L) {
            myState = State.DEAD_OBJECT
            throw NativeTrackException("write(${buf.size} / $blocking) failed, track died")
        }
        if (ret < 0) {
            throw NativeTrackException("write(${buf.size} / $blocking) failed: $ret")
        }
        return ret
    }
    fun write(buf: ByteArray, offset: Int, size: Int?, blocking: Boolean, timestamp: Long): Long {
        TODO("Implement HW_AV_SYNC write API")
    }
    private external fun writeInternal(ptr: Long, buf: ByteBuffer, offset: Int, size: Int, blocking: Boolean): Long
    private external fun writeInternal(ptr: Long, buf: ByteArray, offset: Int, size: Int, blocking: Boolean): Long
    private external fun writeInternal(ptr: Long, buf: FloatArray, offset: Int, size: Int, blocking: Boolean): Long

    fun channelCount(): Int {
        return Integer.bitCount(channelMask().toInt())
    }

    fun frameSize(): Int { // in bytes
        val bps = bitsPerSampleForFormat(format())
        if (bps == 0) // compressed
            return 1
        return channelCount() * (bps / 8)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createVolumeShaper(config: VolumeShaper.Configuration): VolumeShaper {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        return proxy!!.createVolumeShaper(config)
    }

    fun getUnderrunFrames(): UInt {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            getUnderrunFramesInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get underrun frames", t)
        }.toUInt()
    }
    private external fun getUnderrunFramesInternal(ptr: Long): Int

    fun setParameters(params: String) {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        val ret = try {
            setParametersInternal(ptr, params)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to set parameters $params", t)
        }
        if (ret == -32) {
            myState = State.DEAD_OBJECT
            throw NativeTrackException("setParameters() failed, track died")
        }
        if (ret != 0) {
            throw NativeTrackException("setParameters($params) failed: $ret")
        }
    }
    private external fun setParametersInternal(ptr: Long, params: String): Int

    fun getParameters(params: String): String {
        if (myState != State.ALIVE)
            throw IllegalStateException("state is $myState")
        return try {
            getParametersInternal(ptr, params)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get parameters $params", t)
        }
    }
    private external fun getParametersInternal(ptr: Long, params: String): String

    @RequiresApi(Build.VERSION_CODES.P)
    fun selectPresentation(presentation: AudioPresentation) {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val ret = proxy!!.setPresentation(presentation)
        if (ret != 0) {
            throw NativeTrackException("selectPresentation failed: $ret")
        }
    }

    /**
     * Retrieve current position in milliseconds (`out[0]`) and anchor realtime in nanoseconds (`out[1]`).
     */
    fun getTimestamp(out: LongArray) {
        if (out.size != 2)
            throw IllegalArgumentException("wrong size for getTimestamp: ${out.size}")
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val ret = try {
            getTimestampInternal(ptr, out)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get timestamps", t)
        }
        if (ret == -32) {
            myState = State.DEAD_OBJECT
            throw NativeTrackException("getTimestamp() failed, track died")
        }
        if (ret != 0) {
            throw NativeTrackException("getTimestamp() failed: $ret")
        }
    }
    private external fun getTimestampInternal(ptr: Long, out: LongArray): Int

    enum class Timebase {
        Monotonic,
        Boottime,
    }
    class ExtendedTimestamp(private val mPosition: LongArray, private val mTimeNs: LongArray,
                            private val mTimebaseOffset: LongArray, val mFlushed: Long) {
        data class Timestamp(val position: Long, val time: Long, val timebase: Timebase, val location: TimestampLocation)
        fun getBestTimestamp(timebase: Timebase): Timestamp? {
            getTimestamp(TimestampLocation.Kernel, timebase)?.let { return it }
            return getTimestamp(TimestampLocation.Server, timebase)
        }
        fun getTimestamp(location: TimestampLocation, timebase: Timebase): Timestamp? {
            val i = when (location) {
	            TimestampLocation.Client -> 0
	            TimestampLocation.Server -> 1
	            TimestampLocation.Kernel -> 2
	            TimestampLocation.ServerPriorToLastKernelOk -> 3
	            TimestampLocation.KernelPriorToLastKernelOk -> 4
            }
            if (mTimeNs[i] > 0) {
                return Timestamp(
                    mPosition[i], mTimeNs[i] +
                            mTimebaseOffset[if (timebase == Timebase.Boottime) 1 else 0], timebase,
                    if (i == 2) TimestampLocation.Kernel else TimestampLocation.Server
                )
            }
            return null
        }
    }
    @RequiresApi(Build.VERSION_CODES.N)
    fun getTimestamp(): ExtendedTimestamp {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val mPosition = LongArray(5)
        val mTimeNs = LongArray(5)
        val mTimebaseOffset = LongArray(2)
        val mFlushed = LongArray(1)
        val ret = try {
            getTimestamp2Internal(ptr, mPosition, mTimeNs, mTimebaseOffset, mFlushed)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get ext timestamps", t)
        }
        if (ret != 0) {
            throw NativeTrackException("getTimestamp() failed: $ret")
        }
        return ExtendedTimestamp(mPosition, mTimeNs, mTimebaseOffset, mFlushed[0])
    }
    @RequiresApi(Build.VERSION_CODES.N)
    private external fun getTimestamp2Internal(ptr: Long, mPosition: LongArray, mTimeNs: LongArray,
                                               mTimebaseOffset: LongArray, mFlushed: LongArray): Int

    enum class TimestampLocation {
        Client,
        Server,
        Kernel,
        ServerPriorToLastKernelOk,
        KernelPriorToLastKernelOk,
    }
    @RequiresApi(Build.VERSION_CODES.N)
    fun pendingDuration(location: TimestampLocation): Int {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        val location2 = when (location) {
	        TimestampLocation.Client -> 1
	        TimestampLocation.Server -> 2
	        TimestampLocation.Kernel -> 3
	        TimestampLocation.ServerPriorToLastKernelOk -> 4
	        TimestampLocation.KernelPriorToLastKernelOk -> 5
        }
        val data = try {
            pendingDurationInternal(ptr, location2)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to get pending duration from $location", t)
        }
        val ret = (data shr 32).toInt()
        val out = data.toInt()
        if (ret != 0) {
            throw NativeTrackException("failed to get pending duration from $location, ret = $ret")
        }
        return out
    }
    @RequiresApi(Build.VERSION_CODES.N)
    private external fun pendingDurationInternal(ptr: Long, location: Int): Long

    @RequiresApi(Build.VERSION_CODES.O)
    fun hasStarted(): Boolean {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return try {
            hasStartedInternal(ptr)
        } catch (t: Throwable) {
            throw NativeTrackException("failed to check if stopped", t)
        }
    }
    private external fun hasStartedInternal(ptr: Long): Boolean

    @RequiresApi(Build.VERSION_CODES.S)
    fun setLogSessionId(params: LogSessionId) {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        proxy!!.logSessionId = params
    }

    @RequiresApi(Build.VERSION_CODES.S)
    fun getLogSessionId(): LogSessionId {
        if (myState == State.RELEASED)
            throw IllegalStateException("state is $myState")
        return proxy!!.logSessionId
    }

    class NativeTrackException : Exception {
        constructor(message: String) : super(message)
        constructor(message: String, cause: Throwable) : super(message, cause)
    }
    enum class State {
        DEAD_OBJECT, // we got killed by lower layer
        RELEASED, // release() called
        ALIVE, // ready to use
    }

    interface Callback {
        fun onUnderrun()
        fun onMarker(markerPosition: Int)
        fun onNewPos(newPos: Int)
        fun onStreamEnd()
        fun onNewIAudioTrack()
        fun onNewTimestamp(timestampMs: Int, timeNanoSec: Long)
        fun onLoopEnd(loopsRemaining: Int)
        fun onBufferEnd()
        fun onMoreData(frameCount: Long, buffer: ByteBuffer): Long // ret = bytes written
        fun onCanWriteMoreData(frameCount: Long, sizeBytes: Long)
        fun onRoutingChanged()
        fun onCodecFormatChanged(metadata: AudioMetadataReadMap?)
    }
    @Volatile var cb: Callback? = null

    // called from native, on callback thread (not main thread!)
    private fun onUnderrun() {
        cb?.onUnderrun()
    }
    // called from native, on callback thread (not main thread!)
    private fun onMarker(markerPosition: Int) {
        cb?.onMarker(markerPosition)
    }
    // called from native, on callback thread (not main thread!)
    private fun onNewPos(newPos: Int) {
        cb?.onNewPos(newPos)
    }
    // called from native, on callback thread (not main thread!)
    private fun onStreamEnd() {
        cb?.onStreamEnd()
    }
    // called from native, on callback thread (not main thread!)
    private fun onNewIAudioTrack() {
        cb?.onNewIAudioTrack()
    }
    // called from native, on callback thread (not main thread!)
    private fun onNewTimestamp(timestampMs: Int, timeNanoSec: Long) {
        cb?.onNewTimestamp(timestampMs, timeNanoSec)
    }
    // called from native, on callback thread (not main thread!)
    private fun onLoopEnd(loopsRemaining: Int) {
        cb?.onLoopEnd(loopsRemaining)
    }
    // called from native, on callback thread (not main thread!)
    private fun onBufferEnd() {
        cb?.onBufferEnd()
    }
    // called from native, on callback thread (not main thread!)
    // Be careful to not hold a reference to the buffer after returning. It will immediately be invalid!
    private fun onMoreData(frameCount: Long, buffer: ByteBuffer): Long {
        cb?.let {
            return it.onMoreData(frameCount, buffer)
        }
        return 0 // amount of bytes written
    }
    // called from native, on callback thread (not main thread!)
    private fun onCanWriteMoreData(frameCount: Long, sizeBytes: Long) {
        cb?.onCanWriteMoreData(frameCount, sizeBytes)
    }
    // called from native, on random thread (not main thread!) - only M for now, N+ uses proxy
    private fun onAudioDeviceUpdate(ioHandle: Int, routedDevices: IntArray) {
        cb?.onRoutingChanged()
    }
    // called on audio track initialization thread, most often main thread but not always
    private fun onRoutingChanged() {
        cb?.onRoutingChanged()
    }
    // called on random thread
    private fun onCodecFormatChanged(metadata: AudioMetadataReadMap?) {
        cb?.onCodecFormatChanged(metadata)
    }
}