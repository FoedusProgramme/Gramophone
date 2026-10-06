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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.TintedIcon

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
