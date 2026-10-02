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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
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
 * A Material `DropdownMenu` on the lowest container colour above the surface, opening and closing
 * over eased durations rather than the theme's springs. With an [anchorPosition], it goes there
 * instead of below its anchor: [MenuAnchorPosition.End] for a submenu opening beside its entry.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    anchorPosition: MenuAnchorPosition? = null,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialTheme(motionScheme = MenuMotionScheme) {
        val color = MaterialTheme.colorScheme.surfaceContainerLow
        if (anchorPosition == null) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismissRequest,
                modifier = modifier,
                properties = properties,
                containerColor = color,
                content = content,
            )
        } else {
            DropdownMenuPopup(
                expanded = expanded,
                onDismissRequest = onDismissRequest,
                // Up by the padding above the first entry, which then lines up with the anchor
                popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
                    anchorPosition,
                    offset = DpOffset(0.dp, -MENU_VERTICAL_PADDING),
                ),
                properties = properties,
            ) {
                // What DropdownMenu puts its entries on
                Surface(
                    shape = MenuDefaults.shape,
                    color = color,
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
}

/** Above and below a menu's entries, as in DropdownMenu. */
private val MENU_VERTICAL_PADDING = 8.dp
