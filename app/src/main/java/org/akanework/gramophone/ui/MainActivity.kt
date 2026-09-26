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

import androidx.activity.compose.setContent
import org.akanework.gramophone.ui.components.player.rememberPlayerSheetController
import android.app.NotificationManager
import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.provider.MediaStore
import android.provider.Settings
import android.view.Choreographer
import android.view.SearchEvent
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Log
import androidx.media3.session.DefaultMediaNotificationProvider
import coil3.imageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.library.LibraryWriteRepository
import org.akanework.gramophone.logic.needsMissingOnDestroyCallWorkarounds
import org.akanework.gramophone.logic.postAtFrontOfQueueAsync
import org.akanework.gramophone.logic.ui.BaseActivity
import org.akanework.gramophone.ui.components.compose.AppDialogHostState
import org.akanework.gramophone.ui.components.compose.LibraryGate
import org.akanework.gramophone.ui.components.compose.MediaConsentHost
import org.akanework.gramophone.ui.nav.AppRoot
import org.akanework.gramophone.ui.nav.HomeKey
import org.akanework.gramophone.ui.nav.LocalReportFullyDrawn
import org.akanework.gramophone.ui.nav.NavViewModel
import org.akanework.gramophone.ui.nav.PlaylistKey
import org.akanework.gramophone.ui.nav.SearchKey
import org.akanework.gramophone.ui.nav.warmUpNavAxisEasing
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import uk.akane.libphonograph.manipulator.PlaylistSerializer.Entry
import uk.akane.libphonograph.reader.FlowReader

/**
 * MainActivity:
 *   Core of gramophone, one and the only activity
 * used across the application.
 *
 * @author AkaneTan, nift4
 */
class MainActivity : BaseActivity() {

    companion object {
        const val PLAYBACK_AUTO_START_FOR_FGS = "AutoStartFgs"
        const val PLAYBACK_AUTO_PLAY_ID = "AutoStartId"
        const val PLAYBACK_AUTO_PLAY_POSITION = "AutoStartPos"
        const val FAVORITE_ENTRY = "FavoriteEntry"
        const val FAVORITE_STATE = "FavoriteState"
    }

    // Import our viewModels.
    private val controllerViewModel: MediaControllerViewModel by viewModel()
    private val navViewModel: NavViewModel by viewModel()

    private val handler = Handler(Looper.getMainLooper())
    private val reportFullyDrawnRunnable = Runnable { if (!ready) reportFullyDrawn() }
    private var ready = false

    /**
     * onCreate - core of MainActivity.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("MainActivity", "onCreate($intent)")
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(controllerViewModel)
        lifecycleScope.launch(Dispatchers.Default) { warmUpNavAxisEasing() }
        // TODO: should Activity.setMediaController() or Activity.setVolumeControlStream() be
        //  called? latter will probably not do particularly much, and former will
        //  forward events to our session no matter whether it makes sense or not to currently
        //  handle volume there... but it's still better than not getting the key events I guess?

        setContent {
            GramophoneTheme {
                val dialogs = remember { AppDialogHostState() }
                MediaConsentHost()
                LibraryGate(
                    smartScanFirst = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    onDenied = ::onLibraryPermissionDenied,
                    // TODO(U9): intent handling moves off this callback.
                    onReady = ::onLibraryLoaded,
                    // If library load takes more than 2s, exit splash to avoid ANR
                    startSplashTimeout = {
                        if (!ready) handler.postDelayed(reportFullyDrawnRunnable, 2000)
                    },
                )
                val playerSheet = rememberPlayerSheetController(
                    toggleFavorite = libraryWrites::markFavorite
                )
                CompositionLocalProvider(LocalReportFullyDrawn provides ::maybeReportFullyDrawn) {
                    AppRoot(
                        backStack = navViewModel.backStack,
                        playerSheet = playerSheet,
                        dialogs = dialogs,
                        debug = BuildConfig.DEBUG,
                    )
                }
            }
        }

        if (navViewModel.backStack.lastOrNull() != HomeKey)
            handler.post { maybeReportFullyDrawn() }
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
        val autoPlayId = intent.extras?.getString(PLAYBACK_AUTO_PLAY_ID) ?: (if
                (intent.action == "org.akanework.gramophone.action.PLAY_MEDIA_FROM_SUGGESTION")
                intent.data?.let {
                    try {
                        ContentUris.parseId(it)
                        "MediaStore:${it.lastPathSegment}"
                    } catch (_: NumberFormatException) {
                        null
                    } } else null)
        var willAutoPlayLater = false
        autoPlayId?.let { id ->
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
        IntentCompat.getParcelableExtra(intent, FAVORITE_ENTRY, Entry::class.java)
            ?.let {
                val state = intent.getBooleanExtra(FAVORITE_STATE, false)
                libraryWrites.markFavorite(listOf(it), state)
            }
        if (intent.action == Intent.ACTION_VIEW) {
            val id = intent.getStringExtra("playlist")?.toLongOrNull()
            if (id != null) {
                navViewModel.navigateTo(PlaylistKey(id, null))
            }
        }
        if (intent.action == Intent.ACTION_SEARCH ||
            intent.action == "com.google.android.gms.actions.SEARCH_ACTION") {
            navViewModel.navigateTo(SearchKey(intent.getStringExtra(SearchManager.QUERY)))
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
                // TODO: support sub queries or at least focus to use a different type of search.
                navViewModel.navigateTo(SearchKey(mainQuery))
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

    override fun onSearchRequested(): Boolean {
        navViewModel.navigateTo(SearchKey(null))
        return true
    }

    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean {
        return onSearchRequested()
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

    override fun onProvideAssistContent(outContent: AssistContent?) {
        super.onProvideAssistContent(outContent)

        val instance = controllerViewModel.get()
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
                val strict = StrictMode.getThreadPolicy()
                try {
                    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
                    if (uri != null) {
                        outContent.clipData = ClipData.newUri(contentResolver,
                            item?.mediaMetadata?.title ?: "", uri)
                    }
                } finally {
                    StrictMode.setThreadPolicy(strict)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "unable to generate clip data", e)
            }
        }
    }

    private fun onLibraryLoaded() {
        Log.i("MainActivity", "onLibraryLoaded()")
        doPlayFromIntent(intent)
    }

    private fun maybeReportFullyDrawn() {
        if (!ready) reportFullyDrawn()
    }

    private fun onLibraryPermissionDenied() {
        maybeReportFullyDrawn() // TODO: is this still needed?
        Toast.makeText(this, getString(R.string.grant_audio), Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.setData("package:$packageName".toUri())
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        // https://github.com/androidx/media/issues/805
        if (needsMissingOnDestroyCallWorkarounds()
            && (controllerViewModel.get()?.playWhenReady != true || controllerViewModel.get()?.mediaItemCount == 0)
        ) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
        super.onDestroy()
        // we don't ever want covers to be the cause of service being killed by too high mem usage
        // (this is placed after super.onDestroy() to make sure all ImageViews are dead)
        imageLoader.memoryCache?.clear()
    }

    private val reader: FlowReader by inject()
    private val libraryWrites: LibraryWriteRepository by inject()
}
