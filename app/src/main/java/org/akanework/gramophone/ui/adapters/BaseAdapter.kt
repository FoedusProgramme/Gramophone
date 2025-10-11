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
import android.content.res.Configuration
import android.net.Uri
import androidx.media3.common.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getStringStrict
import org.akanework.gramophone.logic.ui.DefaultItemHeightHelper
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.placeholderScaleToFit
import org.akanework.gramophone.logic.utils.flows.PauseManagingSharedFlow.Companion.sharePauseableIn
import org.akanework.gramophone.logic.utils.flows.repeatPausingWithLifecycle
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.CustomGridLayoutManager
import org.akanework.gramophone.ui.components.NowPlayingDrawable
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.getAdapterType
import uk.akane.libphonograph.items.Item

@OptIn(ExperimentalCoroutinesApi::class)
abstract class BaseAdapter<T : Any>(
    protected val fragment: Fragment,
    liveData: Flow<List<T>?>,
    sortHelper: Sorter.Helper<T>,
    naturalOrderHelper: Sorter.NaturalOrderHelper<T>?,
    initialSortType: Sorter.Type,
    private val pluralStr: Int,
    defaultLayoutType: LayoutType,
    val isSubFragment: Int? = null,
    rawOrderExposed: Sorter.Type? = null,
    private val allowDiffUtils: Boolean = false,
    private val canSort: Boolean = true
) : AdapterFragment.BaseInterface<BaseAdapter.ViewHolder>(), PopupTextProvider, ItemHeightHelper {

    override val canChangeLayout = true
    override val context = fragment.requireContext()
    protected val liveDataAgent = MutableStateFlow(liveData)
    protected inline val mainActivity
        get() = context as MainActivity
    override val layoutInflater: LayoutInflater
        get() = fragment.layoutInflater
    private val listHeight = context.resources.getDimensionPixelSize(R.dimen.list_height)
    private val largerListHeight =
        context.resources.getDimensionPixelSize(R.dimen.larger_list_height)
    private var gridHeight: Int? = null
    private var lockedInGridSize = false
    private val sorter = Sorter(sortHelper, naturalOrderHelper, rawOrderExposed)
    val decorAdapter by lazy { createDecorAdapter() }
    override val concatAdapter by lazy { ConcatAdapter(
        ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(), decorAdapter, this) }
    override val itemHeightHelper by lazy {
        DefaultItemHeightHelper.concatItemHeightHelper(decorAdapter, { 1 }, this)
    }
    override val itemCountForDecor
        get() = itemCount
    protected var list: Pair<List<T>, List<T>>? = null
    protected var recyclerView: MyRecyclerView? = null
        private set

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    private val prefSortType: Sorter.Type by lazy {
        if (canSort) try {
            Sorter.Type.valueOf(
                prefs.getStringStrict(
                    "S" + getAdapterType(this).toString(),
                    Sorter.Type.None.toString()
                )!!
            )
        } catch (_: IllegalArgumentException) { Sorter.Type.None }
        else Sorter.Type.None
    }

    private val prefLayoutType: LayoutType by lazy {
        try {
            LayoutType.valueOf(
                prefs.getStringStrict(
                    "L" + getAdapterType(this).toString(),
                    LayoutType.NONE.toString()
                )!!
            )
        } catch (_: IllegalArgumentException) { LayoutType.NONE }
    }

    override var layoutType: LayoutType? = null
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            if (recyclerView != null && recyclerView!!.width != 0)
                calculateGridSizeIfNeeded()
            lockedInGridSize = false
            notifyDataSetChanged() // we change view type for all items
        }
    override val sortType: MutableStateFlow<Sorter.Type> = MutableStateFlow(
        // TODO(ASAP): leaking this in prefSortType leads to uninitialized read in getAdapterType(),
        //  which is why folder adapter cannot read saved sort values.
        if (prefSortType != Sorter.Type.None && sortTypes.contains(prefSortType))
            prefSortType
        else
            initialSortType
    )
    @OptIn(ExperimentalCoroutinesApi::class)
    private val flow = liveDataAgent.flatMapLatest { it }
        .sharePauseableIn(CoroutineScope(Dispatchers.Default),
            SharingStarted.WhileSubscribed(), replay = 0)
        .combine(sortType) { it, st ->
        val l = it ?: emptyList()
        l to ArrayList(l).apply {
            val (cmp, reverseFirst) = sorter.getComparator(st)
            if (reverseFirst) reverse()
            if (cmp != null) {
                sortWith { o1, o2 ->
                    if (isPinned(o1) && !isPinned(o2)) -1
                    else if (!isPinned(o1) && isPinned(o2)) 1
                    else cmp.compare(o1, o2)
                }
            }
        }.toList()
    }.sharePauseableIn(CoroutineScope(Dispatchers.Default), SharingStarted.WhileSubscribed(5000), replay = 1)
    override val sortTypes: Set<Sorter.Type>
        get() = if (canSort) sorter.getSupportedTypes() else setOf(Sorter.Type.None)

    init {
        val mayBlock = isSubFragment != null
        var blockMutex = if (mayBlock) Mutex() else null
        var onListLoadedCompleter = if (mayBlock)
            CompletableDeferred<Pair<Pair<List<T>, List<T>>, Pair<DiffUtil.DiffResult?, Boolean>>>() else null
        val deferred = if (mayBlock) onListLoadedCompleter else null
        val onListLoaded = { it: Pair<List<T>, List<T>>, diff: DiffUtil.DiffResult?, sizeChanged: Boolean ->
            list = it
            if (diff != null)
                diff.dispatchUpdatesTo(this@BaseAdapter)
            else
                @SuppressLint("NotifyDataSetChanged") notifyDataSetChanged()
            if (sizeChanged) decorAdapter.updateSongCounter()
            onListUpdated()
            recyclerView?.doOnLayout {
                recyclerView?.postOnAnimation { reportFullyDrawn() }
            }
        }
        repeatPausingWithLifecycle(fragment.viewLifecycleOwner, Dispatchers.Default) {
            flow.collectLatest {
                val old = list
                if (old === it) {
                    throw IllegalStateException("error, shouldn't ever see same list twice :/")
                }
                val diff = if ((old?.second?.isNotEmpty() == true && it.second.isNotEmpty())
                    || allowDiffUtils
                )
                    DiffUtil.calculateDiff(SongDiffCallback(old?.second?.toList() ?: emptyList(), it.second.toList()))
                else null
                val sizeChanged = (old?.second?.size ?: 0) != it.second.size
                val mutex = blockMutex
                if (mutex != null) {
                    mutex.withLock {
                        val deferred2 = onListLoadedCompleter
                        if (deferred2 != null) {
                            deferred2.complete(it to (diff to sizeChanged))
                            onListLoadedCompleter = null
                        } else {
                            withContext(Dispatchers.Main + NonCancellable) {
                                onListLoaded(it, diff, sizeChanged)
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main + NonCancellable) {
                        onListLoaded(it, diff, sizeChanged)
                    }
                }
            }
        }
        if (mayBlock) {
            runBlocking {
                try {
                    withTimeoutOrNull(2000) {
                        deferred!!.await()
                    }
                } finally {
                    blockMutex!!.withLock {
                        if (deferred!!.isCompleted) {
                            val (it, other) = deferred.getCompleted()
                            onListLoaded(it, other.first, other.second)
                        }
                        onListLoadedCompleter = null
                        blockMutex = null
                    }
                }
            }
        }
        // TODO(ASAP): leaking this in prefLayoutType leads to uninitialized read in getAdapterType(),
        //  which is why folder adapter cannot read saved layout values.
        layoutType =
            if (prefLayoutType != LayoutType.NONE && prefLayoutType != defaultLayoutType)
                prefLayoutType
            else
                defaultLayoutType
    }

    protected open val defaultCover: Int = R.drawable.ic_default_cover

    class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val songCover: ImageView = view.findViewById(R.id.cover)
        val nowPlaying: ImageView = view.findViewById(R.id.now_playing)
        val title: TextView = view.findViewById(R.id.title)
        val subTitle: TextView = view.findViewById(R.id.artist)
        val trackCount: TextView? = view.findViewById(R.id.track_count)
        val moreButton: MaterialButton? = view.findViewById(R.id.more)
    }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        if (list != null) {
            recyclerView.doOnLayout {
                recyclerView.postOnAnimation { reportFullyDrawn() }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun getItemCount(): Int = list?.second?.size ?: 0

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            layoutInflater.inflate(viewType, parent, false),
        )

    override fun sort(type: Sorter.Type) {
        sortType.value = type
    }

    protected open fun onListUpdated() {}

    protected open fun createDecorAdapter(): BaseDecorAdapter<out BaseAdapter<T>> {
        return BaseDecorAdapter(this, pluralStr)
    }

    override fun getItemViewType(position: Int): Int {
        return when (layoutType) {
            LayoutType.GRID, LayoutType.COMPACT_GRID -> R.layout.adapter_grid_card
            LayoutType.COMPACT_LIST -> R.layout.adapter_list_card
            LayoutType.LIST, null -> R.layout.adapter_list_card_larger
            else -> throw IllegalArgumentException()
        }
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val item = list!!.second[position]
        if (layoutType == LayoutType.GRID || layoutType == LayoutType.COMPACT_GRID) {
            lockedInGridSize = true
            val newHeight = gridHeight!!
            if (holder.itemView.layoutParams.height != newHeight) {
                holder.itemView.updateLayoutParams<ViewGroup.LayoutParams> {
                    height = newHeight
                }
            }
            holder.trackCount!!.text = trackCountOf(item)
            holder.itemView.setOnLongClickListener {
                val popupMenu = PopupMenu(it.context, it)
                onMenu(item, popupMenu)
                popupMenu.show()
                true
            }
        }
        holder.title.text = titleOf(item) ?: virtualTitleOf(item)
        holder.subTitle.text = subTitleOf(item)
        holder.songCover.load(coverOf(item)) {
            placeholderScaleToFit(defaultCover)
            crossfade(true)
            error(defaultCover)
        }
        holder.itemView.setOnClickListener { onClick(item) }
        holder.moreButton?.setOnClickListener {
            val popupMenu = PopupMenu(it.context, it)
            onMenu(item, popupMenu)
            popupMenu.show()
        }
    }

    // need to call notifyDataSetChanged() afterwards, unless lockedInGridSize == false
    private fun calculateGridSizeIfNeeded() {
        if (layoutType != LayoutType.GRID && layoutType != LayoutType.COMPACT_GRID) return
        if (recyclerView != null && recyclerView!!.width != 0) {
            val cardPadding =
                context.resources.getDimensionPixelSize(R.dimen.grid_card_side_padding)
            val marginTop = context.resources.getDimensionPixelSize(R.dimen.grid_card_margin_top)
            val marginLabel =
                context.resources.getDimensionPixelSize(R.dimen.grid_card_margin_label)
            val paddingBottom =
                context.resources.getDimensionPixelSize(R.dimen.grid_card_padding_bottom)
            val labelHeight =
                context.resources.getDimensionPixelSize(R.dimen.grid_card_label_height)
            // first find out cover's width...
            var w = recyclerView!!.width
            w -= recyclerView!!.paddingLeft + recyclerView!!.paddingRight // view padding
            w -= 2 * cardPadding // item decoration
            w /= CustomGridLayoutManager.FULL_SPAN_COUNT / getSpanSize() // we want width of one item
            w -= 2 * cardPadding // side padding
            // ...then use it to calculate height
            var h = w // cover is constrained 1:1
            h += marginTop // top padding of cover
            h += labelHeight // account for label height
            h += 2 * marginLabel // label vertical margin
            h += paddingBottom // bottom padding of whole card
            gridHeight = h
        } else {
            throw IllegalStateException("$recyclerView == null || ${recyclerView?.width} == 0")
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onWidthChanged(width: Int) {
        calculateGridSizeIfNeeded()
        if (lockedInGridSize) {
            lockedInGridSize = false
            Log.w(
                "BaseAdapter",
                "RecyclerView width changed after locking, this must not happen during startup"
            )
            notifyDataSetChanged()
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.itemView.setOnClickListener(null)
        holder.itemView.setOnLongClickListener(null)
        holder.moreButton?.setOnClickListener(null)
        (holder.nowPlaying.drawable as? NowPlayingDrawable?)?.level2Done = null
        holder.nowPlaying.setImageDrawable(null)
        holder.nowPlaying.visibility = View.GONE
        holder.songCover.dispose()
        super.onViewRecycled(holder)
    }

    private fun toId(item: T): String {
        return sorter.sortingHelper.getId(item)
    }

    protected open fun titleOf(item: T): String? {
        return if (sorter.sortingHelper.canGetTitle())
            sorter.sortingHelper.getTitle(item) else "null"
    }

    private fun trackCountOf(item: T): String {
        if (sorter.sortingHelper.canGetSize()) {
            if (sorter.sortingHelper.canGetArtist() /* see subTitleOf */) {
                val s = sorter.sortingHelper.getSize(item)
                return context.resources.getQuantityString(
                    R.plurals.songs, s, s
                )
            } else if (sorter.sortingHelper.canGetAlbumSize()) {
                val s = sorter.sortingHelper.getAlbumSize(item)
                return context.resources.getQuantityString(
                    R.plurals.albums, s, s
                )
            } else
                return ""
        }
        if (sorter.sortingHelper.canGetAlbumTitle()) {
            return sorter.sortingHelper.getAlbumTitle(item) ?: "null"
        }
        return "null"
    }

    protected abstract fun virtualTitleOf(item: T): String
    private fun subTitleOf(item: T): String {
        return if (sorter.sortingHelper.canGetArtist())
            sorter.sortingHelper.getArtist(item) ?: context.getString(R.string.unknown_artist)
        else if (sorter.sortingHelper.canGetSize()) {
            val s = sorter.sortingHelper.getSize(item)
            return context.resources.getQuantityString(
                R.plurals.songs, s, s
            )
        } else "null"
    }

    protected open fun coverOf(item: T): Uri? {
        return sorter.sortingHelper.getCover(item)
    }

    protected abstract fun onClick(item: T)
    protected abstract fun onMenu(item: T, popupMenu: PopupMenu)
    private fun isPinned(item: T): Boolean {
        return titleOf(item) == null
    }

    private inner class SongDiffCallback(
        private val oldList: List<T>,
        private val newList: List<T>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size

        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = toId(oldList[oldItemPosition]) == toId(newList[newItemPosition])

        override fun areContentsTheSame(
            oldItemPosition: Int,
            newItemPosition: Int,
        ) = oldList[oldItemPosition] == newList[newItemPosition]
    }

    final override fun getPopupText(view: View, position: Int): CharSequence {
        // position here refers to pos in ConcatAdapter(!)
        // if this crashes with IndexOutOfBoundsException, list access isn't guarded enough?
        // lib only ever gets popup text for what RecyclerView believes to be the first view
        val daic = decorAdapter.itemCount
        return (if (position >= daic)
            sorter.getFastScrollHintFor(list!!.second[position - daic],
                position - daic, sortType.value)
        else null) ?: "-"
    }

    enum class LayoutType {
        NONE, LIST, COMPACT_LIST, GRID, COMPACT_GRID
    }

    abstract class StoreItemHelper<T : Item>(
        typesSupported: Set<Sorter.Type> = setOf(
            Sorter.Type.ByTitleDescending, Sorter.Type.ByTitleAscending,
            Sorter.Type.BySizeDescending, Sorter.Type.BySizeAscending
        )
    ) : Sorter.Helper<T>(typesSupported) {
        override fun getId(item: T): String {
            return item.id.toString()
        }

        override fun getTitle(item: T): String? {
            return item.title
        }

        override fun getSize(item: T): Int {
            return item.songList.size
        }

        override fun getCover(item: T): Uri? {
            return item.songList.firstOrNull()?.mediaMetadata?.artworkUri
        }
    }

    override fun getItemHeightFromZeroTo(to: Int): Int {
        val count = ((to / (CustomGridLayoutManager.FULL_SPAN_COUNT / getSpanSize())
            .toFloat()) + 0.5f).toInt()
        return count * when (layoutType) {
            LayoutType.GRID, LayoutType.COMPACT_GRID -> gridHeight!!
            LayoutType.COMPACT_LIST -> listHeight
            LayoutType.LIST, null -> largerListHeight
            else -> throw IllegalArgumentException()
        }
    }

    fun getSpanSize(): Int {
        val isList = layoutType != LayoutType.GRID && layoutType != LayoutType.COMPACT_GRID
        val lowWidth = context.resources.configuration.orientation ==
                Configuration.ORIENTATION_PORTRAIT ||
                context.resources.configuration.screenWidthDp < 600
        return when {
            isList && lowWidth -> CustomGridLayoutManager.LIST_PORTRAIT_SPAN_SIZE
            isList -> CustomGridLayoutManager.LIST_LANDSCAPE_SPAN_SIZE
            layoutType == LayoutType.GRID && lowWidth -> CustomGridLayoutManager.GRID_PORTRAIT_SPAN_SIZE
            layoutType == LayoutType.GRID -> CustomGridLayoutManager.GRID_LANDSCAPE_SPAN_SIZE
            layoutType == LayoutType.COMPACT_GRID && lowWidth -> CustomGridLayoutManager.COMPACT_GRID_PORTRAIT_SPAN_SIZE
            layoutType == LayoutType.COMPACT_GRID -> CustomGridLayoutManager.COMPACT_GRID_LANDSCAPE_SPAN_SIZE
            else -> throw IllegalStateException("invalid span size for layout")
        }
    }
}