/*
 *     Copyright (C) 2024 nift4
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

package org.akanework.gramophone.logic.ui

import androidx.core.content.ContextCompat
import coil3.asImage
import coil3.request.ImageRequest
import coil3.size.ScaleDrawable

fun ImageRequest.Builder.placeholderScaleToFit(placeholder: Int) {
    placeholder {
        ScaleDrawable(
            ContextCompat.getDrawable(
                it.context,
                placeholder
            )!!
        ).asImage()
    }
}