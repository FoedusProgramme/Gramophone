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
import android.os.Build
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.media3.session.DefaultMediaNotificationProvider
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.di.appModule
import org.akanework.gramophone.di.viewModelModule
import org.akanework.gramophone.logic.init.CrashHandler
import org.akanework.gramophone.logic.init.ImageLoaderFactory
import org.akanework.gramophone.logic.init.LoggingInitializer
import org.akanework.gramophone.logic.init.StrictModeInitializer
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
import kotlin.time.Duration.Companion.milliseconds

class GramophoneApplication : Application(), SingletonImageLoader.Factory {

    companion object {
        private const val TAG = "GramophoneApplication"
    }

    init {
        @SuppressLint("DefaultUncaughtExceptionDelegation")
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
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
            modules(appModule, viewModelModule)
        }
        // disk read and write on first launch, but unavoidable as the night mode has to be known
        // before any activity starts
        val themeMode = defaultPrefs.getString("theme_mode", "0")
        StrictModeInitializer.install()
        android.util.Log.d(TAG, "GramophoneApplication.onCreate()")
        LoggingInitializer.install()
        // Resolve eagerly where they used to be constructed: both register observers/receivers in
        // their constructors, so keep the same start-up timing as before the Koin migration.
        // FlowReader pulls in SettingsRepository, which migrates and loads the filter preferences
        // on ApplicationScope.
        get<UacManager>()
        get<FlowReader>()
        // Set application theme when launching.
        themeModeOf(themeMode).applyToSystem(this)
        // This is a separate thread to avoid disk read on main thread and improve startup time
        get<ApplicationScope>().launch {
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

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoaderFactory.create(context)
}
