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

package org.akanework.gramophone.ui.components

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.getRecyclerView
import org.akanework.gramophone.ui.adapters.BaseAdapter
import org.akanework.gramophone.ui.adapters.BaseDecorAdapter

/**
 * CustomGridLayoutManager:
 *   A grid layout manager for making the grid view
 * intact.
 *
 * @author AkaneTan
 */
class CustomGridLayoutManager(
    context: Context
) : GridLayoutManager(context, FULL_SPAN_COUNT) {
    constructor(
        context: Context, attrs: AttributeSet?, defStyleAttr: Int,
        defStyleRes: Int
    ) : this(context)

    companion object {
        const val FULL_SPAN_COUNT = 12
        const val LIST_PORTRAIT_SPAN_SIZE = 12 // 100%
        const val LIST_LANDSCAPE_SPAN_SIZE = 6 // 50%
        const val GRID_PORTRAIT_SPAN_SIZE = 6 // 50%
        const val GRID_LANDSCAPE_SPAN_SIZE = 3 // 25%
        const val COMPACT_GRID_PORTRAIT_SPAN_SIZE = 4 // 33%
        const val COMPACT_GRID_LANDSCAPE_SPAN_SIZE = 2 // 16%
    }

    init {
        spanSizeLookup =
            object : SpanSizeLookup() {
                init {
                    isSpanGroupIndexCacheEnabled = true
                }

                private fun getAdapterAndPosForPos(pos: Int): Pair<RecyclerView.Adapter<*>, Int> {
                    val parent = getRecyclerView()
                    if (parent == null) {
                        throw IllegalStateException("null RecyclerView while looking up span")
                    }
                    var adapter = parent.adapter
                    var itemPosition = pos
                    loop@ while (adapter is ConcatAdapter) {
                        for (it in adapter.adapters) {
                            val c = it.itemCount
                            if (itemPosition < c) {
                                adapter = it
                                continue@loop
                            }
                            itemPosition -= c
                        }
                        throw IllegalStateException("can't find desired adapter?")
                    }
                    return adapter!! to itemPosition
                }

                override fun getSpanSize(position: Int): Int {
                    val (adapter, pos) = getAdapterAndPosForPos(position)
                    return when (adapter) {
                        is BaseDecorAdapter<*> -> {
                            spanCount
                        }

                        is BaseAdapter<*> -> {
                            adapter.getSpanSize()
                        }

                        else -> {
                            throw IllegalStateException("unsupported adapter ${adapter.javaClass.name}")
                        }
                    }
                }

                override fun getSpanIndex(position: Int, spanCount: Int): Int {
                    val (adapter, pos) = getAdapterAndPosForPos(position)
                    return when (adapter) {
                        is BaseDecorAdapter<*> -> {
                            0
                        }

                        is BaseAdapter<*> -> {
                            val spanSize = adapter.getSpanSize()
                            (pos % (spanCount / spanSize)) * spanSize
                        }

                        else -> {
                            throw IllegalStateException("unsupported adapter ${adapter.javaClass.name}")
                        }
                    }
                }
            }
    }
}
