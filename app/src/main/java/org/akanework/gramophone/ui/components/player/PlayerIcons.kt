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

package org.akanework.gramophone.ui.components.player

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.R

/** An icon's size unless its modifier gives it one, like Icon's. */
private val DEFAULT_ICON_SIZE = 24.dp

/** An icon whose [tint] is read in the draw phase. 24dp unless [modifier] sizes it. */
@Composable
internal fun TintedIcon(
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
            .size(DEFAULT_ICON_SIZE)
            .then(semantics)
            .drawBehind { with(painter) { draw(size, colorFilter = ColorFilter.tint(tint())) } },
    )
}

/** An icon whose [tint] is read in the draw phase. 24dp unless [modifier] sizes it. */
@Composable
internal fun TintedIcon(
    image: ImageVector,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) = TintedIcon(rememberVectorPainter(image), tint, modifier, contentDescription)

/** The play button's icon, which morphs from play to pause while [playing]. */
@Composable
internal fun PlayPauseIcon(
    playing: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) = PlayPauseIcon(playing, ColorProducer { tint }, modifier, contentDescription)

/** [PlayPauseIcon] with its [tint] read in the draw phase. */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
internal fun PlayPauseIcon(
    playing: Boolean,
    tint: ColorProducer,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val image = AnimatedImageVector.animatedVectorResource(R.drawable.ic_play_to_pause_anim)
    val painter = rememberAnimatedVectorPainter(image, atEnd = playing)
    TintedIcon(painter, tint, modifier, contentDescription)
}
