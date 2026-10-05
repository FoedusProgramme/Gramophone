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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import org.akanework.gramophone.logic.getStringStrict
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.ui.library.LayoutType
import org.akanework.gramophone.ui.library.Sorter
import uk.akane.libphonograph.reader.FlowReader

/**
 * The sort choice of one list. `"S<adapterType>"` holds the active [Sorter.Type] and
 * `"S<adapterType>_reverse_<base>"` remembers the reverse toggle per base type.
 */
@Stable
class SortPrefState(
    private val adapterType: Int,
    val prefs: SharedPreferences,
    val sortTypes: Set<Sorter.Type>,
    initialSortType: Sorter.Type,
) {
    private val sortKey get() = "S$adapterType"
    private fun reverseKey(base: Sorter.Type) = "S${adapterType}_reverse_$base"

    val sortTypeFlow = MutableStateFlow(readSortType(initialSortType))
    var sortType: Sorter.Type by mutableStateOf(sortTypeFlow.value)
        private set

    private fun readSortType(initial: Sorter.Type): Sorter.Type {
        val pref = try {
            Sorter.Type.valueOf(prefs.getStringStrict(sortKey, Sorter.Type.None.toString())!!)
        } catch (_: IllegalArgumentException) {
            Sorter.Type.None
        }
        return if (pref != Sorter.Type.None && sortTypes.contains(pref)) pref else initial
    }

    /** The menu entry that is active: either [sortType] itself or its inverse. */
    fun activeSortBase(candidates: Collection<Sorter.Type>): Sorter.Type? =
        candidates.find { it == sortType || Sorter.Type.inverse(it) == sortType }

    val isReversed: Boolean
        get() {
            val base = activeSortBase(SORT_MENU_ORDER) ?: return false
            return sortType != base && sortType != Sorter.Type.None
        }

    val canReverse: Boolean get() = Sorter.Type.inverse(sortType) != null

    private fun apply(type: Sorter.Type) {
        sortTypeFlow.value = type
        sortType = type
    }

    /** User picked [baseType] in the sort menu: honour the remembered reverse flag for it. */
    fun selectSort(baseType: Sorter.Type) {
        if (activeSortBase(listOf(baseType)) != null) return
        val reverse = prefs.getBoolean(reverseKey(baseType), false)
        val target = if (!reverse) baseType else Sorter.Type.inverse(baseType) ?: baseType
        apply(target)
        prefs.edit { putString(sortKey, target.toString()) }
    }

    fun toggleReverse() {
        val base = activeSortBase(SORT_MENU_ORDER) ?: return
        val reversed = !isReversed
        val target = if (reversed) Sorter.Type.inverse(base) ?: base else base
        apply(target)
        prefs.edit {
            putBoolean(reverseKey(base), reversed)
            putString(sortKey, target.toString())
        }
    }

    companion object {
        /** Base sort types in `sort_menu.xml` order. */
        val SORT_MENU_ORDER = listOf(
            Sorter.Type.NaturalOrder, Sorter.Type.ByTitleAscending,
            Sorter.Type.ByArtistAscending, Sorter.Type.ByArtistYearAscending,
            Sorter.Type.ByAlbumTitleAscending, Sorter.Type.ByAlbumArtistAscending,
            Sorter.Type.ByAlbumArtistYearAscending, Sorter.Type.ByAlbumYearDescending,
            Sorter.Type.BySizeDescending, Sorter.Type.ByAddDateDescending,
            Sorter.Type.ByReleaseDateDescending, Sorter.Type.ByModifiedDateDescending,
            Sorter.Type.ByFilePathAscending,
        )
    }
}

/**
 * The state of one list: the sorted items plus the layout and sort choices, persisted under the
 * `"L<adapterType>"` and `"S<adapterType>"` preference keys. Home tabs keep theirs in
 * [HomeViewModel], so the sorted list survives the home being covered by another page.
 */
@Stable
class LibraryTabState<T : Any>(
    val spec: LibraryTabSpec<T>,
    val prefs: SharedPreferences,
    reader: FlowReader,
    scope: CoroutineScope,
    flowOverride: Flow<List<T>>? = null,
) {
    private val sorter = Sorter(spec.helper, null, spec.rawOrderExposed)
    val sort = SortPrefState(spec.adapterType, prefs, sorter.getSupportedTypes(), spec.initialSortType)
    val sortTypes: Set<Sorter.Type> get() = sort.sortTypes
    val sortType: Sorter.Type get() = sort.sortType

    var layoutType: LayoutType by mutableStateOf(readLayoutType())
        private set

    /** Sorted items (empty until the first list arrived, see [loaded]). */
    var items: List<T> by mutableStateOf(emptyList())
        internal set
    var loaded: Boolean by mutableStateOf(false)
        internal set

    /** Title of the queue created from this list, when it is not the tab's own. */
    var queueTitleOverride: String? by mutableStateOf(null)

    /** The raw list combined with the sort choice. Shared, so re-subscribing within 5s is free. */
    val sortedFlow: Flow<List<T>> = (flowOverride ?: spec.flow(reader, prefs))
        .combine(sort.sortTypeFlow) { list, st -> sortList(list, st) }
        .sharePauseableIn(
            CoroutineScope(scope.coroutineContext + Dispatchers.Default),
            SharingStarted.WhileSubscribed(5000), replay = 1
        )

    fun titleOf(item: T): String? = spec.titleOf(prefs, item)

    fun isPinned(item: T) = titleOf(item) == null

    internal fun sortList(list: List<T>, type: Sorter.Type): List<T> =
        ArrayList(list).apply {
            val (cmp, reverseFirst) = sorter.getComparator(type)
            if (reverseFirst) reverse()
            if (cmp != null) {
                sortWith { o1, o2 ->
                    if (isPinned(o1) && !isPinned(o2)) -1
                    else if (!isPinned(o1) && isPinned(o2)) 1
                    else if (isPinned(o1) && isPinned(o2))
                        compareBy<T> { spec.getPinnedOrder(it) }.compare(o1, o2)
                    else cmp.compare(o1, o2)
                }
            }
        }

    fun fastScrollHintFor(item: T, position: Int): String =
        sorter.getFastScrollHintFor(item, position, sortType) ?: "-"

    private val layoutKey get() = "L" + spec.adapterType

    private fun readLayoutType(): LayoutType {
        val pref = try {
            LayoutType.valueOf(prefs.getStringStrict(layoutKey, LayoutType.NONE.toString())!!)
        } catch (_: IllegalArgumentException) {
            LayoutType.NONE
        }
        return if (pref != LayoutType.NONE && pref != spec.defaultLayoutType) pref
        else spec.defaultLayoutType
    }

    fun selectLayout(type: LayoutType) {
        if (layoutType == type) return
        layoutType = type
        prefs.edit { putString(layoutKey, type.toString()) }
    }
}
