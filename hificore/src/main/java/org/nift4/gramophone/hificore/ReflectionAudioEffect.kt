package org.nift4.gramophone.hificore

import android.annotation.SuppressLint
import android.media.audiofx.AudioEffect
import android.os.Build
import androidx.annotation.RequiresApi
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Class constructor.
 *
 * @param type type of effect engine created. See [android.media.audiofx.AudioEffect.EFFECT_TYPE_ENV_REVERB],
 *            [AudioEffect.EFFECT_TYPE_EQUALIZER]... Types corresponding to
 *            built-in effects are defined by AudioEffect class. Other types
 *            can be specified provided they correspond an existing OpenSL
 *            ES interface ID and the corresponding effect is available on
 *            the platform. If an unspecified effect type is requested, the
 *            constructor with throw the IllegalArgumentException. This
 *            parameter can be set to [EFFECT_TYPE_NULL] in which
 *            case only the uuid will be used to select the effect.
 * @param uuid unique identifier of a particular effect implementation.
 *            Must be specified if the caller wants to use a particular
 *            implementation of an effect type. This parameter can be set to
 *            [EFFECT_TYPE_NULL] in which case only the type will
 *            be used to select the effect.
 * @param priority the priority level requested by the application for
 *            controlling the effect engine. As the same effect engine can
 *            be shared by several applications, this parameter indicates
 *            how much the requesting application needs control of effect
 *            parameters. The normal priority is 0, above normal is a
 *            positive number, below normal a negative number.
 * @param audioSession system wide unique audio session identifier.
 *            The effect will be attached to the MediaPlayer or AudioTrack in
 *            the same audio session.
 *
 * @throws java.lang.IllegalArgumentException
 * @throws java.lang.UnsupportedOperationException
 * @throws java.lang.RuntimeException
 */
@Suppress("unused")
@SuppressLint("PrivateApi", "BlockedPrivateApi", "SoonBlockedPrivateApi")
open class ReflectionAudioEffect(type: UUID, uuid: UUID, priority: Int, audioSession: Int) {
	companion object {
		val EFFECT_TYPE_NULL by lazy {
			AudioEffect::class.java.getDeclaredField("EFFECT_TYPE_NULL").get(null) as UUID
		}
		fun isEffectTypeAvailable(type: UUID, uuid: UUID): Boolean {
			val desc = AudioEffect.queryEffects() ?: return false
			for (i in desc.indices) {
				if (type == EFFECT_TYPE_NULL || desc[i]!!.type == type) {
					if (uuid == EFFECT_TYPE_NULL || desc[i]!!.uuid == uuid) {
						return true
					}
				}
			}
			return false
		}
		fun isEffectTypeOffloadable(type: UUID, uuid: UUID): Boolean {
			TODO("implement this using AudioSystem::queryEffect")
		}

        data class AudioConfigBase(val sampleRate: Int, val channelMask: Int, val format: Int)

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private fun getEffectConfigs(ptr: Long): Pair<AudioConfigBase, AudioConfigBase> {
            val out = IntArray(6)
            val ret = getEffectConfigs(ptr, out)
            if (ret != 0) {
                throw IllegalStateException("getEffectConfigs() failed: $ret")
            }
            return AudioConfigBase(out[0], out[1], out[2]) to
                    AudioConfigBase(out[3], out[4], out[5])
        }
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        private external fun getEffectConfigs(ptr: Long, out: IntArray): Int
	}
	private val effect: AudioEffect = AudioEffect::class.java
		.getDeclaredConstructor(
			UUID::class.java, UUID::class.java,
			Int::class.java, Int::class.java)
		.newInstance(type, uuid, priority, audioSession)
	private val nativeEffectField by lazy {
		AudioEffect::class.java.getDeclaredField("mNativeAudioEffect").apply {
			isAccessible = true
		}
	}
	private val adapterClazz by lazy {
		Class.forName("org.nift4.audiofxfwd.OnParameterChangeListenerAdapter") }
	private val setParameterListenerFn by lazy {
		adapterClazz.getDeclaredMethod("getSetter").invoke(null) as Method
	}
	private val setParameterFn by lazy { AudioEffect::class.java.getDeclaredMethod(
		"setParameter", ByteArray::class.java, ByteArray::class.java) }
	private val getParameterFn by lazy { AudioEffect::class.java.getDeclaredMethod(
		"getParameter", ByteArray::class.java, ByteArray::class.java) }
	private val commandFn by lazy { AudioEffect::class.java.getDeclaredMethod("command",
		Int::class.java, ByteArray::class.java, ByteArray::class.java) }

