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

package org.akanework.gramophone.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.ui.CardWidgetProvider
import org.akanework.gramophone.ui.MainActivity
import uk.akane.libphonograph.items.hdArtworkUri

abstract class BaseWidgetProvider : AppWidgetProvider() {

    abstract fun buildViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
    ): RemoteViews

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
            val views = buildViews(context, appWidgetManager, appWidgetId, state, actions)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        val artUri = state.bestArtworkUri
        if (artUri != null && artUri != cachedArtworkUri && artUri != failedArtworkUri) {
            loadArtworkAndRefresh(context, artUri)
        }
    }

    private fun loadArtworkAndRefresh(
        context: Context,
        uri: Uri
    ) {
        if (loadingArtworkUri == uri) return
        loadingArtworkUri = uri
        coroutineScope.launch {
            try {
                val bitmap: Bitmap? = try {
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .size(256, 256)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    (result.image as? BitmapImage)?.bitmap
                        ?: result.image?.asDrawable(context.resources)?.toBitmap()
                } catch (t: Throwable) {
                    Log.w(TAG, "Artwork load failed: $uri", t)
                    null
                } finally {
                    withContext(Dispatchers.Main) {
                        loadingArtworkUri = null
                    }
                }

                withContext(Dispatchers.Main) {
                    val currentState = buildCurrentPlaybackState(context)
                    if (currentState.bestArtworkUri == uri) {
                        if (bitmap != null) {
                            cachedArtworkUri = uri
                            cachedArtworkBitmap = bitmap
                            failedArtworkUri = null
                            DesktopWidgetManager.updateAllWidgets(context)
                        } else {
                            failedArtworkUri = uri
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error in widget artwork loading workflow", t)
            }
        }
    }

    companion object {
        private const val TAG = "BaseWidgetProvider"

        const val ACTION_REPEAT = "org.akanework.gramophone.ACTION_CARD_WIDGET_REPEAT"
        const val ACTION_SHUFFLE = "org.akanework.gramophone.ACTION_CARD_WIDGET_SHUFFLE"
        const val ACTION_FAVORITE = "org.akanework.gramophone.ACTION_CARD_WIDGET_FAVORITE"
        private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.w(TAG, "Uncaught exception in widget coroutine scope", throwable)
        }
        private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)
        private var cachedArtworkUri: Uri? = null
        private var cachedArtworkBitmap: Bitmap? = null
        private var loadingArtworkUri: Uri? = null
        private var failedArtworkUri: Uri? = null

        fun buildCurrentPlaybackState(context: Context): CardWidgetPlaybackState {
            val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
            val player = service?.endedWorkaroundPlayer
            if (player != null) {
                val mediaItem = player.currentMediaItem
                val artworkUri = mediaItem?.mediaMetadata?.artworkUri
                val hdArtworkUri = mediaItem?.mediaMetadata?.hdArtworkUri
                val bestUri = hdArtworkUri ?: artworkUri
                val cachedBitmap = if (bestUri != null && bestUri == cachedArtworkUri) cachedArtworkBitmap else null

                return CardWidgetPlaybackState(
                    title = mediaItem?.mediaMetadata?.title?.toString().orEmpty(),
                    artist = mediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
                    isPlaying = player.isPlaying,
                    isFavorite = (mediaItem?.mediaMetadata?.userRating as? HeartRating)?.isHeart == true,
                    isShuffle = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode,
                    artworkUri = artworkUri,
                    hdArtworkUri = hdArtworkUri,
                    artworkBitmap = cachedBitmap
                )
            } else {
                val saved = CardWidgetStore.loadPlaybackState(context)
                val bestUri = saved.bestArtworkUri
                val cachedBitmap = if (bestUri != null && bestUri == cachedArtworkUri) cachedArtworkBitmap else null
                return saved.copy(artworkBitmap = cachedBitmap)
            }
        }

        fun savePlaybackSnapshot(context: Context) {
            val state = buildCurrentPlaybackState(context)
            if (state.hasTrack) {
                CardWidgetStore.savePlaybackState(context, state)
            }
        }

        @OptIn(UnstableApi::class)
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

            // Seek previous / next via PlaybackPendingIntentBuilder only uses startService on API 26+
            // which is rejected by Android when the app is in background/dead ("Background start not allowed").
            // When the service is not active, fallback to openAppPi so user click smoothly opens the app.
            val isServiceAlive = GramophonePlaybackService.instanceForWidgetAndLyricsOnly != null
            val prevPi = if (isServiceAlive) {
                PlaybackPendingIntentBuilder(
                    context,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    GramophonePlaybackService::class.java
                ).build()
            } else {
                openAppPi
            }

            val nextPi = if (isServiceAlive) {
                PlaybackPendingIntentBuilder(
                    context,
                    Player.COMMAND_SEEK_TO_NEXT,
                    GramophonePlaybackService::class.java
                ).build()
            } else {
                openAppPi
            }

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
    }
}
