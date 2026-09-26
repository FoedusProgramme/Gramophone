/*
 *     Copyright (C) 2025 Akane Foundation
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

package org.akanework.gramophone.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.media3.common.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.compose.rememberStringPreference
import org.akanework.gramophone.ui.components.settings.DropdownPreferenceRow
import org.akanework.gramophone.ui.components.settings.NavigationPreferenceRow
import org.akanework.gramophone.ui.components.settings.PreferenceGroup
import org.akanework.gramophone.ui.components.settings.PreferenceScreen
import org.akanework.gramophone.ui.components.settings.PreferenceSectionHeader
import org.akanework.gramophone.ui.components.settings.SwitchPreferenceRow
import java.io.File
import java.nio.charset.Charset

@Composable
fun ExperimentalSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colorAccuracy = rememberBooleanPreference("color_accuracy", false)
    val offload = rememberStringPreference("offload", "0")
    val mqPreview = rememberBooleanPreference("mq_preview", false)

    val rows = buildList<@Composable (Shape) -> Unit> {
        add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_color_accuracy),
                subtitle = stringResource(R.string.settings_color_accuracy_summary),
                checked = colorAccuracy.value,
                onCheckedChange = { colorAccuracy.set(it) },
            )
        }
        if (BuildConfig.DEBUG) add { shape ->
            NavigationPreferenceRow(
                shape,
                title = stringResource(R.string.settings_crash_application),
                subtitle = stringResource(R.string.settings_crash_application_summary),
                onClick = { throw IllegalArgumentException("I crashed your app >:)", RuntimeException("skill issue")) },
            )
        }
        add { shape ->
            NavigationPreferenceRow(
                shape,
                title = stringResource(R.string.settings_export_logs),
                onClick = { exportLogs(context.applicationContext) },
            )
        }
        if (Flags.OFFLOAD) add { shape ->
            DropdownPreferenceRow(
                shape,
                title = stringResource(R.string.settings_audio_offload),
                entries = stringArrayResource(R.array.offload_switch).toList(),
                values = stringArrayResource(R.array.offload_switch_val).toList(),
                value = offload.value,
                onValueChange = { offload.set(it) },
            )
        }
        if (Flags.MQ_PREVIEW) add { shape ->
            SwitchPreferenceRow(
                shape,
                title = stringResource(R.string.settings_mq_enabled),
                checked = mqPreview.value,
                onCheckedChange = { mqPreview.set(it) },
            )
        }
    }

    PreferenceScreen(title = stringResource(R.string.settings_experimental_settings), onBack = onBack, modifier = modifier) {
        PreferenceSectionHeader(stringResource(R.string.settings_category_misc))
        PreferenceGroup(rows)
    }
}

/**
 * Dumps logcat to the cache and hands the file to a share sheet. Runs on its own scope, so
 * leaving the page does not cut the dump short. [context] must be the application's.
 */
private fun exportLogs(context: Context) {
    Log.w("Gramophone", "Exporting logs...")
    CoroutineScope(Dispatchers.IO).launch {
        val p = ProcessBuilder()
            .command("logcat", "-dball")
            .start()
        val stdout = p.inputStream.readBytes().toString(Charset.defaultCharset())
        val stderr = p.errorStream.readBytes().toString(Charset.defaultCharset())
        runInterruptible {
            p.waitFor()
        }
        val selfLogDir = File(context.cacheDir, "SelfLog")
        val f = File(
            selfLogDir.also { it.mkdirs() },
            "GramophoneLog${System.currentTimeMillis()}.txt"
        )
        f.writeText(
            "SDK: ${Build.VERSION.SDK_INT}\nDevice: ${Build.BRAND} ${Build.DEVICE} " +
                    "(${Build.MANUFACTURER} ${Build.PRODUCT} ${Build.MODEL})\nVersion: " +
                    "${BuildConfig.MY_VERSION_NAME} ${BuildConfig.RELEASE_TYPE} (${context.packageName})" +
                    "\n$stdout\n$stderr"
        )
        withContext(Dispatchers.Main) {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TITLE, "Gramophone Logs")
                putExtra(
                    Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileProvider",
                        f
                    )
                )
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        }
    }
}
