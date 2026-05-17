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
import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Choreographer
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks
import androidx.fragment.app.commit
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Log
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
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.needsMissingOnDestroyCallWorkarounds
import org.akanework.gramophone.logic.postAtFrontOfQueueAsync
import org.akanework.gramophone.logic.ui.BaseActivity
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.components.PlayerBottomSheet
import org.akanework.gramophone.ui.fragments.BaseFragment
import org.akanework.gramophone.ui.fragments.SearchFragment
import org.akanework.gramophone.ui.fragments.ViewPagerFragment
import org.nift4.mediastorecompat.MediaStoreCompat
import uk.akane.libphonograph.manipulator.ItemManipulator
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import java.io.File

/**
 * MainActivity:
 *   Core of gramophone, one and the only activity
 * used across the application.
 *
 * @author AkaneTan, nift4
 */
class MainActivity : BaseActivity() {

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
    lateinit var playerBottomSheet: PlayerBottomSheet
        private set
    private lateinit var intentSenderDelete: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var addToPlaylistIntentSender: ActivityResultLauncher<IntentSenderRequest>
    private var pendingRequest: Bundle? = null
    private var pendingDeleteRequest: Bundle? = null

    fun updateLibrary(smartScanFirst: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                      then: (() -> Unit)? = null) {
        // If library load takes more than 2s, exit splash to avoid ANR
        if (!ready) handler.postDelayed(reportFullyDrawnRunnable, 2000)
        CoroutineScope(Dispatchers.Default).launch {
            if (smartScanFirst)
                MediaStoreCompat.smartScan(this@MainActivity.gramophoneApplication)
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
        Log.i("MainActivity", "onCreate($intent)")
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(controllerViewModel)
        enableEdgeToEdgeProperly()
        if (savedInstanceState?.containsKey("AddToPlaylistPendingRequest") == true) {
            pendingRequest = savedInstanceState.getBundle("AddToPlaylistPendingRequest")
        }
        if (savedInstanceState?.containsKey("DeletePendingRequest") == true) {
            pendingDeleteRequest = savedInstanceState.getBundle("DeletePendingRequest")
        }
        intentSenderDelete =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                val req = pendingDeleteRequest
                    ?: throw IllegalStateException("pending delete request is null")
                pendingDeleteRequest = null
                CoroutineScope(Dispatchers.Default).launch {
                    ItemManipulator.continueDeleteFromPendingIntent(this@MainActivity, it.resultCode, req)
                }
            }
        addToPlaylistIntentSender =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                val req = pendingRequest
                    ?: throw IllegalStateException("pending playlist add request is null")
                pendingRequest = null
                CoroutineScope(Dispatchers.Default).launch {
                    doAddToPlaylist(it.resultCode, req)
                }
            }
        // TODO: should Activity.setMediaController() or Activity.setVolumeControlStream() be
        //  called? latter will probably not do particularly much, and former will
        //  forward events to our session no matter whether it makes sense or not to currently
        //  handle volume there... but it's still better than not getting the key events I guess?

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

