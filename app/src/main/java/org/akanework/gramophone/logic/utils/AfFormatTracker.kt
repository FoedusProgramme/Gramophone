package org.akanework.gramophone.logic.utils

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Parcelable
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink.AudioTrackConfig
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.parcelize.Parcelize
import org.nift4.gramophone.hificore.AudioSystemHiddenApi
import org.nift4.gramophone.hificore.AudioTrackHiddenApi

@Parcelize
data class AfFormatInfo(
    val routedDeviceName: String?, val routedDeviceId: Int?,
    val routedDeviceType: Int?, val audioSessionId: Int, val mixPortId: Int?,
    val mixPortName: String?, val mixPortFlags: Int?, val mixPortHwModule: Int?,
    val mixPortFast: Boolean?, val ioHandle: Int?, val sampleRateHz: UInt?,
    val audioFormat: String?, val channelCount: Int?, val channelMask: Int?,
    val grantedFlags: Int?, val policyPortId: Int?, val afTrackFlags: Int?,
    val isBluetoothOffload: Boolean?
) : Parcelable

@Parcelize
data class AudioTrackInfo(
    val encoding: Int, val sampleRateHz: Int, val channelConfig: Int,
    val offload: Boolean
) : Parcelable {
    companion object {
        fun fromMedia3AudioTrackConfig(config: AudioTrackConfig) =
            AudioTrackInfo(
                config.encoding, config.sampleRate, config.channelConfig,
                config.offload
            )
    }
}

