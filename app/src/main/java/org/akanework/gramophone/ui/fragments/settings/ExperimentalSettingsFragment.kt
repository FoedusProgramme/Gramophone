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

package org.akanework.gramophone.ui.fragments.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.media3.common.util.Log
import androidx.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingsActivity
import java.io.File
import java.nio.charset.Charset

class ExperimentalSettingsActivity : BaseSettingsActivity(
    R.string.settings_experimental_settings,
    { ExperimentalSettingsFragment() })

class ExperimentalSettingsFragment : BasePreferenceFragment() {

    private lateinit var e: Exception

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_experimental, rootKey)
        findPreference<Preference>("crash")!!.isVisible = BuildConfig.DEBUG
        if (!Flags.OFFLOAD)
            findPreference<Preference>("offload")!!.isVisible = false
        if (!Flags.MQ_PREVIEW)
            findPreference<Preference>("mq_preview")!!.isVisible = false
        if (BuildConfig.DEBUG)
            e = RuntimeException("skill issue")
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO).launch {
            val selfLogDir = File(requireContext().cacheDir, "SelfLog")
            selfLogDir.listFiles()?.forEach(File::delete)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "crash" && BuildConfig.DEBUG) {
            throw IllegalArgumentException("I crashed your app >:)", e)
        } else if (preference.key == "self_log") {
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
                val selfLogDir = File(requireContext().cacheDir, "SelfLog")
                val f = File(
                    selfLogDir.also { it.mkdirs() },
                    "GramophoneLog${System.currentTimeMillis()}.txt"
                )
                f.writeText(
                    "SDK: ${Build.VERSION.SDK_INT}\nDevice: ${Build.BRAND} ${Build.DEVICE} " +
                            "(${Build.MANUFACTURER} ${Build.PRODUCT} ${Build.MODEL})\nVersion: " +
                            "${BuildConfig.MY_VERSION_NAME} ${BuildConfig.RELEASE_TYPE} (${context?.packageName})" +
                            "\n$stdout\n$stderr"
                )
                withContext(Dispatchers.Main) {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TITLE, "Gramophone Logs")
                        putExtra(
                            Intent.EXTRA_STREAM,
                            FileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.fileProvider",
                                f
                            )
                        )
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    startActivity(shareIntent)
                }
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}
