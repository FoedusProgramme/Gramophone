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
package org.akanework.gramophone.ui.components.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
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
    SwipeToDismissBox(
        state = rememberSwipeToDismissBoxState(),
        backgroundContent = {},
        modifier = modifier,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
        onDismiss = { onDismissed() },
        content = content,
    )
}
