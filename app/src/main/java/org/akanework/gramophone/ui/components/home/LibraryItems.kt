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

import androidx.compose.foundation.layout.width
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.akanework.gramophone.ui.LocalCardSurface

/** Centre like the View framework does: integer division, i.e. the odd pixel goes to the end. */
val FloorCenter = Alignment { size, space, _ ->
    IntOffset((space.width - size.width) / 2, (space.height - size.height) / 2)
}
val FloorCenterVertically = Alignment.Vertical { size, space -> (space - size) / 2 }

/** `MaterialButton` icon placement: horizontally rounded half up, vertically floored. */
val IconCenter = Alignment { size, space, _ ->
    IntOffset((space.width - size.width + 1) / 2, (space.height - size.height) / 2)
}

// Values from res/values/dimens.xml and the adapter_*_card layouts.
val LIST_HEIGHT = 60.dp
private val LIST_ROW_PADDING = 6.dp
private val LIST_COVER_START = 18.dp

/** Width of the track number shown instead of the cover. */
private val LIST_NUMBER_WIDTH = 28.dp

/** Between the screen's (inset) edge and the home's sheet of items. */
val LIBRARY_SIDE_MARGIN = 12.dp

/**
 * From the screen's (inset) edge to a list row's cover on the home. The mini player's cover
 * starts here too, lining up with the covers.
 */
val LIBRARY_COVER_START = LIBRARY_SIDE_MARGIN + LIST_ROW_PADDING + LIST_COVER_START
val EDITABLE_ROW_HEIGHT = 75.dp
val FOLDER_CARD_HEIGHT = 75.dp
val LIST_ROUND_CORNER_SIZE = 6.dp
val GRID_ROUND_CORNER_SIZE = 10.dp
val GRID_CARD_SIDE_PADDING = 12.dp
val GRID_CARD_MARGIN_TOP = 8.dp
val GRID_CARD_MARGIN_LABEL = 12.5.dp
val GRID_CARD_PADDING_BOTTOM = 0.dp
val GRID_CARD_LABEL_HEIGHT = 85.sp
val DECOR_HEIGHT = 48.dp

/** Between the home's items, where the sheet's surface-container-low shows through. */
val LIBRARY_ITEM_GAP = 2.dp

/** The corners the home's items turn to that gap. */
val LIBRARY_ITEM_CORNER = 2.dp

/** The corners of the sheet the home's items sit in, and of the items at its two ends. */
val LIBRARY_GROUP_CORNER = 28.dp

/** Every corner of the playing song's card, setting it apart from its neighbours. */
val LIBRARY_PLAYING_CORNER = 12.dp

/**
 * An item's shape: the group's corner on the sides given as true, which are the group's own
 * ends, and the small corner elsewhere, where the item meets another. At [emphasis] 1 every
 * corner is [LIBRARY_PLAYING_CORNER], the way the playing song's card stands apart.
 */
fun libraryItemShape(
    topStart: Boolean = false,
    topEnd: Boolean = false,
    bottomStart: Boolean = false,
    bottomEnd: Boolean = false,
    emphasis: Float = 0f,
): Shape {
    fun corner(groupEnd: Boolean) =
        lerp(if (groupEnd) LIBRARY_GROUP_CORNER else LIBRARY_ITEM_CORNER, LIBRARY_PLAYING_CORNER, emphasis)
    return RoundedCornerShape(
        topStart = corner(topStart),
        topEnd = corner(topEnd),
        bottomEnd = corner(bottomEnd),
        bottomStart = corner(bottomStart),
    )
}

/**
 * The shape of cell [index] of [count] in a grid of [columns], the last row closing the group,
 * and the first row opening it when [opensGroup].
 */
fun libraryCellShape(
    index: Int, count: Int, columns: Int, emphasis: Float = 0f, opensGroup: Boolean = false,
): Shape {
    val firstRow = opensGroup && index < columns
    val lastRow = index / columns == (count - 1) / columns
    val column = index % columns
    return libraryItemShape(
        topStart = firstRow && column == 0,
        topEnd = firstRow && (column == columns - 1 || index == count - 1),
        bottomStart = lastRow && column == 0,
        bottomEnd = lastRow && (column == columns - 1 || index == count - 1),
        emphasis = emphasis,
    )
}

/** One of the home's items: a card of [shape] on the sheet. */
@Composable
fun Modifier.libraryItemCard(shape: Shape = libraryItemShape()): Modifier =
    clip(shape).background(LocalCardSurface.current)

/** `rp_buttons`: a borderless 24dp ripple, used by every 48dp icon button in the lists. */
@Composable
fun Modifier.iconButtonRipple(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = ripple(bounded = false, radius = 24.dp, color = Color(0xFFAAAAAA)),
    onClick = onClick,
)