	/**
	 * The effect enabled state
	 *
	 * Creating an audio effect does not automatically apply this effect on the audio source. It
	 * creates the resources necessary to process this effect but the audio signal is still bypassed
	 * through the effect engine. Calling this method will make that the effect is actually applied
	 * or not to the audio content being played in the corresponding audio session.
	 *
	 * @return true if the effect is enabled, false otherwise.
	 * @throws IllegalStateException
	 */
	var enabled
		get() = effect.enabled
		set(value) {
			val ret = effect.setEnabled(value)
			if (ret != AudioEffect.SUCCESS) {
				throw IllegalStateException("enable failed with code $ret")
			}
		}

	/**
	 * Returns effect unique identifier. This system wide unique identifier can
	 * be used to attach this effect to a MediaPlayer or an AudioTrack when the
	 * effect is an auxiliary effect (Reverb)
	 *
	 * @return the effect identifier.
	 * @throws IllegalStateException
	 */
	val id
		get() = effect.id

	/**
	 * Get the effect descriptor.
	 *
	 * @see AudioEffect.Descriptor
	 * @throws IllegalStateException
	 */
	val descriptor: AudioEffect.Descriptor
		get() = effect.descriptor

	/**
	 * Checks if this AudioEffect object is controlling the effect engine.
	 *
	 * @return true if this instance has control of effect engine, false
	 *         otherwise.
	 * @throws IllegalStateException
	 */
	fun hasControl() = effect.hasControl()

	/**
	 * Releases the native AudioEffect resources. It is a good practice to
	 * release the effect engine when not in use as control can be returned to
	 * other applications or the native resources released.
	 */
	fun release() {
		effect.release()
	}

	/**
	 * Sets the listener AudioEffect notifies when the effect engine is enabled
	 * or disabled.
	 *
	 * @param listener
	 */
	fun setEnableStatusListener(listener: AudioEffect.OnEnableStatusChangeListener?) {
		effect.setEnableStatusListener(listener)
	}

	/**
	 * Sets the listener AudioEffect notifies when the effect engine control is
	 * taken or returned.
	 *
	 * @param listener
	 */
	fun setControlStatusListener(listener: AudioEffect.OnControlStatusChangeListener?) {
		effect.setControlStatusListener(listener)
	}

	/**
	 * Sets the listener AudioEffect notifies when a parameter is changed.
	 *
	 * @param listener
	 */
	fun setBaseParameterListener(listener: OnParameterChangeListener?) {
		val adapter = listener?.let { adapterClazz.getDeclaredConstructor(
			org.nift4.audiofxfwd.OnParameterChangeListener::class.java)
            .apply { isAccessible = true }
			.newInstance(org.nift4.audiofxfwd.OnParameterChangeListener { e, i, b, b1 ->
				listener.onParameterChange(effect, i, b, b1)
			}) }
		setParameterListenerFn.invoke(effect, adapter)
	}

	@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
	fun getConfigs(): Pair<AudioConfigBase, AudioConfigBase> {
		try {
			effect.getId()
		} catch (_: IllegalStateException) {
			throw IllegalStateException("getConfigs() called on released AudioEffect")
		}
		val ptr = nativeEffectField.getLong(effect)
		return getEffectConfigs(ptr)
	}

