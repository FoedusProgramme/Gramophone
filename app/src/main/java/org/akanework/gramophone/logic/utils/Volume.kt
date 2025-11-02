package org.akanework.gramophone.logic.utils

import android.media.audiofx.AudioEffect
import androidx.media3.common.util.Log
import org.nift4.gramophone.hificore.ReflectionAudioEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Class constructor. See also OpenSL ES 1.1 specification, SLVolumeItf interface.
 *
 * The system will change the volume level stored in the effect without notifying the listener if:
 * - The computed volume (sw master volume * stream volume * shaper volume) changes
 * - An effect is started or stopped
 * - Audio track paused (set to volume -96dB) / unpaused (set to former volume)
 *
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
class Volume(priority: Int, audioSession: Int) : ReflectionAudioEffect(
	EFFECT_TYPE_VOLUME, EFFECT_TYPE_NULL, priority, audioSession) {
	companion object {
		private val EFFECT_TYPE_VOLUME =
			UUID.fromString("09e8ede0-ddde-11db-b4f6-0002a5d5c51b")
		fun isAvailable() = isEffectTypeAvailable(EFFECT_TYPE_VOLUME, EFFECT_TYPE_NULL)
		fun isOffloadable() = isEffectTypeOffloadable(EFFECT_TYPE_VOLUME, EFFECT_TYPE_NULL)
	}

	var level: Short // SLmillibel
		get() = getShortParameter(0)
		set(value) {
			setParameter(0, value)
		}
	val maxLevel: Short // SLmillibel
		get() = getShortParameter(1)
	var mute: Boolean
		get() = getBoolParameter(2)
		set(value) {
			setParameter(2, value)
		}
	var enableStereoPosition: Boolean
		get() = getBoolParameter(3)
		set(value) {
			setParameter(3, value)
		}
	var stereoPosition: Short // SLpermille
		get() = getShortParameter(4)
		set(value) {
			setParameter(4, value)
		}

	fun setParameterListener(listener: (Int, Int) -> Unit) {
		setBaseParameterListener { _, status, param, value ->
			val paramI = ByteBuffer.wrap(param).order(ByteOrder.nativeOrder())
				.asIntBuffer().get()
			if (status == AudioEffect.SUCCESS) {
				when (paramI) {
					0, 1, 4 -> {
						val valueS = ByteBuffer.wrap(param)
							.order(ByteOrder.nativeOrder()).asShortBuffer().get()
						listener(paramI, valueS.toInt())
					}
					2, 3 -> {
						val valueB = ByteBuffer.wrap(param)
							.order(ByteOrder.nativeOrder()).asIntBuffer().get() != 0
						listener(paramI, if (valueB) 1 else 0)
					}
					else -> Log.e("Volume", "unknown param $paramI changed")
				}
			} else {
				Log.e("Volume", "param $paramI change failed? $status")
			}
		}
	}
}