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

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.comparators.SupportComparator
import org.akanework.gramophone.logic.emitOrDie
import org.akanework.gramophone.logic.getStringStrict
import org.akanework.gramophone.logic.ui.DefaultItemHeightHelper
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.getAdapterType
import uk.akane.libphonograph.items.FileNode
import java.util.concurrent.atomic.AtomicInteger

class DetailedFolderAdapter(
    private val fragment: Fragment,
    val isDetailed: Boolean,
    savedInstanceState: Bundle?
) : AdapterFragment.BaseInterface<RecyclerView.ViewHolder>() {
    companion object {
        private const val TAG = "DetailedFolderAdapter"
    }

    private val mainActivity = fragment.requireActivity() as MainActivity
    override val context
        get() = mainActivity
    override val layoutInflater
        get() = fragment.layoutInflater
    override val canChangeLayout = false
    override var layoutType: BaseAdapter.LayoutType?
        get() = null
        set(_) {
            throw UnsupportedOperationException("layout type not impl")
        }
    override val itemCountForDecor: Int
        get() = folderAdapter.itemCount
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private var prefSortType: Sorter.Type = try {
        Sorter.Type.valueOf(
            prefs.getStringStrict(
                "S" + getAdapterType(this).toString(),
                Sorter.Type.None.toString()
            )!!
        )
    } catch (_: IllegalArgumentException) {
        Sorter.Type.None
    }
    override val sortTypes = setOf(
        Sorter.Type.ByFilePathAscending, Sorter.Type.BySizeDescending,
        Sorter.Type.ByAddDateDescending, Sorter.Type.ByModifiedDateDescending
    )
    override val sortType = MutableStateFlow(
        if (prefSortType != Sorter.Type.None && sortTypes.contains(prefSortType))
            prefSortType
        else
            Sorter.Type.ByFilePathAscending
    )
    private var fileNodePath = MutableStateFlow<List<String>?>(savedInstanceState
        ?.getStringArrayList("Path"))
    val qTitle = fileNodePath.map { it?.lastOrNull() ?: "/" }
    private val liveData = if (isDetailed) mainActivity.reader.folderStructureFlow
    else mainActivity.reader.shallowFolderFlow
    private val dataFlow = liveData.combineTransform(fileNodePath) { root, path ->
        var item: FileNode? = null
        if (path != null) {
            item = root
            for (path in path) {
                item = item?.folderList?.get(path)
            }
        }
        // 1. path is null because we don't have any location yet, choose default path;
        // 2. item is null because folder no longer exists on disk, reset to default path
        if (item == null) {
            item = root
            var newPath = mutableListOf<String>()
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
    }.sharePauseableIn(CoroutineScope(Dispatchers.Default), WhileSubscribed(), replay = 1)
    private val folderFlow = dataFlow.combine(sortType) { item, sortType ->
        when (sortType) {
            Sorter.Type.BySizeDescending -> item.folderList.values.sortedByDescending {
                it.folderList.size + it.songList.size
            }

            Sorter.Type.ByAddDateDescending -> item.folderList.values.sortedByDescending {
                it.addDate ?: Long.MIN_VALUE
            }

            Sorter.Type.ByModifiedDateDescending -> item.folderList.values.sortedByDescending {
                it.modifiedDate ?: Long.MIN_VALUE
            }

            else -> item.folderList.values.sortedWith(
                SupportComparator.createAlphanumericComparator(cnv = {
                    it.folderName
                })
            )
        }
    }
    private var folderList: ImmutableList<FileNode> = persistentListOf()
    private var scope: CoroutineScope? = null
    private val folderPopAdapter: FolderPopAdapter = FolderPopAdapter(this)
    private val folderAdapter: FolderListAdapter = FolderListAdapter(mainActivity, this)
    private val decorAdapter =
        BaseDecorAdapter<DetailedFolderAdapter>(this, R.plurals.folders_plural)
    private val songAdapter: SongAdapter =
        SongAdapter(fragment, qTitle, dataFlow.map { it.songList }, folder = true).apply {
            onFullyDrawnListener = { reportFullyDrawn() }
            decorAdapter.jumpUpPos = { 0 }
            decorAdapter.offsetPos = { this@DetailedFolderAdapter.decorAdapter.itemCount +
                    folderPopAdapter.itemCount + folderAdapter.itemCount }
        }
    override val concatAdapter: ConcatAdapter =
        ConcatAdapter(
            ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(),
            decorAdapter, this, folderPopAdapter, folderAdapter, songAdapter.concatAdapter
        )
    override val itemHeightHelper by lazy {
        DefaultItemHeightHelper.concatItemHeightHelper(
            decorAdapter, { decorAdapter.itemCount },
            DefaultItemHeightHelper.concatItemHeightHelper(
                folderPopAdapter,
                { folderPopAdapter.itemCount },
                DefaultItemHeightHelper.concatItemHeightHelper(
                    folderAdapter,
                    { folderAdapter.itemCount }, songAdapter.itemHeightHelper
                )
            )
        )
    }
    private var recyclerView: MyRecyclerView? = null

    init {
        decorAdapter.jumpDownPos =
            { decorAdapter.itemCount + folderPopAdapter.itemCount + folderAdapter.itemCount }
    }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        this.scope = CoroutineScope(Dispatchers.Default)
        this.scope!!.launch {
            folderFlow.collect { newList ->
                val oldList = folderList
                val canDiff = this@DetailedFolderAdapter.recyclerView != null
                        && folderAdapter.onFullyDrawnListener == null /* not changing folder */
                val diffResult = if (canDiff) DiffUtil.calculateDiff(
                    DiffCallback(oldList, newList)
                ) else null
                withContext(Dispatchers.Main) {
                    folderList = newList.toImmutableList()
                    if (diffResult != null)
                        diffResult.dispatchUpdatesTo(folderAdapter)
                    else
                        @Suppress("NotifyDataSetChanged")
                        folderAdapter.notifyDataSetChanged()
                    decorAdapter.updateSongCounter()
                    this@DetailedFolderAdapter.recyclerView?.doOnLayout {
                        this@DetailedFolderAdapter.recyclerView?.postOnAnimation {
                            folderAdapter.reportFullyDrawn()
                        }
                    }
                }
            }
        }
        this.scope!!.launch {
            fileNodePath.collect {
                withContext(Dispatchers.Main) {
                    folderPopAdapter.enabled = !it.isNullOrEmpty()
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scope!!.cancel()
        scope = null
    }

    fun onSaveInstanceState(outState: Bundle) {
        val path = fileNodePath.replayCache.firstOrNull()
        if (path != null) {
            outState.putStringArrayList("Path", ArrayList(path))
        }
    }

    override fun onTabReselected() {
        songAdapter.onTabReselected()
    }

    fun enter(path: String?) {
        val currentPath = fileNodePath.replayCache.firstOrNull() ?: return
        if (path != null) {
            update(false) {
                fileNodePath.emitOrDie(currentPath + path)
            }
        } else if (currentPath.isNotEmpty()) {
            update(true) {
                // Remove last
                fileNodePath.emitOrDie(currentPath.subList(0, currentPath.size - 1))
            }
        }
    }

    override fun sort(type: Sorter.Type) {
        sortType.value = type
    }

    private class DiffCallback(
        private val oldList: List<FileNode>,
        private val newList: List<FileNode>,
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition].folderName == newList[newItemPosition].folderName

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition].folderList.size == newList[newItemPosition].folderList.size
                && oldList[oldItemPosition].songList.size == newList[newItemPosition].songList.size
    }

    private fun update(invertedDirection: Boolean, doUpdate: () -> Unit) {
        recyclerView.let {
            if (it == null) {
                doUpdate()
                return@let
            }
            val animation = AnimationUtils.loadAnimation(
                it.context,
                if (invertedDirection) R.anim.slide_out_right else R.anim.slide_out_left
            )
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    it.alpha = 0f
                    it.itemAnimator = null
                    val i = AtomicInteger(2)
                    if (songAdapter.onFullyDrawnListener != null)
                        throw IllegalStateException("unexpected onFullyDrawnListener")
                    val next = {
                        it.alpha = 1f
                        it.itemAnimator = DefaultItemAnimator()
                        it.startAnimation(
                            AnimationUtils.loadAnimation(
                                it.context,
                                if (invertedDirection) R.anim.slide_in_left else R.anim.slide_in_right
                            )
                        )
                    }
                    songAdapter.onFullyDrawnListener = {
                        if (i.decrementAndGet() == 0) next()
                    }
                    folderAdapter.onFullyDrawnListener = {
                        if (i.decrementAndGet() == 0) next()
                    }
                    doUpdate()
                }

                override fun onAnimationRepeat(animation: Animation) {}
            })
            it.startAnimation(animation)
        }
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        var newPos = position
        if (newPos < decorAdapter.itemCount) {
            return "-"
        }
        newPos -= decorAdapter.itemCount
        if (newPos < folderPopAdapter.itemCount) {
            return "-"
        }
        newPos -= folderPopAdapter.itemCount
        if (newPos < folderAdapter.itemCount) {
            return folderAdapter.getPopupText(view, newPos)
        }
        newPos -= folderAdapter.itemCount
        if (newPos < songAdapter.concatAdapter.itemCount) {
            return songAdapter.getPopupText(view, newPos)
        }
        throw IllegalStateException()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        throw UnsupportedOperationException()

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) =
        throw UnsupportedOperationException()

    override fun getItemCount() = 0


    private inner class FolderListAdapter(
        private val activity: MainActivity,
        frag: DetailedFolderAdapter
    ) : FolderCardAdapter(frag), PopupTextProvider {

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = folderList[position]
            holder.folderName.text = item.folderName
            holder.folderSubtitle.text =
                activity.resources.getQuantityString(
                    R.plurals.items,
                    (item.folderList.size +
                            item.songList.size),
                    (item.folderList.size +
                            item.songList.size)
                )
            holder.itemView.setOnClickListener {
                folderFragment.enter(item.folderName)
            }
        }

        override fun getPopupText(view: View, position: Int): CharSequence {
            return folderList[position].folderName.first().toString()
        }

        override fun getItemCount(): Int = folderList.size

        var onFullyDrawnListener: (() -> Unit)? = null
        fun reportFullyDrawn() {
            onFullyDrawnListener?.invoke()
            onFullyDrawnListener = null
        }
    }

    private class FolderPopAdapter(private val frag: DetailedFolderAdapter) :
        FolderCardAdapter(frag) {

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.folderName.text = frag.mainActivity.getString(R.string.upper_folder)
            holder.folderSubtitle.text = ""
            holder.itemView.setOnClickListener {
                folderFragment.enter(null)
            }
        }

        var enabled = false
            set(value) {
                if (field != value) {
                    field = value
                    if (value) {
                        notifyItemInserted(0)
                    } else {
                        notifyItemRemoved(0)
                    }
                }
            }

        override fun getItemCount(): Int = if (enabled) 1 else 0
    }

    abstract class FolderCardAdapter(val folderFragment: DetailedFolderAdapter) :
        MyRecyclerView.Adapter<FolderCardAdapter.ViewHolder>(), ItemHeightHelper {
        override fun getItemViewType(position: Int): Int = R.layout.adapter_folder_card

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                folderFragment.fragment.layoutInflater
                    .inflate(R.layout.adapter_folder_card, parent, false),
            )

        override fun onViewRecycled(holder: ViewHolder) {
            holder.itemView.setOnClickListener(null)
            super.onViewRecycled(holder)
        }

        override fun getItemHeightFromZeroTo(to: Int): Int {
            return to * folderFragment.mainActivity.resources.getDimensionPixelSize(
                R.dimen.folder_card_height
            )
        }

        class ViewHolder(
            view: View,
        ) : RecyclerView.ViewHolder(view) {
            val folderName: TextView = view.findViewById(R.id.title)
            val folderSubtitle: TextView = view.findViewById(R.id.subtitle)
        }
    }
}