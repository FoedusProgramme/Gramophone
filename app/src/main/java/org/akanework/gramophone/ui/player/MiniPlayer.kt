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

package org.akanework.gramophone.ui.player

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.compose.BottomSheetState
import org.akanework.gramophone.ui.components.compose.rememberBooleanPreference
import org.akanework.gramophone.ui.components.player.FullPlayerActions
import org.akanework.gramophone.ui.components.player.MiniBarColors
import org.akanework.gramophone.ui.components.player.NowPlayingColors
import org.akanework.gramophone.ui.components.player.NowPlayingSheetState
import org.akanework.gramophone.ui.components.player.PlayPauseIcon
import org.akanework.gramophone.ui.components.player.PlayerSheetPlayerState
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_ARTWORK_CORNER
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_BUTTON_SIZE
import org.akanework.gramophone.ui.components.player.PlayerUtilities.MINI_ICON_SIZE
import org.akanework.gramophone.ui.player.components.PlayerArtwork

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MiniPlayer(
    sheetState: BottomSheetState,
    playerState: PlayerSheetPlayerState,
    actions: FullPlayerActions,
    miniBarColors: State<MiniBarColors>,
    nowPlayingColors: State<NowPlayingColors>,
    cookieCover: Boolean,
    modifier: Modifier = Modifier,
) {
    val windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    val expanded = MaterialTheme.colorScheme.surfaceContainerLow
    val barColours by nowPlayingColors
    val backgroundColour = barColours.bar
    val (contentColor, playButtonContainer, playButtonContent) = miniBarColors.value


    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .drawBehind {
                val extra = 64.dp.toPx()
                val totalHeight = size.height + extra

                // overdraw fade
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to backgroundColour,
                            (size.height / totalHeight) to backgroundColour,
                            1f to Color.Transparent,
                        ),
                        startY = 0f,
                        endY = totalHeight,
                    ),
                    size = Size(size.width, totalHeight),
                )
            },
    ) {
        LinearProgressIndicator(
            progress = { playerState.positionFraction.coerceIn(0f, 1f) },
            drawStopIndicator = { },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
        )
        /* background progress bar
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(playerState.positionFraction.coerceIn(0f, 1f))
                .drawBehind { drawRect(lerp(barColours.fill, expanded, sheetState.progress.coerceIn(0f, 1f))) },
        )
         */

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
                .fillMaxSize(),
        ) {
            Box(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = modifier
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(48.dp)
                    ) {
                        PlayerArtwork(
                            playerState = playerState,
                            requestWidth = maxHeight.value,
                            cookieCover = cookieCover,
                            roundedCornerSize = MINI_ARTWORK_CORNER,
                            onClick = actions.openSongDetails
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            text = playerState.title?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = playerState.artist?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            FilledIconButton(
                onClick = actions.playPause,
                modifier = Modifier.size(MINI_BUTTON_SIZE),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = playButtonContainer,
                    contentColor = playButtonContent,
                ),
            ) {
                PlayPauseIcon(
                    playing = playerState.showPause,
                    tint = playButtonContent,
                    modifier = Modifier.size(MINI_ICON_SIZE),
                    contentDescription = stringResource(if (playerState.showPause) R.string.pause else R.string.play),
                )
            }
            IconButton(onClick = actions.next, modifier = Modifier.size(MINI_BUTTON_SIZE)) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = stringResource(R.string.skip_next),
                    tint = contentColor,
                    modifier = Modifier.size(MINI_ICON_SIZE),
                )
            }
        }
    }
}
