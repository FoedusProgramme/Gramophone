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
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.media3.common.C
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
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
            if (adapter is SongAdapter || adapter is AlbumAdapter) View.VISIBLE else View.GONE
        holder.shuffleAll.visibility =
            if (adapter is SongAdapter || adapter is AlbumAdapter) View.VISIBLE else View.GONE
        holder.counter.text = context.resources.getQuantityString(pluralStr, count, count)
        holder.sortButton.visibility =
            if (adapter.sortType.value != Sorter.Type.None || adapter.canChangeLayout) View.VISIBLE else View.GONE
        holder.sortButton.setOnClickListener { view ->
            val popupMenu = PopupMenu(context, view)
            popupMenu.inflate(R.menu.sort_menu)
            val buttonMap = mapOf(
                Pair(R.id.natural, Sorter.Type.NaturalOrder),
                Pair(R.id.name, Sorter.Type.ByTitleAscending),
                Pair(R.id.artist, Sorter.Type.ByArtistAscending),
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
            buttonMap.forEach {
                popupMenu.menu.findItem(it.key).isVisible = adapter.sortTypes.contains(it.value)
            }
            layoutMap.forEach {
                popupMenu.menu.findItem(it.key).isVisible = adapter.canChangeLayout
            }
            popupMenu.menu.findItem(R.id.display).isVisible = adapter.canChangeLayout
            if (adapter.sortType.value != Sorter.Type.None) {
                when (adapter.sortType.value) {
                    in buttonMap.values -> {
                        popupMenu.menu.findItem(
                            buttonMap.entries
                                .first { it.value == adapter.sortType.value }.key
                        ).isChecked = true
                    }

                    else -> throw IllegalStateException("Invalid sortType ${adapter.sortType.value.name}")
                }
            }
            if (adapter.canChangeLayout) {
                when (adapter.layoutType) {
                    in layoutMap.values -> {
                        popupMenu.menu.findItem(
                            layoutMap.entries
                                .first { it.value == adapter.layoutType }.key
                        ).isChecked = true
                    }

                    else -> throw IllegalStateException("Invalid layoutType ${adapter.layoutType?.name}")
                }
            }
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    in buttonMap.keys -> {
                        if (!menuItem.isChecked) {
                            adapter.sort(buttonMap[menuItem.itemId]!!)
                            menuItem.isChecked = true
                            prefs.edit {
                                putString(
                                    "S" + getAdapterType(adapter).toString(),
                                    buttonMap[menuItem.itemId].toString()
                                )
                            }
                        }
                        true
                    }

                    in layoutMap.keys -> {
                        if (!menuItem.isChecked) {
                            adapter.layoutType = layoutMap[menuItem.itemId]!!
                            menuItem.isChecked = true
                            prefs.edit {
                                putString(
                                    "L" + getAdapterType(adapter).toString(),
                                    layoutMap[menuItem.itemId].toString()
                                )
                            }
                        }
                        true
                    }

                    else -> onExtraMenuButtonPressed(menuItem)
                }
            }
            onSortButtonPressed(popupMenu)
            popupMenu.show()
        }
        holder.playAll.setOnClickListener {
            if (adapter is SongAdapter) {
                val mediaController = adapter.getActivity().getPlayer()
                val songList = adapter.getSongList()
                mediaController?.apply {
                    shuffleModeEnabled = false
                    repeatMode = REPEAT_MODE_OFF
                    setMediaItems(songList)
                    if (songList.isNotEmpty()) {
                        prepare()
                        play()
                    }
                }
            } else if (adapter is AlbumAdapter) {
                val list = adapter.getAlbumList()
                val controller = adapter.getActivity().getPlayer()
                controller?.repeatMode = REPEAT_MODE_OFF
                controller?.shuffleModeEnabled = false
                list.takeIf { it.isNotEmpty() }?.also { albums ->
                    controller?.setMediaItems(albums.flatMap { it.songList })
                    controller?.prepare()
                    controller?.play()
                } ?: controller?.setMediaItems(listOf())
            }
        }
        holder.shuffleAll.setOnClickListener {
            if (adapter is SongAdapter) {
                val songList = adapter.getSongList()
                val controller = adapter.getActivity().getPlayer()
                controller?.shuffleModeEnabled = true
                controller?.setMediaItems(songList)
                if (songList.isNotEmpty()) {
                    controller?.prepare()
                    controller?.play()
                }
            } else if (adapter is AlbumAdapter) {
                val list = adapter.getAlbumList()
                val controller = adapter.getActivity().getPlayer()
                controller?.repeatMode = REPEAT_MODE_OFF
                controller?.shuffleModeEnabled = false
                list.takeIf { it.isNotEmpty() }?.also { albums ->
	                controller?.setMediaItems(albums.shuffled().flatMap { it.songList })
                    controller?.prepare()
                    controller?.play()
                } ?: controller?.setMediaItems(listOf())
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
        val smoothScroller = object : LinearSmoothScroller(context) {
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
                ))// + context.resources.displayMetrics.heightPixels / 3).coerceAtMost(viewEnd)
            }

            override fun getVerticalSnapPreference(): Int {
                return SNAP_TO_START
            }

            /*override fun calculateTimeForDeceleration(dx: Int): Int {
                return 500
            }*/
        }
        smoothScroller.targetPosition = pos
        recyclerView?.startSmoothScrollCompat(smoothScroller)
    }

    protected open fun onSortButtonPressed(popupMenu: PopupMenu) {}
    protected open fun onExtraMenuButtonPressed(menuItem: MenuItem): Boolean = false

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
}