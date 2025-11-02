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

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.comparators.SupportComparator
import org.akanework.gramophone.logic.getStringStrict
import org.akanework.gramophone.logic.ui.DefaultItemHeightHelper
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.getAdapterType
import uk.akane.libphonograph.items.FileNode
import java.util.concurrent.atomic.AtomicInteger

class DetailedFolderAdapter(
    private val fragment: Fragment,
    val isDetailed: Boolean
) : AdapterFragment.BaseInterface<RecyclerView.ViewHolder>() {
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
    } catch (_: IllegalArgumentException) { Sorter.Type.None }
    override val sortTypes = setOf(Sorter.Type.ByFilePathAscending, Sorter.Type.BySizeDescending)
    override val sortType = MutableStateFlow(
        if (prefSortType != Sorter.Type.None && sortTypes.contains(prefSortType))
        prefSortType
    else
        Sorter.Type.ByFilePathAscending)
    private val liveData = if (isDetailed) mainActivity.reader.folderStructureFlow
        else mainActivity.reader.shallowFolderFlow
    private var scope: CoroutineScope? = null
    private val folderPopAdapter: FolderPopAdapter = FolderPopAdapter(this)
    private val folderAdapter: FolderListAdapter =
        FolderListAdapter(listOf(), mainActivity, this)
    private val songList = MutableSharedFlow<List<MediaItem>>(1)
    private val decorAdapter = BaseDecorAdapter<DetailedFolderAdapter>(this, R.plurals.folders_plural)
    private val songAdapter: SongAdapter =
        SongAdapter(fragment, songList, folder = true).apply {
            onFullyDrawnListener = { reportFullyDrawn() }
            decorAdapter.jumpUpPos = { 0 }
        }
    override val concatAdapter: ConcatAdapter =
        ConcatAdapter(ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(),
            decorAdapter, this, folderPopAdapter, folderAdapter, songAdapter.concatAdapter)
    override val itemHeightHelper by lazy {
        DefaultItemHeightHelper.concatItemHeightHelper(decorAdapter, { decorAdapter.itemCount },
            DefaultItemHeightHelper.concatItemHeightHelper(folderPopAdapter, { folderPopAdapter.itemCount },
            DefaultItemHeightHelper.concatItemHeightHelper(folderAdapter,
                { folderAdapter.itemCount }, songAdapter.itemHeightHelper)))
    }
    private var root: FileNode? = null
    private var fileNodePath = ArrayList<String>()
    private var recyclerView: MyRecyclerView? = null
    init {
        decorAdapter.jumpDownPos = { decorAdapter.itemCount + folderPopAdapter.itemCount + folderAdapter.itemCount }
    }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        this.scope = CoroutineScope(Dispatchers.Default)
        this.scope!!.launch {
            liveData.collect {
                if (root !== it)
                    withContext(Dispatchers.Main) {
                        onChanged(it)
                    }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scope!!.cancel()
        scope = null
    }

    fun onChanged(value: FileNode) {
        root = value
        var value = value
        if (fileNodePath.isEmpty() && isDetailed) {
            while (value.folderList.size == 1) {
                fileNodePath.add(value.folderList.keys.first())
                value = value.folderList.values.first()
            }
        }
        update(null)
    }

    fun enter(path: String?) {
        if (path != null) {
            fileNodePath.add(path)
            update(false)
        } else if (fileNodePath.isNotEmpty()) {
            fileNodePath.removeAt(fileNodePath.lastIndex)
            update(true)
        }
    }

    override fun sort(type: Sorter.Type) {
        sortType.value = type
        update(null)
    }

    private fun update(invertedDirection: Boolean?) {
        var item = root
        for (path in fileNodePath) {
            item = item?.folderList?.get(path)
        }
        if (item == null) {
            fileNodePath.clear()
            item = root
        }
        val doUpdate = { canDiff: Boolean ->
            folderPopAdapter.enabled = fileNodePath.isNotEmpty()
            folderAdapter.updateList(item?.folderList?.values ?: listOf(), canDiff, sortType.value)
            runBlocking { songList.emit(item?.songList ?: listOf()) }
        }
        recyclerView.let {
            if (it == null || invertedDirection == null) {
                doUpdate(it != null)
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
                    doUpdate(false)
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


    private class FolderListAdapter(
        private var folderList: List<FileNode>,
        private val activity: MainActivity,
        frag: DetailedFolderAdapter
    ) : FolderCardAdapter(frag), PopupTextProvider {

        @SuppressLint("SetTextI18n")
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

        @SuppressLint("NotifyDataSetChanged")
        fun updateList(newCollection: Collection<FileNode>, canDiff: Boolean, sortType: Sorter.Type) {
            val newList = newCollection.toMutableList()
            CoroutineScope(Dispatchers.Default).launch {
                val diffResult = if (canDiff) DiffUtil.calculateDiff(
                    DiffCallback(folderList, newList)) else null
                val newList2 = if (sortType == Sorter.Type.BySizeDescending)
                    newList.sortedByDescending { it.folderList.size + it.songList.size }
                else
                    newList.sortedWith(
                    SupportComparator.createAlphanumericComparator(cnv = {
                        it.folderName
                    }))
                withContext(Dispatchers.Main) {
                    folderList = newList2
                    if (diffResult != null)
                        diffResult.dispatchUpdatesTo(this@FolderListAdapter)
                    else
                        notifyDataSetChanged()
                    folderFragment.decorAdapter.updateSongCounter()
                    folderFragment.recyclerView?.doOnLayout {
                        folderFragment.recyclerView?.postOnAnimation { reportFullyDrawn() }
                    }
                }
            }
        }

        private inner class DiffCallback(
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
            ) = oldList[oldItemPosition] == newList[newItemPosition]

        }

        var onFullyDrawnListener: (() -> Unit)? = null
        private fun reportFullyDrawn() {
            onFullyDrawnListener?.invoke()
            onFullyDrawnListener = null
        }
    }

    private class FolderPopAdapter(private val frag: DetailedFolderAdapter) : FolderCardAdapter(frag) {

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