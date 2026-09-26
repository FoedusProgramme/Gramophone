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

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.AddToQueue
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.THEME_ANIMATION_MS
import org.akanework.gramophone.ui.components.player.LocalHarmonizeCovers
import org.akanework.gramophone.ui.components.player.harmonizeBy
import org.akanework.gramophone.ui.components.player.rememberArtworkColorScheme
import org.akanework.gramophone.ui.library.LayoutType
import org.akanework.gramophone.ui.library.Sorter
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
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) },
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
                trailingIcon = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
                onClick = { showLayouts = true },
            )
        }
    }
}

/** The icon of one entry of the per-item sheet. */
private fun menuActionIcon(action: LibraryMenuAction): ImageVector = when (action) {
    LibraryMenuAction.Play -> Icons.Outlined.PlayArrow
    LibraryMenuAction.PlayNext -> Icons.Outlined.QueuePlayNext
    LibraryMenuAction.AddToQueue -> Icons.Outlined.AddToQueue
    LibraryMenuAction.GoToAlbum -> Icons.Outlined.Album
    LibraryMenuAction.GoToArtist -> Icons.Outlined.Groups
    LibraryMenuAction.Rename -> Icons.Outlined.Edit
    LibraryMenuAction.AddToPlaylist -> Icons.AutoMirrored.Outlined.PlaylistAdd
    LibraryMenuAction.Details -> Icons.AutoMirrored.Outlined.ListAlt
    LibraryMenuAction.Delete -> Icons.Outlined.Delete
    LibraryMenuAction.Share -> Icons.Outlined.Share
}

/** The item's cover, kind, title and subtitle, and the play button beside them. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryItemSheetHeader(
    title: String,
    subtitle: String,
    category: String,
    cover: Uri?,
    @DrawableRes defaultCover: Int,
    onPlay: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    // The play button wears the cover's own container colours, leant towards the theme's hue
    // the way the mini player's bar and the playing row do (not on a page themed from a cover).
    // Both fall back to the theme's own when content based colour is off or the cover holds no
    // usable colour.
    val coverScheme = rememberArtworkColorScheme(cover)
    val appPrimary = MaterialTheme.colorScheme.primary
    val harmony = if (LocalHarmonizeCovers.current) 1f else 0f
    val playFill by animateColorAsState(
        coverScheme.primaryContainer.harmonizeBy(appPrimary, harmony), tween(THEME_ANIMATION_MS),
        label = "play fill",
    )
    val onPlayFill by animateColorAsState(
        coverScheme.onPrimaryContainer.harmonizeBy(appPrimary, harmony), tween(THEME_ANIMATION_MS),
        label = "play icon",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = SHEET_MARGIN, end = SHEET_MARGIN, top = 4.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryCover(
            uri = cover,
            defaultCover = defaultCover,
            cornerRadius = 12.dp,
            modifier = Modifier.size(72.dp),
        )
        Column(Modifier.weight(1f).padding(start = 20.dp, end = 16.dp)) {
            SingleLineText(category, 13.sp, 400, onSurfaceVariant, Modifier.fillMaxWidth())
            // The title and the subtitle roll along while they do not fit.
            SingleLineText(
                title, 19.sp, 500, onSurface, Modifier.fillMaxWidth().basicMarquee(),
            )
            SingleLineText(
                subtitle, 14.sp, 400, onSurfaceVariant, Modifier.fillMaxWidth().basicMarquee(),
            )
        }
        Box(
            Modifier
                .size(56.dp)
                .clip(MaterialShapes.Clover4Leaf.toShape())
                .background(playFill)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = stringResource(R.string.play),
                tint = onPlayFill,
                // The play triangle is not centred in its box, the FAB shifts it too.
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Between the sheet's edge and what it holds: the header's content, and the group of action
 * cards, which line up with the header rather than with the sheet's own edges.
 */
private val SHEET_MARGIN = 24.dp

/** Between an action card's edge and its icon. */
private val ACTION_CARD_PADDING = 24.dp

/** One action of the per-item sheet: its icon and its title, a card of the sheet's group. */
@Composable
private fun LibraryItemSheetAction(
    action: LibraryMenuAction,
    shape: Shape,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SHEET_MARGIN)
            .libraryItemCard(shape)
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = ACTION_CARD_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = menuActionIcon(action),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        SingleLineText(
            stringResource(action.title), 16.sp, 400, MaterialTheme.colorScheme.onSurface,
            Modifier.weight(1f).padding(start = 24.dp),
        )
    }
}

/**
 * `BottomSheetDefaults.DragHandle`: the same 32x4dp pill, in the same colour and shape, but with
 * less room above and below it than that one's built-in 22dp. Only the spacing differs; swiping
 * is handled by the sheet itself, and the click / accessibility semantics are added around it.
 */
@Composable
private fun LibrarySheetDragHandle() {
    Surface(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Box(Modifier.size(width = 32.dp, height = 4.dp))
    }
}

/**
 * The per-item menu, as a bottom sheet: the item's header over its actions, each one an icon
 * and a title. The actions make up one group with the home library's corners: its ends turn
 * [LIBRARY_GROUP_CORNER], what meets another card [LIBRARY_ITEM_CORNER], and the sheet shows
 * through the [LIBRARY_ITEM_GAP]s between them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItemSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String,
    subtitle: String,
    category: String,
    cover: Uri?,
    @DrawableRes defaultCover: Int,
    actions: List<LibraryMenuAction>,
    onPlay: () -> Unit,
    onAction: (LibraryMenuAction) -> Unit,
) {
    if (!expanded) return
    // Partially expanded is skipped: the sheet's own height is always the wanted one.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    // Run what a click does, then slide the sheet away before the menu leaves the composition.
    val dismissAfter: (() -> Unit) -> Unit = { block ->
        block()
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { LibrarySheetDragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LIBRARY_ITEM_GAP),
        ) {
            LibraryItemSheetHeader(
                title, subtitle, category, cover, defaultCover,
                onPlay = { dismissAfter(onPlay) },
            )
            actions.forEachIndexed { index, action ->
                LibraryItemSheetAction(
                    action = action,
                    shape = libraryItemShape(
                        topStart = index == 0,
                        topEnd = index == 0,
                        bottomStart = index == actions.lastIndex,
                        bottomEnd = index == actions.lastIndex,
                    ),
                    onClick = { dismissAfter { onAction(action) } },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
