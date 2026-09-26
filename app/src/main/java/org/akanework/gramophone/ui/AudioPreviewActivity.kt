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
package org.akanework.gramophone.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.mp3.Mp3Extractor
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getStringStrict
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.hasScopedStorageV1
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.playOrPause
import org.akanework.gramophone.logic.ui.BaseActivity
import org.akanework.gramophone.logic.utils.CalculationUtils.convertDurationToTimeStamp
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.utils.ReplayGainAudioProcessor
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneExtractorsFactory
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneMediaSourceFactory
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneRenderFactory
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.home.LibraryCover
import org.akanework.gramophone.ui.components.home.rememberDefaultCoverPainter
import org.akanework.gramophone.ui.components.home.textViewStyle
import org.akanework.gramophone.ui.components.player.PlayPauseIcon
import org.akanework.gramophone.ui.components.player.PlayerUtilities
import org.akanework.gramophone.ui.components.player.SquigglyProgressBar
import uk.akane.libphonograph.toUriCompat
import java.io.File

/*
 * The audio preview: a small player in a dialog over whatever app handed us a file, with a way
 * into the app proper when the file is in the library.
 */

private const val TAG = "AudioPreviewActivity"
private const val POSITION_POLL_MS = 100L
private val DIALOG_CORNER = 28.dp
private val COVER_SIZE = 50.dp

/** What the dialog shows, fed by the player's callbacks. */
@Stable
private class PreviewState {
    var title by mutableStateOf<String?>(null)
    var artist by mutableStateOf<String?>(null)
    var artworkData by mutableStateOf<ByteArray?>(null)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var canOpen by mutableStateOf(false)
}

class AudioPreviewActivity : BaseActivity() {
    companion object {
        private const val PERMISSION_READ_MEDIA_AUDIO = 100
    }

