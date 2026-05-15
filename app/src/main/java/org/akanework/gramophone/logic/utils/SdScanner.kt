/*
 * Copyright (C) 2024 nift4
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.akanework.gramophone.logic.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.util.Consumer
import org.nift4.mediastorecompat.Log
import org.nift4.mediastorecompat.MediaStoreCompat
import org.nift4.mediastorecompat.StorageManagerCompat
import java.io.File
import java.io.IOException

/**
 * Scan folders for media files recursively.
 *
 * Doesn't support playlist files on Android 9 or earlier, use [org.nift4.mediastorecompat.MediaStoreCompat.scanEverything] for
 * those versions.
 */
internal class SdScanner(private val context: Context, var progressFrequencyMs: Int = 250) {
    val progress = SimpleProgress()
    private val filesToProcess = hashSetOf<File>()
    private var lastUpdate = 0L
    private var roots: Set<File>? = null
    private var ignoreDb: Boolean? = null

    fun scan(inRoots: Set<File>, inIgnoreDb: Boolean, ignoreMtime: Boolean) {
        roots = inRoots
        this.ignoreDb = inIgnoreDb
        lastUpdate = SystemClock.elapsedRealtime()
        for (root in roots!!) {
            progress.set(SimpleProgress.Step.DIR_SCAN, root.path, null)
            root.walk().forEach {
                SystemClock.elapsedRealtime().apply {
                    if (lastUpdate + progressFrequencyMs < this) {
                        lastUpdate = this
                        progress.set(
                            SimpleProgress.Step.DIR_SCAN, root.path, null
                        )
                    }
                }
                if (!shouldScan(it, false)) {
                    return@forEach
                }
                filesToProcess.add(it)
            }
        }
        context.contentResolver.query(
            MediaStoreCompat.FILES_EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_MODIFIED
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val modifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val totalSize = cursor.count
            while (cursor.moveToNext()) {
                val mediaFile = File(cursor.getString(dataColumn))
                SystemClock.elapsedRealtime().apply {
                    if (lastUpdate + progressFrequencyMs < this) {
                        lastUpdate = this
                        progress.set(
                            SimpleProgress.Step.DATABASE, mediaFile.path,
                            (100 * cursor.position) / totalSize
                        )
                    }
                }
                if ((ignoreMtime || !filesToProcess.contains(mediaFile) ||
                            mediaFile.lastModified() / 1000L >
                            cursor.getLong(modifiedColumn))
                    && shouldScan(mediaFile, true)
                ) {
                    filesToProcess.add(mediaFile)
                } else {
                    filesToProcess.remove(mediaFile)
                }
            }
        }
        if (filesToProcess.isEmpty()) {
            scannerEnded()
        } else {
            val pathsToProcess = filesToProcess.map { it.absolutePath }.toMutableList()
            MediaScannerConnection.scanFile(
                context,
                pathsToProcess.toTypedArray(),
                null
            ) { path: String, _: Uri ->
                if (!pathsToProcess.remove(path)) {
                    Log.w("SdScanner", "Android scanned $path but we never asked it to do so")
                }
                SystemClock.elapsedRealtime().apply {
                    if (lastUpdate + progressFrequencyMs < this) {
                        lastUpdate = this
                        progress.set(
                            SimpleProgress.Step.SCAN, path,
                            (100 * (filesToProcess.size - pathsToProcess.size))
                                    / filesToProcess.size
                        )
                    }
                }
                if (pathsToProcess.isEmpty()) {
                    scannerEnded()
                }
            }
        }
    }

    private fun scannerEnded() {
        progress.set(SimpleProgress.Step.DONE, null, 100)
        progress.reset()
        filesToProcess.clear()
        roots = null
        ignoreDb = null
    }

    @Throws(IOException::class)
    fun shouldScan(inFile: File?, fromDb: Boolean): Boolean {
        var file = inFile
        if (ignoreDb != false && fromDb) {
            return true
        }
        while (file != null) {
            if (roots!!.contains(file)) {
                return true
            }
            file = file.parentFile
        }
        return false
    }

    fun cleanup() {
        progress.cleanup()
    }

    class SimpleProgress {
        var step = Step.NOT_STARTED
            private set
        var path: String? = null
            private set
        var percentage: Int? = null
            private set
        private val listeners = arrayListOf<Consumer<SimpleProgress>>()

        fun set(step: Step, path: String?, percentage: Int?) {
            this.step = step
            this.path = path
            this.percentage = percentage
            listeners.forEach { it.accept(this) }
        }

        fun addListener(listener: Consumer<SimpleProgress>) {
            listeners.add(listener)
        }

        fun removeListener(listener: Consumer<SimpleProgress>) {
            listeners.remove(listener)
        }

        fun reset() {
            step = Step.NOT_STARTED
            path = null
            percentage = null
        }

        fun cleanup() {
            listeners.clear()
        }

        enum class Step {
            NOT_STARTED, DIR_SCAN, DATABASE, SCAN, DONE
        }
    }

    companion object {
        fun scan(
            context: Context, root: File, ignoreDb: Boolean, ignoreMtime: Boolean = false,
            progressFrequencyMs: Int = 250, listener: Consumer<SimpleProgress>? = null
        ) {
            val scanner = SdScanner(context, progressFrequencyMs)
            if (listener != null) {
                scanner.progress.addListener { t ->
                    if (t.step == SimpleProgress.Step.DONE) {
                        // remove listener again to avoid leaking memory
                        scanner.cleanup()
                    }
                    listener.accept(t)
                }
            }
            scanner.scan(setOf(root), ignoreDb, ignoreMtime)
        }

        fun scanEverything(
            context: Context,
            progressFrequencyMs: Int = 250,
            ignoreMtime: Boolean = true,
            listener: Consumer<SimpleProgress>? = null
        ) {
            val scanner = SdScanner(context, progressFrequencyMs)
            if (listener != null) {
                scanner.progress.addListener { t ->
                    if (t.step == SimpleProgress.Step.DONE) {
                        // remove listener again to avoid leaking memory
                        scanner.cleanup()
                    }
                    listener.accept(t)
                }
            }
            val roots = hashSetOf<File>()
            for (volume in StorageManagerCompat.getStorageVolumes(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    volume.mediaStoreVolumeName == null) continue
                if (!volume.state.startsWith(Environment.MEDIA_MOUNTED)) continue
                roots.add(volume.directory!!)
            }
            scanner.scan(roots, false, ignoreMtime)
        }
    }
}