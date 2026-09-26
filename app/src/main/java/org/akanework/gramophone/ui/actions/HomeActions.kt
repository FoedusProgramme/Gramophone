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
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.lifecycleScope
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.SdScanner
import org.akanework.gramophone.ui.MainActivity
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
    fun search(activity: MainActivity) {
        activity.navigateTo(SearchKey(null))
    }

    fun run(activity: MainActivity, action: HomeMenuAction) {
        when (action) {
            HomeMenuAction.Equalizer -> {
                val intent =
                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        // EXTRA_PACKAGE_NAME is probably not needed but might as well add for good measure
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, activity.packageName)
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, activity.getPlayer()?.audioSessionId)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                try {
                    if (Settings.System.getString(activity.contentResolver, "firebase.test.lab") != "true") {
                        activity.startingActivity.launch(intent)
                    }
                } catch (_: ActivityNotFoundException) {
                    // Let's show a toast here if no system inbuilt EQ was found.
                    Toast.makeText(activity, R.string.equalizer_not_found, Toast.LENGTH_LONG).show()
                }
            }

            HomeMenuAction.QuickRefresh -> {
                val imageLoader = SingletonImageLoader.get(activity)
                imageLoader.memoryCache?.clear()
                activity.updateLibrary {
                    showRefreshDoneSnackBar(
                        activity, runBlocking { activity.reader.songListFlow.first().size }
                    )
                }
            }

            HomeMenuAction.Refresh -> {
                val context = activity
                val imageLoader = SingletonImageLoader.get(context)
                imageLoader.memoryCache?.clear()
                activity.dialogs.show(AppDialog.Message(
                    title = context.getString(R.string.did_you_know),
                    message = context.getString(R.string.refresh_did_you_know),
                    icon = Icons.Outlined.Refresh,
                ))
                Toast.makeText(context, R.string.refreshing_wait, Toast.LENGTH_LONG).show()
                CoroutineScope(Dispatchers.Default).launch {
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
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(context, str, Toast.LENGTH_SHORT).show()
                                }
                                return@scanEverything
                            }
                            activity.updateLibrary(false) {
                                showRefreshDoneSnackBar(
                                    activity, runBlocking { activity.reader.songListFlow.first().size }
                                )
                            }
                        }
                    } else {
                        val job = launch(Dispatchers.IO) {
                            MediaStoreCompat.scanEverything(context)
                        }
                        while (!job.isCompleted) {
                            delay(5000)
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(
                                    context, context.getString(R.string.refreshing_wait),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }

            HomeMenuAction.Settings -> activity.navigateTo(MainSettingsKey())

            HomeMenuAction.Shuffle -> {
                val controller = activity.getPlayer()
                runBlocking { activity.reader.songListFlow.first() }.takeIf { it.isNotEmpty() }
                    ?.also {
                        LibraryActions.shuffleAll(activity, it, activity.getString(R.string.category_songs))
                    } ?: controller?.setMediaItems(listOf())
            }
        }
    }

    private fun showRefreshDoneSnackBar(activity: MainActivity, count: Int) {
        activity.lifecycleScope.launch {
            activity.dialogs.snackbar(
                activity.getString(R.string.refreshed_songs, count),
                activity.getString(R.string.dismiss),
            )
        }
    }
}
