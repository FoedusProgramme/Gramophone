package org.nift4.gramophone.hificore

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import androidx.core.content.getSystemService
import androidx.media3.common.audio.AudioManagerCompat
import androidx.media3.common.util.Log
import com.google.common.util.concurrent.MoreExecutors
import java.lang.reflect.Method

/**
 * Some private utility methods from AudioSystem. Sadly, most of AudioSystem calls into
 * permission-restricted APM or AF methods. But a few methods are available.
 *
 * Some methods which are public API in AudioManager, but need to be called via C++ to support
 * hidden/non-public enum members, reside in NativeTrack instead.
 *
 * Methods that seem feasible but are currently not needed, hence not implemented, are:
 * getAAudioHardwareBurstMinUsec, getAAudioMixerBurstCount, getDeviceConnectionState,
 * listAudioProductStrategies, getProductStrategyFromAudioAttributes,
 * addSupportedLatencyModesCallback, addAudioDeviceCallback, removeSupportedLatencyModesCallback,
 * removeAudioDeviceCallback, getParameters/setParameters (vers with io handle), getRenderPosition
 */
object AudioSystemHiddenApi {
    private const val TAG = "AudioSystemHiddenApi"
    private const val TRACE_TAG = "GpNativeTrace2"
    private val libLoaded
        get() = AudioTrackHiddenApi.libLoaded

    // ======= DESCRIPTOR API =======

    // TODO: implement full descriptor API: getDeviceIdsForIo, getFrameCountHAL, getLatency,
    //  getFrameCount, something to enumerate AudioIoDescriptor, get channel mask, get patch,
    //  get is input, add/removeAudioPortCallback (through AudioManager) for detecting IO handle of
    //  AAudio. see AudioSystem::getIoDescriptor

