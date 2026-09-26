/*
 *     Copyright (C) 2025 Akane Foundation
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

package org.akanework.gramophone.ui.components

import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.defaultPrefs
import org.akanework.gramophone.logic.getAudioFormat
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.getLyrics
import org.akanework.gramophone.logic.utils.AudioFormatDetector
import org.akanework.gramophone.logic.utils.AudioFormatDetector.AudioFormatInfo
import org.akanework.gramophone.logic.utils.AudioFormatDetector.AudioQuality
import org.akanework.gramophone.logic.utils.AudioFormatDetector.SpatialFormat
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.nav.AlbumKey
import org.akanework.gramophone.ui.nav.ArtistKey
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId

// Temp class for view integration, should be deleted later
// after integration finishes
class NowPlayingController(
    private val activity: MainActivity,
    private val updateLyrics: (SemanticLyrics?) -> Unit,
    private val minimize: () -> Unit,
    private val onQualityChanged: (iconRes: Int?, text: String?) -> Unit,
    private val openQueue: () -> Unit,
    private val closeQueue: () -> Unit,
) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val instance get() = activity.getPlayer()
    private val prefs = activity.defaultPrefs
    private val handler = Handler(Looper.getMainLooper())
    private var enableQualityInfo = prefs.getBooleanStrict("audio_quality_info", false)
    private var currentFormat: AudioFormatDetector.AudioFormats? = null
    private var lastQualityInfo: AudioFormatInfo? = null

    private val formatUpdateRunnable = Runnable {
        pushQuality(if (enableQualityInfo) AudioFormatDetector.detectAudioFormat(currentFormat) else null)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        activity.controllerViewModel.customCommandListeners.addCallback(activity.lifecycle) { _, command, _ ->
            when (command.customAction) {
                GramophonePlaybackService.SERVICE_TIMER_CHANGED -> { /* timer state is polled */ }

                GramophonePlaybackService.SERVICE_GET_LYRICS ->
                    updateLyrics(instance?.getLyrics())

                GramophonePlaybackService.SERVICE_GET_AUDIO_FORMAT -> {
                    currentFormat = instance?.getAudioFormat()
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                        !handler.hasCallbacks(formatUpdateRunnable)
                    ) {
                        handler.postDelayed(formatUpdateRunnable, 300)
                    }
                }

                else -> return@addCallback Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                )
            }
            Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    fun refreshLyrics() = updateLyrics(instance?.getLyrics())

    fun onStop() {
        closeQueue()
    }

    fun release() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        handler.removeCallbacks(formatUpdateRunnable)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == null || key == "audio_quality_info") {
            enableQualityInfo = prefs.getBooleanStrict("audio_quality_info", false)
            pushQuality(if (enableQualityInfo) AudioFormatDetector.detectAudioFormat(currentFormat) else null)
        }
    }

    private fun pushQuality(info: AudioFormatInfo?) {
        if (info == lastQualityInfo) return
        lastQualityInfo = info
        if (info == null) {
            onQualityChanged(null, null)
            return
        }
        onQualityChanged(qualityIcon(info), qualityText(info))
    }

    private fun qualityIcon(info: AudioFormatInfo): Int? = when (info.spatialFormat) {
        SpatialFormat.SURROUND_5_0, SpatialFormat.SURROUND_5_1,
        SpatialFormat.SURROUND_6_1, SpatialFormat.SURROUND_7_1 -> R.drawable.ic_surround_sound

        SpatialFormat.DOLBY_AC3, SpatialFormat.DOLBY_EAC3, SpatialFormat.DOLBY_EAC3_JOC,
        SpatialFormat.DOLBY_AC4, SpatialFormat.DOLBY_TRUEHD -> R.drawable.ic_dolby

        SpatialFormat.DTS, SpatialFormat.DTS_EXPRESS,
        SpatialFormat.DTS_HD, SpatialFormat.DTS_UHD -> R.drawable.ic_dts

        else -> when (info.quality) {
            AudioQuality.HIRES -> R.drawable.ic_high_res
            AudioQuality.HD -> R.drawable.ic_hd
            AudioQuality.CD -> R.drawable.ic_cd
            AudioQuality.HQ -> R.drawable.ic_hq
            AudioQuality.LOSSY -> R.drawable.ic_lossy
            else -> null
        }
    }

    private fun qualityText(info: AudioFormatInfo): String = buildString {
        var hadFirst = false
        info.bitDepth?.let { hadFirst = true; append("${it}bit") }
        info.sampleRate?.let {
            if (hadFirst) append(" / ") else hadFirst = true
            append("${it / 1000f}kHz")
        }
        info.sourceChannels?.let {
            if (hadFirst) append(" / ") else hadFirst = true
            append("${it}ch")
        }
        info.bitrate?.let {
            if (hadFirst) append(" / ")
            append("${it / 1000}kbps")
        }
    }

    fun openAlbumPage() {
        minimize()
        activity.navigateTo(AlbumKey(instance?.currentMediaItem?.mediaMetadata?.albumId))
    }

    fun openArtistPage() {
        minimize()
        activity.navigateTo(ArtistKey(instance?.currentMediaItem?.mediaMetadata?.artistId, false))
    }

    fun showQueue() {
        if (instance != null) openQueue()
    }
}
