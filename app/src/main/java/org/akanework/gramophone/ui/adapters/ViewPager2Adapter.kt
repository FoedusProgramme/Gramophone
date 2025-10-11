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
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getStringStrict
import org.akanework.gramophone.logic.hasImprovedMediaStore
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.fragments.AdapterFragment

/**
 * This is the ViewPager2 adapter.
 */
class ViewPager2Adapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val context: Context,
    private val viewPager2: ViewPager2
) : FragmentStateAdapter(fragmentManager, lifecycle),
    SharedPreferences.OnSharedPreferenceChangeListener, DefaultLifecycleObserver {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private var tabs = mapSettingToTabList(prefs.getStringStrict("tabs", "")!!)

    init {
        prefs.registerOnSharedPreferenceChangeListener(this)
        lifecycle.addObserver(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key != "tabs") return
        val currentItemId = tabs[viewPager2.currentItem]
        tabs = mapSettingToTabList(prefs.getStringStrict("tabs", "")!!)
        viewPager2.adapter!!.notifyDataSetChanged()
        if (tabs.contains(currentItemId)) {
            val newPosition = tabs.indexOfFirst { it == currentItemId }
            viewPager2.setCurrentItem(newPosition, false)
        }

        (context as? MainActivity)
            ?.findViewById<TabLayout>(R.id.tab_layout)
            ?.visibility = if (getItemCount() < 2) View.GONE else View.VISIBLE
    }

    fun getLabelResId(position: Int) = tabs[position]!!.label

    override fun getItemCount() = tabs.indexOf(null)
        .also { if (it == -1) throw IllegalStateException("indexOf null is -1 in tab list?") }

    override fun createFragment(position: Int): Fragment =
        AdapterFragment().apply {
            arguments = Bundle().apply {
                putInt("ID", tabs[position]!!.id)
            }
        }

    override fun getItemId(position: Int): Long {
        return tabs[position]!!.id.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return tabs.any { it?.id?.toLong() == itemId }
    }

    companion object {
        enum class Tab(val id: Int, val label: Int) {
            // Do not rename entries here, names are written to disk. Order is default tab order
            Songs(R.id.songs, R.string.category_songs),
            Albums(R.id.albums, R.string.category_albums),
            Artists(R.id.artists, R.string.category_artists),
            Genres(R.id.genres, R.string.category_genres),
            Dates(R.id.dates, R.string.category_dates),
            Folders(R.id.folders, R.string.folders),
            FileSystem(R.id.detailed_folders, R.string.filesystem),
            Playlist(R.id.playlists, R.string.category_playlists)
        }

        fun mapSettingToTabList(setting: String): List<Tab?> {
            val stList = if (!setting.isEmpty())
                setting.split(",").flatMap {
                    if (it.isEmpty())
                        listOf(null)
                    else
                        try {
                            val t = Tab.valueOf(it)
                            if (!hasImprovedMediaStore() && t == Tab.Genres)
                                listOf() else listOf(t)
                        } catch (_: IllegalArgumentException) {
                            listOf() // this tab was removed
                        }
                }.toMutableList()
            else mutableListOf()
            Tab.entries.forEach {
                if (stList.indexOf(it) != stList.lastIndexOf(it))
                    stList.removeAll { i -> i == it }
                if (!stList.contains(it) && (it != Tab.Genres || hasImprovedMediaStore()))
                    stList.add(it)
            }
            if (!stList.contains(null))
                stList.add(null)
            return stList
        }

        fun mapTabListToSetting(tabList: List<Tab?>) = tabList.joinToString(",") { it?.name ?: "" }
    }
}