    @SuppressLint("PrivateApi") // only used below U, stable private API
    private fun getAfService(): IBinder? {
        return try {
            Class.forName("android.os.ServiceManager").getMethod(
                "getService", String::class.java
            ).invoke(null, "media.audio_flinger") as IBinder?
        } catch (e: Throwable) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            null
        }
    }

    private fun obtainParcel(binder: IBinder) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Parcel.obtain(binder) else Parcel.obtain()

    private fun readStatus(parcel: Parcel): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val status = parcel.readInt()
        if (status == 0) return true
        Log.e(TAG, "binder transaction failed with status $status")
        return false
    }

    @SuppressLint("PrivateApi") // only Android T, private API stability
    private fun simplifyAudioFormatDescription(out: Parcel): Int? {
        return try {
            Class.forName("android.media.audio.common.AidlConversion").getDeclaredMethod(
                "aidl2legacy_AudioFormatDescription_Parcel_audio_format_t", Parcel::class.java
            ).also {
                it.isAccessible = true
            }.invoke(null, out) as Int
        } catch (e: Throwable) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            null
        }
    }

    fun getFormat(output: Int): UInt? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            TODO("use AudioSystem::getIoDescriptor instead of hardcoded binder numbers")
        val af = getAfService() ?: return null
        val inParcel = obtainParcel(af)
        val outParcel = obtainParcel(af)
        try {
            inParcel.writeInterfaceToken(af.interfaceDescriptor!!)
            inParcel.writeInt(output)
            // IAudioFlingerService.format(audio_io_handle_t)
            Log.d(TRACE_TAG, "trying to call format() via binder")
            try {
                af.transact(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 4 else 5,
                    inParcel, outParcel, 0
                )
            } catch (e: Throwable) {
                Log.e(TAG, Log.getThrowableString(e)!!)
                return null
            }
            Log.d(TRACE_TAG, "done calling format() via binder")
            if (!readStatus(outParcel))
                return null
            // In T, return value changed from legacy audio_format_t to AudioFormatDescription
            // https://cs.android.com/android/_/android/platform/frameworks/av/+/b60bd1b586b74ddf375257c4d07323e271d84ff3
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (outParcel.readInt() != 1 /* kNonNullParcelableFlag */) {
                    Log.e(TAG, "got a null parcelable unexpectedly")
                    return null
                }
                return simplifyAudioFormatDescription(outParcel)?.toUInt()
            } else
                return outParcel.readInt().toUInt()
        } finally {
            inParcel.recycle()
            outParcel.recycle()
        }
    }

    fun getSampleRate(output: Int): UInt? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            TODO("use AudioSystem::getIoDescriptor instead of hardcoded binder numbers")
        val af = getAfService() ?: return null
        val inParcel = obtainParcel(af)
        val outParcel = obtainParcel(af)
        try {
            inParcel.writeInterfaceToken(af.interfaceDescriptor!!)
            inParcel.writeInt(output)
            // IAudioFlingerService.sampleRate(audio_io_handle_t)
            Log.d(TRACE_TAG, "trying to call sampleRate() via binder")
            try {
                af.transact(3, inParcel, outParcel, 0)
            } catch (e: Throwable) {
                Log.e(TAG, Log.getThrowableString(e)!!)
                return null
            }
            Log.d(TRACE_TAG, "done calling format() via binder")
            if (!readStatus(outParcel))
                return null
            return outParcel.readInt().toUInt()
        } finally {
            inParcel.recycle()
            outParcel.recycle()
        }
    }

    // ======= AUDIO PORT / MIX PORT =======

    data class MixPort(val id: Int, val ioHandle: Int, val name: String?, val flags: Int?,
                       val channelMask: Int?, val format: UInt?, val sampleRate: UInt?,
                       val hwModule: Int?, val fast: Boolean?)

    @SuppressLint("PrivateApi") // sorry, not sorry...
    private fun listAudioPorts(): Pair<List<Any>, Int>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return null // while listAudioPorts exists in L, it just returns an error
        val ports = ArrayList<Any?>()
        val generation = IntArray(1)
        try {
            Class.forName("android.media.AudioSystem").getMethod(
                "listAudioPorts", ArrayList::class.java, IntArray::class.java
            ).invoke(null, ports, generation) as Int
        } catch (e: Throwable) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            return null
        }
        if (ports.contains(null))
            Log.e(TAG, "why does listAudioPorts() return a null port?!")
        return ports.filterNotNull() to generation[0]
    }

    private fun getMixPort(port: Any): MixPort {
        val ioHandle = port.javaClass.getMethod("ioHandle").invoke(port) as Int
        val id = port.javaClass.getMethod("id").invoke(port) as Int
        val name = port.javaClass.getMethod("name").invoke(port) as String?
        val mixPortData = getMixPortMetadata(id, ioHandle)
        // flags exposed to app process since below commit which first appeared in T release.
        // https://cs.android.com/android/_/android/platform/frameworks/av/+/99809024b36b243ad162c780c1191bb503a8df47
        // https://cs.android.com/android/_/android/platform/frameworks/av/+/0805de160715e82fcf59f9367a43b96a352abd11
        return MixPort(id, ioHandle, name, flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            mixPortData?.get(3) else null, channelMask = mixPortData?.get(2),
            format = mixPortData?.get(1)?.toUInt(), sampleRate = mixPortData?.get(0)?.toUInt(),
            hwModule = mixPortData?.get(4), fast = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                mixPortData?.let { it[5] == 0 } else null)
    }

    fun getMixPortForThread(oid: Int?): MixPort? {
        if (oid == null)
            return null
        val ports = listAudioPorts()
        if (ports != null)
            for (port in ports.first) {
                try {
                    if (port.javaClass.canonicalName != "android.media.AudioMixPort") continue
                    val ioHandle = port.javaClass.getMethod("ioHandle").invoke(port) as Int
                    if (ioHandle != oid) continue
                    return getMixPort(port)
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getThrowableString(t)!!)
                }
            }
        return null
    }

    fun getPrimaryMixPort(): MixPort? {
        val ports = listAudioPorts()
        if (ports != null)
            for (port in ports.first) {
                try {
                    if (port.javaClass.canonicalName != "android.media.AudioMixPort") continue
                    val mixPort = getMixPort(port)
                    // TODO: support android below T where flags is null
                    if (mixPort.flags != null && (mixPort.flags and 2 /* AUDIO_OUTPUT_FLAG_PRIMARY */) != 0)
                        return mixPort
                } catch (t: Throwable) {
                    Log.e(TAG, Log.getThrowableString(t)!!)
                }
            }
        return null
    }

    private fun getMixPortMetadata(id: Int, io: Int): IntArray? {
        if (!libLoaded)
            return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return null // need listAudioPorts or getAudioPort
        return try {
            Log.d(TRACE_TAG, "calling native findAfFlagsForPortInternal")
            val result = findAfFlagsForPortInternal(id, io)
                .also { Log.d(TRACE_TAG, "native findAfFlagsForPortInternal is done: $it") }
            if (result == null) return null // something went wrong. native layer logged reason to logcat
            return result
        } catch (e: Throwable) {
            Log.e(TAG, Log.getThrowableString(e)!!)
            null
        }
    }
    @Suppress("unused") // for parameters
    private external fun findAfFlagsForPortInternal(id: Int, sr: Int): IntArray?

    // ====== MISC ======

    private val adapterCache = hashMapOf<VolumeChangeListener, Any>()
    private val adapterClazz by lazy {
        Class.forName("org.nift4.audiosysfwd.AudioVolumeGroupCallbackAdapter") }
    private val addAudioVolumeGroupCallbackFn by lazy {
        adapterClazz.getDeclaredMethod("getAdd").invoke(null) as Method
    }
    private val removeAudioVolumeGroupCallbackFn by lazy {
        adapterClazz.getDeclaredMethod("getRemove").invoke(null) as Method
    }
    private val adapterClazzLegacy by lazy {
        Class.forName("org.nift4.audiofxfwd.VolumeGroupCallbackAdapter") }
    private val addVolumeGroupCallbackFn by lazy {
        adapterClazzLegacy.getDeclaredMethod("getAdd").invoke(null) as Method
    }
    private val removeVolumeGroupCallbackFn by lazy {
        adapterClazzLegacy.getDeclaredMethod("getRemove").invoke(null) as Method
    }
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun addVolumeCallback(context: Context, cb: VolumeChangeListener) {
        if (adapterCache.containsKey(cb))
            throw IllegalArgumentException("already registered $cb")
        try {
            if (Build.VERSION.SDK_INT >= 36) {
                val adapter = adapterClazz.getDeclaredConstructor(
                    org.nift4.audiosysfwd.AudioVolumeGroupCallback::class.java
                ).apply { isAccessible = true }
                    .newInstance(org.nift4.audiosysfwd.AudioVolumeGroupCallback {
                        // other two fields seem to be always unset
                        cb.onVolumeChanged(it.groupId, it.flags)
                    })
                adapterCache[cb] = adapter
                val ret = addAudioVolumeGroupCallbackFn.invoke(null, adapter) as Int
                if (ret != 0) {
                    throw IllegalArgumentException("registerAudioVolumeGroupCallback: $ret")
                }
            } else {
                val audioManager = context.getSystemService<AudioManager>()
                val adapter = adapterClazzLegacy.getDeclaredConstructor(
                    org.nift4.audiofxfwd.VolumeGroupCallback::class.java
                ).apply { isAccessible = true }
                    .newInstance(org.nift4.audiofxfwd.VolumeGroupCallback { a, b ->
                        cb.onVolumeChanged(a, b)
                    })
                adapterCache[cb] = adapter
                addVolumeGroupCallbackFn.invoke(audioManager, MoreExecutors.directExecutor(), adapter)
            }
        } catch (t: Throwable) {
            throw IllegalStateException("failed to add vol cb", t)
        }
    }
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun removeVolumeCallback(context: Context, cb: VolumeChangeListener) {
        val adapter = adapterCache[cb] ?: throw IllegalArgumentException("never registered $cb")
        try {
            if (Build.VERSION.SDK_INT >= 36) {
                removeAudioVolumeGroupCallbackFn.invoke(null, adapter)
            } else {
                val audioManager = context.getSystemService<AudioManager>()
                removeVolumeGroupCallbackFn.invoke(audioManager, adapter)
            }
        } catch (t: Throwable) {
            throw IllegalStateException("failed to add vol cb", t)
        }
    }
    fun interface VolumeChangeListener {
        fun onVolumeChanged(groupId: Int, flags: Int)
    }

    // TODO: getMasterVolume, getMasterBalance for PostAmpAudioSink headroom calculation
    // TODO: addErrorCallback, removeErrorCallback for audioflinger crash detect out of curiosity
    // TODO: getMasterMono for offload detect
}