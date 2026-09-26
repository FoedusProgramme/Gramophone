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
package org.akanework.gramophone.ui.components.home

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * A song row that can be dragged by its handle and removed by its button: the playlist editor's
 * and the queue's rows. Same height and cover as the compact library row.
 */

private val HANDLE_SLOT = 40.dp
private val COVER_SIZE = 50.dp
private val TEXT_MARGIN = 18.dp

@Composable
fun EditableSongRow(
    title: String,
    subtitle: String,
    cover: Uri?,
    @DrawableRes defaultCover: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    showControls: Boolean = true,
    colors: LibraryRowColors = defaultLibraryRowColors(),
) {
    Row(
        modifier
            .background(colors.container, colors.containerShape)
            .fillMaxWidth()
            .height(EDITABLE_ROW_HEIGHT)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = FloorCenterVertically,
    ) {
        if (showControls) {
            Box(
                Modifier.size(HANDLE_SLOT).then(handleModifier),
                contentAlignment = IconCenter,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragHandle,
                    contentDescription = null,
                    tint = colors.icon,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        LibraryCover(
            uri = cover,
            defaultCover = defaultCover,
            cornerRadius = LIST_ROUND_CORNER_SIZE,
            modifier = Modifier.padding(start = if (showControls) 0.dp else 18.dp).size(COVER_SIZE),
        )
        Column(Modifier.weight(1f).padding(start = TEXT_MARGIN)) {
            SingleLineText(
                title, 17.sp, 400, colors.title,
                Modifier.fillMaxWidth(),
            )
            SingleLineText(
                subtitle, 14.sp, 400, colors.subtitle,
                Modifier.fillMaxWidth(),
            )
        }
        if (showControls) {
            LibraryIconButton(
                icon = Icons.Outlined.Close,
                iconSize = 24.dp,
                tint = colors.icon,
                onClick = onRemove,
            )
        }
    }
}
