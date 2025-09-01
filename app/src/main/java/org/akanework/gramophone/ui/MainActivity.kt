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

package org.akanework.gramophone.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import androidx.fragment.app.commit
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import coil3.imageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.enableEdgeToEdgeProperly
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.needsMissingOnDestroyCallWorkarounds
import org.akanework.gramophone.logic.postAtFrontOfQueueAsync
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.components.PlayerBottomSheet
import org.akanework.gramophone.ui.fragments.BaseFragment
import org.akanework.gramophone.ui.fragments.ViewPagerFragment
import uk.akane.libphonograph.manipulator.ItemManipulator
import java.io.File

/**
 * MainActivity:
 *   Core of gramophone, one and the only activity
 * used across the application.
 *
 * @author AkaneTan, nift4
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_READ_MEDIA_AUDIO = 100
        const val PLAYBACK_AUTO_START_FOR_FGS = "AutoStartFgs"
        const val PLAYBACK_AUTO_PLAY_ID = "AutoStartId"
        const val PLAYBACK_AUTO_PLAY_POSITION = "AutoStartPos"
    }

    // Import our viewModels.
    val controllerViewModel: MediaControllerViewModel by viewModels()
    val startingActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private val handler = Handler(Looper.getMainLooper())
    private val reportFullyDrawnRunnable = Runnable { if (!ready) reportFullyDrawn() }
    private var ready = false
    private var autoPlay = false
    lateinit var playerBottomSheet: PlayerBottomSheet
        private set
    lateinit var intentSender: ActivityResultLauncher<IntentSenderRequest>
        private set
    private lateinit var addToPlaylistIntentSender: ActivityResultLauncher<IntentSenderRequest>
    private var pendingRequest: Bundle? = null

    fun updateLibrary(then: (() -> Unit)? = null) {
        // If library load takes more than 2s, exit splash to avoid ANR
        if (!ready) handler.postDelayed(reportFullyDrawnRunnable, 2000)
        CoroutineScope(Dispatchers.Default).launch {
            this@MainActivity.gramophoneApplication.reader.refresh()
            withContext(Dispatchers.Main) {
                onLibraryLoaded()
                then?.let { it() }
            }
        }
    }

    /**
     * onCreate - core of MainActivity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(controllerViewModel)
        enableEdgeToEdgeProperly()
        if (savedInstanceState?.containsKey("AddToPlaylistPendingRequest") == true) {
            pendingRequest = savedInstanceState.getBundle("AddToPlaylistPendingRequest")
        }
        intentSender =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}
        addToPlaylistIntentSender =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                doAddToPlaylist(it.resultCode, pendingRequest
                    ?: throw IllegalStateException("pending playlist add request is null"))
            }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object :
            FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                super.onFragmentStarted(fm, f)
                if (fm.fragments.lastOrNull() != f) return
                // this won't be called in case we show()/hide() so
                // we handle that case in BaseFragment
                if (f is BaseFragment && f.wantsPlayer != null) {
                    playerBottomSheet.visible = f.wantsPlayer
                }
            }
        }, false)

        // Set content Views.
        setContentView(R.layout.activity_main)
        if (BuildConfig.DEBUG) {
            @SuppressLint("SetTextI18n")
            findViewById<ViewGroup>(R.id.rootView).addView(TextView(this).apply {
                text = "DEBUG"
                setTextColor(Color.RED)
                translationZ = 9999999f
                translationX = 50f
            })
        }
        playerBottomSheet = findViewById(R.id.player_layout)

        // Check all permissions.
        if (!hasAudioPermission()) {
            // Ask if was denied.
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
        } else {
            // If all permissions are granted, we can update library now.
            if (!this@MainActivity.reader.hadFirstRefresh) {
                updateLibrary()
            } else onLibraryLoaded() // <-- when recreating activity due to rotation
        }
        // ViewPagerFragment will call reportFullyDrawn itself, for every other fragment we'll handle it
        // (this will happen on activity recreation with non-empty fragment backstack)
        if (supportFragmentManager.findFragmentById(R.id.fragment_viewpager) !is ViewPagerFragment)
            handler.post { maybeReportFullyDrawn() }
    }

    @kotlin.OptIn(FlowPreview::class)
    fun addToPlaylistDialog(song: File?) {
        if (song == null) {
            Toast.makeText(this@MainActivity, R.string.edit_playlist_failed, Toast.LENGTH_LONG).show()
            return
        }
        val playlists = runBlocking { reader.playlistListFlow.first().filter { it.id != null } }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_to_playlist)
            .setIcon(R.drawable.ic_playlist_play)
            .setItems((playlists.map { it.title } + getString(R.string.create_playlist)).toTypedArray())
            { d, item ->
                if (playlists.size == item) {
                    PlaylistAdapter.playlistNameDialog(this, R.string.create_playlist, "") { name ->
                        CoroutineScope(Dispatchers.Default).launch {
                            val f = try {
                                ItemManipulator.createPlaylist(this@MainActivity, name)
                            } catch (e: Exception) {
                                Log.e("MainActivity", Log.getStackTraceString(e))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, R.string.create_failed_playlist, Toast.LENGTH_LONG).show()
                                }
                                return@launch
                            }
                            try {
                                ItemManipulator.setPlaylistContent(this@MainActivity, f, listOf(song))
                            } catch (e: Exception) {
                                Log.e("MainActivity", Log.getStackTraceString(e))
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, R.string.edit_playlist_failed, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    return@setItems
                }
                val pl = playlists[item]
                setPlaylist(pl.path!!, ContentUris.withAppendedId(
                    @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), pl.id!!
                ), true, listOf(song))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    fun setPlaylist(playlist: File, uri: Uri, addToEnd: Boolean, songs: List<File>) {
        setPlaylist(playlist, uri, addToEnd, ArrayList(songs.map { it.absolutePath }))
    }

    fun setPlaylist(playlist: File, uri: Uri, addToEnd: Boolean, songs: ArrayList<String>) {
        val data = Bundle().apply {
            putBoolean("AddToEnd", addToEnd)
            putStringArrayList("Songs", songs)
            putString("PlaylistPath", playlist.absolutePath)
        }
        if (ItemManipulator.needRequestWrite(this, uri)) {
            pendingRequest = data
            val pendingIntent = MediaStore.createWriteRequest(contentResolver, listOf(uri))
            addToPlaylistIntentSender.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } else {
            doAddToPlaylist(RESULT_OK, data)
        }
    }

    private fun doAddToPlaylist(resultCode: Int, data: Bundle) {
        if (resultCode == RESULT_OK) {
            val add = data.getBoolean("AddToEnd")
            val path = File(data.getString("PlaylistPath")!!)
            val songs = data.getStringArrayList("Songs")!!.map { File(it) }
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    if (add)
                        ItemManipulator.addToPlaylist(this@MainActivity, path, songs)
                    else
                        ItemManipulator.setPlaylistContent(this@MainActivity, path, songs)
                } catch (e: Exception) {
                    Log.e("MainActivity", Log.getStackTraceString(e))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, R.string.edit_playlist_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, R.string.edit_playlist_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        if (pendingRequest != null) {
            outState.putBundle("AddToPlaylistPendingRequest", pendingRequest)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        autoPlay = intent.extras?.getBoolean(PLAYBACK_AUTO_START_FOR_FGS, false) == true
        if (ready) {
            doPlayFromIntent(intent)
        }
    }

    private fun doPlayFromIntent(intent: Intent) {
        intent.extras?.getString(PLAYBACK_AUTO_PLAY_ID)?.let { id ->
            val pos = intent.extras?.getLong(PLAYBACK_AUTO_PLAY_POSITION, C.TIME_UNSET) ?: C.TIME_UNSET
            controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
                runBlocking { reader.idMapFlow.firstOrNull() }
                    .let { col ->
                        val mediaItem = id.toLongOrNull()?.let { col?.let { it2 -> it2[it] } }
                        if (mediaItem != null) {
                            controller.setMediaItem(mediaItem, pos)
                            controller.prepare()
                            controller.play()
                        } else {
                            Log.e("MainActivity", "can't find file with ID $id in library with ${col?.size} items")
                            Toast.makeText(this@MainActivity, R.string.cannot_find_file, Toast.LENGTH_LONG).show()
                        }
                    }
                dispose()
            }
        }
    }

    // https://twitter.com/Piwai/status/1529510076196630528
    override fun reportFullyDrawn() {
        handler.removeCallbacks(reportFullyDrawnRunnable)
        if (ready) throw IllegalStateException("ready is already true")
        ready = true
        Choreographer.getInstance().postFrameCallback {
            handler.postAtFrontOfQueueAsync {
                try {
                    super.reportFullyDrawn()
                } catch (e: SecurityException) {
                    // samsung SM-G570M on SDK 26: Permission Denial: broadcast from android asks to run as user
                    // -1 but is calling from user 0; this requires android.permission.INTERACT_ACROSS_USERS_FULL
                    // or android.permission.INTERACT_ACROSS_USERS
                    Log.w("MainActivity", e)
                }
            }
        }
    }

    fun onLibraryLoaded() {
        doPlayFromIntent(intent)
    }

    fun maybeReportFullyDrawn() {
        if (!ready) reportFullyDrawn()
    }

    /**
     * onRequestPermissionResult:
     *   Update library after permission is granted.
     */
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
                updateLibrary()
            } else {
                maybeReportFullyDrawn() // TODO: is this still needed?
                Toast.makeText(this, getString(R.string.grant_audio), Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.setData("package:$packageName".toUri())
                startActivity(intent)
                finish()
            }
        }
    }

    /**
     * startFragment:
     *   Used by child fragments / drawer to start
     * a fragment inside MainActivity's fragment
     * scope.
     *
     * @param frag: Target fragment.
     */
    fun startFragment(frag: Fragment, args: (Bundle.() -> Unit)? = null) {
        supportFragmentManager.commit {
            addToBackStack(System.currentTimeMillis().toString())
            hide(supportFragmentManager.fragments.last())
            add(R.id.container, frag.apply { args?.let { arguments = Bundle().apply(it) } })
        }
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        // https://github.com/androidx/media/issues/805
        if (needsMissingOnDestroyCallWorkarounds()
            && (getPlayer()?.playWhenReady != true || getPlayer()?.mediaItemCount == 0)
        ) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
        super.onDestroy()
        // we don't ever want covers to be the cause of service being killed by too high mem usage
        // (this is placed after super.onDestroy() to make sure all ImageViews are dead)
        imageLoader.memoryCache?.clear()
    }

    /**
     * getPlayer:
     *   Returns a media controller.
     */
    fun getPlayer() = controllerViewModel.get()

    fun consumeAutoPlay(): Boolean {
        return autoPlay.also { autoPlay = false }
    }

    inline val reader
        get() = gramophoneApplication.reader
}
