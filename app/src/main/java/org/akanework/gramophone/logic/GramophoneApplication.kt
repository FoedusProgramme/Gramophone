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
import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Debug
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.provider.MediaStore
import androidx.media3.common.util.Log
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.fragment.app.strictmode.FragmentStrictMode
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.ContentMetadata
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.NullRequestDataException
import coil3.size.pxOrElse
import coil3.toCoilUri
import coil3.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.source
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.BugHandlerActivity
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.LyricWidgetProvider
import org.lsposed.hiddenapibypass.LSPass
import org.nift4.gramophone.hificore.UacManager
import uk.akane.libphonograph.Constants
import uk.akane.libphonograph.reader.FlowReader
import uk.akane.libphonograph.utils.MiscUtils
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

class GramophoneApplication : Application(), SingletonImageLoader.Factory,
    Thread.UncaughtExceptionHandler, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "GramophoneApplication"
        // not actually defined in API, but CTS tested
        // https://cs.android.com/android/platform/superproject/main/+/main:packages/providers/MediaProvider/src/com/android/providers/media/LocalUriMatcher.java;drc=ddf0d00b2b84b205a2ab3581df8184e756462e8d;l=182
        private const val MEDIA_ALBUM_ART = "albumart"
    }

    init {
        @SuppressLint("DefaultUncaughtExceptionDelegation")
        Thread.setDefaultUncaughtExceptionHandler(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LSPass.setHiddenApiExemptions("")
        }
        if (BuildConfig.DEBUG) {
            System.setProperty("kotlinx.coroutines.debug", "on")
            @OptIn(ExperimentalComposeRuntimeApi::class)
            Composer.setDiagnosticStackTraceEnabled(true)
        }
    }

    val minSongLengthSecondsFlow = MutableSharedFlow<Long>(replay = 1)
    val blackListSetFlow = MutableSharedFlow<Set<String>>(replay = 1)
    val shouldUseEnhancedCoverReadingFlow = if (hasScopedStorageWithMediaTypes()) null else
        MutableSharedFlow<Boolean?>(replay = 1)
    val recentlyAddedFilterSecondFlow = MutableStateFlow(1_209_600L)
    lateinit var reader: FlowReader
        private set
    lateinit var uacManager: UacManager
        private set

    override fun onCreate() {
        super.onCreate()
        // disk read and write on first launch, but unavoidable as threads would race setDefaultNightMode
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (BuildConfig.DEBUG) {
            // Use StrictMode to find anti-pattern issues
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectAll()
                    .let {
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                            Build.VERSION.SDK_INT == Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                            it.permitExplicitGc() // platform bug, now fixed
                        } else it
                    }
                    .let {
                        if (Debug.isDebuggerConnected())
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
                    .let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            it.detectNonSdkApiUsage()
                        } else it
                    }
                    .permitNonSdkApiUsage()
                    .penaltyDeath()
                    .build()
            )
            FragmentStrictMode.defaultPolicy = FragmentStrictMode.Policy.Builder()
                .detectFragmentReuse()
                .detectFragmentTagUsage()
                .detectRetainInstanceUsage()
                .detectSetUserVisibleHint()
                //.detectTargetFragmentUsage() TODO onDisplayPreferenceDialog()
                .detectWrongFragmentContainer()
                .detectWrongNestedHierarchy()
                .penaltyDeath()
                .build()
        }
        android.util.Log.d(TAG, "GramophoneApplication.onCreate()")
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
        uacManager = UacManager(this)
        Flags.PLAYLIST_EDITING = prefs.getBooleanStrict("playlist_editing", false)
        reader = FlowReader(
            this,
            if (BuildConfig.DISABLE_MEDIA_STORE_FILTER) MutableStateFlow(0) else
                minSongLengthSecondsFlow,
            blackListSetFlow,
            if (hasScopedStorageWithMediaTypes()) MutableStateFlow(null) else
                shouldUseEnhancedCoverReadingFlow!!,
            recentlyAddedFilterSecondFlow,
            MutableStateFlow(true), "gramophoneAlbumCover"
        )
        // Set application theme when launching.
        when (prefs.getString("theme_mode", "0")) {
            "0" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }

            "1" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }

            "2" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
        // This is a separate thread to avoid disk read on main thread and improve startup time
        CoroutineScope(Dispatchers.Default).launch {
            onSharedPreferenceChanged(prefs, null) // reload all values
            prefs.registerOnSharedPreferenceChangeListener(this@GramophoneApplication)

            // https://github.com/androidx/media/issues/805
            if (needsMissingOnDestroyCallWorkarounds()) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
            }

            LyricWidgetProvider.update(this@GramophoneApplication)
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        runBlocking {
            if (key == null || key == "mediastore_filter") {
                minSongLengthSecondsFlow.emit(
                    prefs.getInt(
                        "mediastore_filter",
                        resources.getInteger(R.integer.filter_default_sec)
                    ).toLong()
                )
            }
            if (key == null || key == "folderFilter") {
                blackListSetFlow.emit(prefs.getStringSet("folderFilter", setOf()) ?: setOf())
            }
            if ((key == null || key == "album_covers") && !hasScopedStorageWithMediaTypes()) {
                shouldUseEnhancedCoverReadingFlow!!.emit(prefs.getBoolean("album_covers", true))
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .diskCache(null)
            .components {
                add(Fetcher.Factory { data, options, _ ->
                    if (data !is Uri) return@Factory null
                    if (data.scheme != "gramophoneSongCover") return@Factory null
                    return@Factory Fetcher {
                        val file = File(data.path!!)
                        val uri = ContentUris.appendId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon(), data.authority!!.toLong()
                        ).appendPath(MEDIA_ALBUM_ART).build()
                        val bmp = if (options.size.width.pxOrElse { 0 } > 300
                            && options.size.height.pxOrElse { 0 } > 300) try {
                            if (hasScopedStorageV1()) {
                                ThumbnailUtils.createAudioThumbnail(file, options.size.let {
                                    Size(
                                        it.width.pxOrElse { throw IllegalArgumentException("missing required size") },
                                        it.height.pxOrElse { throw IllegalArgumentException("missing required size") })
                                }, null)
                            } else null // TODO: fallback for <Q?
                        } catch (e: IOException) {
                            if (e.message != "No embedded album art found" &&
                                e.message != "No thumbnails in Downloads directories" &&
                                e.message != "No thumbnails in top-level directories" &&
                                e.message != "No album art found")
                                throw e
                            null
                        } else null
                        if (bmp != null) {
                            ImageFetchResult(
                                bmp.asImage(), true, DataSource.DISK
                            )
                        } else {
                            if (uri == null) return@Fetcher null
                            val stream = contentResolver.openAssetFileDescriptor(uri, "r")
                            checkNotNull(stream) { "Unable to open '$uri'." }
                            SourceFetchResult(
                                source = ImageSource(
                                    source = stream.createInputStream().source().buffer(),
                                    fileSystem = options.fileSystem,
                                    metadata = ContentMetadata(uri.toCoilUri(), stream),
                                ),
                                mimeType = contentResolver.getType(uri),
                                dataSource = DataSource.DISK,
                            )
                        }
                    }
                })
                add(Fetcher.Factory { data, options, _ ->
                    if (data !is Uri) return@Factory null
                    if (data.scheme != "gramophoneAlbumCover") return@Factory null
                    return@Factory Fetcher {
                        val cover = MiscUtils.findBestCover(File(data.path!!))
                        if (cover == null) {
                            val uri =
                                ContentUris.withAppendedId(Constants.baseAlbumCoverUri, data.authority!!.toLong())
                            val contentResolver = options.context.contentResolver
                            val afd = contentResolver.openAssetFileDescriptor(uri, "r")
                            checkNotNull(afd) { "Unable to open '$uri'." }
                            return@Fetcher SourceFetchResult(
                                source = ImageSource(
                                    source = afd.createInputStream().source().buffer(),
                                    fileSystem = options.fileSystem,
                                    metadata = ContentMetadata(data, afd),
                                ),
                                mimeType = contentResolver.getType(uri),
                                dataSource = DataSource.DISK,
                            )
                        }
                        return@Fetcher SourceFetchResult(
                            ImageSource(cover.toOkioPath(), options.fileSystem, null, null, null),
                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(cover.extension),
                            DataSource.DISK
                        )
                    }
                })
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
                                && (throwable !is IOException
                                        || throwable.message != "No album art found")
                            ) {
                                println(Log.getThrowableString(throwable)!!)
                            }
                        }
                    })
            }
            .build()
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        /*if (e.javaClass.name.endsWith("ForegroundServiceDidNotStartInTimeException") &&
            Build.BRAND == "samsung" &&
            Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2) {
            exitProcess(11)
        } TODO only uncomment if we know its needed, see https://github.com/FoedusProgramme/Gramophone/issues/204#issuecomment-2952400651*/
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
}
