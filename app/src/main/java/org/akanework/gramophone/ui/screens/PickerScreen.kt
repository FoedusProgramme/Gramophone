/*
 *     Copyright (C) 2025 Akane Foundation
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
package org.akanework.gramophone.ui.screens

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.akanework.gramophone.ui.components.home.GLASS_BAR_HEIGHT
import org.akanework.gramophone.ui.components.home.GlassTitleBar
import org.akanework.gramophone.ui.components.home.LIBRARY_GROUP_CORNER
import org.akanework.gramophone.ui.components.home.LIBRARY_ITEM_GAP
import org.akanework.gramophone.ui.components.home.LIST_HEIGHT
import org.akanework.gramophone.ui.components.home.LibraryFastScroller
import org.akanework.gramophone.ui.components.home.LibraryIconButton
import org.akanework.gramophone.ui.components.home.LibraryListRow
import org.akanework.gramophone.ui.components.home.iosOverscroll
import org.akanework.gramophone.ui.components.home.libraryItemCard
import org.akanework.gramophone.ui.components.home.libraryItemShape
import org.akanework.gramophone.ui.components.home.rememberIosFlingBehavior
import org.akanework.gramophone.ui.components.home.rememberIosOverscrollState
import org.akanework.gramophone.ui.library.LayoutType

/** One thing to pick, as the picker activities list it. */
class PickerEntry<T : Any>(
    val item: T,
    val title: String,
    val subtitle: String,
    val cover: Uri?,
    @DrawableRes val defaultCover: Int,
)

/**
 * The picker activities' page: a title bar and a plain list, each row handing its item back
 * through [onPick]. Sorted by title by the caller.
 */
@Composable
fun <T : Any> PickerScreen(
    title: String,
    entries: List<PickerEntry<T>>,
    onPick: (T) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val overscroll = rememberIosOverscrollState()
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    val barTopPadding = insets.calculateTopPadding() + GLASS_BAR_HEIGHT
    val density = LocalDensity.current
    val gapPx = with(density) { LIBRARY_ITEM_GAP.roundToPx() }
    val rowHeightPx = with(density) { LIST_HEIGHT.roundToPx() } + gapPx
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    Box(modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState).background(background)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(1),
                state = gridState,
                modifier = Modifier.fillMaxSize().iosOverscroll(overscroll),
                contentPadding = libraryContentPadding(top = barTopPadding),
                verticalArrangement = Arrangement.spacedBy(LIBRARY_ITEM_GAP),
                flingBehavior = rememberIosFlingBehavior(gridState),
                overscrollEffect = null,
            ) {
                itemsIndexed(entries) { index, entry ->
                    LibraryListRow(
                        layout = LayoutType.COMPACT_LIST,
                        title = entry.title,
                        subtitle = entry.subtitle,
                        cover = entry.cover,
                        defaultCover = entry.defaultCover,
                        hasMenu = false,
                        onClick = { onPick(entry.item) },
                        onMenu = {},
                        modifier = Modifier.libraryItemCard(
                            libraryItemShape(
                                topStart = index == 0, topEnd = index == 0,
                                bottomStart = index == entries.lastIndex, bottomEnd = index == entries.lastIndex,
                            )
                        ),
                    )
                }
            }
            LibraryFastScroller(
                gridState = gridState,
                itemCount = entries.size,
                headerCount = 0,
                columns = 1,
                rowHeightPx = rowHeightPx,
                headerHeightPx = 0,
                hintFor = { i -> entries.getOrNull(i)?.title?.firstOrNull()?.uppercase() ?: "-" },
                modifier = Modifier.padding(top = barTopPadding, bottom = LIBRARY_GROUP_CORNER),
            )
        }
        GlassTitleBar(
            hazeState = hazeState,
            title = title,
            scrolled = { Float.MAX_VALUE },
            toolbarPaddingStart = 4.dp,
            titlePaddingStart = 4.dp,
            navigationIcon = {
                LibraryIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    iconSize = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onBack,
                )
            },
        )
    }
}
