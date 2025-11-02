package org.akanework.gramophone.logic.utils.exoplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.ToFloatPcmAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import org.akanework.gramophone.logic.utils.PostAmpAudioSink
import org.akanework.gramophone.logic.utils.ReplayGainAudioProcessor
import org.nift4.alacdecoder.AlacRenderer

class GramophoneRenderFactory(context: Context,
                              private val rgAp: ReplayGainAudioProcessor,
                              private val configurationListener: (Format?) -> Unit,
                              private val audioSinkListener: (DefaultAudioSink) -> Unit) :
    DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) {
        // empty
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: @ExtensionRendererMode Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: java.util.ArrayList<Renderer>
    ) {
        out.add(AlacRenderer(eventHandler, eventListener, audioSink))
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out
        )
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: java.util.ArrayList<Renderer>
    ) {
        // empty
    }

    override fun buildImageRenderers(context: Context, out: java.util.ArrayList<Renderer>) {
        // empty
    }

    override fun buildCameraMotionRenderers(
        context: Context,
        extensionRendererMode: Int,
        out: java.util.ArrayList<Renderer>
    ) {
        // empty
    }

    override fun buildAudioSink(
        context: Context,
        pcmEncodingRestrictionLifted: Boolean,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        val builder = DefaultAudioSink.Builder(context)
        if (pcmEncodingRestrictionLifted || !enableFloatOutput) {
            builder.setPcmEncodingRestrictionLifted(pcmEncodingRestrictionLifted)
        } else {
            @Suppress("deprecation")
            builder.setEnableFloatOutput(true)
        }
        val float = ToFloatPcmAudioProcessor()
        builder.setAudioProcessorChain(object : AudioProcessorChain {
            override fun getAudioProcessors(inputFormat: Format): Array<out AudioProcessor> {
                rgAp.setRootFormat(inputFormat)
                // TODO(ASAP): do i wish to hardcode float conversion always?
                return arrayOf(float, rgAp)
            }

            override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters {
                return PlaybackParameters.DEFAULT
            }

            override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean {
                return false
            }

            override fun getMediaDuration(playoutDuration: Long): Long {
                return playoutDuration
            }

            override fun getSkippedOutputFrameCount(): Long {
                return 0
            }
        })
        val root = builder.setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).build()
        audioSinkListener(root)
        return MyForwardingAudioSink(
	        //PostAmpAudioSink( TODO(ASAP)
		        root//, rgAp, context
			//)
        )
    }

    inner class MyForwardingAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
        override fun configure(
            inputFormat: Format,
            specifiedBufferSize: Int,
            outputChannels: IntArray?
        ) {
            super.configure(inputFormat, specifiedBufferSize, outputChannels)
            configurationListener(inputFormat)
        }

        override fun reset() {
            super.reset()
            configurationListener(null)
        }

        override fun release() {
            super.release()
            configurationListener(null)
        }
    }
}
