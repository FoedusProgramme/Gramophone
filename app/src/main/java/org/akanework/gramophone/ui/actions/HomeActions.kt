/*
 *     Copyright (C) 2026 Akane Foundation
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
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.ui.graphics.vector.ImageVector
import coil3.SingletonImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.library.LibraryRefresher
import org.akanework.gramophone.ui.components.compose.AppDialog
import org.akanework.gramophone.ui.nav.MainSettingsKey
import org.akanework.gramophone.ui.nav.SearchKey

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
     * [refresher], which outlives the screen and reports the result to the user by itself.
     */
    fun run(
        env: AppActionEnv,
        equalizer: ActivityResultLauncher<Intent>,
        refresher: LibraryRefresher,
        action: HomeMenuAction,
    ) {
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
                SingletonImageLoader.get(env.context).memoryCache?.clear()
                refresher.refresh(reportToUser = true)
            }

            HomeMenuAction.Refresh -> {
                SingletonImageLoader.get(env.context).memoryCache?.clear()
                env.dialogs.show(AppDialog.Message(
                    title = env.getString(R.string.did_you_know),
                    message = env.getString(R.string.refresh_did_you_know),
                    icon = Icons.Outlined.Refresh,
                ))
                Toast.makeText(env.context, R.string.refreshing_wait, Toast.LENGTH_LONG).show()
                refresher.fullRescan()
            }

            HomeMenuAction.Settings -> env.navigate(MainSettingsKey)

            HomeMenuAction.Shuffle -> env.scope.launch {
                val songs = env.reader.songListFlow.first()
                if (songs.isNotEmpty()) {
                    LibraryActions.shuffleAll(env, songs, env.getString(R.string.category_songs))
                } else {
                    env.player?.setMediaItems(listOf())
                }
            }
        }
    }
}