/** A 48dp `MaterialButton` with only an icon (insets 0, `rp_buttons` background). */
@Composable
fun LibraryIconButton(
    icon: ImageVector,
    iconSize: Dp,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(48.dp).iconButtonRipple(onClick),
        contentAlignment = IconCenter,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The cover `ImageView` inside its filled `MaterialCardView`: `colorSurfaceContainer` behind a
 * centre-cropped Coil image, falling back to [defaultCover] (fit, which for a square slot is
 * the same as crop) while loading and on error.
 */
@Composable
fun LibraryCover(
    uri: Uri?,
    @DrawableRes defaultCover: Int,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val fallback = rememberDefaultCoverPainter(defaultCover)
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(uri)
            .crossfade(true)
            .build(),
        contentDescription = null,
        placeholder = fallback,
        error = fallback,
        fallback = fallback,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    )
}

/** `adapter_list_card_larger` (LIST). */
@Composable
fun LibraryListRow(
    title: String,
    subtitle: String,
    cover: Uri?,
    @DrawableRes defaultCover: Int,
    hasMenu: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    colors: LibraryRowColors = defaultLibraryRowColors(),
    menu: @Composable () -> Unit = {},
    /** Shown in place of the cover, for lists whose covers would all be the same. */
    number: Int? = null,
    /** Shown before the menu button, such as a song's length. */
    trailing: String? = null,
) {
    Row(
        modifier
            .background(colors.container, colors.containerShape)
            .fillMaxWidth()
            .height(LIST_HEIGHT)
            // The View row is clickable but has no selectable background: no ripple.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = LIST_ROW_PADDING, end = LIST_ROW_PADDING),
        verticalAlignment = FloorCenterVertically,
    ) {
        if (number != null) {
            Box(
                // Aligns with the 24dp start margin of the page title.
                Modifier.padding(start = LIST_COVER_START).width(LIST_NUMBER_WIDTH),
                contentAlignment = Alignment.CenterStart,
            ) {
                SingleLineText(number.toString(), 15.sp, 500, colors.subtitle)
            }
        } else {
            LibraryCover(
                uri = cover,
                defaultCover = defaultCover,
                cornerRadius = LIST_ROUND_CORNER_SIZE,
                modifier = Modifier.padding(start = LIST_COVER_START).size(46.dp),
            )
        }
        Column(
            Modifier.weight(1f).padding(start = if (number != null) 4.dp else 16.dp),
        ) {
            SingleLineText(
                title, 14.sp, 500, colors.title,
                Modifier.fillMaxWidth(),
            )
            SingleLineText(
                subtitle, 14.sp, 400, colors.subtitle,
                Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) {
            SingleLineText(
                trailing, 14.sp, 400, colors.subtitle,
                Modifier.padding(start = 8.dp, end = if (hasMenu) 0.dp else 12.dp),
            )
        }
        if (hasMenu) {
            Box {
                LibraryIconButton(
                    icon = Icons.Outlined.MoreVert,
                    iconSize = 24.dp,
                    tint = colors.icon,
                    onClick = onMenu,
                )
                menu()
            }
        }
    }
}

/** `adapter_grid_card` (GRID / COMPACT_GRID). The menu opens on long press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryGridCard(
    title: String,
    subtitle: String,
    trackCount: String,
    cover: Uri?,
    @DrawableRes defaultCover: Int,
    hasMenu: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    colors: LibraryRowColors = defaultLibraryRowColors(),
    menu: @Composable () -> Unit = {},
) {
    val labelHeight = with(LocalDensity.current) { GRID_CARD_LABEL_HEIGHT.toDp() }
    Column(
        modifier
            .background(colors.container, colors.containerShape)
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = if (hasMenu) onMenu else null,
            )
            .padding(bottom = GRID_CARD_PADDING_BOTTOM),
    ) {
        LibraryCover(
            uri = cover,
            defaultCover = defaultCover,
            cornerRadius = GRID_ROUND_CORNER_SIZE,
            modifier = Modifier
                .padding(start = GRID_CARD_SIDE_PADDING, end = GRID_CARD_SIDE_PADDING, top = GRID_CARD_MARGIN_TOP)
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Row(
            Modifier.fillMaxWidth().height(labelHeight + GRID_CARD_MARGIN_LABEL * 2),
            verticalAlignment = FloorCenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = GRID_CARD_SIDE_PADDING)
                    .height(labelHeight),
            ) {
                SingleLineText(
                    title, 15.sp, 500, colors.title,
                    Modifier.fillMaxWidth(),
                )
                SingleLineText(
                    subtitle, 15.sp, 500, colors.subtitle,
                    Modifier.fillMaxWidth(),
                )
                SingleLineText(
                    trackCount, 12.sp, 500, colors.subtitle,
                    Modifier.fillMaxWidth(),
                )
            }
            }
        menu()
    }
}

/** Folder icon in a transparent card, title and item count. */
@Composable
fun LibraryFolderRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(FOLDER_CARD_HEIGHT)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 24.dp, end = 54.dp),
        verticalAlignment = FloorCenterVertically,
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(LIST_ROUND_CORNER_SIZE))) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize().padding(10.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 18.dp)) {
            SingleLineText(title, 17.sp, 400, MaterialTheme.colorScheme.onSurface)
            SingleLineText(subtitle, 14.sp, 400, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
