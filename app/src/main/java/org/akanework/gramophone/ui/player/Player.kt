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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.ui.MediaControllerViewModel
import org.akanework.gramophone.ui.components.compose.BottomSheet
import org.akanework.gramophone.ui.components.compose.BottomSheetState
import org.akanework.gramophone.ui.components.compose.rememberBottomSheetState
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlay
import org.akanework.gramophone.ui.components.lyrics.LyricsOverlayState
import org.akanework.gramophone.ui.components.lyrics.LyricsPadding
import org.akanework.gramophone.ui.components.player.AnimatedColorScheme
import org.akanework.gramophone.ui.components.player.FullPlayerActions
import org.akanework.gramophone.ui.components.player.MiniBarColors
import org.akanework.gramophone.ui.components.player.PlayerControls
import org.akanework.gramophone.ui.components.player.PlayerDialog
import org.akanework.gramophone.ui.components.player.PlayerDialogs
import org.akanework.gramophone.ui.components.player.PlayerSheetController
import org.akanework.gramophone.ui.components.player.PlayerSheetPlayerState
import org.akanework.gramophone.ui.components.player.PlayerUtilities.ART_TOP_GAP
import org.akanework.gramophone.ui.components.player.PlayerUtilities.QUEUE_PREVIEW_HEIGHT
import org.akanework.gramophone.ui.components.player.TopButtonColumn
import org.akanework.gramophone.ui.components.player.TopButtonRow
import org.akanework.gramophone.ui.components.player.nowPlayingColors
import org.akanework.gramophone.ui.components.player.rememberAnimatedColorScheme
import org.akanework.gramophone.ui.components.player.rememberArtworkColorScheme
import org.akanework.gramophone.ui.nav.NAV_TRANSITION_MS
import org.akanework.gramophone.ui.nav.NavAxisEasing
import org.akanework.gramophone.ui.player.components.PlayerArtwork
import org.akanework.gramophone.ui.theme.harmonizeBy

@Composable
fun BottomSheetPlayer(
    playerSheetState: BottomSheetState,
    sheetController: PlayerSheetController,
    playerLayout: PlayerLayout,
    modifier: Modifier = Modifier,
) {
    val playerState = sheetController.playerState
    val pageTinted = sheetController.pageTinted
    val actions = sheetController.actions
    val nowPlayingSheetState = sheetController.sheetState
    val dialogCallbacks = sheetController.dialogCallbacks

    // Its colours are read in the draw phase where they can be, so a song change's colour
    // animation mostly redraws instead of recomposing the player every frame.
    val targetScheme = rememberArtworkColorScheme(playerState.artworkUri)
    // Without a cover it's the app theme, which animates by itself when the theme changes
    val coverScheme = rememberAnimatedColorScheme(
        targetScheme, live = targetScheme === MaterialTheme.colorScheme,
    )
    var activeDialog by remember { mutableStateOf<PlayerDialog?>(null) }
    PlayerDialogs(
        activeDialog, coverScheme.target, dialogCallbacks, onDismiss = { activeDialog = null },
    )
    // How far the bar leans towards the app's hue: fully, except on a page themed from a cover,
    // which shows the cover's own colours. Animated with the page transition. Only read in the
    // draw phase and in the mini bar content, so the animation does not recompose the sheet on
    // every frame.
    val harmony = animateFloatAsState(
        if (pageTinted) 0f else 1f,
        tween(NAV_TRANSITION_MS, easing = NavAxisEasing),
        label = "page harmony",
    )
    val appPrimary = rememberUpdatedState(MaterialTheme.colorScheme.primary)
    val barColors = remember(coverScheme) {
        derivedStateOf { nowPlayingColors(appPrimary.value, harmony.value, coverScheme::color) }
    }
    val miniBarColors = remember(coverScheme) {
        derivedStateOf {
            val primary = appPrimary.value
            val f = harmony.value
            MiniBarColors(
                content = coverScheme.color { onSurface }.harmonizeBy(primary, f),
                playButton = coverScheme.color { secondaryContainer }.harmonizeBy(primary, f),
                onPlayButton = coverScheme.color { onSecondaryContainer }.harmonizeBy(primary, f),
            )
        }
    }

    BottomSheet(
        state = playerSheetState,
        modifier = modifier,
        contentBackground = {
            val expanded = coverScheme.color { surfaceContainerLow }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(lerp(barColors.value.bar, expanded, playerSheetState.progress.coerceIn(0f, 1f)))
                    },
            )
        },
        collapsedContent = {
            MiniPlayer(
                sheetState = playerSheetState,
                playerState = playerState,
                actions = actions,
                miniBarColors = miniBarColors,
                nowPlayingColors = barColors,
                cookieCover = playerLayout.cookieCover,
            )
        },
        content = {
            if (playerLayout.dualPane) {
                LandscapePlayer(
                    sheetState = playerSheetState,
                    playerLayout = playerLayout,
                    playerState = playerState,
                    mediaController = sheetController.mediaController,
                    actions = actions,
                    lyrics = sheetController.lyrics,
                    scheme = coverScheme,
                    onOpenDialog = { activeDialog = it },
                )
            } else {
                PortraitPlayer(
                    sheetState = playerSheetState,
                    playerLayout = playerLayout,
                    playerState = playerState,
                    mediaController = sheetController.mediaController,
                    actions = actions,
                    lyrics = sheetController.lyrics,
                    scheme = coverScheme,
                    onOpenDialog = { activeDialog = it },
                )
            }
        },
    )
}