class AfFormatTracker(
    private val context: Context, private val playbackHandler: Handler,
    private val handler: Handler
) : AnalyticsListener {
    companion object {
        private const val LOG_EVENTS = true
        private const val TAG = "AfFormatTracker"
    }
    // only access sink or track on PlaybackThread
    private var lastAudioTrack: AudioTrack? = null
    private var lastPeriodUid: Any? = null
    private var audioSink: DefaultAudioSink? = null
    var format: AfFormatInfo? = null
        private set
    var formatChangedCallback: ((AfFormatInfo?, Any?) -> Unit)? = null

    private val routingChangedListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        AudioRouting.OnRoutingChangedListener {
            router -> this@AfFormatTracker.onRoutingChanged(router as AudioTrack)
        } as Any
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        @Suppress("deprecation")
        AudioTrack.OnRoutingChangedListener {
            router -> this@AfFormatTracker.onRoutingChanged(router)
        } as Any
    } else null

    private fun onRoutingChanged(router: AudioTrack) {
        val audioTrack = (audioSink ?: throw NullPointerException(
            "audioSink is null in onAudioTrackInitialized"
        )).getAudioTrack()
        if (router !== audioTrack) return // stale callback
        // reaching here implies router == lastAudioTrack
        buildFormat(audioTrack, lastPeriodUid)
    }

    // TODO why do we have to reflect on app code, there must be a better solution
    private fun DefaultAudioSink.getAudioTrack(): AudioTrack? {
        val cls = javaClass
        val field = cls.getDeclaredField("audioTrack")
        field.isAccessible = true
        return field.get(this) as AudioTrack?
    }

    fun setAudioSink(sink: DefaultAudioSink) {
        this.audioSink = sink
    }

    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioTrackConfig
    ) {
        format = null
        playbackHandler.post {
            val audioTrack = (audioSink ?: throw NullPointerException(
                "audioSink is null in onAudioTrackInitialized"
            )).getAudioTrack()
            if (audioTrack != lastAudioTrack) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    lastAudioTrack?.removeOnRoutingChangedListener(
                        routingChangedListener as AudioRouting.OnRoutingChangedListener
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("deprecation")
                    lastAudioTrack?.removeOnRoutingChangedListener(
                        routingChangedListener as AudioTrack.OnRoutingChangedListener
                    )
                }
                lastPeriodUid?.let { formatChangedCallback?.invoke(null, it) }
                this.lastAudioTrack = audioTrack
                this.lastPeriodUid = eventTime.mediaPeriodId?.periodUid
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    audioTrack?.addOnRoutingChangedListener(
                        routingChangedListener as AudioRouting.OnRoutingChangedListener,
                        playbackHandler
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("deprecation")
                    audioTrack?.addOnRoutingChangedListener(
                        routingChangedListener as AudioTrack.OnRoutingChangedListener,
                        playbackHandler
                    )
                }
            }
            buildFormat(audioTrack, eventTime.mediaPeriodId?.periodUid)
        }
    }

    override fun onAudioTrackReleased(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioTrackConfig
    ) {
        playbackHandler.post {
            if (lastAudioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    lastAudioTrack?.removeOnRoutingChangedListener(
                        routingChangedListener as AudioRouting.OnRoutingChangedListener
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    @Suppress("deprecation")
                    lastAudioTrack?.removeOnRoutingChangedListener(
                        routingChangedListener as AudioTrack.OnRoutingChangedListener
                    )
                }
                lastAudioTrack = null
                formatChangedCallback?.invoke(null, lastPeriodUid)
                lastPeriodUid = null
                format = null
            }
        }
    }

    private fun buildFormat(audioTrack: AudioTrack?, periodUid: Any?) {
        audioTrack?.let {
            if (audioTrack.state == AudioTrack.STATE_UNINITIALIZED) return@let null
            val rd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                audioTrack.routedDevice else null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                handler.post {
                    val sd = MediaRoutes.getSelectedAudioDevice(context)
                    if (rd != sd)
                        Log.w(TAG, "routedDevice ${rd?.productName}(${rd?.id}) is not the same as MediaRoute " +
                                "selected device ${sd?.productName}(${sd?.id})")
                }
            }
            val deviceProductName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                rd?.productName.toString() else null
            val deviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                rd?.type else null
            val deviceId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                rd?.id else null
            val ioHandle = AudioTrackHiddenApi.getOutput(audioTrack)
            val halSampleRate = AudioTrackHiddenApi.getHalSampleRate(audioTrack)
            val grantedFlags = AudioTrackHiddenApi.getGrantedFlags(audioTrack)
            val mixPort = AudioSystemHiddenApi.getMixPortForThread(ioHandle)
            val primaryHw = AudioSystemHiddenApi.getPrimaryMixPort()?.hwModule
            val latency = try {
                // this call writes to mAfLatency and mLatency fields, hence call dump after this
                AudioTrack::class.java.getMethod("getLatency").invoke(audioTrack) as Int
            } catch (t: Throwable) {
                Log.e(TAG, Log.getThrowableString(t)!!)
                null
            }
            val dump = AudioTrackHiddenApi.dump(audioTrack)
            val isBluetoothOffload = if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || deviceType == AudioDeviceInfo.TYPE_BLE_SPEAKER
                || deviceType == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
                mixPort?.hwModule?.let { it == primaryHw }
            } else null
            AfFormatInfo(
                deviceProductName, deviceId, deviceType,
                audioTrack.audioSessionId,
                mixPort?.id, mixPort?.name, mixPort?.flags, mixPort?.hwModule, mixPort?.fast,
                ioHandle, halSampleRate ?: mixPort?.sampleRate,
                audioFormatToString(AudioTrackHiddenApi.getHalFormat(audioTrack) ?: mixPort?.format),
                AudioTrackHiddenApi.getHalChannelCount(audioTrack),
                mixPort?.channelMask, grantedFlags, AudioTrackHiddenApi.getPortIdFromDump(dump),
                AudioTrackHiddenApi.findAfTrackFlags(dump, latency, audioTrack, grantedFlags), isBluetoothOffload
            )
        }.let {
            if (LOG_EVENTS)
                Log.d(TAG, "audio hal format changed to: $it")
            format = it
            formatChangedCallback?.invoke(it, periodUid)
        }
    }

    private fun audioFormatToString(audioFormat: UInt?): String {
        for (encoding in AudioFormatDetector.Encoding.entries) {
            if (encoding.isSupportedAsNative && encoding.native == audioFormat)
                encoding.enc2?.let { return it }
        }
        return "AUDIO_FORMAT_($audioFormat)"
    }
}