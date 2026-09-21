/*
 *     Copyright (C) 2023 Akane Foundation
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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.flows.repeatPausingWithLifecycle
import java.io.File

@SuppressLint("NotifyDataSetChanged")
class BlacklistFolderAdapter(
    private val activity: AppCompatActivity,
    private val prefs: SharedPreferences,
    private val isWhitelist: Boolean
) : MyRecyclerView.Adapter<BlacklistFolderAdapter.ViewHolder>() {
    private var folderArray: List<String>? = null
    private var folderFilter: Set<String>? = null

    init {
        repeatPausingWithLifecycle(activity, Dispatchers.Default) {
            (if (isWhitelist) activity.gramophoneApplication.reader.foldersForWhitelistFlow else
                activity.gramophoneApplication.reader.foldersFlow).combine(if (isWhitelist)
                activity.gramophoneApplication.whiteListSetFlow else activity.gramophoneApplication
                    .blackListSetFlow) { newFolderArray, newFolderFilter ->
                val sortedFolderArray = newFolderArray.sorted()
                val oldFolderArray = folderArray?.toList()
                val oldFolderFilter = folderFilter?.toSet()
                val diff = if (oldFolderArray != null && oldFolderFilter != null)
                    DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                        override fun getOldListSize(): Int {
                            return oldFolderArray.size
                        }

                        override fun getNewListSize(): Int {
                            return sortedFolderArray.size
                        }

                        override fun areItemsTheSame(
                            oldItemPosition: Int,
                            newItemPosition: Int
                        ): Boolean {
                            return oldFolderArray[oldItemPosition] == sortedFolderArray[newItemPosition]
                        }

                        override fun areContentsTheSame(
                            oldItemPosition: Int,
                            newItemPosition: Int
                        ): Boolean {
                            return oldFolderFilter.contains(oldFolderArray[oldItemPosition]) ==
                                    newFolderFilter.contains(sortedFolderArray[newItemPosition])
                        }

                    }, true) else null
                withContext(Dispatchers.Main + NonCancellable) {
                    folderArray = sortedFolderArray
                    folderFilter = newFolderFilter
                    if (diff != null)
                        diff.dispatchUpdatesTo(this@BlacklistFolderAdapter)
                    else
                        notifyDataSetChanged()
                }
            }.collect()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            activity.layoutInflater.inflate(
                R.layout.adapter_blacklist_folder_card,
                parent,
                false
            )
        )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: MaterialCheckBox = view.findViewById(R.id.checkbox)
        val folderLocation: TextView = view.findViewById(R.id.title)
    }

    override fun getItemCount(): Int = folderArray?.size ?: 0

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.checkBox.isChecked = folderFilter!!.contains(folderArray!![position])
        holder.folderLocation.text = folderArray!![position]
        holder.checkBox.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                prefs.edit {
                    putStringSet(
                        if (isWhitelist) "folderAllow" else "folderFilter",
                        folderFilter!!.let {
                            if (holder.checkBox.isChecked)
                                it + folderArray!![position]
                            else
                                it - folderArray!![position]
                        })
                }
            }
        }
        holder.itemView.setOnClickListener {
            holder.checkBox.isChecked = !holder.checkBox.isChecked
            holder.checkBox.callOnClick()
        }
    }
}