    private lateinit var player: ExoPlayer
    private val state = PreviewState()
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private var askedForPermissionInSettings = false

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rgAp = ReplayGainAudioProcessor()
        player = ExoPlayer.Builder(
            this,
            GramophoneRenderFactory(this, rgAp, {}, {})
                .setEnableHighResolutionPcmOutput(true)
                .setEnableDecoderFallback(true)
                .setEnableAudioOutputPlaybackParameters(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
            GramophoneMediaSourceFactory(
                DefaultDataSource.Factory(this),
                GramophoneExtractorsFactory().also {
                    it.setConstantBitrateSeekingEnabled(true)
                    it.setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
                })
        )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setTrackSelector(DefaultTrackSelector(this).apply {
                setParameters(
                    buildUponParameters()
                        .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                        .setAudioOffloadPreferences(
                            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                                .apply {
                                    val config = prefs.getStringStrict("offload", "0")?.toIntOrNull()
                                    if (config != null && config > 0 && Flags.OFFLOAD) {
                                        rgAp.setOffloadEnabled(true)
                                        setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                                        setIsGaplessSupportRequired(config == 2)
                                    }
                                }
                                .build()))
            })
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return
                updateMediaMetadata()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateMediaMetadata()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: @Player.TimelineChangeReason Int) {
                updateMediaMetadata()
            }
        })

        setContent {
            GramophoneTheme {
                Dialog(onDismissRequest = { finish() }) {
                    Surface(
                        shape = RoundedCornerShape(DIALOG_CORNER),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        PreviewContent(
                            state = state,
                            onPlayPause = {
                                if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                                player.playOrPause()
                            },
                            onSeek = { player.seekTo(it) },
                            onOpen = { openInGramophone() },
                            poll = { syncPosition() },
                        )
                    }
                }
            }
        }

        if (!hasAudioPermission())
            ActivityCompat.requestPermissions(
                this,
                if (hasScopedStorageWithMediaTypes())
                    arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
                else if (hasScopedStorageV2())
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                else
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                PERMISSION_READ_MEDIA_AUDIO,
            )
        else
            handleIntent(intent)
    }

    private fun syncPosition() {
        val duration = player.contentDuration.let { if (it == C.TIME_UNSET) null else it }
            ?: player.mediaMetadata.durationMs ?: 0L
        state.durationMs = duration
        state.positionMs = player.currentPosition.coerceIn(0L, duration.coerceAtLeast(0L))
    }

    private fun updateMediaMetadata() {
        state.title = player.mediaMetadata.title?.toString()
        state.artist = player.mediaMetadata.artist?.toString()
        state.artworkData = player.mediaMetadata.artworkData
        syncPosition()
    }

    private fun openInGramophone() {
        player.currentMediaItem?.mediaId?.let {
            if (it != MediaItem.DEFAULT_MEDIA_ID) it else null
        }?.let { id ->
            startActivity(Intent(this, MainActivity::class.java).also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                it.putExtra(MainActivity.PLAYBACK_AUTO_PLAY_ID, id)
                player.contentPosition.let { pos ->
                    if (pos != C.TIME_UNSET)
                        it.putExtra(MainActivity.PLAYBACK_AUTO_PLAY_POSITION, pos)
                }
            })
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_READ_MEDIA_AUDIO) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                handleIntent(intent)
            } else {
                askedForPermissionInSettings = true
                Toast.makeText(this, getString(R.string.grant_audio), Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.setData("package:$packageName".toUri())
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (askedForPermissionInSettings && hasAudioPermission()) {
            askedForPermissionInSettings = false
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (hasAudioPermission())
            handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        scope.launch {
            if (intent.action != Intent.ACTION_VIEW) return@launch
            val uri = intent.data ?: return@launch
            Log.i(TAG, "Audio preview opening $uri")
            var fileUri: Uri? = null
            val queryUri = when (uri.scheme) {
                "file" -> {
                    fileUri = uri
                    null
                }
                "content" if uri.host == MediaStore.AUTHORITY -> uri
                "content" -> try {
                    if (hasScopedStorageV1()) MediaStore.getMediaUri(this@AudioPreviewActivity, uri) else null
                } catch (e: Exception) {
                    if (e is SecurityException || e.message == "Provider for this Uri is not supported."
                        || e.message?.startsWith("Invalid URI: ") == true
                        || e.message?.startsWith("No item at") == true
                        || e.message?.contains("Missing file for") == true
                    )
                        Log.w(TAG, e.javaClass.name + ": " + e.message)
                    else
                        Log.e(TAG, Log.getStackTraceString(e))
                    null
                } ?: run {
                    val lp = Uri.decode(uri.lastPathSegment)
                    if (lp?.toUri()?.scheme == "file") { // Material Files hands file uris over like this.
                        fileUri = lp.toUri()
                        if (!fileUri.toFile().canRead())
                            fileUri = null // probably .nomedia?
                    } else {
                        val pfd = try {
                            contentResolver.openFileDescriptor(uri, "r")
                        } catch (e: Exception) {
                            Log.e(TAG, Log.getStackTraceString(e))
                            null
                        }
                        if (pfd == null) return@run null
                        val l = try {
                            Os.readlink("/proc/self/fd/" + pfd.fd)
                        } catch (e: ErrnoException) {
                            Log.w(TAG, "get fd ${pfd.fd} failed", e)
                            return@run null
                        } finally {
                            pfd.close()
                        }
                        val f = File(l)
                        if (f.canRead())
                            fileUri = "file://$l".toUri()
                        else
                            Log.w(TAG, "found $l from fd but it doesn't exist")
                    }
                    null
                }
                else -> null
            }
            Log.i(TAG, "Audio preview opening $uri with query=$queryUri file=$fileUri")
            val cursor = if (queryUri != null || fileUri != null) contentResolver.query(
                queryUri ?: MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA
                ),
                if (queryUri == null) MediaStore.Audio.Media.DATA + " = ?" else null,
                if (queryUri == null) arrayOf(fileUri!!.toFile().absolutePath) else null,
                null
            ) else null
            val mediaItem = MediaItem.Builder()
            var inLibrary = false
            run {
                if (cursor?.moveToFirst() == true) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    if (id != 0L) {
                        val durationMs = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                        val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                        val data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                        mediaItem.setUri(File(data).toUriCompat())
                        mediaItem.setMediaId(id.toString())
                        mediaItem.setMediaMetadata(
                            MediaMetadata.Builder().setTitle(title).setDurationMs(durationMs).build()
                        )
                        inLibrary = true
                        Log.i(TAG, "Audio preview found ID $id for query=$queryUri file=$fileUri (was uri=$uri)")
                        return@run
                    } else {
                        Log.i(TAG, "Audio preview found no ID for query=$queryUri file=$fileUri (was uri=$uri)")
                    }
                } else {
                    Log.i(TAG, "Audio preview found no data for query=$queryUri file=$fileUri (was uri=$uri)")
                }
                mediaItem.setUri(fileUri ?: queryUri ?: uri)
            }
            cursor?.close()
            withContext(Dispatchers.Main) {
                state.canOpen = inLibrary
                try {
                    player.setMediaItem(mediaItem.build())
                } catch (e: IllegalStateException) {
                    if (e.message?.startsWith("No suitable media source factory found for content type:") != true)
                        throw e
                    Toast.makeText(this@AudioPreviewActivity, R.string.cannot_play_file, Toast.LENGTH_LONG).show()
                    finish()
                    return@withContext
                }
                player.prepare()
                player.play()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player.playWhenReady = false
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun PreviewContent(
    state: PreviewState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpen: () -> Unit,
    poll: () -> Unit,
) {
    val defaultProgressBar = rememberBooleanPreference("default_progress_bar", false).value
    LaunchedEffect(Unit) {
        while (isActive) {
            poll()
            delay(POSITION_POLL_MS)
        }
    }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = scrub ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownPositionMs = scrub?.let { (it * duration).toLong() } ?: state.positionMs
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.artworkData != null) {
                AsyncImage(
                    model = state.artworkData,
                    contentDescription = null,
                    placeholder = rememberDefaultCoverPainter(R.drawable.ic_default_cover),
                    error = rememberDefaultCoverPainter(R.drawable.ic_default_cover),
                    modifier = Modifier.size(COVER_SIZE).clip(RoundedCornerShape(6.dp)),
                )
            } else {
                LibraryCover(uri = null, defaultCover = R.drawable.ic_default_cover, cornerRadius = 6.dp, modifier = Modifier.size(COVER_SIZE))
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                BasicText(
                    text = state.title ?: stringResourceCompat(R.string.unknown_title),
                    style = textViewStyle(15.sp, 500, scheme.onSurface),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = state.artist ?: stringResourceCompat(R.string.unknown_artist),
                    style = textViewStyle(12.sp, 400, scheme.onSurfaceVariant),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            androidx.compose.foundation.layout.Box(
                Modifier.size(48.dp).clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                PlayPauseIcon(playing = state.isPlaying, tint = scheme.onSurface, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        val barModifier = Modifier.fillMaxWidth().height(48.dp)
        if (defaultProgressBar) {
            Slider(
                value = fraction,
                onValueChange = { scrub = it },
                onValueChangeFinished = {
                    scrub?.let { onSeek((it * duration).toLong()) }
                    scrub = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = scheme.primary,
                    activeTrackColor = scheme.primary,
                    inactiveTrackColor = scheme.primary.copy(alpha = PlayerUtilities.SQUIGGLY_TRACK_ALPHA),
                ),
                modifier = barModifier,
            )
        } else {
            SquigglyProgressBar(
                fraction = fraction,
                animating = state.isPlaying && scrub == null,
                color = scheme.primary,
                trackColor = scheme.primary.copy(alpha = PlayerUtilities.SQUIGGLY_TRACK_ALPHA),
                modifier = barModifier,
                onScrub = { scrub = it },
                onSeek = {
                    onSeek((it * duration).toLong())
                    scrub = null
                },
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(convertDurationToTimeStamp(shownPositionMs), color = scheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(convertDurationToTimeStamp(state.durationMs), color = scheme.onSurfaceVariant, fontSize = 14.sp)
        }
        if (state.canOpen) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResourceCompat(R.string.open_in_gramophone), color = scheme.primary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun stringResourceCompat(id: Int): String = androidx.compose.ui.res.stringResource(id)
