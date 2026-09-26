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

import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.media3.common.util.Log
import androidx.media3.session.DefaultMediaNotificationProvider
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.map.AndroidUriMapper
import coil3.request.NullRequestDataException
import coil3.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.di.appModule
import org.akanework.gramophone.logic.ui.BugHandlerActivity
import org.akanework.gramophone.logic.utils.CoilArtPipeline
import org.akanework.gramophone.ui.LyricWidgetProvider
import org.akanework.gramophone.ui.theme.applyToSystem
import org.akanework.gramophone.ui.theme.themeModeOf
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.lsposed.hiddenapibypass.LSPass
import org.nift4.gramophone.hificore.UacManager
import uk.akane.libphonograph.reader.FlowReader
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds

class GramophoneApplication : Application(), SingletonImageLoader.Factory,
    Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "GramophoneApplication"
    }

    init {
        @SuppressLint("DefaultUncaughtExceptionDelegation")
        Thread.setDefaultUncaughtExceptionHandler(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.MODEL != "robolectric") {
            HiddenApiBypass.setHiddenApiExemptions("")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LSPass.setHiddenApiExemptions("")
        }
        if (BuildConfig.DEBUG) {
            System.setProperty("kotlinx.coroutines.debug", "on")
            @OptIn(ExperimentalComposeRuntimeApi::class)
            Composer.setDiagnosticStackTraceEnabled(true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@GramophoneApplication)
            modules(appModule)
        }
        // disk read and write on first launch, but unavoidable as the night mode has to be known
        // before any activity starts
        val prefs = defaultPrefs
        val themeMode = prefs.getString("theme_mode", "0")
        if (BuildConfig.DEBUG && !isColorOS()) {
            // Use StrictMode to find antipattern issues
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectAll()
                    .let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            it.permitExplicitGc() // platform calls System.gc() on activity destroy
                        } else it
                    }
                    .let {
                        if (Debug.isDebuggerConnected() || isAlpsBoostFwkPresent())
                            it.permitDiskReads()
                        else it
                    }
                    .penaltyLog()
                    .penaltyDialog()
                    .build()
            )
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectAll()
                    // detectAll does in fact not detect everything :)
                    .let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            it.detectImplicitDirectBoot()
                        } else it
                    }
                    .penaltyLog()
                    .let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            it.penaltyDeathOnFileUriExposure()
                        } else it
                    }
                    .build()
            )
        }
        android.util.Log.d(TAG, "GramophoneApplication.onCreate()")
        org.nift4.mediastorecompat.Log.setLogger(object : org.nift4.mediastorecompat.Log.Logger {
            override fun d(
                tag: String,
                message: String,
                throwable: Throwable?
            ) {
                Log.d(tag, message, throwable)
            }

            override fun i(
                tag: String,
                message: String,
                throwable: Throwable?
            ) {
                Log.i(tag, message, throwable)
            }

            override fun w(
                tag: String,
                message: String,
                throwable: Throwable?
            ) {
                Log.w(tag, message, throwable)
            }

            override fun e(
                tag: String,
                message: String,
                throwable: Throwable?
            ) {
                Log.e(tag, message, throwable)
            }
        })
        if (!android.util.Log.isLoggable(TAG, android.util.Log.INFO)) {
            Log.setLogger(object : Log.Logger {
                override fun d(
                    tag: String,
                    message: String,
                    throwable: Throwable?
                ) {
                    android.util.Log.e(tag, "[DEBUG] $message", throwable)
                }

                override fun i(
                    tag: String,
                    message: String,
                    throwable: Throwable?
                ) {
                    android.util.Log.e(tag, "[INFO] $message", throwable)
                }

                override fun w(
                    tag: String,
                    message: String,
                    throwable: Throwable?
                ) {
                    android.util.Log.e(tag, "[WARN] $message", throwable)
                }

                override fun e(
                    tag: String,
                    message: String,
                    throwable: Throwable?
                ) {
                    android.util.Log.e(tag, "[ERROR] $message", throwable)
                }
            })
        }
        // Resolve eagerly where they used to be constructed: both register observers/receivers in
        // their constructors, so keep the same start-up timing as before the Koin migration.
        // FlowReader pulls in SettingsRepository, which migrates and loads the filter preferences
        // on ApplicationScope.
        get<UacManager>()
        get<FlowReader>()
        // Set application theme when launching.
        themeModeOf(themeMode).applyToSystem(this)
        // This is a separate thread to avoid disk read on main thread and improve startup time
        CoroutineScope(Dispatchers.Default).launch {
            // https://github.com/androidx/media/issues/805
            if (needsMissingOnDestroyCallWorkarounds()) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
            }

            LyricWidgetProvider.update(this@GramophoneApplication)

            delay(10000.milliseconds) // Wait until we are idle with useless IO
            withContext(Dispatchers.IO) {
                // Clean up old logs
                val selfLogDir = File(cacheDir, "SelfLog")
                selfLogDir.listFiles()?.forEach(File::delete)
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .diskCache(null)
            .components {
                add(CoilArtPipeline.ThumbnailKeyer())
                add(CoilArtPipeline.AlbumThumbnailKeyer())
                add(CoilArtPipeline.AudioCoverKeyer())
                add(AndroidUriMapper())
                add(CoilArtPipeline.ThumbnailMapper())
                add(CoilArtPipeline.AudioCoverMapper())
                add(CoilArtPipeline.AlbumThumbnailMapper())
                add(CoilArtPipeline.ThumbnailFetcherFactory())
                add(CoilArtPipeline.AlbumThumbnailFetcherFactory())
                add(CoilArtPipeline.SongCoverFetcherFactory())
            }
            .run {
                if (!BuildConfig.DEBUG) this else
                    logger(object : Logger {
                        override var minLevel = Logger.Level.Verbose
                        override fun log(
                            tag: String,
                            level: Logger.Level,
                            message: String?,
                            throwable: Throwable?
                        ) {
                            if (level < minLevel) return
                            val println = { it: String ->
                                when (level) {
                                    Logger.Level.Verbose -> Log.d(tag, it)
                                    Logger.Level.Debug -> Log.d(tag, it)
                                    Logger.Level.Info -> Log.i(tag, it)
                                    Logger.Level.Warn -> Log.w(tag, it)
                                    Logger.Level.Error -> Log.e(tag, it)
                                }
                            }
                            if (message != null) {
                                println(message)
                            }
                            // Let's keep the log readable and ignore normal events' stack traces.
                            if (throwable != null && throwable !is NullRequestDataException
                                && throwable !is CoilArtPipeline.NoAlbumArtException
                                && (throwable !is IOException
                                        || throwable.message != "No album art found"
                                        && throwable.message != "No embedded album art found"
                                        && throwable.message != "No thumbnails in Downloads directories"
                                        && throwable.message != "No thumbnails in top-level directories")
                            ) {
                                println(Log.getThrowableString(throwable)!!)
                            }
                        }
                    })
            }
            .build()
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        // TODO convert to notification that opens BugHandlerActivity on click, and let JVM
        //  go through the normal exception process (to get stats from play). disadvantage: we can't
        //  cheat the statistic that way
        val exceptionMessage = Log.getThrowableString(e)
        val threadName = Thread.currentThread().name
        Log.e(TAG, "Error on thread $threadName:\n $exceptionMessage")
        val intent = Intent(this, BugHandlerActivity::class.java)
        intent.putExtra("exception_message", exceptionMessage)
        intent.putExtra("thread", threadName)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        exitProcess(10)
    }

    private fun isAlpsBoostFwkPresent(): Boolean {
        try {
            Class.forName("com.mediatek.boostfwk.BoostFwkManagerImpl")
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    private fun isColorOS(): Boolean {
        val props = listOf(
            "ro.build.version.opporom",
            "ro.oplus.os.version"
        )
        return props.any {
            !getSystemProperty(it).isNullOrBlank()
        }
    }
}
