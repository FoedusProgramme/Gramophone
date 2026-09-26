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

package org.akanework.gramophone.ui.actions

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.SdScanner
import org.akanework.gramophone.ui.components.compose.AppDialog
import org.akanework.gramophone.ui.nav.MainSettingsKey
import org.akanework.gramophone.ui.nav.SearchKey
import org.nift4.mediastorecompat.MediaStoreCompat

/** The home toolbar menu entries (search is an action button). */
enum class HomeMenuAction(val title: Int, val icon: ImageVector) {
    Shuffle(R.string.home_menu_shuffle, Icons.Outlined.Shuffle),
    QuickRefresh(R.string.home_menu_quick_refresh, Icons.Outlined.Refresh),
    Refresh(R.string.home_menu_refresh, Icons.Outlined.Refresh),
    Equalizer(R.string.home_menu_equalizer, Icons.Outlined.Equalizer),
    Settings(R.string.home_menu_settings, Icons.Outlined.Settings),
}

/** The home toolbar actions. */
object HomeActions {
    fun search(env: AppActionEnv) {
        env.navigate(SearchKey(null))
    }

    /**
     * Runs [action]. [equalizer] launches the system equalizer; the library scans run on
     * [AppActionEnv.appScope], as they must outlive the screen.
     */
    fun run(
        env: AppActionEnv,
        equalizer: ActivityResultLauncher<Intent>,
        action: HomeMenuAction,
    ) {
        val refresher = env.refresher
        val appScope = env.appScope
        when (action) {
            HomeMenuAction.Equalizer -> {
                val context = env.context
                val intent =
                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        // EXTRA_PACKAGE_NAME is probably not needed but might as well add for good measure
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, env.player?.audioSessionId)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                try {
                    if (Settings.System.getString(context.contentResolver, "firebase.test.lab") != "true") {
                        equalizer.launch(intent)
                    }
                } catch (_: ActivityNotFoundException) {
                    // Let's show a toast here if no system inbuilt EQ was found.
                    Toast.makeText(context, R.string.equalizer_not_found, Toast.LENGTH_LONG).show()
                }
            }

            HomeMenuAction.QuickRefresh -> {
                val imageLoader = SingletonImageLoader.get(env.context)
                imageLoader.memoryCache?.clear()
                refresher.refresh {
                    showRefreshDoneSnackBar(
                        env, runBlocking { env.reader.songListFlow.first().size }
                    )
                }
            }

            HomeMenuAction.Refresh -> {
                val imageLoader = SingletonImageLoader.get(env.context)
                imageLoader.memoryCache?.clear()
                env.dialogs.show(AppDialog.Message(
                    title = env.getString(R.string.did_you_know),
                    message = env.getString(R.string.refresh_did_you_know),
                    icon = Icons.Outlined.Refresh,
                ))
                Toast.makeText(env.context, R.string.refreshing_wait, Toast.LENGTH_LONG).show()
                // The scan outlives the screen, so it only holds on to the application.
                val context = env.context.applicationContext
                appScope.launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        SdScanner.scanEverything(context, 5000) { progress ->
                            if (progress.step != SdScanner.SimpleProgress.Step.DONE) {
                                val str = if (progress.percentage == null)
                                    context.getString(R.string.refreshing_wait)
                                else context.getString(
                                    R.string.still_refreshing,
                                    progress.step.ordinal,
                                    SdScanner.SimpleProgress.Step.DONE.ordinal - 1,
                                    "${progress.percentage}%"
                                )
                                appScope.launch(Dispatchers.Main) {
                                    Toast.makeText(context, str, Toast.LENGTH_SHORT).show()
                                }
                                return@scanEverything
                            }
                            refresher.refresh(smartScanFirst = false) {
                                showRefreshDoneSnackBar(
                                    env, runBlocking { env.reader.songListFlow.first().size }
                                )
                            }
                        }
                    } else {
                        val job = launch(Dispatchers.IO) {
                            MediaStoreCompat.scanEverything(context)
                        }
                        while (!job.isCompleted) {
                            delay(5000)
                            appScope.launch(Dispatchers.Main) {
                                Toast.makeText(
                                    context, context.getString(R.string.refreshing_wait),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }

            HomeMenuAction.Settings -> env.navigate(MainSettingsKey())

            HomeMenuAction.Shuffle -> {
                val controller = env.player
                runBlocking { env.reader.songListFlow.first() }.takeIf { it.isNotEmpty() }
                    ?.also {
                        LibraryActions.shuffleAll(env, it, env.getString(R.string.category_songs))
                    } ?: controller?.setMediaItems(listOf())
            }
        }
    }

    /** No-op once the screen that asked is gone: [AppActionEnv.scope] is cancelled with it. */
    private fun showRefreshDoneSnackBar(env: AppActionEnv, count: Int) {
        env.scope.launch {
            env.dialogs.snackbar(
                env.getString(R.string.refreshed_songs, count),
                env.getString(R.string.dismiss),
            )
        }
    }
}
