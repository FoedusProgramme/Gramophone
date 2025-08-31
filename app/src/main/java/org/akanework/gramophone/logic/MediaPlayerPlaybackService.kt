/*
 *     Copyright (C) 2024 Akane Foundation
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

package org.akanework.gramophone.logic

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.akanework.gramophone.R

/**
 * Basic MediaPlayer-based playback service to replace ExoPlayer
 */
class MediaPlayerPlaybackService : Service(), MediaPlayer.OnPreparedListener,
    MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    companion object {
        private const val TAG = "MediaPlayerService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "playback_channel"
    }

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    
    private var currentUri: Uri? = null
    private var isPlayWhenReady = false

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerPlaybackService = this@MediaPlayerPlaybackService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createMediaPlayer()
    }

    private fun createMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setOnPreparedListener(this@MediaPlayerPlaybackService)
            setOnCompletionListener(this@MediaPlayerPlaybackService)
            setOnErrorListener(this@MediaPlayerPlaybackService)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
        }
    }

    fun setMediaItem(uri: Uri) {
        try {
            currentUri = uri
            mediaPlayer?.apply {
                reset()
                setDataSource(this@MediaPlayerPlaybackService, uri)
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting media item", e)
        }
    }

    fun play() {
        if (requestAudioFocus()) {
            isPlayWhenReady = true
            mediaPlayer?.start()
            updateNotification()
        }
    }

    fun pause() {
        isPlayWhenReady = false
        mediaPlayer?.pause()
        updateNotification()
    }

    fun stop() {
        isPlayWhenReady = false
        mediaPlayer?.stop()
        abandonAudioFocus()
        stopForeground(true)
    }

    fun seekTo(position: Long) {
        mediaPlayer?.seekTo(position.toInt())
    }

    fun getCurrentPosition(): Long {
        return mediaPlayer?.currentPosition?.toLong() ?: 0L
    }

    fun getDuration(): Long {
        return mediaPlayer?.duration?.toLong() ?: 0L
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> stop()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_GAIN -> if (isPlayWhenReady) play()
                    }
                }
                .build()
            
            audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> stop()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_GAIN -> if (isPlayWhenReady) play()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun updateNotification() {
        val notification = createNotification()
        if (isPlaying()) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            stopForeground(false)
        }
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gramophone")
            .setContentText("Playing audio")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(if (isPlaying()) R.drawable.ic_pause else R.drawable.ic_play, 
                      if (isPlaying()) "Pause" else "Play", null)
            .setOngoing(true)
            .build()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        Log.d(TAG, "MediaPlayer prepared")
        if (isPlayWhenReady) {
            play()
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        Log.d(TAG, "MediaPlayer completed")
        // TODO: Handle playlist navigation
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        abandonAudioFocus()
    }
}