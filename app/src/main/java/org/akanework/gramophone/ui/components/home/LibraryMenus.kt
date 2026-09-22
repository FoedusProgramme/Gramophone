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

package org.akanework.gramophone.ui.components.home

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.adapters.BaseAdapter.LayoutType
import org.akanework.gramophone.ui.adapters.Sorter
import org.akanework.gramophone.ui.state.LibraryMenuAction
import org.akanework.gramophone.ui.state.SortPrefState

private val sortTitles = mapOf(
    Sorter.Type.NaturalOrder to R.string.natural_order,
    Sorter.Type.ByTitleAscending to R.string.sort_by_name,
    Sorter.Type.ByArtistAscending to R.string.sort_by_artist,
    Sorter.Type.ByArtistYearAscending to R.string.sort_by_artist_year,
    Sorter.Type.ByAlbumTitleAscending to R.string.sort_by_album,
    Sorter.Type.ByAlbumArtistAscending to R.string.sort_by_album_artist,
    Sorter.Type.ByAlbumArtistYearAscending to R.string.sort_by_album_artist_year,
    Sorter.Type.ByAlbumYearDescending to R.string.sort_by_album_year,
    Sorter.Type.BySizeDescending to R.string.sort_by_size,
    Sorter.Type.ByAddDateDescending to R.string.sort_by_add_date,
    Sorter.Type.ByReleaseDateDescending to R.string.sort_by_release_date,
    Sorter.Type.ByModifiedDateDescending to R.string.sort_by_modified_date,
    Sorter.Type.ByFilePathAscending to R.string.sort_by_file_path,
)

/** Layout entries in `sort_menu.xml`'s "display" submenu order. */
private val layoutEntries = listOf(
    LayoutType.COMPACT_LIST to R.string.compact_list,
    LayoutType.LIST to R.string.list,
    LayoutType.COMPACT_GRID to R.string.compact_grid,
    LayoutType.GRID to R.string.grid,
)

@Composable
private fun MenuText(text: String) {
    Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Normal)
}

/**
 * The `sort_menu` as a DropdownMenu: a radio group of the supported sort types, an optional
 * extra checkbox (album artist), the reverse-order checkbox and a "Layout" submenu.
 */
@Composable
fun SortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sortTypes: Set<Sorter.Type>,
    activeSort: Sorter.Type?,
    isReversed: Boolean,
    canReverse: Boolean,
    onSelectSort: (Sorter.Type) -> Unit,
    onToggleReverse: () -> Unit,
    layoutType: LayoutType?,
    onSelectLayout: ((LayoutType) -> Unit)?,
    extraCheckbox: Pair<String, Boolean>? = null,
    onExtraCheckbox: () -> Unit = {},
) {
    var showLayouts by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) showLayouts = false }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 172.dp),
    ) {
        if (showLayouts && onSelectLayout != null) {
            DropdownMenuItem(
                text = { MenuText(stringResource(R.string.layout)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) },
                onClick = { showLayouts = false },
            )
            layoutEntries.forEach { (type, title) ->
                DropdownMenuItem(
                    text = { MenuText(stringResource(title)) },
                    leadingIcon = { RadioButton(selected = layoutType == type, onClick = null) },
                    onClick = { onSelectLayout(type); onDismiss() },
                )
            }
            return@DropdownMenu
        }
        if (extraCheckbox != null) {
            DropdownMenuItem(
                text = { MenuText(extraCheckbox.first) },
                leadingIcon = { Checkbox(checked = extraCheckbox.second, onCheckedChange = null) },
                onClick = { onExtraCheckbox(); onDismiss() },
            )
        }
        SortPrefState.SORT_MENU_ORDER.forEach { type ->
            if (!sortTypes.contains(type)) return@forEach
            DropdownMenuItem(
                text = { MenuText(stringResource(sortTitles.getValue(type))) },
                leadingIcon = { RadioButton(selected = activeSort == type, onClick = null) },
                onClick = { onSelectSort(type); onDismiss() },
            )
        }
        if (canReverse) {
            DropdownMenuItem(
                text = { MenuText(stringResource(R.string.reverse_order)) },
                leadingIcon = { Checkbox(checked = isReversed, onCheckedChange = null) },
                onClick = { onToggleReverse(); onDismiss() },
            )
        }
        if (onSelectLayout != null) {
            DropdownMenuItem(
                text = { MenuText(stringResource(R.string.layout)) },
                trailingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                onClick = { showLayouts = true },
            )
        }
    }
}

/** The per-item `more_menu` / `more_menu_less` as a DropdownMenu. */
@Composable
fun LibraryItemMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<LibraryMenuAction>,
    onAction: (LibraryMenuAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 172.dp),
    ) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { MenuText(stringResource(action.title)) },
                onClick = { onAction(action); onDismiss() },
            )
        }
    }
}
