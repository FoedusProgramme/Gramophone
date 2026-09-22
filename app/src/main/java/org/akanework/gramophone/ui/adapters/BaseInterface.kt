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

package org.akanework.gramophone.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.StateFlow
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView

/** Base of the RecyclerView adapters that carry their own header ("decor") row. */
abstract class BaseInterface<T : RecyclerView.ViewHolder>
    : MyRecyclerView.Adapter<T>(), PopupTextProvider {
    abstract val concatAdapter: ConcatAdapter
    abstract val itemHeightHelper: ItemHeightHelper?
    var onFullyDrawnListener: (() -> Unit)? = null
    abstract fun onTabReselected()
    protected fun reportFullyDrawn() {
        onFullyDrawnListener?.invoke()
        onFullyDrawnListener = null
    }

    // for decor
    abstract val context: Context
    abstract val layoutInflater: LayoutInflater
    abstract val canChangeLayout: Boolean
    abstract val sortType: StateFlow<Sorter.Type>
    abstract val sortTypes: Set<Sorter.Type>
    abstract var layoutType: BaseAdapter.LayoutType?
    abstract fun sort(type: Sorter.Type)
    abstract val itemCountForDecor: Int
}
