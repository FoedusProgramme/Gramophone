package org.akanework.gramophone.logic.utils

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.Log
import org.nift4.gramophone.hificore.AdaptiveDynamicRangeCompression
import java.nio.ByteBuffer

class ReplayGainAudioProcessor : BaseAudioProcessor() {
	companion object {
		private const val TAG = "ReplayGainAP"
	}
	private var compressor: AdaptiveDynamicRangeCompression? = null
	private var waitingForFlush = false
	var mode = ReplayGainUtil.Mode.None
		private set
	var rgGain = 0 // dB
		private set
	var nonRgGain = 0 // dB
		private set
	var boostGain = 0 // dB
		private set
	var offloadEnabled = false
		private set
	var reduceGain = false
		private set
	var boostGainChangedListener: (() -> Unit)? = null
	var offloadEnabledChangedListener: (() -> Unit)? = null
	private var gain = 1f
	private var kneeThresholdDb: Float? = null
	private var tags: ReplayGainUtil.ReplayGainInfo? = null
	override fun queueInput(inputBuffer: ByteBuffer) {
		val frameCount = inputBuffer.remaining() / inputAudioFormat.bytesPerFrame
		val outputBuffer = replaceOutputBuffer(frameCount * outputAudioFormat.bytesPerFrame)
		if (inputBuffer.hasRemaining()) {
			if (compressor != null) {
				compressor!!.compress(
					inputAudioFormat.channelCount,
					gain,
					kneeThresholdDb!!, 1f, inputBuffer,
					outputBuffer, frameCount
				)
				inputBuffer.position(inputBuffer.limit())
				outputBuffer.position(frameCount * outputAudioFormat.bytesPerFrame)
			} else {
				if (gain == 1f) {
					outputBuffer.put(inputBuffer)
				} else {
					while (inputBuffer.hasRemaining()) {
						outputBuffer.putFloat(inputBuffer.getFloat() * gain)
					}
				}
			}
		}
		outputBuffer.flip()
	}

	override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
		if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
			throw UnhandledAudioFormatException(
				"Invalid PCM encoding. Expected float PCM.", inputAudioFormat
			)
		}
		// TODO(ASAP): if setMode and setNonRgGain required reconfiguration, we could be a lot lazier.
		if ((tags?.trackGain == null && tags?.albumGain == null)
			|| (tags?.trackGain != null && tags?.trackGain != 1f)
			|| (tags?.albumGain != null && tags?.albumGain != 1f)) {
			//return inputAudioFormat TODO(ASAP)
		}
		// if there's RG metadata but it says we don't need to do anything, we can skip all work.
		return AudioProcessor.AudioFormat.NOT_SET
	}

	@Synchronized
	fun setMode(mode: ReplayGainUtil.Mode) {
		this.mode = mode
	}

	@Synchronized
	fun setRgGain(rgGain: Int) {
		this.rgGain = rgGain
	}

	@Synchronized
	fun setNonRgGain(nonRgGain: Int) {
		this.nonRgGain = nonRgGain
	}

	fun setBoostGain(boostGain: Int) {
		val changed: Boolean
		val listener: (() -> Unit)?
		synchronized(this) {
			changed = this.boostGain != boostGain
			listener = boostGainChangedListener
			this.boostGain = boostGain
		}
		if (changed) {
			listener?.invoke()
		}
	}

	@Synchronized
	fun setReduceGain(reduceGain: Boolean) {
		this.reduceGain = reduceGain
	}

	fun setOffloadEnabled(offloadEnabled: Boolean) {
		val changed: Boolean
		val listener: (() -> Unit)?
		synchronized(this) {
			changed = this.offloadEnabled != offloadEnabled
			listener = offloadEnabledChangedListener
			this.offloadEnabled = offloadEnabled
		}
		if (changed) {
			listener?.invoke()
		}
	}

	fun setRootFormat(inputFormat: Format) {
		tags = ReplayGainUtil.parse(inputFormat)
	}

	private fun computeGain() {
		val mode: ReplayGainUtil.Mode
		val rgGain: Int
		val nonRgGain: Int
		val reduceGain: Boolean
		synchronized(this) {
			mode = this.mode
			rgGain = this.rgGain
			nonRgGain = this.nonRgGain
			reduceGain = this.reduceGain
		}
		val gain = ReplayGainUtil.calculateGain(tags, mode, rgGain,
			reduceGain, ReplayGainUtil.RATIO)
		this.gain = gain?.first ?: ReplayGainUtil.dbToAmpl(nonRgGain.toFloat())
		this.kneeThresholdDb = gain?.second
		if (kneeThresholdDb != null) {
			if (compressor == null)
				compressor = AdaptiveDynamicRangeCompression()
			Log.w(TAG, "using dynamic range compression")
			compressor!!.init(
				inputAudioFormat.sampleRate,
				ReplayGainUtil.TAU_ATTACK, ReplayGainUtil.TAU_RELEASE,
				ReplayGainUtil.RATIO
			)
		} else {
			onReset() // delete compressor
		}
	}

	override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
		waitingForFlush = false
		computeGain()
	}

	override fun onReset() {
		compressor?.release()
		compressor = null
	}
}