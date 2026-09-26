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

package org.akanework.gramophone.logic.utils

import android.media.MediaRouter
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.media3.common.util.Log

object MediaRoutes {
    private const val TAG = "MediaRoutes"
    private val addressRegex = Regex(".*, address=(.*), deduplicationIds=.*")

    fun getSelectedAudioDevice(context: Context): AudioDeviceInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val router = MediaRouter2.getInstance(context)
            val route = router.systemController.selectedRoutes.firstOrNull()
            route?.getAudioDeviceForRoute(context)
        } else {
            // Before R, read the platform MediaRouter directly, as androidx does. Its selected
            // live audio route is the default route or, when connected, Bluetooth.
            val router = ContextCompat.getSystemService(context, MediaRouter::class.java)!!
            router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
                .getAudioDeviceForRoute(context, router)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("DiscouragedApi")
    fun MediaRoute2Info.getAudioDeviceForRoute(context: Context): AudioDeviceInfo? {
        if (!isSystemRoute) {
            Log.e(
                TAG,
                "Route is not flagged as system route, however Gramophone only supports system routes?"
            )
            return null
        }
        val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // MediaRoute2Info started getting both type and address in Android 14.
            val address = addressRegex.matchEntire(toString())?.groupValues?.getOrNull(1)
            if (address == null && Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            // was added in U QPR, so expected to not work in some U devices
                Log.e(TAG, "trying to find audio device by type: failed to parse address in $this")
            // These proper mapping are established since Android 16 (but U/V ones are usable).
            return when (type) {
                MediaRoute2Info.TYPE_BLUETOOTH_A2DP ->
                    audioManager.firstOutputDeviceByType(
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        name = name, address = address
                    )

                MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                )

                MediaRoute2Info.TYPE_WIRED_HEADSET -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET
                )

                MediaRoute2Info.TYPE_WIRED_HEADPHONES -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                )

                MediaRoute2Info.TYPE_HDMI -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                        audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_HDMI)
                    else
                        audioManager.firstOutputDeviceByType(
                            AudioDeviceInfo.TYPE_HDMI,
                            AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC
                        )
                }

                MediaRoute2Info.TYPE_USB_DEVICE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                        audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_USB_DEVICE)
                    else
                        audioManager.firstOutputDeviceByType(
                            AudioDeviceInfo.TYPE_USB_DEVICE,
                            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY
                        )
                }

                MediaRoute2Info.TYPE_USB_ACCESSORY -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_USB_ACCESSORY
                )

                MediaRoute2Info.TYPE_DOCK -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_DOCK,
                    AudioDeviceInfo.TYPE_DOCK_ANALOG
                )

                MediaRoute2Info.TYPE_USB_HEADSET -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_USB_HEADSET
                )

                MediaRoute2Info.TYPE_HEARING_AID -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_HEARING_AID
                )

                MediaRoute2Info.TYPE_BLE_HEADSET ->
                    audioManager.firstOutputDeviceByType(
                        AudioDeviceInfo.TYPE_BLE_HEADSET,
                        AudioDeviceInfo.TYPE_BLE_SPEAKER,
                        AudioDeviceInfo.TYPE_BLE_BROADCAST,
                        name = name,
                        address = address
                    )

                MediaRoute2Info.TYPE_HDMI_ARC -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_HDMI_ARC
                )

                MediaRoute2Info.TYPE_HDMI_EARC -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_HDMI_EARC
                )

                MediaRoute2Info.TYPE_LINE_DIGITAL -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_LINE_DIGITAL
                )

                MediaRoute2Info.TYPE_LINE_ANALOG -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_LINE_ANALOG
                )

                MediaRoute2Info.TYPE_AUX_LINE -> audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_AUX_LINE
                )

                @SuppressLint("SwitchIntDef") // AOSP forgot to add it to switch def...
                MediaRoute2Info.TYPE_MULTICHANNEL_SPEAKER_GROUP ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_MULTICHANNEL_GROUP)
                    } else null

                MediaRoute2Info.TYPE_UNKNOWN ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) null else
                        audioManager.firstOutputDeviceByType(
                            AudioDeviceInfo.TYPE_LINE_DIGITAL,
                            AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_AUX_LINE
                        )

                else -> {
                    Log.e(TAG, "Route type $type is not mapped to audio device type?")
                    null
                }
            }
            // We shouldn't fall through, below code path no longer works on U+.
        }
        // TODO: can we get type from hidden API instead and use above code path?
        try {
            if (TextUtils.equals(
                    Resources.getSystem().getText(
                        Resources.getSystem()
                            .getIdentifier("bluetooth_a2dp_audio_route_name", "string", "android")
                    ), description
                )
            )
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    audioManager.firstOutputDeviceByType(
                        AudioDeviceInfo.TYPE_HEARING_AID,
                        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
                        AudioDeviceInfo.TYPE_BLE_BROADCAST, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    )
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    audioManager.firstOutputDeviceByType(
                        AudioDeviceInfo.TYPE_HEARING_AID,
                        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    )
                else
                    audioManager.firstOutputDeviceByType(
                        AudioDeviceInfo.TYPE_HEARING_AID,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    )
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is bluetooth", t)
        }
        try {
            if (TextUtils.equals(
                    Resources.getSystem().getText(
                        Resources.getSystem()
                            .getIdentifier("default_audio_route_name_hdmi", "string", "android")
                    ), name
                )
            )
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    audioManager.firstOutputDeviceByType(
                        AudioDeviceInfo.TYPE_HDMI,
                        AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC
                    )
                else
                    audioManager.firstOutputDeviceByType(
                        AudioDeviceInfo.TYPE_HDMI,
                        AudioDeviceInfo.TYPE_HDMI_ARC
                    )
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is hdmi", t)
        }
        try {
            if (TextUtils.equals(
                    Resources.getSystem().getText(
                        Resources.getSystem()
                            .getIdentifier("default_audio_route_name_usb", "string", "android")
                    ), name
                )
            )
                return audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET
                )
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is usb", t)
        }
        try {
            if (TextUtils.equals(
                    Resources.getSystem().getText(
                        Resources.getSystem()
                            .getIdentifier(
                                "default_audio_route_name_headphones",
                                "string",
                                "android"
                            )
                    ), name
                )
            )
                return audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG
                )
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is wired headphone", t)
        }
        try {
            if (TextUtils.equals(
                    Resources.getSystem().getText(
                        Resources.getSystem()
                            .getIdentifier(
                                "default_audio_route_name_dock_speakers",
                                "string",
                                "android"
                            )
                    ), name
                )
            )
                return audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_DOCK)
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is dock", t)
        }
        try {
            if (TextUtils.equals(
                    Resources.getSystem().getText(
                        Resources.getSystem()
                            .getIdentifier("default_audio_route_name", "string", "android")
                    ), name
                )
            )
            // TODO: It seems like speaker is the default fallback for anything. So filtering
            //  for speaker will not work well.
            // https://cs.android.com/android/platform/superproject/+/android10-release:frameworks/base/services/core/java/com/android/server/audio/AudioDeviceInventory.java;l=863;drc=f7345252b8b33fe7cf69622f55e4226b6ef0100d
                return audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is speaker", t)
        }
        Log.e(TAG, "failed to detect default route by name, this should never happen")
        return null
    }

    // Approximation of audio device based on best effort
    // Inspired by https://github.com/timschneeb/RootlessJamesDSP/blob/593c0dc/app/src/main/java/me/timschneeberger/rootlessjamesdsp/utils/RoutingObserver.kt
    @SuppressLint("DiscouragedApi")
    private fun MediaRouter.RouteInfo.getAudioDeviceForRoute(
        context: Context,
        router: MediaRouter,
    ): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            throw IllegalStateException("getAudioDeviceForRoute must not be called on R+")
        // Same checks androidx's RouteInfo derives from the platform route.
        val name = getName(context)
        // System routes (the default route and Bluetooth) share the system category.
        val isSystemRoute = category == router.defaultRoute.category
        val isDefault = this == router.defaultRoute
        // The only other system live audio route is Bluetooth. Remote display routes are video,
        // and wired, USB and HDMI outputs replace the default route instead.
        val isBluetooth = isSystemRoute && !isDefault &&
                supportedTypes and MediaRouter.ROUTE_TYPE_LIVE_AUDIO != 0
        val isDeviceSpeaker = isDefault && TextUtils.equals(
            Resources.getSystem().getText(
                Resources.getSystem().getIdentifier("default_audio_route_name", "string", "android")
            ), name
        )
        if (!isSystemRoute) { // MediaRouteProviderService, but shouldn't get selected by itself
            Log.e(
                TAG,
                "Route is not flagged as system route, however Gramophone only supports system routes?"
            )
            return null
        }
        val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)!!
        if (isBluetooth) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_HEARING_AID,
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, name = name
                )
            else
                audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    name = name
                )
        }
        if (!isDefault) {
            Log.e(
                TAG,
                "Non-default non-bluetooth system route selected, but remote display routes are hidden. $this cannot exist."
            )
            return null
        }
        try {
            if (TextUtils.equals(
                    Resources.getSystem().getText(
                        Resources.getSystem().getIdentifier(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                                "default_audio_route_name_hdmi" else
                                    "default_media_route_name_hdmi", "string", "android")
                    ), name
                )
            )
                return audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_HDMI,
                    AudioDeviceInfo.TYPE_HDMI_ARC
                )
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is hdmi", t)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                TextUtils.equals(
                    Resources.getSystem().getText(
                        Resources.getSystem()
                            .getIdentifier("default_audio_route_name_usb", "string", "android")
                    ), name
                )
            )
                return audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET
                )
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is usb", t)
        }
        try {
            if (TextUtils.equals(
                    Resources.getSystem().getText(
                        Resources.getSystem()
                            .getIdentifier(
                                "default_audio_route_name_headphones",
                                "string",
                                "android"
                            )
                    ), name
                )
            )
                return audioManager.firstOutputDeviceByType(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_LINE_ANALOG
                )
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is wired headphone", t)
        }
        try {
            if (isDeviceSpeaker)
            // TODO: It seems like speaker is the default fallback for anything. So filtering
            //  for speaker will not work well.
            // https://cs.android.com/android/platform/superproject/+/android10-release:frameworks/base/services/core/java/com/android/server/audio/AudioDeviceInventory.java;l=863;drc=f7345252b8b33fe7cf69622f55e4226b6ef0100d
                return audioManager.firstOutputDeviceByType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        } catch (t: Resources.NotFoundException) {
            Log.w(TAG, "Failed to check if $this is speaker", t)
        }
        Log.e(TAG, "failed to detect default route by name, this should never happen")
        return null
    }

    private fun AudioManager.firstOutputDeviceByType(
        vararg type: Int,
        name: CharSequence? = null,
        address: CharSequence? = null
    ): AudioDeviceInfo? {
        val devices = getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val devicesByType = devices.filter { type.contains(it.type) }
        if (devicesByType.isEmpty()) {
            Log.w(TAG, "firstOutputDeviceByType() returning empty result")
            return null
        }
        if (devicesByType.size == 1)
            return devicesByType.first()
        if (name == null && address == null)
            return devicesByType.find { type[0] == it.type } ?: devicesByType.first()
        val devicesByName = (if (address != null && address != "null" &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ) {
            val devicesByAddress = devicesByType.filter {
                it.address == address
                        // BT MAC addresses are censored by AudioManager but not MediaRouter2
                        || (it.address.startsWith("XX:XX:XX:XX:")
                        && it.address.substring(12) == address.substring(12))
            }
            if (devicesByAddress.size == 1)
                return devicesByAddress[0]
            else if (devicesByAddress.size > 1) {
                val devicesByAddressAndName = devicesByAddress.filter {
                    it.productName.contentEquals(name)
                            || it.cleanUpProductName().contentEquals(name)
                }
                if (devicesByAddressAndName.size == 1)
                    return devicesByAddressAndName[0]
                else devicesByAddressAndName.ifEmpty { null }
            } else null
        } else null) ?: devicesByType.filter {
            it.productName.contentEquals(name)
                    || it.cleanUpProductName().contentEquals(name)
        }
        if (devicesByName.size == 1)
            return devicesByName[0]
        if (devicesByName.size > 1) {
            // Same model of USB connected twice?
            val theDevice = devicesByName[0]
            devicesByName.forEachIndexed { index, device ->
                if (!((Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            theDevice.audioProfiles == device.audioProfiles) &&
                            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                                    (theDevice.encapsulationModes.contentEquals(device.encapsulationModes) &&
                                            theDevice.encapsulationMetadataTypes.contentEquals(
                                                device.encapsulationMetadataTypes
                                            ))) &&
                            theDevice.channelCounts.contentEquals(device.channelCounts) &&
                            theDevice.encodings.contentEquals(device.encodings) &&
                            theDevice.sampleRates.contentEquals(device.sampleRates) &&
                            theDevice.channelMasks.contentEquals(device.channelMasks) &&
                            theDevice.channelIndexMasks.contentEquals(device.channelIndexMasks) &&
                            theDevice.type == device.type)
                ) {
                    Log.e(
                        TAG, "Weird, got more than one AudioDeviceInfo with same name but not " +
                                "same content?"
                    )
                    return null
                }
            }
            // Ok great, while we did find >1 devices, they all have same capabilities so it
            // (probably) doesn't matter which one we look at.
            return theDevice
        }
        Log.e(
            TAG, "Weird, got more than one AudioDeviceInfo for type but none match desired " +
                    "name? desired name: $name"
        )
        devicesByType.forEach {
            Log.e(
                TAG, "This device does not match desired name $name because it's actually " +
                        it.productName
            )
        }
        return devicesByType[0]
    }

    private fun AudioDeviceInfo.cleanUpProductName(): String = productName.let {
        if (it.startsWith("USB-Audio - "))
            it.substring("USB-Audio - ".length)
        else it.toString()
    }
}