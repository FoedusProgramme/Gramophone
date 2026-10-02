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

import android.app.NotificationManager
import android.app.assist.AssistContent
import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.view.Choreographer
import android.view.SearchEvent
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.Log
import androidx.media3.session.DefaultMediaNotificationProvider
import coil3.imageLoader
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.needsMissingOnDestroyCallWorkarounds
import org.akanework.gramophone.logic.postAtFrontOfQueueAsync
import org.akanework.gramophone.logic.ui.BaseActivity
import org.akanework.gramophone.ui.intent.PlayIntentParser
import org.akanework.gramophone.ui.intent.PlayIntentViewModel
import org.akanework.gramophone.ui.nav.HomeKey
import org.akanework.gramophone.ui.nav.NavViewModel
import org.akanework.gramophone.ui.nav.SearchKey
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * MainActivity:
 *   Core of gramophone, one and the only activity
 * used across the application.
 *
 * @author AkaneTan, nift4
 */
class MainActivity : BaseActivity() {

    private val controllerViewModel: MediaControllerViewModel by viewModel()
    private val navViewModel: NavViewModel by viewModel()
    private val playIntentViewModel: PlayIntentViewModel by viewModel()

    private val handler = Handler(Looper.getMainLooper())
    /** Whether the app was reported fully drawn, which also ends the splash screen. */
    private var ready = false
    private val reportFullyDrawnRunnable = Runnable(::maybeReportFullyDrawn)

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("MainActivity", "onCreate($intent)")
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(controllerViewModel)
        playIntentViewModel.bind(controllerViewModel, navViewModel)
        // Taken once per view model: not again after rotation, and after process death only
        // what had not run yet.
        playIntentViewModel.enqueueLaunchIntent(parsePlayIntent(intent))
        // TODO: should Activity.setMediaController() or Activity.setVolumeControlStream() be
        //  called? latter will probably not do particularly much, and former will
        //  forward events to our session no matter whether it makes sense or not to currently
        //  handle volume there... but it's still better than not getting the key events I guess?

        setContent {
            MainRoot(
                onLibraryPermissionDenied = ::onLibraryPermissionDenied,
                startSplashTimeout = ::startSplashTimeout,
                reportFullyDrawn = ::maybeReportFullyDrawn,
            )
        }

        // The home's lists report fully drawn once loaded; don't wait for them when a recreated
        // activity comes back with another page on top.
        if (navViewModel.backStack.lastOrNull() != HomeKey)
            handler.post(reportFullyDrawnRunnable)
    }

    override fun onNewIntent(intent: Intent) {
        Log.i("MainActivity", "onNewIntent($intent)")
        super.onNewIntent(intent)
        setIntent(intent)
        playIntentViewModel.enqueue(parsePlayIntent(intent))
    }

    private fun parsePlayIntent(intent: Intent) =
        PlayIntentParser.parse(intent, prefs.getBooleanStrict("autoplay", false))

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

    private fun maybeReportFullyDrawn() {
        if (!ready) reportFullyDrawn()
    }

    /** If the library takes longer than [SPLASH_TIMEOUT_MS] to load, ends the splash anyway. */
    private fun startSplashTimeout() {
        // Avoids an ANR from keeping the splash up for too long.
        if (!ready) handler.postDelayed(reportFullyDrawnRunnable, SPLASH_TIMEOUT_MS)
    }

    override fun onLibraryPermissionDenied() {
        maybeReportFullyDrawn() // TODO: is this still needed?
        super.onLibraryPermissionDenied()
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

    private companion object {
        const val SPLASH_TIMEOUT_MS = 2000L
    }
}
