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

package org.akanework.gramophone.ui.state

import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emptyFlow
import org.akanework.gramophone.logic.comparators.SupportComparator
import org.akanework.gramophone.logic.emitOrDie
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.ui.LibraryAdapterTypes
import org.akanework.gramophone.ui.library.Sorter
import uk.akane.libphonograph.items.FileNode
import uk.akane.libphonograph.reader.FlowReader

/**
 * One folder as the tab shows it: its [path], its subfolders sorted by the tab's sort preference
 * and its songs sorted by the song list's. All three always come from the same folder, so a page
 * never shows one folder's subfolders with another's songs.
 */
@Immutable
class FolderPage(val path: List<String>, val folders: List<FileNode>, val songs: List<MediaItem>) {
    /** The folder's name, or [ROOT_TITLE] for the root. Also the title of its songs' queue. */
    val title: String = path.lastOrNull() ?: ROOT_TITLE

    companion object {
        const val ROOT_TITLE = "/"
    }
}

/**
 * State of the Folders (shallow) or Filesystem (detailed) tab: the current folder path, the
 * folder shown as a [FolderPage], and the song list's choices as a nested [LibraryTabState].
 */
@Stable
class FolderTabState(
    val isDetailed: Boolean,
    prefs: SharedPreferences,
    reader: FlowReader,
    scope: CoroutineScope,
    /** Where the folder shown is saved, so it survives process death. */
    private val savedState: SavedStateHandle,
) {
    private val pathKey = if (isDetailed) "folder_path_detailed" else "folder_path_shallow"

    val sort = SortPrefState(
        if (isDetailed) LibraryAdapterTypes.FOLDERS_DETAILED else LibraryAdapterTypes.FOLDERS_SHALLOW,
        prefs, SORT_TYPES, Sorter.Type.ByFilePathAscending,
    )

    /** `null` until the default location was chosen, unless one was saved. */
    private val fileNodePath =
        MutableStateFlow<List<String>?>(savedState.get<ArrayList<String>>(pathKey))

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
        emit(path!! to item)
    }.sharePauseableIn(
        CoroutineScope(scope.coroutineContext + Dispatchers.Default),
        SharingStarted.WhileSubscribed(), replay = 1
    )

    /**
     * The song list's sort, layout and queue title. Its items are set from [pageFlow] by [show],
     * so it has no flow of its own.
     */
    val songs = LibraryTabState(
        LibraryTabSpec.FolderSongs, prefs, reader, scope, flowOverride = emptyFlow(),
    )

    /** The current folder, with its subfolders and songs sorted. */
    val pageFlow: Flow<FolderPage> = combine(
        dataFlow, sort.sortTypeFlow, songs.sort.sortTypeFlow,
    ) { (path, item), sortType, songSortType ->
        FolderPage(path, sortFolders(item, sortType), songs.sortList(item.songList, songSortType))
    }.sharePauseableIn(
        CoroutineScope(scope.coroutineContext + Dispatchers.Default),
        SharingStarted.WhileSubscribed(5000), replay = 1
    )

    /** The folder shown, `null` until the first one was loaded. Set from [pageFlow] by [show]. */
    var page: FolderPage? by mutableStateOf(null)
        private set

    /** Shows [page], one of [pageFlow]'s, and hands its songs to the song list. */
    internal fun show(page: FolderPage) {
        this.page = page
        songs.items = page.songs
        songs.loaded = true
        songs.queueTitleOverride = page.title
        // A copy, since the path may be a sublist, which a saved state cannot hold.
        savedState[pathKey] = ArrayList(page.path)
    }

    private fun sortFolders(item: FileNode, sortType: Sorter.Type): List<FileNode> =
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

    fun enter(folder: String?) {
        val currentPath = fileNodePath.value ?: return
        if (folder != null) {
            fileNodePath.value = currentPath + folder
        } else if (currentPath.isNotEmpty()) {
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
