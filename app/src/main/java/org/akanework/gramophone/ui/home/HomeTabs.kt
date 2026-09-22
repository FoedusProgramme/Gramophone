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

package org.akanework.gramophone.ui.home

import org.akanework.gramophone.R
import org.akanework.gramophone.logic.hasImprovedMediaStore

/**
 * The home tabs. Entry names are written to disk (pref key "tabs"), do not rename them.
 * Declaration order is the default tab order.
 */
enum class HomeTab(val id: Int, val label: Int) {
    Songs(R.id.songs, R.string.category_songs),
    Albums(R.id.albums, R.string.category_albums),
    Artists(R.id.artists, R.string.category_artists),
    Genres(R.id.genres, R.string.category_genres),
    Dates(R.id.dates, R.string.category_dates),
    Folders(R.id.folders, R.string.folders),
    FileSystem(R.id.detailed_folders, R.string.filesystem),
    Playlist(R.id.playlists, R.string.category_playlists)
}

/**
 * Parses the "tabs" preference into an ordered list. The single `null` entry separates the
 * visible tabs (before it) from the hidden ones (after it).
 */
fun mapSettingToTabList(setting: String): List<HomeTab?> {
    val stList = if (!setting.isEmpty())
        setting.split(",").flatMap {
            if (it.isEmpty())
                listOf(null)
            else
                try {
                    val t = HomeTab.valueOf(it)
                    if (!hasImprovedMediaStore() && t == HomeTab.Genres)
                        listOf() else listOf(t)
                } catch (_: IllegalArgumentException) {
                    listOf() // this tab was removed
                }
        }.toMutableList()
    else mutableListOf()
    HomeTab.entries.forEach {
        if (stList.indexOf(it) != stList.lastIndexOf(it))
            stList.removeAll { i -> i == it }
        if (!stList.contains(it) && (it != HomeTab.Genres || hasImprovedMediaStore()))
            stList.add(it)
    }
    if (!stList.contains(null))
        stList.add(null)
    return stList
}

fun mapTabListToSetting(tabList: List<HomeTab?>) = tabList.joinToString(",") { it?.name ?: "" }

/** The visible tabs, in order. */
fun visibleHomeTabs(setting: String): List<HomeTab> {
    val all = mapSettingToTabList(setting)
    val sep = all.indexOf(null)
    if (sep == -1) throw IllegalStateException("indexOf null is -1 in tab list?")
    @Suppress("UNCHECKED_CAST")
    return all.subList(0, sep) as List<HomeTab>
}
