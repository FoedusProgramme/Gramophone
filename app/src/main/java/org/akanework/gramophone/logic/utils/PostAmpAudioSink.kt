package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.audio.AudioManagerCompat
import androidx.media3.common.util.Log
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.utils.AudioFormatDetector.audioDeviceTypeToString
import org.nift4.gramophone.hificore.AudioSystemHiddenApi
import org.nift4.gramophone.hificore.ReflectionAudioEffect
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/*
 * For "smart album/track gain selection": the used gain is always determined at audio track init.
 * Album gain will be used if track from same album is either prev or next in playlist. If user adds
 * same album track after we started playing we shall hold onto chosen gain until seek or next
 * track happens. Also that implies that if there are two songs that could be played gaplessly, they
 * should NOT be played gaplessly unless RG gain is also same.
 * TODO(ASAP): for offload only, or always? if only for offload, then RGAP needs to apply gain
 *  outside of flush somehow (do AudioProcessors even get told the config is now to be applied?)
 *  https://github.com/androidx/media/issues/2855
 * That effectively avoids audible volume jumps, and allows to change effect settings for offloaded
 * RG in synchronized way as well, avoiding wrong gain applied to audio frame.
 */
// TODO: what is com.lge.media.EXTRA_VOLUME_STREAM_HIFI_VALUE
// TODO: less hacky https://github.com/nift4/media/commit/22d2156bec74542a0764bf0ec27c839cc70874ed
// TODO(ASAP): setting in UI for boost gain
// TODO(ASAP): fix summary to subtract 15 in settings UI
// TODO(ASAP): impl isEffectTypeOffloadable()
// TODO(ASAP): figure out if we can influence or detect the precedence between volume and DPE
//  and decide on a strategy. so far it seems DPE always works, so one possibility is to just use it
//  or maybe I just don't create DPE if offload is off and always use volume
class PostAmpAudioSink(
	val sink: DefaultAudioSink, val rgAp: ReplayGainAudioProcessor, val context: Context
) : ForwardingAudioSink(sink), AudioSystemHiddenApi.VolumeChangeListener {
	companion object {
		private const val TAG = "PostAmpAudioSink"
	}
	private val receiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action == "android.media.VOLUME_CHANGED_ACTION"
				|| intent?.action == "android.media.MASTER_VOLUME_CHANGED_ACTION"
				|| intent?.action == "android.media.MASTER_MUTE_CHANGED_ACTION"
				|| intent?.action == "android.media.STREAM_MUTE_CHANGED_ACTION") {
				myOnReceiveBroadcast(intent)
			}
		}
	}
	private val audioManager = context.getSystemService<AudioManager>()!!
	private var handler: Handler? = null
	private val isVolumeAvailable by lazy {
		try {
			Volume.isAvailable()
		} catch (e: Throwable) {
			Log.e(TAG, "failed to check if volume is available", e)
			false
		}
	}
	private val isDpeAvailable by lazy {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				ReflectionAudioEffect.isEffectTypeAvailable(
					AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING,
                    ReflectionAudioEffect.EFFECT_TYPE_NULL
				)
			} else {
				false
			}
		} catch (e: Throwable) {
			Log.e(TAG, "failed to check if DPE is available", e)
			false
		}
	}
	private val isVolumeOffloadable by lazy {
		try {
			Volume.isOffloadable()
		} catch (e: Throwable) {
			Log.e(TAG, "failed to check if volume is offloadable", e)
			false
		}
	}
	private val isDpeOffloadable by lazy {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				ReflectionAudioEffect.isEffectTypeOffloadable(
					AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING,
                    ReflectionAudioEffect.EFFECT_TYPE_NULL
				)
			} else {
				false
			}
		} catch (e: Throwable) {
			Log.e(TAG, "failed to check if DPE is offloadable", e)
			false
		}
	}
	private var volumeEffect: Volume? = null
	private var dpeEffect: DynamicsProcessing? = null
	private var dpeCanary: ReflectionAudioEffect? = null
	private var hasVolume = false
	private var hasDpe = false
	private var offloadEnabled: Boolean? = null
	private var format: Format? = null
	private var pendingFormat: Format? = null
	private var tags: ReplayGainUtil.ReplayGainInfo? = null
	private var deviceType: Int? = null
	private var audioSessionId = 0
	private var volume = 1f
	private var rgVolume = 1f

	init {
        var forVolumeChanged = false
        try {
            AudioSystemHiddenApi.addVolumeCallback(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "failed to register volume cb", e)
            forVolumeChanged = true
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                if (forVolumeChanged) // only register if better native callback doesn't work
                    addAction("android.media.VOLUME_CHANGED_ACTION")
                addAction("android.media.MASTER_VOLUME_CHANGED_ACTION")
                addAction("android.media.MASTER_MUTE_CHANGED_ACTION")
                addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
            },
            @SuppressLint("WrongConstant") // why is this needed?
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
		synchronized(rgAp) {
			rgAp.boostGainChangedListener = {
				handler?.post { // if null, there are no effects that need to be notified anyway
					updateVolumeEffect()
					calculateGain(false)
				}
			}
			rgAp.offloadEnabledChangedListener = {
				if (offloadEnabled != null) {
					handler!!.post {
						mySetAudioSessionId(null)
					}
				}
			}
		}
	}

	override fun setListener(listener: AudioSink.Listener) {
		super.setListener(object : AudioSink.Listener by listener {
			override fun onPositionAdvancing(playoutStartSystemTimeMs: Long) {
				updateVolumeEffect()
				listener.onPositionAdvancing(playoutStartSystemTimeMs)
			}

			override fun onOffloadBufferEmptying() {
				listener.onOffloadBufferEmptying()
			}

			override fun onOffloadBufferFull() {
				listener.onOffloadBufferFull()
			}

			override fun onAudioSinkError(audioSinkError: Exception) {
				listener.onAudioSinkError(audioSinkError)
			}

			override fun onAudioCapabilitiesChanged() {
				listener.onAudioCapabilitiesChanged()
			}

			override fun onAudioTrackInitialized(audioTrackConfig: AudioSink.AudioTrackConfig) {
				myApplyPendingConfig()
				listener.onAudioTrackInitialized(audioTrackConfig)
			}

			override fun onAudioTrackReleased(audioTrackConfig: AudioSink.AudioTrackConfig) {
				listener.onAudioTrackReleased(audioTrackConfig)
			}

			override fun onSilenceSkipped() {
				listener.onSilenceSkipped()
			}

			override fun onAudioSessionIdChanged(audioSessionId: Int) {
				mySetAudioSessionId(audioSessionId)
				listener.onAudioSessionIdChanged(audioSessionId)
			}

			@RequiresApi(Build.VERSION_CODES.M)
			override fun onRoutingChanged(router: AudioTrack, routedDevice: AudioDeviceInfo?) {
				myOnRoutingChanged(routedDevice)
				listener.onRoutingChanged(router, routedDevice)
			}
		})
	}

	override fun configure(
		inputFormat: Format,
		specifiedBufferSize: Int,
		outputChannels: IntArray?
	) {
		pendingFormat = inputFormat
		super.configure(inputFormat, specifiedBufferSize, outputChannels)
	}

	override fun setVolume(volume: Float) {
		this.volume = volume
		setVolumeInternal(false)
	}

	private fun setVolumeInternal(force: Boolean) {
		super.setVolume(volume * rgVolume)
		updateVolumeEffect(force)
	}

	private fun myOnReceiveBroadcast(intent: Intent) {
		updateVolumeEffect()
		if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
			onVolumeChanged()
		}
	}

    override fun onVolumeChanged(
        groupId: Int,
        flags: Int
    ) {
        // TODO use below class to find out which group id corresponds to music and only listen to
        //  those change events
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/media/java/android/media/audiopolicy/AudioProductStrategy.java;l=80?q=getAudioProductStrategies&ss=android%2Fplatform%2Fsuperproject%2Fmain
        Log.i(TAG, "volume changed: $groupId, $flags")
        onVolumeChanged()
    }

    private fun onVolumeChanged() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasDpe) {
            calculateGain(false)
        }
    }

	private fun myApplyPendingConfig() {
		format = pendingFormat
		tags = ReplayGainUtil.parse(format)
		calculateGain(false)
		updateVolumeEffect()
	}

	private fun calculateGain(force: Boolean) {
		// Nonchalantly borrow settings from ReplayGainAudioProcessor
		val mode: ReplayGainUtil.Mode
		val rgGain: Int
		val nonRgGain: Int
		val boostGainDb: Int
		val reduceGain: Boolean
		synchronized(rgAp) {
			mode = rgAp.mode
			rgGain = rgAp.rgGain
			nonRgGain = rgAp.nonRgGain
			boostGainDb = rgAp.boostGain
			reduceGain = rgAp.reduceGain
		}
		val useDpe = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasDpe
		val isOffload = true//format?.let { it.sampleMimeType != MimeTypes.AUDIO_RAW } == true TODO(ASAP)
		if (useDpe) {
			try {
				dpeEffect!!.enabled = isOffload || boostGainDb > 0/* && !hasVolume*/
			} catch (e: IllegalStateException) {
				Log.e(TAG, "dpe enable=$isOffload failed", e)
			}
		}
        // prefer volume over DPE because volume may result in too low volume only, DPE may result
        // in too high volume / clipping for a short moment.
		val boostGainDbLimited = if (useDpe && boostGainDb > 0 && /*!hasVolume && */deviceType != null
            && !isAbsoluteVolume(deviceType!!, false)) {
			val maxIndex = AudioManagerCompat.getStreamMaxVolume(audioManager, C.STREAM_TYPE_MUSIC)
			val curIndex = AudioManagerCompat.getStreamVolume(audioManager, C.STREAM_TYPE_MUSIC)
			val minIndex = AudioManagerCompat.getStreamMinVolume(audioManager, C.STREAM_TYPE_MUSIC)
			val minVolumeDb =
				max(
					audioManager.getStreamVolumeDb(
						AudioManager.STREAM_MUSIC, minIndex,
						deviceType!!
					), -96f
				)
			var maxVolumeDb = audioManager.getStreamVolumeDb(
				AudioManager.STREAM_MUSIC, maxIndex,
				deviceType!!
			)
			var curVolumeDb = max(audioManager.getStreamVolumeDb(
				AudioManager.STREAM_MUSIC, curIndex,
				deviceType!!
			), -96f)
			if (maxVolumeDb - minVolumeDb == 1f && curVolumeDb <= 1f && curVolumeDb >= 0f) {
				maxVolumeDb = ReplayGainUtil.amplToDb(maxVolumeDb)
				curVolumeDb = ReplayGainUtil.amplToDb(curVolumeDb)
			}
			val headroomDb = maxVolumeDb - curVolumeDb
			Log.d(TAG, "dpe gain boost: headroom $headroomDb, boost $boostGainDb")
			min(headroomDb, boostGainDb.toFloat())
		} else 0f
		if (isOffload) {
			val calcGain = ReplayGainUtil.calculateGain(tags, mode, rgGain, reduceGain || !useDpe,
				if (useDpe) ReplayGainUtil.RATIO else null)
			val gain = calcGain?.first ?: ReplayGainUtil.dbToAmpl(nonRgGain.toFloat())
			val kneeThresholdDb = calcGain?.second
			rgVolume = if (useDpe) 1f else min(gain, 1f)
			try {
				if (useDpe) {
					dpeEffect!!.setInputGainAllChannelsTo(ReplayGainUtil.amplToDb(gain) + boostGainDbLimited)
                    dpeEffect!!.setLimiterAllChannelsTo(
                        DynamicsProcessing.Limiter(
                            true, kneeThresholdDb != null, 0,
                            ReplayGainUtil.TAU_ATTACK * 1000f,
                            ReplayGainUtil.TAU_RELEASE * 1000f,
                            ReplayGainUtil.RATIO, kneeThresholdDb ?: 999999f, 0f
                        )
                    )
				}
			} catch (e: UnsupportedOperationException) {
				Log.e(TAG, "we raced with someone else about DPE and we lost", e)
			}
		} else {
			if (useDpe && /*(!hasVolume || force) && */boostGainDb > 0) {
                dpeEffect!!.setInputGainAllChannelsTo(boostGainDbLimited)
                dpeEffect!!.setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(
                        true, false, 0,
                        ReplayGainUtil.TAU_ATTACK * 1000f,
                        ReplayGainUtil.TAU_RELEASE * 1000f,
                        ReplayGainUtil.RATIO, 999999f, 0f
                    )
                )
			}
			rgVolume = 1f
		}
		setVolumeInternal(force)
	}

	override fun setAudioSessionId(audioSessionId: Int) {
		mySetAudioSessionId(audioSessionId)
		super.setAudioSessionId(audioSessionId)
	}

	private fun mySetAudioSessionId(id: Int?) {
		if (handler == null)
			handler = Handler(Looper.myLooper()!!)
		val offloadEnabled: Boolean
		synchronized(rgAp) {
			offloadEnabled = rgAp.offloadEnabled
		}
		if (id != null && id != audioSessionId || offloadEnabled != this.offloadEnabled) {
			Log.i(TAG, "set session id to $id")
			if (audioSessionId != 0) {
				if (volumeEffect != null) {
					volumeEffect!!.let {
						CoroutineScope(Dispatchers.Default).launch {
							try {
								it.enabled = false
								it.release()
							} catch (e: Throwable) {
								Log.e(TAG, "failed to release Volume effect", e)
							}
						}
					}
					volumeEffect = null
				}
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dpeEffect != null) {
					// DPE must be released synchronously to avoid getting the old effect instance
					// again, with its old inUse values.
					dpeEffect!!.let {
						try {
							it.enabled = false
							it.release()
						} catch (e: Throwable) {
							Log.e(TAG, "failed to release DPE effect", e)
						}
					}
					dpeEffect = null
				}
			}
			hasVolume = false
			hasDpe = false
			this.offloadEnabled = offloadEnabled
			audioSessionId = id ?: audioSessionId
            // Set a lower priority when creating effects - we are willing to share.
            // (User story "EQ is not working and I have to change a obscure setting to fix it"
            // is worse than user story "it's too quiet when I enable my EQ, but gets louder
            // when I disable it").
			if (audioSessionId != 0) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (isDpeAvailable &&
                        (!offloadEnabled || isDpeOffloadable)
                    ) {
                        createDpeEffect()
                    } else {
                        Log.i(TAG, "didn't init DPE, e=$isDpeAvailable o=$isDpeOffloadable O=$offloadEnabled")
                    }
                }
                if (isVolumeAvailable && (!offloadEnabled || isVolumeOffloadable)) {
                    try {
                        volumeEffect = Volume(-100000, audioSessionId)
                        volumeEffect!!.setControlStatusListener { _, hasControl ->
                            Log.i(TAG, "volume control state is now: $hasControl")
                            hasVolume = hasControl
                            updateVolumeEffect()
                        }
                        hasVolume = volumeEffect!!.hasControl()
                        Log.i(TAG, "init volume, control state is: $hasVolume")
                    } catch (e: Throwable) {
                        Log.e(TAG, "failed to init Volume effect", e)
                    }
                } else {
                    Log.i(TAG, "didn't init volume, e=$isVolumeAvailable o=$isVolumeOffloadable O=$offloadEnabled")
                }
			}
		}
	}

	@RequiresApi(Build.VERSION_CODES.M)
	private fun myOnRoutingChanged(routedDevice: AudioDeviceInfo?) {
		Log.d(TAG, "routed device is now ${routedDevice?.productName} " +
				"(${routedDevice?.type?.let { audioDeviceTypeToString(context, it) }})")
		deviceType = routedDevice?.type
		calculateGain(false)
		updateVolumeEffect()
	}

	@RequiresApi(Build.VERSION_CODES.P)
	private fun createDpeEffect() {
		hasDpe = false
		// DPE has this behaviour which I can really only call a bug, where inUse values are carried
		// over from other apps and the only way to reset it is to entirely release all instances
		// of the effect in ALL apps in this session ID at the same time. Also, sometimes if the
		// effect is busy the constructor randomly throws because it doesn't support priority well
		// - it always tries to set values even if we don't have control. Amazing work, Google. For
		// the DynamicsProcessing DSP to be the best thing ever, the parameter implementation is so
		// stupid I can't even put it into words.
		try {
			try {
				dpeEffect = DynamicsProcessing(
					-100000, audioSessionId,
					DynamicsProcessing.Config.Builder(
						DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
						1, false, 0,
						false, 0, false,
						0, true
					)
						.setAllChannelsTo(DynamicsProcessing.Channel(0f, false,
							0, false, 0,
							false, 0, true
						).apply {
							mbc = DynamicsProcessing.Mbc(
								false, false, 0
							)
							limiter = DynamicsProcessing.Limiter(
								true, false, 0,
								ReplayGainUtil.TAU_ATTACK * 1000f,
								ReplayGainUtil.TAU_RELEASE * 1000f,
								ReplayGainUtil.RATIO, 0f, 0f
							)
							preEq = DynamicsProcessing.Eq(
								false, false, 0
							)
							postEq = DynamicsProcessing.Eq(
								false, false, 0
							)
						})
						.build()
				)
			} catch (t: Throwable) {
				// DynamicsProcessing does not release() the instance if illegal arguments are
				// passed to the constructor. We have to rely on finalize to avoid conflicts with
				// ourselves, hence do a GC. This API is so broken...
				val policy = StrictMode.getThreadPolicy()
				try {
					StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
					System.gc()
				} finally {
					StrictMode.setThreadPolicy(policy)
				}
				throw t
			}
			dpeEffect!!.setControlStatusListener { effect, hasControl ->
				if (effect != dpeEffect) {
					try {
						effect.release()
					} catch (_: Throwable) {}
					return@setControlStatusListener // stale event
				}
				Log.i(TAG, "dpe control state is now: $hasControl")
				try {
					effect.release()
				} catch (_: Throwable) {}
				dpeEffect = null
				hasDpe = false
				if (hasControl) createDpeEffect()
				else {
					createDpeCanary()
					calculateGain(false) // switch from DPE to setVolume()
				}
			}
			hasDpe = dpeEffect!!.hasControl()
			// wow, we got here. very good.
			if (dpeCanary != null) {
				Log.i(TAG, "release dpe canary because we got real dpe")
				try {
					dpeCanary!!.release()
				} catch (e: Throwable) {
					Log.e(TAG, "failed to release DPE canary", e)
				}
				dpeCanary = null
			}
			Log.i(TAG, "init dpe, control state is: $hasDpe")
		} catch (e: Throwable) {
			if (e is UnsupportedOperationException)
				Log.w(TAG, "failed to init DPE effect: $e")
			else
				Log.e(TAG, "failed to init DPE effect", e)
			try {
				dpeEffect?.release()
			} catch (_: Throwable) {}
			dpeEffect = null
			hasDpe = false
			createDpeCanary()
		}
		calculateGain(hasDpe) // switch from setVolume() to DPE (or don't if we did not succeed)
	}

	@RequiresApi(Build.VERSION_CODES.P)
	private fun createDpeCanary() {
		// The bug where constructor sets parameters (which crashes, and hence we can't register a
		// control listener) can be worked around by using AudioEffect class raw via reflection to
		// check when we regain control. (Set lower prio to avoid conflicting with ourselves.)
		if (dpeCanary == null) {
			try {
				dpeCanary = ReflectionAudioEffect(
					AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING,
					ReflectionAudioEffect.EFFECT_TYPE_NULL, -100001, audioSessionId
				)
				dpeCanary!!.setControlStatusListener { _, controlGranted ->
					Log.i(TAG, "dpe canary control state is now: $controlGranted")
					if (controlGranted) {
						try {
							dpeCanary!!.release()
						} catch (e: Throwable) {
							Log.e(TAG, "failed to release DPE canary", e)
						}
						dpeCanary = null
						createDpeEffect()
					} else {
						Log.e(TAG, "DPE canary control, but why did it ever have it?")
						calculateGain(true)
					}
				}
				Log.i(TAG, "init dpe canary")
				if (dpeCanary!!.hasControl()) {
					Log.w(TAG, "release dpe canary because we suddenly have control")
					try {
						dpeCanary!!.release()
					} catch (e: Throwable) {
						Log.e(TAG, "failed to release DPE canary", e)
					}
					dpeCanary = null
					createDpeEffect()
				}
			} catch (e: Throwable) {
				Log.e(TAG, "failed to init DPE canary", e)
				try {
					dpeCanary?.release()
				} catch (_: Throwable) {}
				dpeCanary = null
				// whatever, I give up.
			}
		}
	}

	private fun updateVolumeEffect(force: Boolean = false) {
		val boostGainDb: Int
		synchronized(rgAp) {
			boostGainDb = rgAp.boostGain
		}
		try {
			try {
				if (hasVolume) volumeEffect!!.enabled = boostGainDb > 0 && deviceType != null && !hasDpe
			} catch (e: IllegalStateException) {
				Log.e(TAG, "volume enable failed", e)
			}
			if (!hasVolume || deviceType == null || hasDpe && !force || boostGainDb <= 0) return
            val boostGainDb = if (hasDpe) 0 else boostGainDb
			var minVolumeDb: Float
			var maxVolumeDb: Float
			var curVolumeDb: Float
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				val minIndex = AudioManagerCompat.getStreamMinVolume(audioManager, C.STREAM_TYPE_MUSIC)
				val maxIndex = AudioManagerCompat.getStreamMaxVolume(audioManager, C.STREAM_TYPE_MUSIC)
				val curIndex = AudioManagerCompat.getStreamVolume(audioManager, C.STREAM_TYPE_MUSIC)
				minVolumeDb =
					max(
						audioManager.getStreamVolumeDb(
							AudioManager.STREAM_MUSIC, minIndex,
							deviceType!!
						), -96f
					)
				maxVolumeDb =
					audioManager.getStreamVolumeDb(
						AudioManager.STREAM_MUSIC, maxIndex,
						deviceType!!
					)
				curVolumeDb =
					max(
						audioManager.getStreamVolumeDb(
							AudioManager.STREAM_MUSIC, curIndex,
							deviceType!!
						), -96f
					)
				if (maxVolumeDb - minVolumeDb == 1f) {
					maxVolumeDb = ReplayGainUtil.amplToDb(maxVolumeDb)
					minVolumeDb = ReplayGainUtil.amplToDb(minVolumeDb)
					curVolumeDb = ReplayGainUtil.amplToDb(curVolumeDb)
				}
			} else {
				// TODO(ASAP) support <28 for boost here
				// - https://developer.android.com/reference/android/media/AudioManager#getStreamVolumeDb(int,%20int,%20int)
				//   - problems: sdk 28 required; I read on SO that samsung broke it and it returns values 0~1
				//   - on O you can use it via AudioSystem C++; on O MR1 you can use it via AudioSystem java reflection
				// - AudioSystem::getStreamVolume() in C++
				//   - it was removed recently but exists everywhere I need it https://cs.android.com/android/_/android/platform/frameworks/av/+/38c45a4438915c73434558be9ffc2d4f73516cf2
				//   - interpretation of data changed in M https://android.googlesource.com/platform/frameworks/av/+/ffbc80f5908eaf67a033c6e93a343c39dd6894eb%5E!/
				minVolumeDb = -96f
				maxVolumeDb = 0f
				curVolumeDb = max(-96f, 0f)
            }
			val theVolume = min(
				volumeEffect!!.maxLevel.toInt().toFloat(),
				(curVolumeDb + ReplayGainUtil.amplToDb(volume) +
						boostGainDb) * 100f
			).toInt().toShort()
			Log.d(TAG, "min=$minVolumeDb max=$maxVolumeDb cur=$curVolumeDb --> $theVolume")
			repeat(20) {
				//volumeEffect!!.level = theVolume
			}
		} catch (e: Throwable) {
			Log.e(TAG, "failed to update volume effect state", e)
		}
	}

	override fun play() {
		updateVolumeEffect()
		super.play()
	}

	override fun pause() {
		updateVolumeEffect()
		super.pause()
	}

	override fun flush() {
		updateVolumeEffect()
		super.flush()
	}

	override fun release() {
		context.unregisterReceiver(receiver)
        try {
            AudioSystemHiddenApi.removeVolumeCallback(context, this)
        } catch (e: Exception) {
            Log.w(TAG, "failed to remove volume cb", e)
        }
		super.release()
	}

	override fun handleBuffer(
		buffer: ByteBuffer,
		presentationTimeUs: Long,
		encodedAccessUnitCount: Int
	): Boolean {
		val prev = sink.isAudioTrackStopped()
		val ret = super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
		if (sink.isAudioTrackStopped() != prev) {
			updateVolumeEffect()
		}
		return ret
	}

	private val audioTrackStoppedField by lazy {
		DefaultAudioSink::class.java.getDeclaredField("stoppedAudioTrack").apply {
			isAccessible = true
		}
	}

	private fun DefaultAudioSink.isAudioTrackStopped(): Boolean {
		return audioTrackStoppedField.get(this) as Boolean
	}

    // To get the real volume of mixer taking into account absolute volume:
    // - 15 QPR0 and earlier: use AudioFlinger.streamVolume() to get volume after any prescale or
    //                        force to max done in java (A2DP/HDMI/LEA/ASHA). Returns dB since M.
    //  also, just on 15 QPR0, getStreamVolumeDb(publicApiIndex) will return real volume (ie 0dB) for
	//  A2DP/LEA/ASHA, but not HDMI. but can't differentiate between 15 QPR0 and 15 QPR1 in public
	//  API so this is not useful fallback for case where private API bypass somehow ends up broken.
    // - 15 QPR1 and later: HDMI can no longer be detected at all, so got to be pessimistic.
    //   - 15 QPR1: have to apply adjustDeviceAttenuationForAbsVolume(), ie force 0dB except if the
    //              index is zero and device is not BLE broadcast, then min volume dB, in app code
    //              based on the result of this function.
    //   - 15 QPR2: getOutputForAttr() returns real volume as amplification, but it's reserved for
    //              AudioFlinger - no luck here. do same as QPR1.
    // Alternatively, to avoid pessimism on 15 QPR1 and later, if Volume is offloadable (or offload
    // is disabled) we can create a stopped mixed track (mustn't be offload to avoid wasting
    // resources) and Volume effect and read Volume.level property. TODO: how well does that actually work?
    // If hidden API is not available, we have to be pessimistic and assume no prescale and apply
    // force max based on result of this function.
	// TODO(ASAP): impl the above
	private fun isAbsoluteVolume(deviceType: Int, isA2dpAbsoluteVolumeOff: Boolean, isHdmiCecVolumeOff: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw IllegalStateException("isAbsoluteVolume($deviceType) before M")
        }
		// LEA having abs vol is a safe assumption, as LEA absolute volume is forced. Same for ASHA.
        return !isA2dpAbsoluteVolumeOff && deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                !isHdmiCecVolumeOff && (deviceType == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
                deviceType == AudioDeviceInfo.TYPE_HDMI ||
                deviceType == AudioDeviceInfo.TYPE_HDMI_ARC) ||
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                deviceType == AudioDeviceInfo.TYPE_HEARING_AID ||
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && deviceType == AudioDeviceInfo.TYPE_BLE_BROADCAST) ||
                        deviceType == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        !isHdmiCecVolumeOff && deviceType == AudioDeviceInfo.TYPE_HDMI_EARC)
	}
}