@Composable
fun PortraitPlayer(
    sheetState: BottomSheetState,
    playerLayout: PlayerLayout,
    playerState: PlayerSheetPlayerState,
    mediaController: MediaControllerViewModel,
    actions: FullPlayerActions,
    lyrics: LyricsOverlayState,
    scheme: AnimatedColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val (playerInsets, queueInsets, dualPane, fullHeight, mqEnabled, cookieCover, miniPlayerHeight, queueHeight,
        expandedArtCorner) = playerLayout
    val lyricsCovering by remember(lyrics) { derivedStateOf { lyrics.covering } }
    Column(
        modifier = Modifier
            .windowInsetsPadding(playerInsets)
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (lyricsCovering) 0f else 1f
            }
    ) {
        TopButtonRow(playerState, actions, scheme, onOpenDialog)
        Spacer(Modifier.height(ART_TOP_GAP))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .aspectRatio(1f)
        ) {
            PlayerArtwork(
                playerState = playerState,
                requestWidth = maxHeight.value,
                cookieCover = cookieCover,
                roundedCornerSize = expandedArtCorner,
                onClick = {
                    sheetState.collapseSoft()
                    actions.openSongDetails()
                }
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PlayerControls(
                playerState, actions, scheme, onOpenDialog, 36.dp, Modifier.fillMaxWidth(),
            )
        }
    }

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = 0.dp,
        collapsedBound = queueHeight,
        expandedBound = sheetState.expandedBound - if (fullHeight) QUEUE_PREVIEW_HEIGHT else 0.dp,
        initialAnchor = BottomSheetState.COLLAPSED_ANCHOR,
    )
    BottomSheetQueue(
        queueSheetState = queueSheetState,
        playerLayout = playerLayout.copy(dualPane = false),
        playerState = playerState,
        mediaController = mediaController,
        actions = actions,
        scheme = scheme,
        onClearQueue = sheetState::forceDismiss,
    )

    if (lyricsCovering) {
        val paddingValues = playerInsets.asPaddingValues()
        LyricsOverlay(
            state = lyrics,
            scheme = scheme,
            padding = LyricsPadding(
                left = paddingValues.calculateLeftPadding(layoutDirection).value.toInt(),
                top = paddingValues.calculateTopPadding().value.toInt(),
                right = paddingValues.calculateRightPadding(layoutDirection).value.toInt(),
                bottom = paddingValues.calculateBottomPadding().value.toInt(),
            ),
        )
    }
}


