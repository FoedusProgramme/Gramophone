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
package org.akanework.gramophone.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.ui.BaseActivity
import org.akanework.gramophone.ui.screens.PickerEntry
import org.akanework.gramophone.ui.screens.PickerScreen

/**
 * The activities other apps call to pick a song or a playlist: the library's list, each row
 * returning its item as the result.
 */
abstract class PickerActivity<T : Any> : BaseActivity() {
    companion object {
        private const val PERMISSION_READ_MEDIA_AUDIO = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GramophoneTheme {
                val items by remember { itemsFlow() }.collectAsState(emptyList())
                val entries = remember(items) {
                    items.map { entryOf(it) }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                }
                PickerScreen(
                    title = getTitleStr(),
                    entries = entries,
                    onPick = { onSelected(it) },
                    onBack = { finish() },
                )
            }
        }
        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(
                this,
                if (hasScopedStorageWithMediaTypes())
                    arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
                else if (hasScopedStorageV2())
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                else
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                PERMISSION_READ_MEDIA_AUDIO,
            )
        } else if (!gramophoneApplication.reader.hadFirstRefresh) {
            CoroutineScope(Dispatchers.Default).launch {
                gramophoneApplication.reader.refresh()
            }
        }
    }

    protected abstract fun itemsFlow(): Flow<List<T>>
    protected abstract fun entryOf(item: T): PickerEntry<T>
    protected abstract fun getTitleStr(): String
    protected abstract fun onSelected(item: T)

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_READ_MEDIA_AUDIO) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                CoroutineScope(Dispatchers.Default).launch {
                    gramophoneApplication.reader.refresh()
                }
            } else {
                Toast.makeText(this, getString(R.string.grant_audio), Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.setData("package:$packageName".toUri())
                startActivity(intent)
                finish()
            }
        }
    }
}
