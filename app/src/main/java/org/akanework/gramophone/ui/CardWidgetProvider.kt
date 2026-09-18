/*
 *     Copyright (C) 2026 SteveZMTstudios
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

package org.akanework.gramophone.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.HeartRating
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.PlaybackPendingIntentBuilder
import coil3.BitmapImage
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.ui.widget.CardWidgetActions
import org.akanework.gramophone.ui.widget.CardWidgetPlaybackState
import org.akanework.gramophone.ui.widget.CardWidgetStore
import org.akanework.gramophone.ui.widget.CardWidgetViewsBuilder

@OptIn(UnstableApi::class)
class CardWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        handleWidgetAction(context, action)
    }

    private fun handleWidgetAction(context: Context, action: String) {
        val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
        val player = service?.endedWorkaroundPlayer
        when (action) {
            ACTION_REPEAT -> {
                if (player != null) {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                        else -> Player.REPEAT_MODE_OFF
                    }
                    updateAllWidgets(context)
                }
            }
            ACTION_SHUFFLE -> {
                if (player != null) {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    updateAllWidgets(context)
                }
            }
            ACTION_FAVORITE -> {
                service?.toggleCurrentItemFavorite()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val state = buildCurrentPlaybackState(context)
        val actions = buildWidgetActions(context, state)

        for (appWidgetId in appWidgetIds) {
            val views = CardWidgetViewsBuilder.buildCardResponsiveRemoteViews(
                context, appWidgetManager, appWidgetId, state, actions
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)

            if (state.artworkUri != null && (state.artworkUri != cachedArtworkUri || cachedArtworkBitmap == null)) {
                loadArtworkAndRefresh(context, appWidgetManager, appWidgetId, state, actions, isCircle = false)
            }
        }
    }

    companion object {
        const val ACTION_REPEAT = "org.akanework.gramophone.ACTION_CARD_WIDGET_REPEAT"
        const val ACTION_SHUFFLE = "org.akanework.gramophone.ACTION_CARD_WIDGET_SHUFFLE"
        const val ACTION_FAVORITE = "org.akanework.gramophone.ACTION_CARD_WIDGET_FAVORITE"

        internal var cachedArtworkUri: Uri? = null
        internal var cachedArtworkBitmap: Bitmap? = null

        fun buildCurrentPlaybackState(context: Context): CardWidgetPlaybackState {
            val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
            val player = service?.endedWorkaroundPlayer
            if (player != null) {
                val mediaItem = player.currentMediaItem
                val artworkUri = mediaItem?.mediaMetadata?.artworkUri
                val cachedBitmap = if (artworkUri != null && artworkUri == cachedArtworkUri) cachedArtworkBitmap else null

                val state = CardWidgetPlaybackState(
                    title = mediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                    artist = mediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
                    isPlaying = player.isPlaying,
                    isFavorite = (mediaItem?.mediaMetadata?.userRating as? HeartRating)?.isHeart == true,
                    isShuffle = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode,
                    artworkUri = artworkUri,
                    artworkBitmap = cachedBitmap
                )
                if (state.hasTrack) {
                    CardWidgetStore.saveLastPlaybackState(context, state)
                }
                return state
            } else {
                val saved = CardWidgetStore.loadLastPlaybackState(context)
                val cachedBitmap = if (saved.artworkUri != null && saved.artworkUri == cachedArtworkUri) cachedArtworkBitmap else null
                return saved.copy(artworkBitmap = cachedBitmap)
            }
        }

        fun buildWidgetActions(context: Context, state: CardWidgetPlaybackState): CardWidgetActions {
            val openAppPi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playPausePi = PlaybackPendingIntentBuilder(
                context,
                Player.COMMAND_PLAY_PAUSE,
                GramophonePlaybackService::class.java
            ).setStartAsForegroundService(!state.isPlaying)
                .build()

            val prevPi = PlaybackPendingIntentBuilder(
                context,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                GramophonePlaybackService::class.java
            ).build()

            val nextPi = PlaybackPendingIntentBuilder(
                context,
                Player.COMMAND_SEEK_TO_NEXT,
                GramophonePlaybackService::class.java
            ).build()

            val favoritePi = buildBroadcastPendingIntent(context, ACTION_FAVORITE, 105)
            val repeatPi = buildBroadcastPendingIntent(context, ACTION_REPEAT, 106)
            val shufflePi = buildBroadcastPendingIntent(context, ACTION_SHUFFLE, 107)

            return CardWidgetActions(
                openAppPi = openAppPi,
                favoritePi = favoritePi,
                prevPi = prevPi,
                playPausePi = playPausePi,
                nextPi = nextPi,
                repeatPi = repeatPi,
                shufflePi = shufflePi
            )
        }

        private fun buildBroadcastPendingIntent(
            context: Context,
            action: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, CardWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        internal fun loadArtworkAndRefresh(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            state: CardWidgetPlaybackState,
            actions: CardWidgetActions,
            isCircle: Boolean
        ) {
            val uri = state.artworkUri ?: return
            CoroutineScope(Dispatchers.IO).launch {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(256, 256)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap: Bitmap? = (result.image as? BitmapImage)?.bitmap
                    ?: result.image?.asDrawable(context.resources)?.toBitmap()

                withContext(Dispatchers.Main) {
                    cachedArtworkUri = uri
                    cachedArtworkBitmap = bitmap
                    val updatedState = state.copy(artworkBitmap = bitmap)
                    val views = if (isCircle) {
                        CardWidgetViewsBuilder.buildCircleResponsiveRemoteViews(
                            context, appWidgetManager, appWidgetId, updatedState, actions
                        )
                    } else {
                        CardWidgetViewsBuilder.buildCardResponsiveRemoteViews(
                            context, appWidgetManager, appWidgetId, updatedState, actions
                        )
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        fun hasWidget(context: Context): Boolean {
            val awm = AppWidgetManager.getInstance(context) ?: return false
            return awm.getAppWidgetIds(ComponentName(context, CardWidgetProvider::class.java)).isNotEmpty()
        }

        fun update(context: Context) {
            val awm = AppWidgetManager.getInstance(context) ?: return
            val ids = awm.getAppWidgetIds(ComponentName(context, CardWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                CardWidgetProvider().onUpdate(context, awm, ids)
            }
        }

        fun updateAllWidgets(context: Context) {
            update(context)
            CircleWidgetProvider.update(context)
        }
    }
}
