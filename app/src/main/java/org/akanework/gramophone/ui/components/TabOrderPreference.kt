/*
 *     Copyright (C) 2025 nift4
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
import android.content.res.TypedArray
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.DialogPreference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.home.mapSettingToTabList
import org.akanework.gramophone.ui.home.mapTabListToSetting
import org.akanework.gramophone.ui.components.TabOrderPreference.TabOrderAdapter.TabOrderViewHolder

class TabOrderPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {
    private var _value = ""
    var value
        get() = _value
        set(new) {
            _value = new
            persistString(new)
        }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index) ?: ""
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        _value = getPersistedString((defaultValue as String?) ?: "")
    }

    override fun getDialogLayoutResource(): Int {
        return R.layout.tab_order_dialog
    }

    class TabOrderDialog : PreferenceDialogFragmentCompat() {
        private val adapter by lazy { TabOrderAdapter((preference as TabOrderPreference).value) }

        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)
            val recyclerView = view.findViewById<MyRecyclerView>(R.id.recyclerview)
            recyclerView.adapter = adapter
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(UP or DOWN, 0) {
                override fun isLongPressDragEnabled(): Boolean {
                    return true
                }

                override fun isItemViewSwipeEnabled(): Boolean {
                    return false
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    if (viewHolder is TabOrderAdapter.TabOrderSeparatorViewHolder &&
                        target.bindingAdapterPosition == 0
                    ) return false
                    if (target.bindingAdapterPosition >= adapter.value.indexOf(null) &&
                        viewHolder.bindingAdapterPosition == 0
                    ) return false
                    adapter.value.add(
                        target.bindingAdapterPosition,
                        adapter.value.removeAt(viewHolder.bindingAdapterPosition)
                    )
                    adapter.notifyItemMoved(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int
                ) {
                    throw IllegalStateException()
                }
            }).attachToRecyclerView(recyclerView)
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (positiveResult) {
                val newValue = mapTabListToSetting(adapter.value)
                (preference as TabOrderPreference).let {
                    if (it.callChangeListener(newValue)) {
                        it.value = newValue
                    }
                }
            }
        }

        companion object {
            fun newInstance(key: String): TabOrderDialog {
                return TabOrderDialog().apply {
                    arguments = Bundle().apply {
                        putString(ARG_KEY, key)
                    }
                }
            }
        }
    }

    class TabOrderAdapter(initialValue: String) : MyRecyclerView.Adapter<TabOrderViewHolder>() {
        val value = mapSettingToTabList(initialValue).toMutableList()

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ) =
            if (viewType == 2)
                TabOrderSeparatorViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.tab_order_seperator, parent, false)
                )
            else
                TabOrderItemViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.tab_order_item, parent, false)
                )

        override fun onBindViewHolder(
            holder: TabOrderViewHolder,
            position: Int
        ) {
            if (holder is TabOrderItemViewHolder) {
                val item = value[position]!!
                holder.name.text = holder.itemView.context.getString(item.label)
            }
        }

        override fun getItemCount() = value.count()

        override fun getItemViewType(position: Int): Int {
            return if (value[position] == null) 2 else 1
        }

        open class TabOrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
        class TabOrderItemViewHolder(itemView: View) : TabOrderViewHolder(itemView) {
            val name = itemView.findViewById<TextView>(R.id.tabName)
        }

        class TabOrderSeparatorViewHolder(itemView: View) : TabOrderViewHolder(itemView)
    }
}