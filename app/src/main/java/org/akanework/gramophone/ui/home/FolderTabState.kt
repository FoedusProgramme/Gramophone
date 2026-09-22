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

package org.akanework.gramophone.ui.home

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.comparators.SupportComparator
import org.akanework.gramophone.logic.emitOrDie
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.ui.LibraryAdapterTypes
import org.akanework.gramophone.ui.adapters.Sorter
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.reader.FlowReader

/**
 * State of the Folders (shallow) or Filesystem (detailed) tab: the current folder path, the
 * folder list sorted by the tab's sort preference, and the folder's songs as a nested
 * [LibraryTabState].
 */
@Stable
class FolderTabState(
    val isDetailed: Boolean,
    prefs: SharedPreferences,
    reader: FlowReader,
    scope: CoroutineScope,
) {
    val sort = SortPrefState(
        if (isDetailed) LibraryAdapterTypes.FOLDERS_DETAILED else LibraryAdapterTypes.FOLDERS_SHALLOW,
        prefs, SORT_TYPES, Sorter.Type.ByFilePathAscending,
    )

    /** `null` until the default location was chosen. */
    private val fileNodePath = MutableStateFlow<List<String>?>(null)
    var path: List<String>? by mutableStateOf(null)
        private set

    private val liveData = if (isDetailed) reader.folderStructureFlow else reader.shallowFolderFlow

    private val dataFlow = liveData.combineTransform(fileNodePath) { root, path ->
        var item: FileNode? = null
        if (path != null) {
            item = root
            for (segment in path) {
                item = item?.folderList?.get(segment)
            }
        }
        // 1. path is null because we don't have any location yet, choose default path.
        // 2. item is null because folder no longer exists on disk, reset to default path
        if (item == null) {
            item = root
            val newPath = mutableListOf<String>()
            if (isDetailed) {
                // Enter as many single-child-only folders as we can starting from root
                while (item!!.folderList.size == 1 && item.songList.isEmpty()) {
                    newPath.add(item.folderList.keys.first())
                    item = item.folderList.values.first()
                }
            }
            // This may race with user click if a folder was deleted while a user clicks.
            fileNodePath.emitOrDie(newPath)
            return@combineTransform // we will run again with new path soon
        }
        emit(item)
    }.sharePauseableIn(
        CoroutineScope(scope.coroutineContext + Dispatchers.Default),
        SharingStarted.WhileSubscribed(), replay = 1
    )

    val folderFlow: Flow<List<FileNode>> = dataFlow.combine(sort.sortTypeFlow) { item, sortType ->
        when (sortType) {
            Sorter.Type.BySizeDescending -> item.folderList.values.sortedByDescending {
                it.folderList.size + it.songList.size
            }
            Sorter.Type.BySizeAscending -> item.folderList.values.sortedBy {
                it.folderList.size + it.songList.size
            }
            Sorter.Type.ByAddDateDescending -> item.folderList.values.sortedByDescending {
                it.addDate ?: Long.MIN_VALUE
            }
            Sorter.Type.ByAddDateAscending -> item.folderList.values.sortedBy {
                it.addDate ?: Long.MIN_VALUE
            }
            Sorter.Type.ByModifiedDateDescending -> item.folderList.values.sortedByDescending {
                it.modifiedDate ?: Long.MIN_VALUE
            }
            Sorter.Type.ByModifiedDateAscending -> item.folderList.values.sortedBy {
                it.modifiedDate ?: Long.MIN_VALUE
            }
            Sorter.Type.ByFilePathDescending -> item.folderList.values.sortedWith(
                SupportComparator.createAlphanumericComparator(inverted = true, cnv = { it.folderName })
            )
            else -> item.folderList.values.sortedWith(
                SupportComparator.createAlphanumericComparator(cnv = { it.folderName })
            )
        }
    }.sharePauseableIn(
        CoroutineScope(scope.coroutineContext + Dispatchers.Default),
        SharingStarted.WhileSubscribed(5000), replay = 1
    )

    var folders: List<FileNode> by mutableStateOf(emptyList())
        internal set

    val songs = LibraryTabState(
        LibraryTabSpec.FolderSongs, prefs, reader, scope,
        flowOverride = dataFlow.map { it.songList },
    )

    /** Which way the last [enter] went, for the slide animation. */
    var lastNavigationWasUp: Boolean by mutableStateOf(false)
        private set

    init {
        scope.launch {
            fileNodePath.collect {
                path = it
                songs.queueTitleOverride = it?.lastOrNull() ?: "/"
            }
        }
    }

    fun enter(folder: String?) {
        val currentPath = fileNodePath.value ?: return
        if (folder != null) {
            lastNavigationWasUp = false
            fileNodePath.value = currentPath + folder
        } else if (currentPath.isNotEmpty()) {
            lastNavigationWasUp = true
            fileNodePath.value = currentPath.subList(0, currentPath.size - 1)
        }
    }

    companion object {
        val SORT_TYPES = setOf(
            Sorter.Type.ByFilePathAscending, Sorter.Type.ByFilePathDescending,
            Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending,
            Sorter.Type.ByAddDateDescending, Sorter.Type.ByAddDateAscending,
            Sorter.Type.ByModifiedDateDescending, Sorter.Type.ByModifiedDateAscending,
        )
    }
}
