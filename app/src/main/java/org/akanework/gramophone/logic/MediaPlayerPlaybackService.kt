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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity

/**
 * Enhanced MediaPlayer-based playback service to replace ExoPlayer
 * Provides core functionality while maintaining compatibility with UI components
 */
class MediaPlayerPlaybackService : Service(), MediaPlayer.OnPreparedListener,
    MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    companion object {
        private const val TAG = "MediaPlayerService"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "playback_channel"
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_NEXT = "action_next"
        
        // Service commands for compatibility
        const val SERVICE_GET_AUDIO_FORMAT = "get_audio_format"
        const val SERVICE_GET_LYRICS = "get_lyrics"
        const val SERVICE_GET_SESSION = "get_session"
        const val SERVICE_TIMER_CHANGED = "changed_timer"
        
        var instanceForWidgetAndLyricsOnly: MediaPlayerPlaybackService? = null
    }

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaSession: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    
    private var currentUri: Uri? = null
    private var isPlayWhenReady = false
    private var currentPosition = 0L
    private var playlist = mutableListOf<Uri>()
    private var currentIndex = 0
    private var shuffleMode = false
    private var repeatMode = 0 // 0 = none, 1 = all, 2 = one
    
    private val handler = Handler(Looper.getMainLooper())
    private var progressCallback: ((Long, Long) -> Unit)? = null

    
    private val mediaButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_PREVIOUS -> skipToPrevious()
                ACTION_NEXT -> skipToNext()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlayerPlaybackService = this@MediaPlayerPlaybackService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        instanceForWidgetAndLyricsOnly = this
        
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        setupMediaSession()
        createMediaPlayer()
        
        // Register media button receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_NEXT)
        }
        registerReceiver(mediaButtonReceiver, filter)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun setupMediaSession() {
        mediaSession = MediaSession(this, TAG).apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = play()
                override fun onPause() = pause()
                override fun onSkipToNext() = skipToNext()
                override fun onSkipToPrevious() = skipToPrevious()
                override fun onSeekTo(pos: Long) = seekTo(pos)
                override fun onStop() = stop()
            })
            isActive = true
        }
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

    // Playlist management methods
    fun setPlaylist(uris: List<Uri>, startIndex: Int = 0) {
        playlist.clear()
        playlist.addAll(uris)
        currentIndex = startIndex.coerceIn(0, playlist.size - 1)
        if (playlist.isNotEmpty()) {
            setMediaItem(playlist[currentIndex])
        }
    }
    
    fun addToPlaylist(uri: Uri) {
        playlist.add(uri)
    }
    
    fun removeFromPlaylist(index: Int) {
        if (index in 0 until playlist.size) {
            playlist.removeAt(index)
            if (index <= currentIndex && currentIndex > 0) {
                currentIndex--
            }
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
            updateMediaSessionState()
            updateNotification()
            startProgressUpdates()
        }
    }

    fun pause() {
        isPlayWhenReady = false
        mediaPlayer?.pause()
        updateMediaSessionState()
        updateNotification()
        stopProgressUpdates()
    }

    fun stop() {
        isPlayWhenReady = false
        mediaPlayer?.stop()
        updateMediaSessionState()
        abandonAudioFocus()
        stopForeground(true)
        stopProgressUpdates()
    }
    
    fun skipToNext() {
        if (playlist.isEmpty()) return
        
        currentIndex = when {
            repeatMode == 2 -> currentIndex // repeat one
            shuffleMode -> playlist.indices.random()
            currentIndex < playlist.size - 1 -> currentIndex + 1
            repeatMode == 1 -> 0 // repeat all
            else -> return // no repeat, at end
        }
        
        setMediaItem(playlist[currentIndex])
    }
    
    fun skipToPrevious() {
        if (playlist.isEmpty()) return
        
        currentIndex = when {
            repeatMode == 2 -> currentIndex // repeat one
            shuffleMode -> playlist.indices.random()
            currentIndex > 0 -> currentIndex - 1
            repeatMode == 1 -> playlist.size - 1 // repeat all
            else -> 0 // stay at beginning
        }
        
        setMediaItem(playlist[currentIndex])
    }
    
    fun setShuffleMode(enabled: Boolean) {
        shuffleMode = enabled
        updateMediaSessionState()
    }
    
    fun setRepeatMode(mode: Int) {
        repeatMode = mode.coerceIn(0, 2)
        updateMediaSessionState()
    }

    fun seekTo(position: Long) {
        mediaPlayer?.seekTo(position.toInt())
        currentPosition = position
    }

    fun getCurrentPosition(): Long {
        return mediaPlayer?.currentPosition?.toLong() ?: currentPosition
    }

    fun getDuration(): Long {
        return mediaPlayer?.duration?.toLong() ?: 0L
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
    
    fun getCurrentMediaUri(): Uri? = currentUri
    
    fun getPlaylist(): List<Uri> = playlist.toList()
    
    fun getCurrentIndex(): Int = currentIndex
    
    fun isShuffleModeEnabled(): Boolean = shuffleMode
    
    fun getRepeatMode(): Int = repeatMode
    
    // Progress tracking
    fun setProgressCallback(callback: (Long, Long) -> Unit) {
        progressCallback = callback
    }
    
    private fun startProgressUpdates() {
        stopProgressUpdates()
        handler.post(progressRunnable)
    }
    
    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
    }
    
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying()) {
                val position = getCurrentPosition()
                val duration = getDuration()
                progressCallback?.invoke(position, duration)
                handler.postDelayed(this, 1000) // Update every second
            }
        }
    }
    
    private fun updateMediaSessionState() {
        val state = if (isPlaying()) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or 
                     PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                     PlaybackState.ACTION_SEEK_TO
                     
        val playbackState = PlaybackState.Builder()
            .setState(state, getCurrentPosition(), if (isPlaying()) 1.0f else 0.0f)
            .setActions(actions)
            .build()
            
        mediaSession?.setPlaybackState(playbackState)
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
                .setOnAudioFocusChangeListener(this)
                .build()
            
            audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                this,
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
            audioManager?.abandonAudioFocus(this)
        }
    }
    
    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_GAIN -> if (isPlayWhenReady) play()
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
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying()) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "Pause",
                PendingIntent.getBroadcast(
                    this, 0,
                    Intent(ACTION_PAUSE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "Play", 
                PendingIntent.getBroadcast(
                    this, 0,
                    Intent(ACTION_PLAY),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        val previousAction = NotificationCompat.Action(
            R.drawable.ic_skip_previous,
            "Previous",
            PendingIntent.getBroadcast(
                this, 0,
                Intent(ACTION_PREVIOUS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val nextAction = NotificationCompat.Action(
            R.drawable.ic_skip_next,
            "Next",
            PendingIntent.getBroadcast(
                this, 0,
                Intent(ACTION_NEXT),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gramophone")
            .setContentText("Playing audio")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(mediaSession?.sessionToken))
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        Log.d(TAG, "MediaPlayer prepared")
        updateMediaSessionState()
        if (isPlayWhenReady) {
            play()
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        Log.d(TAG, "MediaPlayer completed")
        if (playlist.isNotEmpty()) {
            skipToNext()
        } else {
            stop()
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
        // Try to skip to next track on error
        if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
            skipToNext()
        } else {
            stop()
        }
        return true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle media button events and other intents
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceForWidgetAndLyricsOnly = null
        
        stopProgressUpdates()
        unregisterReceiver(mediaButtonReceiver)
        
        mediaPlayer?.release()
        mediaPlayer = null
        
        mediaSession?.release()
        mediaSession = null
        
        abandonAudioFocus()
    }
    
    // Compatibility methods for existing UI components
    fun sendCustomCommand(command: String, args: Bundle? = null) {
        when (command) {
            SERVICE_GET_AUDIO_FORMAT -> {
                // Return basic audio format info
                val result = Bundle().apply {
                    putString("format", "MediaPlayer")
                    putInt("sampleRate", 44100) // Default value
                    putInt("bitDepth", 16) // Default value
                }
                // Send broadcast for compatibility
                sendBroadcast(Intent(SERVICE_GET_AUDIO_FORMAT).putExtras(result))
            }
            SERVICE_GET_LYRICS -> {
                // Send empty lyrics for compatibility
                sendBroadcast(Intent(SERVICE_GET_LYRICS))
            }
            SERVICE_GET_SESSION -> {
                // Send session info
                val result = Bundle().apply {
                    putString("sessionToken", mediaSession?.sessionToken?.toString())
                }
                sendBroadcast(Intent(SERVICE_GET_SESSION).putExtras(result))
            }
        }
    }
}