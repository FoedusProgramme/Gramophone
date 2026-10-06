/*
 *     Copyright (C) 2026 Akane Foundation
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
package org.akanework.gramophone.ui.components.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * A list row that can be swiped away sideways. [onDismissed] runs once the row has settled off
 * screen, so the caller can remove it from the list without cutting the slide short. Use it
 * inside a keyed lazy list so the row's swipe state goes away together with the row.
 */
@Composable
fun DismissibleRow(
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    // Not saved: brought back already swiped away (scrolled back into view, say), the row would
    // be removed again without anyone swiping it
    val positionalThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold)
    }
    // Always the same callback, calling the latest: the box dismisses again whenever it changes,
    // and it does as the row moves up or down the list
    val currentOnDismissed by rememberUpdatedState(onDismissed)
    val onDismiss = remember { { _: SwipeToDismissBoxValue -> currentOnDismissed() } }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {},
        modifier = modifier,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        onDismiss = onDismiss,
        content = content,
    )
}
