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

package org.akanework.gramophone.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.BottomSheetState
import org.akanework.gramophone.ui.components.compose.GroupButton
import org.akanework.gramophone.ui.components.compose.GroupToggleButton
import org.akanework.gramophone.ui.components.player.AnimatedColorScheme
import org.akanework.gramophone.ui.components.player.CookieMorphShape
import org.akanework.gramophone.ui.components.player.CrossfadeArtwork
import org.akanework.gramophone.ui.components.player.FullPlayerActions
import org.akanework.gramophone.ui.components.player.PlayerSheetPlayerState
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ACTION_BAR_VERTICAL_PADDING
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ACTION_BUTTON_SIZE
import kotlin.math.roundToInt

@Composable
internal fun ActionBarRow(
    queueSheetState: BottomSheetState,
    playerState: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    scheme: AnimatedColorScheme,
    horizontalMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val repeatMode = playerState.repeatMode
    val shuffle = playerState.shuffleMode
    val favorite = playerState.isFavorite
    val plainTint = scheme.colorProducer { onSurface }
    val checkTint = { on: Boolean ->
        scheme.colorProducer { if (on) onPrimaryContainer else onSurfaceVariant }
    }
    val container = scheme.color { surfaceBright }
    val checkedContainer = scheme.color { primaryContainer }
    val leading = ButtonGroupDefaults.connectedLeadingButtonShapes()
    val middle = ButtonGroupDefaults.connectedMiddleButtonShapes()
    val trailing = ButtonGroupDefaults.connectedTrailingButtonShapes()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = horizontalMargin,
                    top = ACTION_BAR_VERTICAL_PADDING,
                    end = horizontalMargin,
                    bottom = ACTION_BAR_VERTICAL_PADDING,
                ),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val slot = Modifier.weight(1f).height(ACTION_BUTTON_SIZE)
            GroupButton(
                Icons.AutoMirrored.Outlined.Article, stringResource(R.string.dialog_lyrics), plainTint,
                leading, container, actions.showLyrics, slot,
            )
            GroupToggleButton(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Outlined.RepeatOne
                } else {
                    Icons.Outlined.Repeat
                },
                contentDescription = stringResource(R.string.repeat_mode),
                tint = checkTint(repeatMode != Player.REPEAT_MODE_OFF),
                shapes = middle,
                container = container,
                checkedContainer = checkedContainer,
                checked = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = actions.cycleRepeat,
                modifier = slot,
            )
            GroupToggleButton(
                Icons.Outlined.Shuffle, stringResource(R.string.shuffle), checkTint(shuffle), middle,
                container, checkedContainer, shuffle, { actions.toggleShuffle(!shuffle) }, slot,
            )
            GroupToggleButton(
                icon = if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.playlist_favourite),
                // The favourite on the tertiary colours rather than the primary ones
                tint = scheme.colorProducer { if (favorite) onTertiaryContainer else onSurface },
                shapes = trailing,
                container = container,
                checkedContainer = scheme.color { tertiaryContainer },
                checked = favorite,
                onClick = { actions.toggleFavorite(!favorite) },
                modifier = slot,
            )
        }

        IconButton(
            onClick = queueSheetState::expandSoft,
            modifier = Modifier
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = null,
            )
        }
    }
}

@Composable
fun PlayerArtwork(
    playerState: PlayerSheetPlayerState,
    requestWidth: Float,
    cookieCover: Boolean,
    roundedCornerSize: Dp = 0.dp,
    onClick: () -> Unit,
) {
    val artwork = playerState.artworkUri

    val context = LocalPlatformContext.current
    val coverDescription = stringResource(R.string.dialog_album)
    val requestSizePx = requestWidth.roundToInt().coerceAtLeast(1)
    val request = remember(artwork, requestSizePx) {
        ImageRequest.Builder(context)
            .data(artwork)
            .size(requestSizePx)
            .precision(Precision.INEXACT)
            .build()
    }

    CrossfadeArtwork(
        request = request,
        modifier = Modifier
            .graphicsLayer {
                shape = if (cookieCover) {
                    CookieMorphShape(roundedCornerSize.value)
                } else {
                    RoundedCornerShape(roundedCornerSize)
                }
                clip = true
            }
            .fillMaxSize()
            .semantics {
                contentDescription = coverDescription
            }
            .then(Modifier.clickable(onClick = onClick)),
    )
}
