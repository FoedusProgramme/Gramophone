package org.nift4.gramophone.hificore

import androidx.media3.common.util.Log
import java.nio.ByteBuffer

class AdaptiveDynamicRangeCompression {
	companion object {
		private const val TAG = "AdaptiveDRCSw"
		var libLoaded = false
			private set
	}
	private var ptr: Long
	private var inited = false
	private var samplingRate: Int? = null
	private var tauAttack: Float? = null
	private var tauRelease: Float? = null
	private var compressionRatio: Float? = null
	init {
		if (!AudioTrackHiddenApi.libLoaded) {
			try {
				Log.d(TAG, "Loading libhificore.so")
				System.loadLibrary("hificore")
				Log.d(TAG, "Done loading libhificore.so")
			} catch (e: Throwable) {
				throw IllegalStateException("can't load lib for AdaptiveDRC", e)
			}
		}
		// don't set the hidden api one to true, .so is shared for simplicity but hidden api
		// may not wish or be allowed to load/use the library.
		libLoaded = true
		try {
			ptr = create()
		} catch (e: Throwable) {
			throw IllegalStateException("create failed", e)
		}
		if (ptr == 0L) {
			throw IllegalStateException("create failed: NULL")
		}
	}
	private external fun create(): Long
	private external fun releaseNative(ptr: Long)
	private external fun initNative(ptr: Long, samplingRate: Float, tauAttack: Float,
	                                tauRelease: Float, compressionRatio: Float)
	private external fun compressNative(ptr: Long, channelCount: Int, inputAmp: Float,
	                                    kneeThresholdLog: Float, postAmp: Float, `in`: ByteBuffer,
	                                    `out`: ByteBuffer, frameCount: Int)
	// (re-)init, resets cached state such as current energy. should be done when switching songs
	fun init(samplingRate: Int, tauAttack: Float, tauRelease: Float, compressionRatio: Float) {
		this.samplingRate = samplingRate
		this.tauAttack = tauAttack
		this.tauRelease = tauRelease
		this.compressionRatio = compressionRatio
		if (ptr == 0L) {
			throw IllegalStateException("called release() before init()")
		}
		try {
			initNative(ptr, samplingRate.toFloat(), tauAttack, tauRelease, compressionRatio)
		} catch (e: Throwable) {
			throw IllegalStateException("initNative failed", e)
		}
		inited = true
	}
	fun flush() {
		if (samplingRate == null || tauAttack == null || tauRelease == null ||
			compressionRatio == null) {
			throw IllegalStateException("flush() called before init()")
		}
		init(samplingRate!!, tauAttack!!, tauRelease!!,
			compressionRatio!!)
	}
	fun compress(channelCount: Int, inputAmp: Float, kneeThresholdLog: Float,
	             postAmp: Float, `in`: ByteBuffer, `out`: ByteBuffer, frameCount: Int) {
		if (!`in`.isDirect) {
			throw IllegalArgumentException("in buffer not direct")
		}
		if (!`out`.isDirect) {
			throw IllegalArgumentException("out buffer not direct")
		}
		if (`out`.isReadOnly) {
			throw IllegalArgumentException("out buffer read only")
		}
		if (ptr == 0L) {
			throw IllegalStateException("called release() before compress()")
		}
		if (!inited) {
			throw IllegalStateException("called compress() before init()")
		}
		try {
			compressNative(ptr, channelCount, inputAmp, kneeThresholdLog, postAmp,
				`in`, `out`, frameCount)
		} catch (e: Throwable) {
			throw IllegalStateException("compressNative failed", e)
		}
	}
	fun reset() {
		if (ptr == 0L) {
			throw IllegalStateException("called release() before reset()")
		}
		inited = false
	}
	fun release() {
		if (ptr == 0L) {
			throw IllegalStateException("called release() already")
		}
		try {
			releaseNative(ptr)
		} catch (e: Throwable) {
			throw IllegalStateException("releaseNative failed", e)
		}
	}
}