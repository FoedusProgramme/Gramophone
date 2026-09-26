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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The home list's FAB: one pill with an icon per action sharing its width, a circle when there
// is only one.
val LIBRARY_FAB_HEIGHT = 40.dp
private val FAB_WIDTH = 80.dp

/** From the list's container edges to the FAB. */
val LIBRARY_FAB_MARGIN = 16.dp

/** An action of the FAB; its icon is drawn [iconOffsetX] off its cell's centre. */
class LibraryFabAction(
    val icon: ImageVector,
    val iconSize: Dp = 24.dp,
    val iconOffsetX: Dp = 0.dp,
    val onClick: () -> Unit,
)

private fun fabWidth(count: Int): Dp = when (count) {
    0 -> 0.dp
    1 -> LIBRARY_FAB_HEIGHT
    else -> FAB_WIDTH
}

/**
 * The FAB for the bottom end of a list's container, holding every [actions] in one pill. Place
 * it by its end: as the actions change the pill stretches out of and squeezes into its end, its
 * icons staying put at its start and the extra ones clipped at its end. When the first icon
 * changes too, it squeezes away and comes back with the new ones.
 */
@Composable
fun LibraryFab(actions: List<LibraryFabAction>, modifier: Modifier = Modifier) {
    val width = remember { Animatable(fabWidth(actions.size).value) }
    // Actions currently laid out. The old ones stay while the pill shrinks.
    var shown by remember { mutableStateOf(actions) }
    val icons = actions.map { it.icon }
    LaunchedEffect(icons) {
        val spec = tween<Float>(FAB_ANIMATION_MS, easing = FastOutSlowInEasing)
        val start = shown.firstOrNull()?.icon
        val sameStart = start != null && start == icons.firstOrNull()
        if (!sameStart && shown.isNotEmpty() && width.value > 0f) {
            width.animateTo(0f, spec)
            shown = emptyList()
        }
        if (icons.size >= shown.size) shown = actions
        width.animateTo(fabWidth(icons.size).value, spec)
        if (icons.isNotEmpty()) shown = actions
    }
    if (width.value <= 0f || shown.isEmpty()) return
    val cellWidth = if (shown.size == 1) LIBRARY_FAB_HEIGHT else FAB_WIDTH / shown.size
    Surface(
        modifier = modifier.width(width.value.dp).height(LIBRARY_FAB_HEIGHT),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true)) {
            shown.forEachIndexed { index, shownAction ->
                // Use the current action when the icon matches, so the click acts on the current
                // page's list. Layout still comes from the shown action.
                val action = actions.getOrNull(index)
                    ?.takeIf { it.icon == shownAction.icon }
                Box(
                    Modifier
                        .width(cellWidth)
                        .fillMaxHeight()
                        .clickable(enabled = action != null) { action?.onClick?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        shownAction.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .offset(x = shownAction.iconOffsetX)
                            .size(shownAction.iconSize),
                    )
                }
            }
        }
    }
}

private const val FAB_ANIMATION_MS = 300