    @OptIn(FlowPreview::class)
    fun addToPlaylistDialog(item: MediaItem) {
        val song = Entry.ofMediaItem(item)
        if (song == null) {
            Toast.makeText(
                this@MainActivity,
                getString(R.string.edit_playlist_failed, "song == null"),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val playlists = runBlocking { reader.playlistListFlow.first().filter { it.id != null } }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_to_playlist)
            .setIcon(R.drawable.ic_playlist_play)
            .setItems((playlists.map {
                it.title ?: it.path?.absolutePath ?: it.id.toString()
            } + getString(R.string.create_playlist)).toTypedArray())
            { _, item ->
                if (playlists.size == item) {
                    PlaylistAdapter.playlistNameDialog(this, R.string.create_playlist, "") { name ->
                        CoroutineScope(Dispatchers.Default).launch {
                            val f = try {
                                ItemManipulator.createPlaylist(this@MainActivity, name)
                            } catch (e: Exception) {
                                Log.e("MainActivity", Log.getThrowableString(e)!!)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(
                                            R.string.create_failed_playlist,
                                            e.javaClass.name + ": " + e.message
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@launch
                            }
                            try {
                                ItemManipulator.setPlaylistContent(
                                    this@MainActivity,
                                    f,
                                    listOf(song)
                                )
                            } catch (e: Exception) {
                                Log.e("MainActivity", Log.getThrowableString(e)!!)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        getString(
                                            R.string.edit_playlist_failed,
                                            e.javaClass.name + ": " + e.message
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                    return@setItems
                }
                val pl = playlists[item]
                setPlaylist(
                    ContentUris.withAppendedId(
                        @Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"),
                        pl.id!!
                    ), listOf(song)
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    fun setPlaylist(uri: Uri, songs: List<Entry>) {
        val data = Bundle().apply {
            putParcelableArrayList("Songs", ArrayList(songs))
            putParcelable("Uri", uri)
        }
        CoroutineScope(Dispatchers.Default).launch {
            val token = MediaStoreCompat.needRequestBytesWrite(this@MainActivity, uri)
            if (token != null) {
                pendingRequest = data
                val pendingIntent = MediaStoreCompat.createWriteRequest(this@MainActivity,
                    listOf(token))
                addToPlaylistIntentSender.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
            } else {
                doAddToPlaylist(RESULT_OK, data)
            }
        }
    }

    fun runIntentForDelete(intent: IntentSender, bundle: Bundle) {
        try {
            intentSenderDelete.launch(IntentSenderRequest.Builder(intent).build())
            pendingDeleteRequest = bundle
        } catch (e: ActivityNotFoundException) {
            Log.e("MainActivity", "error launching intent", e)
            CoroutineScope(Dispatchers.Default).launch {
                ItemManipulator.continueDeleteFromPendingIntent(
                    this@MainActivity, RESULT_CANCELED,
                    bundle
                )
            }
        }
    }

    private suspend fun doAddToPlaylist(resultCode: Int, data: Bundle) {
        if (resultCode == RESULT_OK) {
            val uri = BundleCompat.getParcelable(data, "Uri", Uri::class.java)!!
            val songs = BundleCompat.getParcelableArrayList(data, "Songs",
                Entry::class.java)!!
            try {
                ItemManipulator.addToPlaylist(this@MainActivity, uri, songs)
            } catch (e: Exception) {
                Log.e("MainActivity", Log.getThrowableString(e)!!)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(
                            R.string.edit_playlist_failed,
                            e.javaClass.name + ": " + e.message
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.edit_playlist_failed, "$resultCode"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (pendingRequest != null) {
            outState.putBundle("AddToPlaylistPendingRequest", pendingRequest)
        }
        if (pendingDeleteRequest != null) {
            outState.putBundle("DeletePendingRequest", pendingDeleteRequest)
        }
    }

    override fun onNewIntent(intent: Intent) {
        Log.i("MainActivity", "onNewIntent($intent)")
        super.onNewIntent(intent)
        if (ready) {
            doPlayFromIntent(intent)
        }
    }

    private fun doPlayFromIntent(intent: Intent) {
        Log.i("MainActivity", "doPlayFromIntent($intent)")
        var willAutoPlayLater = false
        intent.extras?.getString(PLAYBACK_AUTO_PLAY_ID)?.let { id ->
            willAutoPlayLater = true
            val pos =
                intent.extras?.getLong(PLAYBACK_AUTO_PLAY_POSITION, C.TIME_UNSET) ?: C.TIME_UNSET
            controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    withContext(Dispatchers.Default) {
                        val col = reader.idMapFlow.firstOrNull()
                        val item = id.toLongOrNull()?.let { col?.let { it2 -> it2[it] } }
                        if (item == null) {
                            Log.e(
                                "MainActivity",
                                "can't find file with ID $id in library with ${col?.size} items"
                            )
                        }
                        item
                    }.let { mediaItem ->
                        if (mediaItem != null) {
                            controller.setMediaItem(mediaItem, pos)
                            controller.prepare()
                            controller.play()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                R.string.cannot_find_file,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                dispose()
            }
        }
        if (intent.action == Intent.ACTION_SEARCH ||
            intent.action == "com.google.android.gms.actions.SEARCH_ACTION") {
            startFragment(SearchFragment()) {
                Bundle().apply {
                    putString("query", intent.getStringExtra(SearchManager.QUERY))
                }
            }
        }
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_SEARCH
            || intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            // https://developer.android.com/media/implement/assistant#declare_legacy_support_for_voice_actions
            // https://android-developers.googleblog.com/2010/09/supporting-new-music-voice-action.html
            // https://developer.android.com/guide/components/intents-common#PlaySearch
            var focus = intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS)
                ?: ContentResolver.ANY_CURSOR_ITEM_TYPE
            // Validate all extras before sending them to service.
            if (focus != ContentResolver.ANY_CURSOR_ITEM_TYPE &&
                focus != MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE &&
                focus != MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE &&
                focus != MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE &&
                focus != MediaStore.Audio.Media.ENTRY_CONTENT_TYPE &&
                focus != (@Suppress("deprecation") MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE)) {
                Log.w("MainActivity", "unsupported focus " +
                        intent.getStringExtra(MediaStore.EXTRA_MEDIA_FOCUS))
                focus = ContentResolver.ANY_CURSOR_ITEM_TYPE
            }
            val mainQuery: String?
            val subQueries = Bundle()
            subQueries.putString(MediaStore.EXTRA_MEDIA_FOCUS, focus)
            when (focus) {
                MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                    mainQuery = intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                }
                MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
                    mainQuery = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_GENRE, it)
                    }
                }
                MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                    mainQuery = intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ARTIST, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_GENRE, it)
                    }
                }
                MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> {
                    mainQuery = intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ALBUM, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ARTIST, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_GENRE, it)
                    }
                }
                @Suppress("deprecation") MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> {
                    mainQuery = @Suppress("deprecation")
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_PLAYLIST)
                        ?: intent.getStringExtra(SearchManager.QUERY)
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_TITLE, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ALBUM, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_ARTIST, it)
                    }
                    intent.getStringExtra(MediaStore.EXTRA_MEDIA_GENRE)?.let {
                        subQueries.putString(MediaStore.EXTRA_MEDIA_GENRE, it)
                    }
                }
                else -> mainQuery = intent.getStringExtra(SearchManager.QUERY)
            }
            if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
                if (mainQuery != null) {
                    willAutoPlayLater = true
                    controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
                        controller.setMediaItem(
                            MediaItem.Builder()
                                .setRequestMetadata(
                                    MediaItem.RequestMetadata.Builder()
                                        .setSearchQuery(mainQuery) // may be empty
                                        .setExtras(subQueries)
                                        .build()
                                )
                                .build()
                        )
                        controller.prepare()
                        controller.play()
                        dispose()
                    }
                }
            } else {
                startFragment(SearchFragment()) {
                    Bundle().apply {
                        putString("query", intent.getStringExtra(SearchManager.QUERY))
                        // TODO: support sub queries or at least focus to use a different type of
                        //  search fragment.
                    }
                }
            }
        }
        if (intent.action == "org.akanework.gramophone.action.SHUFFLE") {
            ShortcutManagerCompat.reportShortcutUsed(this@MainActivity, "shuffle_all")
            val query = intent.getStringExtra("item_name") ?: ""
            willAutoPlayLater = true
            controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
                controller.shuffleModeEnabled = true
                controller.setMediaItem(
                    MediaItem.Builder()
                        .setRequestMetadata(
                            MediaItem.RequestMetadata.Builder()
                                .setSearchQuery(query) // empty = every song
                                .build()
                        )
                        .build()
                )
                controller.prepare()
                controller.play()
                dispose()
            }
        }
        val autoPlay = intent.extras?.getBoolean(PLAYBACK_AUTO_START_FOR_FGS, false) == true
                || intent.extras?.getBoolean(IntentCompat.EXTRA_START_PLAYBACK, false) == true
                || prefs.getBooleanStrict("autoplay", false)
        if (autoPlay && !willAutoPlayLater) {
            controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
                controller.prepare()
                controller.play()
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
                    Log.w("MainActivity", "reportFullyDrawn failed", e)
                }
            }
        }
    }

    @RequiresApi(23)
    override fun onProvideAssistContent(outContent: AssistContent?) {
        super.onProvideAssistContent(outContent)

        val instance = getPlayer()
        if (instance != null && outContent != null) {
            /* TODO implement schema.org MusicRecording creation here
            https://developer.android.com/training/articles/assistant
           outContent.structuredData = JSONObject()
                .put("@type", "MusicRecording")
                .put("@id", "https://example.com/music/recording")
                .put("name", "Song Title")
                .toString() */
            try {
                val item = instance.currentMediaItem
                val uri = item?.requestMetadata?.mediaUri
                    ?: item?.localConfiguration?.uri
                val contentUri = if (uri?.scheme == "file") {
                    FileProvider.getUriForFile(
                        this,
                        "$packageName.fileProvider",
                        File(uri.path!!)
                    )
                } else uri
                if (contentUri != null) {
                    outContent.clipData = ClipData.newUri(contentResolver,
                        item?.mediaMetadata?.title ?: "", contentUri)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "unable to generate clip data", e)
            }
        }
    }

    fun onLibraryLoaded() {
        Log.i("MainActivity", "onLibraryLoaded()")
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

    inline val reader
        get() = gramophoneApplication.reader
}