@Composable
fun LandscapePlayer(
    sheetState: BottomSheetState,
    playerLayout: PlayerLayout,
    playerState: PlayerSheetPlayerState,
    mediaController: MediaControllerViewModel,
    actions: FullPlayerActions,
    lyrics: LyricsOverlayState,
    scheme: AnimatedColorScheme,
    onOpenDialog: (PlayerDialog) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val (playerInsets, queueInsets, dualPane, fullHeight, mqEnabled, cookieCover, miniPlayerHeight, queueHeight,
        expandedArtCorner) = playerLayout
    val dualPaneQueue = dualPane && playerLayout.mqEnabled
    val lyricsCovering by remember(lyrics) { derivedStateOf { lyrics.covering } }


    @Composable
    fun queueSheet() {
        val queueSheetState = rememberBottomSheetState(
            dismissedBound = 0.dp,
            collapsedBound = queueHeight,
            expandedBound = sheetState.expandedBound - if (fullHeight) QUEUE_PREVIEW_HEIGHT else 0.dp,
            initialAnchor = BottomSheetState.COLLAPSED_ANCHOR,
        )
        BottomSheetQueue(
            queueSheetState = queueSheetState,
            playerLayout = playerLayout.copy(dualPane = dualPaneQueue),
            playerState = playerState,
            mediaController = mediaController,
            scheme = scheme,
            actions = actions,
            onClearQueue = sheetState::forceDismiss,
        )
    }

    Row(
        modifier = Modifier
            .windowInsetsPadding(playerInsets)
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (lyricsCovering) 0f else 1f
            }
    ) {
        // artwork
        BoxWithConstraints(
            modifier = Modifier
                .weight(0.50f)
                .padding(32.dp)
                .aspectRatio(1f)
        ) {
            PlayerArtwork(
                playerState = playerState,
                requestWidth = maxHeight.value,
                cookieCover = cookieCover,
                roundedCornerSize = expandedArtCorner,
                onClick = {
                    sheetState.collapseSoft()
                    actions.openSongDetails()
                }
            )
        }

        // rest of player ui
        Column(
            modifier = Modifier
                .weight(0.50f)
                .fillMaxHeight()
        ) {
            PlayerControls(
                playerState,
                actions,
                scheme,
                onOpenDialog,
                28.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = QueueHeight)
                    .heightIn(min = 200.dp),
                verticalArrangement = Arrangement.Center,
            )

            // draw queue right pane (not mq + landscape)
            if (!dualPaneQueue) {
                queueSheet()
            }
        }

        TopButtonColumn(
            playerState, actions, scheme, onOpenDialog,
            modifier = Modifier
                .padding(top = 16.dp),
        )
    }

    // draw queue full screen (mq + landscape)
    if (dualPaneQueue) {
        queueSheet()
    }

    if (lyricsCovering) {
        val paddingValues = playerInsets.asPaddingValues()
        LyricsOverlay(
            state = lyrics,
            scheme = scheme,
            padding = LyricsPadding(
                left = paddingValues.calculateLeftPadding(layoutDirection).value.toInt(),
                top = paddingValues.calculateTopPadding().value.toInt(),
                right = paddingValues.calculateRightPadding(layoutDirection).value.toInt(),
                bottom = paddingValues.calculateBottomPadding().value.toInt(),
            ),
        )
    }
}

data class PlayerLayout(
    val playerInsets: WindowInsets,
    val queueInsets: WindowInsets,
    val dualPane: Boolean,
    val fullHeight: Boolean,
    val mqEnabled: Boolean,
    val cookieCover: Boolean,
    val miniPlayerHeight: Dp,
    val queueHeight: Dp,
    val expandedArtCorner: Dp,
)

val MiniPlayerHeight = 64.dp
val QueueHeight = 120.dp

val INSETS_U = WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
val INSETS_N = WindowInsetsSides.Horizontal + WindowInsetsSides.Top
val INSETS_C = WindowInsetsSides.Vertical + WindowInsetsSides.End
val INSETS_E = WindowInsetsSides.Vertical + WindowInsetsSides.Start
