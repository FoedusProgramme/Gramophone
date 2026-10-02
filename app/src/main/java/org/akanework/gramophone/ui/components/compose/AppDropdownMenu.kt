/*
 *     Copyright (C) 2026 The Gramophone authors
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

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

/** Material 3's standard easing, quick off the mark and settling gently. */
private val MenuEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** How long a menu takes to grow or shrink, and to fade. */
private const val MENU_SPATIAL_MS = 250
private const val MENU_EFFECTS_MS = 150

/** Eased durations where the theme has springs, for a menu opening and closing. */
private object MenuMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = tween(MENU_SPATIAL_MS, easing = MenuEasing)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = tween(MENU_SPATIAL_MS, easing = MenuEasing)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = tween(MENU_SPATIAL_MS, easing = MenuEasing)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(MENU_EFFECTS_MS, easing = MenuEasing)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(MENU_EFFECTS_MS, easing = MenuEasing)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(MENU_EFFECTS_MS, easing = MenuEasing)
}

/**
 * A Material dropdown menu on the lowest container colour above the surface, with large corners,
 * opening and closing over eased durations rather than the theme's springs. It goes below its
 * anchor, or at [anchorPosition]: [MenuAnchorPosition.End] for a submenu opening beside its entry.
 *
 * Its shadow fades in with it: Material's DropdownMenu fades a layer of its own size, which cuts
 * the shadow off until the fade ends, so this one fades the menu with [SHADOW_ROOM] around it.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchorPosition: MenuAnchorPosition = MenuAnchorPosition.Below,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val room = with(LocalDensity.current) { SHADOW_ROOM.roundToPx() }
    val position = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
        anchorPosition,
        // Beside its anchor, up by the padding above the first entry, which then lines up with it
        offset = if (anchorPosition == MenuAnchorPosition.Below) DpOffset.Zero
            else DpOffset(0.dp, -MENU_VERTICAL_PADDING),
    )
    val positionWithRoom = remember(position, room) { RoomyPositionProvider(position, room) }
    MaterialTheme(motionScheme = MenuMotionScheme) {
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            popupPositionProvider = positionWithRoom,
            // The room may go past the edge of the screen; the menu is kept on it
            properties = PopupProperties(
                focusable = true,
                dismissOnClickOutside = dismissOnClickOutside,
                clippingEnabled = false,
            ),
        ) {
            // What DropdownMenu puts its entries on
            Surface(
                modifier = Modifier.padding(SHADOW_ROOM),
                shape = MenuShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = MenuDefaults.TonalElevation,
                shadowElevation = MenuDefaults.ShadowElevation,
            ) {
                Column(
                    modifier
                        .padding(vertical = MENU_VERTICAL_PADDING)
                        .width(IntrinsicSize.Max)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}

/**
 * Places a menu drawn [room] inside its popup where [menu] places the menu itself, and grows it
 * from the same point.
 */
private class RoomyPositionProvider(
    private val menu: DropdownMenuPopupPositionProvider,
    private val room: Int,
) : DropdownMenuPopupPositionProvider {
    override var transformOrigin by mutableStateOf(TransformOrigin.Center)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val size = IntSize(popupContentSize.width - 2 * room, popupContentSize.height - 2 * room)
        val position = menu.calculatePosition(anchorBounds, windowSize, layoutDirection, size)
        val origin = menu.transformOrigin
        transformOrigin = TransformOrigin(
            (room + origin.pivotFractionX * size.width) / popupContentSize.width,
            (room + origin.pivotFractionY * size.height) / popupContentSize.height,
        )
        return IntOffset(position.x - room, position.y - room)
    }
}

/** Above and below a menu's entries, as in DropdownMenu. */
private val MENU_VERTICAL_PADDING = 8.dp

/** Material 3 expressive's large corners. */
private val MenuShape = RoundedCornerShape(16.dp)

/** Room around a menu for its shadow, see [AppDropdownMenu]. */
private val SHADOW_ROOM = 8.dp
