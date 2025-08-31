package org.akanework.gramophone.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.hasScopedStorageV1
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import org.akanework.gramophone.ui.components.SquigglyProgress
import uk.akane.libphonograph.toUriCompat
import java.io.File

private const val TAG = "AudioPreviewActivity"

class AudioPreviewActivity : AppCompatActivity(), View.OnClickListener {

    companion object {
        private const val PERMISSION_READ_MEDIA_AUDIO = 100
    }

    private lateinit var d: AlertDialog
    private lateinit var player: MediaPlayer
    private lateinit var audioTitle: TextView
    private lateinit var artistTextView: TextView
    private lateinit var currentPositionTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var albumArt: ImageView
    private lateinit var timeSlider: Slider
    private lateinit var timeSeekbar: SeekBar
    private lateinit var playPauseButton: MaterialButton
    private lateinit var progressDrawable: SquigglyProgress
    private lateinit var openIcon: ImageView
    private lateinit var openText: TextView
    private lateinit var prefs: SharedPreferences

    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private val handler = Handler(Looper.getMainLooper())
    private var runnableRunning = false
    private var isUserTracking = false
    private var askedForPermissionInSettings = false
    
    private val updateSliderRunnable = object : Runnable {
        override fun run() {
            val duration = player.duration.takeIf { it > 0 }?.toLong()
            if (duration != null) {
                timeSlider.valueTo = duration.toFloat().coerceAtLeast(1f)
                timeSeekbar.max = duration.toInt()
                durationTextView.text = convertDurationToTimeStamp(duration)
            }
            val currentPosition = player.currentPosition.toFloat().coerceAtMost(timeSlider.valueTo)
                .coerceAtLeast(timeSlider.valueFrom)
            if (!isUserTracking) {
                timeSlider.value = currentPosition
                timeSeekbar.progress = currentPosition.toInt()
                currentPositionTextView.text = convertDurationToTimeStamp(currentPosition.toLong())
            }
            if (runnableRunning) handler.postDelayed(this, 100)
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "default_progress_bar" -> updateSliderVisibility()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        d = MaterialAlertDialogBuilder(this)
            .setView(R.layout.activity_audio_preview)
            .setOnDismissListener {
                runnableRunning = false
                player.release()
                handler.postDelayed(this::finish, 200)
            }
            .show()
        
        initializeViews()
        setupPlayer()
        loadAudioFile()
    }

    private fun initializeViews() {
        audioTitle = d.findViewById(R.id.title_text_view)!!
        artistTextView = d.findViewById(R.id.artist_text_view)!!
        currentPositionTextView = d.findViewById(R.id.current_position_text_view)!!
        durationTextView = d.findViewById(R.id.duration_text_view)!!
        albumArt = d.findViewById(R.id.album_art)!!
        timeSlider = d.findViewById(R.id.time_slider)!!
        timeSeekbar = d.findViewById(R.id.slider_squiggly)!!
        playPauseButton = d.findViewById(R.id.play_pause_replay_button)!!
        openIcon = d.findViewById(R.id.open_icon)!!
        openText = d.findViewById(R.id.open_text_view)!!
        progressDrawable = timeSeekbar.progressDrawable as SquigglyProgress

        updateSliderVisibility()
        
        playPauseButton.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.start()
            }
            updatePlayPauseButton()
        }

        timeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                player.seekTo(value.toInt())
                currentPositionTextView.text = convertDurationToTimeStamp(value.toLong())
            }
        }

        timeSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentPositionTextView.text = convertDurationToTimeStamp(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserTracking = true
                progressDrawable.animate = false
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    player.seekTo(it.progress)
                    isUserTracking = false
                    progressDrawable.animate = true
                }
            }
        })
    }

    private fun setupPlayer() {
        player = MediaPlayer()
        player.setOnPreparedListener {
            updatePlayPauseButton()
            runnableRunning = true
            handler.post(updateSliderRunnable)
        }
        player.setOnCompletionListener {
            updatePlayPauseButton()
        }
        player.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
            Toast.makeText(this, R.string.cannot_play_file, Toast.LENGTH_LONG).show()
            finish()
            true
        }
    }

    private fun loadAudioFile() {
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        if (!hasAudioPermission(this)) {
            if (!askedForPermissionInSettings) {
                requestPermissions()
            } else {
                finish()
            }
            return
        }

        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    player.setDataSource(this@AudioPreviewActivity, uri)
                    player.prepareAsync()
                }
                
                // Load metadata
                val audioInfo = getAudioInfo(uri)
                withContext(Dispatchers.Main) {
                    audioTitle.text = audioInfo.title ?: "Unknown"
                    artistTextView.text = audioInfo.artist ?: "Unknown Artist"
                    
                    // Load album art if available
                    audioInfo.albumArt?.let { artPath ->
                        val bitmap = BitmapFactory.decodeFile(artPath)
                        albumArt.setImageBitmap(bitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading audio file", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AudioPreviewActivity, R.string.cannot_play_file, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun getAudioInfo(uri: Uri): AudioInfo {
        val cursor = contentResolver.query(
            uri,
            arrayOf(
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM_ID
            ),
            null, null, null
        )
        
        cursor?.use {
            if (it.moveToFirst()) {
                val title = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val artist = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                return AudioInfo(title, artist, null)
            }
        }
        
        return AudioInfo(null, null, null)
    }

    private fun updateSliderVisibility() {
        val useDefaultProgressBar = prefs.getBooleanStrict("default_progress_bar", false)
        if (useDefaultProgressBar) {
            timeSlider.visibility = View.VISIBLE
            timeSeekbar.visibility = View.GONE
        } else {
            timeSlider.visibility = View.GONE
            timeSeekbar.visibility = View.VISIBLE
        }
    }

    private fun updatePlayPauseButton() {
        if (player.isPlaying) {
            playPauseButton.icon = AppCompatResources.getDrawable(this, R.drawable.ic_pause)
        } else {
            playPauseButton.icon = AppCompatResources.getDrawable(this, R.drawable.ic_play)
        }
    }

    private fun requestPermissions() {
        when {
            hasScopedStorageV2(this) -> {
                requestPermissions(arrayOf("android.permission.READ_MEDIA_AUDIO"), PERMISSION_READ_MEDIA_AUDIO)
            }
            hasScopedStorageV1(this) -> {
                requestPermissions(arrayOf("android.permission.READ_EXTERNAL_STORAGE"), PERMISSION_READ_MEDIA_AUDIO)
            }
            else -> {
                requestPermissions(arrayOf("android.permission.READ_EXTERNAL_STORAGE"), PERMISSION_READ_MEDIA_AUDIO)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_READ_MEDIA_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAudioFile()
            } else {
                finish()
            }
        }
    }

    override fun onClick(v: View?) {
        // TODO: Implement navigation to main app
    }

    override fun onPause() {
        super.onPause()
        if (player.isPlaying) {
            player.pause()
            updatePlayPauseButton()
        }
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        runnableRunning = false
        player.release()
    }

    private data class AudioInfo(
        val title: String?,
        val artist: String?,
        val albumArt: String?
    )
}