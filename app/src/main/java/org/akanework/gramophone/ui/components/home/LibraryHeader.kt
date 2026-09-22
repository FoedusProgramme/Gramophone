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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * `general_decor`: the 48dp row above every list with the item counter on the left and the
 * icon buttons on the right. Buttons are only laid out when their callback is non-null.
 */
@Composable
fun LibraryHeader(
    counterText: String,
    modifier: Modifier = Modifier,
    onCounterClick: (() -> Unit)? = null,
    onCreatePlaylist: (() -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    onShuffleAll: (() -> Unit)? = null,
    onSort: (() -> Unit)? = null,
    onJumpUp: (() -> Unit)? = null,
    onJumpDown: (() -> Unit)? = null,
    sortMenu: @Composable () -> Unit = {},
) {
    val tint = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxWidth().height(DECOR_HEIGHT)) {
        SingleLineText(
            counterText, 15.sp, 600, MaterialTheme.colorScheme.onSurface,
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
                .let {
                    if (onCounterClick != null) it.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCounterClick,
                    ) else it
                },
        )
        Row(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 9.dp),
            verticalAlignment = FloorCenterVertically,
        ) {
            if (onCreatePlaylist != null)
                LibraryIconButton(Icons.Rounded.Add, 26.dp, tint, onCreatePlaylist)
            if (onPlayAll != null)
                LibraryIconButton(Icons.Rounded.PlayArrow, 26.dp, tint, onPlayAll)
            if (onShuffleAll != null)
                LibraryIconButton(Icons.Rounded.Shuffle, 22.dp, tint, onShuffleAll)
            if (onSort != null) {
                Box {
                    LibraryIconButton(Icons.AutoMirrored.Rounded.Sort, 24.dp, tint, onSort)
                    sortMenu()
                }
            }
            if (onJumpUp != null)
                LibraryIconButton(Icons.Rounded.ArrowUpward, 24.dp, tint, onJumpUp)
            if (onJumpDown != null)
                LibraryIconButton(Icons.Rounded.ArrowDownward, 24.dp, tint, onJumpDown)
        }
    }
}
