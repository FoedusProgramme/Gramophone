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

import androidx.compose.foundation.Indication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.ui.components.home.IconCenter

/*
 * The buttons the screens share. Their sizes, shapes and colours are parameters, defaulting to
 * the usual ones; colours given as producers are read where they're drawn, so a scheme that
 * animates (the player's, following the cover) doesn't recompose them.
 */

object AppButtonDefaults {
    /** An icon button's size, as Material's touch target. */
    val Size = 48.dp

    /** An icon's size, as Material's. */
    val IconSize = 24.dp

    /** The lists' borderless grey ripple, the View lists' `rp_buttons`. */
    val ListRipple = ripple(bounded = false, radius = 24.dp, color = Color(0xFFAAAAAA))
}

/** [content] on a [shape]d tile of [container] colour, or on nothing without one, as a button. */
@Composable
fun TileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    container: ColorProducer? = null,
    indication: Indication? = ripple(),
    onLongClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .then(if (container == null) Modifier else Modifier.drawBehind { drawRect(container()) })
            .combinedClickable(
                interactionSource = null,
                indication = indication,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/** An [icon] button: the icon, [iconSize] big and [tint]ed, on a [TileButton]. */
@Composable
fun IconTileButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: ColorProducer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = AppButtonDefaults.IconSize,
    shape: Shape = CircleShape,
    container: ColorProducer? = null,
    indication: Indication? = ripple(),
    onLongClick: (() -> Unit)? = null,
) = TileButton(onClick, modifier, shape, container, indication, onLongClick) {
    TintedIcon(icon, tint, Modifier.size(iconSize), contentDescription)
}

/** The lists' icon button: 48dp, with their ripple, its icon placed as `MaterialButton` did. */
@Composable
fun LibraryIconButton(
    icon: ImageVector,
    iconSize: Dp,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: ColorProducer? = null,
) = TileButton(
    onClick = onClick,
    modifier = modifier.size(AppButtonDefaults.Size),
    container = container,
    indication = AppButtonDefaults.ListRipple,
    contentAlignment = IconCenter,
) {
    TintedIcon(icon, { tint }, Modifier.size(iconSize))
}

/** The back arrow at the start of a page's bar. */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LibraryIconButton(
        icon = Icons.AutoMirrored.Outlined.ArrowBack,
        iconSize = AppButtonDefaults.IconSize,
        tint = MaterialTheme.colorScheme.onSurface,
        onClick = onBack,
        modifier = modifier,
    )
}

/** A button of a connected group, like the player's bottom row, of the group's [shapes]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupButton(
    icon: ImageVector,
    contentDescription: String,
    tint: ColorProducer,
    shapes: ToggleButtonShapes,
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shapes = ButtonShapes(shapes.shape, shapes.pressedShape),
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = container),
        elevation = null,
        contentPadding = PaddingValues(0.dp),
    ) {
        TintedIcon(icon, tint, Modifier.size(AppButtonDefaults.IconSize), contentDescription)
    }
}

/** A toggle of a connected group, on [container], or [checkedContainer] while [checked]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupToggleButton(
    icon: ImageVector,
    contentDescription: String,
    tint: ColorProducer,
    shapes: ToggleButtonShapes,
    container: Color,
    checkedContainer: Color,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = modifier,
        shapes = shapes,
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = container,
            checkedContainerColor = checkedContainer,
        ),
        elevation = null,
        contentPadding = PaddingValues(0.dp),
    ) {
        TintedIcon(icon, tint, Modifier.size(AppButtonDefaults.IconSize), contentDescription)
    }
}

/** An icon whose [tint] is read in the draw phase. As big as an icon unless [modifier] sizes it. */
@Composable
fun TintedIcon(
    painter: Painter,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    Spacer(
        modifier
            .size(AppButtonDefaults.IconSize)
            .then(semantics)
            .drawBehind { with(painter) { draw(size, colorFilter = ColorFilter.tint(tint())) } },
    )
}

/** An icon whose [tint] is read in the draw phase. As big as an icon unless [modifier] sizes it. */
@Composable
fun TintedIcon(
    image: ImageVector,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) = TintedIcon(rememberVectorPainter(image), tint, modifier, contentDescription)
