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

package org.akanework.gramophone.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.HeartRating
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
import org.akanework.gramophone.ui.widget.CardWidgetViewsBuilder

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
            ACTION_PREVIOUS -> {
                if (player != null) player.seekToPrevious()
                else sendMediaButtonFallback(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            ACTION_PLAY_PAUSE -> {
                if (player != null) {
                    if (player.isPlaying) player.pause() else player.play()
                } else {
                    sendMediaButtonFallback(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            }
            ACTION_NEXT -> {
                if (player != null) player.seekToNext()
                else sendMediaButtonFallback(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            ACTION_SHUFFLE -> {
                if (player != null) {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    update(context)
                }
            }
            ACTION_FAVORITE -> service?.toggleCurrentItemFavorite()
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
        val state = buildCurrentPlaybackState()
        val actions = buildWidgetActions(context)

        for (appWidgetId in appWidgetIds) {
            val initialViews = CardWidgetViewsBuilder.buildResponsiveRemoteViews(
                context, appWidgetManager, appWidgetId, state, actions
            )
            appWidgetManager.updateAppWidget(appWidgetId, initialViews)

            if (state.artworkUri != null && (state.artworkUri != cachedArtworkUri || cachedArtworkBitmap == null)) {
                loadArtworkAndRefresh(context, appWidgetManager, appWidgetId, state, actions)
            }
        }
    }

    private fun buildCurrentPlaybackState(): CardWidgetPlaybackState {
        val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
        val player = service?.endedWorkaroundPlayer
        val mediaItem = player?.currentMediaItem
        val artworkUri = mediaItem?.mediaMetadata?.artworkUri
        val cachedBitmap = if (artworkUri != null && artworkUri == cachedArtworkUri) cachedArtworkBitmap else null

        return CardWidgetPlaybackState(
            title = mediaItem?.mediaMetadata?.title?.toString().orEmpty(),
            artist = mediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
            isPlaying = player?.isPlaying == true,
            isFavorite = (mediaItem?.mediaMetadata?.userRating as? HeartRating)?.isHeart == true,
            isShuffle = player?.shuffleModeEnabled == true,
            artworkUri = artworkUri,
            artworkBitmap = cachedBitmap
        )
    }

    private fun buildWidgetActions(context: Context): CardWidgetActions {
        val openAppPi = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return CardWidgetActions(
            openAppPi = openAppPi,
            prevPi = buildActionPendingIntent(context, ACTION_PREVIOUS, 101),
            playPausePi = buildActionPendingIntent(context, ACTION_PLAY_PAUSE, 102),
            nextPi = buildActionPendingIntent(context, ACTION_NEXT, 103),
            shufflePi = buildActionPendingIntent(context, ACTION_SHUFFLE, 104),
            favoritePi = buildActionPendingIntent(context, ACTION_FAVORITE, 105)
        )
    }

    private fun loadArtworkAndRefresh(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
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
                val views = CardWidgetViewsBuilder.buildResponsiveRemoteViews(
                    context, appWidgetManager, appWidgetId, updatedState, actions
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun buildActionPendingIntent(
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

    private fun sendMediaButtonFallback(context: Context, keyCode: Int) {
        val serviceIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setClass(context, GramophonePlaybackService::class.java)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w("CardWidgetProvider", "Failed to start service for media button", e)
        }
    }

    companion object {
        const val ACTION_PREVIOUS = "org.akanework.gramophone.ACTION_CARD_WIDGET_PREVIOUS"
        const val ACTION_PLAY_PAUSE = "org.akanework.gramophone.ACTION_CARD_WIDGET_PLAY_PAUSE"
        const val ACTION_NEXT = "org.akanework.gramophone.ACTION_CARD_WIDGET_NEXT"
        const val ACTION_SHUFFLE = "org.akanework.gramophone.ACTION_CARD_WIDGET_SHUFFLE"
        const val ACTION_FAVORITE = "org.akanework.gramophone.ACTION_CARD_WIDGET_FAVORITE"

        private var cachedArtworkUri: Uri? = null
        private var cachedArtworkBitmap: Bitmap? = null

        fun hasWidget(context: Context): Boolean {
            val awm = AppWidgetManager.getInstance(context) ?: return false
            return awm.getAppWidgetIds(ComponentName(context, CardWidgetProvider::class.java)).isNotEmpty()
        }

        fun update(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            if (awm != null) {
                val ids = awm.getAppWidgetIds(ComponentName(context, CardWidgetProvider::class.java))
                if (ids.isNotEmpty()) {
                    CardWidgetProvider().onUpdate(context, awm, ids)
                }
            }
        }
    }
}
