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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.logic.ui.QuickLinearSmoothScroller
import org.akanework.gramophone.logic.setMediaItemsWithTitle
import org.akanework.gramophone.ui.fragments.AdapterFragment
import org.akanework.gramophone.ui.getAdapterType

open class BaseDecorAdapter<T : AdapterFragment.BaseInterface<*>>(
    protected val adapter: T,
    private val pluralStr: Int
) : MyRecyclerView.Adapter<BaseDecorAdapter.ViewHolder>(), ItemHeightHelper {

    protected val context: Context = adapter.context
    private val dpHeight = context.resources.getDimensionPixelSize(R.dimen.decor_height)
    private var recyclerView: MyRecyclerView? = null
    private var prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    var jumpUpPos: (() -> Int)? = null
    var jumpDownPos: (() -> Int)? = null
    var offsetPos: (() -> Int)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val view = adapter.layoutInflater.inflate(R.layout.general_decor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val count = adapter.itemCountForDecor
        holder.playAll.visibility =
            if (adapter is SongAdapter && adapter.isSubFragment != R.id.songs ||
                adapter is AlbumAdapter) View.VISIBLE else View.GONE
        holder.shuffleAll.visibility =
            if (adapter is SongAdapter && adapter.isSubFragment != R.id.songs ||
                adapter is AlbumAdapter) View.VISIBLE else View.GONE
        holder.counter.text = context.resources.getQuantityString(pluralStr, count, count)
        if (adapter is SongAdapter) {
            holder.counter.setOnClickListener {
                goToPlayingSong()
            }
        }
        holder.sortButton.visibility =
            if (adapter.sortType.value != Sorter.Type.None || adapter.canChangeLayout) View.VISIBLE else View.GONE
        holder.sortButton.setOnClickListener { view ->
            val listPopupWindow = ListPopupWindow(context)
            listPopupWindow.anchorView = view
            listPopupWindow.setDropDownGravity(Gravity.END)
            listPopupWindow.isModal = true
            listPopupWindow.width = context.resources.getDimensionPixelSize(R.dimen.sort_popup_width)

            val buttonMap = mapOf(
                Pair(R.id.natural, Sorter.Type.NaturalOrder),
                Pair(R.id.name, Sorter.Type.ByTitleAscending),
                Pair(R.id.artist, Sorter.Type.ByArtistAscending),
                Pair(R.id.artist_year, Sorter.Type.ByArtistYearAscending),
                Pair(R.id.album, Sorter.Type.ByAlbumTitleAscending),
                Pair(R.id.album_artist, Sorter.Type.ByAlbumArtistAscending),
                Pair(R.id.album_artist_year, Sorter.Type.ByAlbumArtistYearAscending),
                Pair(R.id.album_year, Sorter.Type.ByAlbumYearDescending),
                Pair(R.id.size, Sorter.Type.BySizeDescending),
                Pair(R.id.add_date, Sorter.Type.ByAddDateDescending),
                Pair(R.id.release_date, Sorter.Type.ByReleaseDateDescending),
                Pair(R.id.mod_date, Sorter.Type.ByModifiedDateDescending),
                Pair(R.id.file_path, Sorter.Type.ByFilePathAscending)
            )

            val layoutMap = mapOf(
                Pair(R.id.list, BaseAdapter.LayoutType.LIST),
                Pair(R.id.compact_list, BaseAdapter.LayoutType.COMPACT_LIST),
                Pair(R.id.grid, BaseAdapter.LayoutType.GRID),
                Pair(R.id.compact_grid, BaseAdapter.LayoutType.COMPACT_GRID)
            )

            val items = mutableListOf<PopupItem>()
            
            // 1. Reverse Toggle
            val inverse = Sorter.Type.inverse(adapter.sortType.value)
            if (inverse != null) {
                val currentSort = adapter.sortType.value
                val activeEntry = buttonMap.entries.find { it.value == currentSort || Sorter.Type.inverse(it.value) == currentSort }
                val defaultSort = activeEntry?.value ?: Sorter.Type.None
                
                items.add(PopupItem.Switch(
                    R.id.reverse_order,
                    context.getString(R.string.reverse_order),
                    currentSort != defaultSort && currentSort != Sorter.Type.None
                ) { isChecked ->
                    val activeId = buttonMap.entries.find { 
                        it.value == adapter.sortType.value || Sorter.Type.inverse(it.value) == adapter.sortType.value 
                    }?.key ?: -1
                    val baseType = buttonMap[activeId]
                    if (baseType != null) {
                        val targetType = if (isChecked) Sorter.Type.inverse(baseType) ?: baseType else baseType
                        if (adapter.sortType.value != targetType) {
                            adapter.sort(targetType)
                            prefs.edit { putString("S" + getAdapterType(adapter).toString(), targetType.toString()) }
                        }
                    }
                })
                items.add(PopupItem.Divider)
            }

            // 2. Extra items (Album Artist etc)
            val extraItems = mutableListOf<PopupItem>()
            onSortPopupPopulating(extraItems)
            if (extraItems.isNotEmpty()) {
                items.addAll(extraItems)
                items.add(PopupItem.Divider)
            }

            // 3. Sort Modes
            buttonMap.forEach { (resId, type) ->
                if (adapter.sortTypes.contains(type)) {
                    val currentSort = adapter.sortType.value
                    val isSelected = currentSort == type || Sorter.Type.inverse(type) == currentSort
                    items.add(PopupItem.Radio(
                        resId,
                        context.getString(when (resId) {
                            R.id.natural -> R.string.natural_order
                            R.id.name -> R.string.sort_by_name
                            R.id.artist -> R.string.sort_by_artist
                            R.id.artist_year -> R.string.sort_by_artist_year
                            R.id.album -> R.string.sort_by_album
                            R.id.album_artist -> R.string.sort_by_album_artist
                            R.id.album_artist_year -> R.string.sort_by_album_artist_year
                            R.id.album_year -> R.string.sort_by_album_year
                            R.id.size -> R.string.sort_by_size
                            R.id.add_date -> R.string.sort_by_add_date
                            R.id.release_date -> R.string.sort_by_release_date
                            R.id.mod_date -> R.string.sort_by_modified_date
                            R.id.file_path -> R.string.sort_by_file_path
                            else -> 0
                        }),
                        isSelected
                    ) {
                        val isReversed = (items.find { it is PopupItem.Switch && it.id == R.id.reverse_order } as? PopupItem.Switch)?.isChecked == true
                        val targetType = if (isReversed) Sorter.Type.inverse(type) ?: type else type
                        adapter.sort(targetType)
                        prefs.edit { putString("S" + getAdapterType(adapter).toString(), targetType.toString()) }
                        listPopupWindow.dismiss()
                    })
                }
            }

            // 4. Layout Modes
            if (adapter.canChangeLayout) {
                items.add(PopupItem.Divider)
                items.add(PopupItem.Header(context.getString(R.string.layout)))
                layoutMap.forEach { (resId, type) ->
                    items.add(PopupItem.Radio(
                        resId,
                        context.getString(when (resId) {
                            R.id.list -> R.string.list
                            R.id.compact_list -> R.string.compact_list
                            R.id.grid -> R.string.grid
                            R.id.compact_grid -> R.string.compact_grid
                            else -> 0
                        }),
                        adapter.layoutType == type
                    ) {
                        adapter.layoutType = type
                        prefs.edit { putString("L" + getAdapterType(adapter).toString(), type.toString()) }
                        listPopupWindow.dismiss()
                    })
                }
            }

            val adapter2 = SortPopupAdapter(context, items)
            listPopupWindow.setAdapter(adapter2)
            listPopupWindow.show()
        }
        holder.playAll.setOnClickListener {
            if (adapter is SongAdapter) {
                val controller = adapter.getActivity().getPlayer()
                val songList = adapter.getSongList()
                controller?.apply {
                    setMediaItemsWithTitle(
                        songList,
                        title = runBlocking { adapter.queueTitle!!.first() },
                        shuffleEnabled = false,
                        repeatMode = REPEAT_MODE_OFF,
                    )
                    if (songList.isNotEmpty()) {
                        prepare()
                        play()
                    }
                }
            } else if (adapter is AlbumAdapter) {
                val list = adapter.getAlbumList()
                val controller = adapter.getActivity().getPlayer()
                controller?.apply {
                    list.takeIf { it.isNotEmpty() }?.also { albums ->
                        setMediaItemsWithTitle(
                            albums.flatMap { it.songList },
                            title = runBlocking { adapter.queueTitle.first() },
                            shuffleEnabled = false,
                            repeatMode = REPEAT_MODE_OFF,
                        )
                        prepare()
                        play()
                    } ?: setMediaItems(listOf())
                }
            }
        }
        holder.shuffleAll.setOnClickListener {
            ShortcutManagerCompat.reportShortcutUsed(context, "shuffle_all")
            if (adapter is SongAdapter) {
                val songList = adapter.getSongList()
                val controller = adapter.getActivity().getPlayer()
                controller?.apply {
                    setMediaItemsWithTitle(
                        songList,
                        title = runBlocking { adapter.queueTitle!!.first() },
                        shuffleEnabled = true,
                    )
                    if (songList.isNotEmpty()) {
                        prepare()
                        play()
                    }
                }
            } else if (adapter is AlbumAdapter) {
                val list = adapter.getAlbumList()
                val controller = adapter.getActivity().getPlayer()
                controller?.apply {
                    list.takeIf { it.isNotEmpty() }?.also { albums ->
                        setMediaItemsWithTitle(
                            albums.shuffled().flatMap { it.songList },
                            title = context.getString(R.string.shuffled,
                                    runBlocking { adapter.queueTitle.first() }),
                            shuffleEnabled = false,
                            repeatMode = REPEAT_MODE_OFF,
                        )
                        prepare()
                        play()
                    } ?: setMediaItems(listOf())
                }
            }
        }
        holder.jumpUp.visibility = if (jumpUpPos != null) View.VISIBLE else View.GONE
        holder.jumpUp.setOnClickListener {
            scrollToViewPosition(jumpUpPos!!())
        }
        holder.jumpDown.visibility = if (jumpDownPos != null) View.VISIBLE else View.GONE
        holder.jumpDown.setOnClickListener {
            scrollToViewPosition(jumpDownPos!!())
        }
    }

    fun goToPlayingSong() {
        if (adapter is SongAdapter) {
            adapter.getPlayingSong()?.let { scrollToViewPosition(
                (offsetPos?.invoke() ?: 0) + itemCount + it) }
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.sortButton.setOnClickListener(null)
        holder.playAll.setOnClickListener(null)
        holder.shuffleAll.setOnClickListener(null)
        holder.jumpUp.setOnClickListener(null)
        holder.jumpDown.setOnClickListener(null)
        super.onViewRecycled(holder)
    }

    override fun onAttachedToRecyclerView(recyclerView: MyRecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: MyRecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    private fun scrollToViewPosition(pos: Int) {
        val smoothScroller = object : QuickLinearSmoothScroller(context) {
            override fun calculateDtToFit(
                viewStart: Int,
                viewEnd: Int,
                boxStart: Int,
                boxEnd: Int,
                snapPreference: Int
            ): Int {
                return (super.calculateDtToFit(
                    viewStart,
                    viewEnd,
                    boxStart,
                    boxEnd,
                    snapPreference
                )) + (viewEnd - viewStart) / 2
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }
        }
        smoothScroller.targetPosition = pos
        recyclerView?.startSmoothScrollCompat(smoothScroller)
    }

    protected open fun onSortPopupPopulating(items: MutableList<PopupItem>) {}

    override fun getItemCount(): Int = 1
    override fun getItemViewType(position: Int): Int = R.layout.general_decor

    class ViewHolder(
        view: View,
    ) : RecyclerView.ViewHolder(view) {
        val sortButton: MaterialButton = view.findViewById(R.id.sort)
        val createPlaylist: MaterialButton = view.findViewById(R.id.create_playlist)
        val playAll: MaterialButton = view.findViewById(R.id.play_all)
        val shuffleAll: MaterialButton = view.findViewById(R.id.shuffle_all)
        val jumpUp: MaterialButton = view.findViewById(R.id.jumpUp)
        val jumpDown: MaterialButton = view.findViewById(R.id.jumpDown)
        val counter: TextView = view.findViewById(R.id.song_counter)
    }

    fun updateSongCounter() {
        notifyItemChanged(0)
    }

    override fun getItemHeightFromZeroTo(to: Int): Int {
        return if (to > 0) dpHeight else 0
    }

    sealed class PopupItem {
        object Divider : PopupItem()
        data class Header(val title: String) : PopupItem()
        data class Radio(val id: Int, val title: String, val isSelected: Boolean, val onClick: () -> Unit) : PopupItem()
        data class Switch(val id: Int, val title: String, var isChecked: Boolean, val onToggle: (Boolean) -> Unit) : PopupItem()
    }

    private class SortPopupAdapter(
        private val context: Context,
        private val items: List<PopupItem>
    ) : android.widget.BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = items[position]
            return when (item) {
                is PopupItem.Divider -> {
                    LayoutInflater.from(context).inflate(R.layout.item_popup_divider, parent, false)
                }
                is PopupItem.Header -> {
                    TextView(context).apply {
                        text = item.title
                        val px8 = (8 * context.resources.displayMetrics.density).toInt()
                        val px16 = (16 * context.resources.displayMetrics.density).toInt()
                        setPadding(px16, px8, px16, px8 / 2)
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
                        setTextColor(context.getColor(R.color.md_theme_primary))
                    }
                }
                is PopupItem.Radio -> {
                    val view = convertView?.takeIf { it.id == R.id.sort_radio_item_root }
                        ?: LayoutInflater.from(context).inflate(R.layout.item_sort_radio, parent, false)
                    
                    val title = view.findViewById<TextView>(R.id.title)
                    val radio = view.findViewById<RadioButton>(R.id.radio)
                    
                    title.text = item.title
                    radio.isChecked = item.isSelected
                    
                    view.setOnClickListener { item.onClick() }
                    view
                }
                is PopupItem.Switch -> {
                    val view = convertView?.takeIf { it.id == R.id.sort_switch_item_root }
                        ?: LayoutInflater.from(context).inflate(R.layout.switch_item, parent, false)
                    
                    val title = view.findViewById<TextView>(R.id.title)
                    val switch = view.findViewById<MaterialSwitch>(R.id.switch_view)
                    
                    title.text = item.title
                    switch.isChecked = item.isChecked
                    
                    view.setOnClickListener {
                        item.isChecked = !item.isChecked
                        switch.isChecked = item.isChecked
                        item.onToggle(item.isChecked)
                    }
                    view
                }
            }
        }
    }
}