	/**
	 * Set effect parameter. The setParameter method is provided in several
	 * forms addressing most common parameter formats. This form is the most
	 * generic one where the parameter and its value are both specified as an
	 * array of bytes. The parameter and value type and length are therefore
	 * totally free. For standard effect defined by OpenSL ES, the parameter
	 * format and values must match the definitions in the corresponding OpenSL
	 * ES interface.
	 *
	 * @param param the identifier of the parameter to set
	 * @param value the new value for the specified parameter
	 * @throws IllegalStateException
	 */
	fun setParameter(param: ByteArray, value: ByteArray) {
		val ret = setParameterFn.invoke(effect, param, value) as Int
		if (ret != AudioEffect.SUCCESS) {
			throw IllegalStateException("setParameter failed with code $ret")
		}
	}

	// could do setParameterDeferred and setParameterCommit if needed, so far not needed
	// TODO: impl https://cs.android.com/android/_/android/platform/frameworks/av/+/de8caf42b35fcd4ef24eac5396ec6e813b0e1bea
	//  if it turns out to be useful

	/**
	 * Get effect parameter. The getParameter method is provided in several
	 * forms addressing most common parameter formats. This form is the most
	 * generic one where the parameter and its value are both specified as an
	 * array of bytes. The parameter and value type and length are therefore
	 * totally free.
	 *
	 * @param param the identifier of the parameter to set
	 * @param value the new value for the specified parameter
	 * @return the number of meaningful bytes in value array in case of success
	 * @throws IllegalStateException
	 */
	fun getParameter(param: ByteArray, value: ByteArray): Int {
		val ret = getParameterFn.invoke(effect, param, value) as Int
		if (ret < 0) {
			throw IllegalStateException("getParameter failed with code $ret")
		}
		return ret
	}

	/**
	 * Send a command to the effect engine. This method is intended to send
	 * proprietary commands to a particular effect implementation.
	 * In case of success, returns the number of meaningful bytes in reply array.
	 * In case of failure, the returned value is negative and implementation specific.
	 */
	@Throws(java.lang.IllegalStateException::class)
	fun command(cmdCode: Int, command: ByteArray, reply: ByteArray): Int {
		return commandFn.invoke(effect, cmdCode, command, reply) as Int
	}

	fun setParameter(param: Int, value: Int) = setParameter(
		ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
			.putInt(param).array(),
		ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
			.putInt(value).array()
	)

	fun setParameter(param: Int, value: Short) = setParameter(
		ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
			.putInt(param).array(),
		ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
			.putShort(value).array()
	)

	fun setParameter(param: Int, value: Boolean) = setParameter(
		param, if (value) 1 else 0
	)

	fun getParameter(param: Int, value: ByteArray) = getParameter(
		ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
			.putInt(param).array(),
		value
	)

	fun getShortParameter(param: Int): Short {
		val value = ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.nativeOrder())
		val ret = getParameter(
			ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
				.putInt(param).array(),
			value.array()
		)
		if (ret != value.limit()) {
			throw IllegalStateException("getShortParameter() failed: $ret")
		}
		return value.short
	}

	fun getIntParameter(param: Int): Int {
		val value = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
		val ret = getParameter(
			ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
				.putInt(param).array(),
			value.array()
		)
		if (ret != value.limit()) {
			throw IllegalStateException("getIntParameter() failed: $ret")
		}
		return value.int
	}

	fun getBoolParameter(param: Int): Boolean {
		val ret = getIntParameter(param)
		return when (ret) {
			0 -> false
			1 -> true
			else -> throw IllegalStateException("getBoolParameter(): invalid bool $ret")
		}
	}

	fun interface OnParameterChangeListener {
		/**
		 * Called on the listener to notify it that a parameter value has changed.
		 * @param effect the effect on which the interface is registered.
		 * @param status status of the set parameter operation.
		 * @param param ID of the modified parameter.
		 * @param value the new parameter value.
		 */
		fun onParameterChange(
			effect: AudioEffect, status: Int, param: ByteArray,
			value: ByteArray
		)
	}
}