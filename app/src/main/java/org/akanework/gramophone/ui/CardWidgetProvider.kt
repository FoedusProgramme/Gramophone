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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.HeartRating
import androidx.preference.PreferenceManager
import coil3.BitmapImage
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.dpToPx
import kotlin.math.abs

class CardWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PREVIOUS -> {
                val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                if (service != null) {
                    service.endedWorkaroundPlayer?.seekToPrevious()
                } else {
                    sendMediaButtonFallback(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
            }
            ACTION_PLAY_PAUSE -> {
                val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                if (service != null) {
                    if (service.endedWorkaroundPlayer?.isPlaying == true) {
                        service.endedWorkaroundPlayer?.pause()
                    } else {
                        service.endedWorkaroundPlayer?.play()
                    }
                } else {
                    sendMediaButtonFallback(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
            }
            ACTION_NEXT -> {
                val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                if (service != null) {
                    service.endedWorkaroundPlayer?.seekToNext()
                } else {
                    sendMediaButtonFallback(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }
            ACTION_SHUFFLE -> {
                val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                val player = service?.endedWorkaroundPlayer
                if (player != null) {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                    update(context)
                }
            }
            ACTION_FAVORITE -> {
                val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
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
        val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
        val mediaItem = service?.endedWorkaroundPlayer?.currentMediaItem
        val isPlaying = service?.endedWorkaroundPlayer?.isPlaying == true
        val isShuffle = service?.endedWorkaroundPlayer?.shuffleModeEnabled == true
        val isFavorite = (mediaItem?.mediaMetadata?.userRating as? HeartRating)?.isHeart == true

        val duration = service?.endedWorkaroundPlayer?.duration ?: 0L
        val position = service?.endedWorkaroundPlayer?.currentPosition ?: 0L
        val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

        val title = if (mediaItem != null) (mediaItem.mediaMetadata.title?.toString() ?: "") else ""
        val artist = if (mediaItem != null) (mediaItem.mediaMetadata.artist?.toString() ?: "") else ""
        val artworkUri = mediaItem?.mediaMetadata?.artworkUri

        val openAppPi = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevPi = buildActionPendingIntent(context, ACTION_PREVIOUS, 101)
        val playPausePi = buildActionPendingIntent(context, ACTION_PLAY_PAUSE, 102)
        val nextPi = buildActionPendingIntent(context, ACTION_NEXT, 103)
        val shufflePi = buildActionPendingIntent(context, ACTION_SHUFFLE, 104)
        val favoritePi = buildActionPendingIntent(context, ACTION_FAVORITE, 105)

        val cachedBitmap = if (artworkUri != null && artworkUri == cachedArtworkUri) cachedArtworkBitmap else null

        for (appWidgetId in appWidgetIds) {
            val initialViews = buildResponsiveRemoteViews(
                context,
                appWidgetManager,
                appWidgetId,
                title,
                artist,
                isPlaying,
                isFavorite,
                isShuffle,
                progress,
                openAppPi,
                favoritePi,
                prevPi,
                playPausePi,
                nextPi,
                shufflePi,
                cachedBitmap
            )
            appWidgetManager.updateAppWidget(appWidgetId, initialViews)

            if (artworkUri != null && (artworkUri != cachedArtworkUri || cachedArtworkBitmap == null)) {
                CoroutineScope(Dispatchers.IO).launch {
                    val request = ImageRequest.Builder(context)
                        .data(artworkUri)
                        .size(256, 256)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    val bitmap: Bitmap? = (result.image as? BitmapImage)?.bitmap
                        ?: result.image?.asDrawable(context.resources)?.toBitmap()
                    withContext(Dispatchers.Main) {
                        cachedArtworkUri = artworkUri
                        cachedArtworkBitmap = bitmap
                        val viewsWithBitmap = buildResponsiveRemoteViews(
                            context,
                            appWidgetManager,
                            appWidgetId,
                            title,
                            artist,
                            isPlaying,
                            isFavorite,
                            isShuffle,
                            progress,
                            openAppPi,
                            favoritePi,
                            prevPi,
                            playPausePi,
                            nextPi,
                            shufflePi,
                            bitmap
                        )
                        appWidgetManager.updateAppWidget(appWidgetId, viewsWithBitmap)
                    }
                }
            }
        }
    }

    private fun buildResponsiveRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        title: String,
        artist: String,
        isPlaying: Boolean,
        isFavorite: Boolean,
        isShuffle: Boolean,
        progress: Float,
        openAppPi: PendingIntent,
        favoritePi: PendingIntent,
        prevPi: PendingIntent,
        playPausePi: PendingIntent,
        nextPi: PendingIntent,
        shufflePi: PendingIntent,
        bitmap: Bitmap?
    ): RemoteViews {
        val pillSingleButtonViews = buildPillRemoteViews(
            context,
            isPlaying,
            openAppPi,
            playPausePi,
            bitmap,
            showCover = false
        )

        val pillViews = buildPillRemoteViews(
            context,
            isPlaying,
            openAppPi,
            playPausePi,
            bitmap,
            showCover = true
        )

        val circleViews = buildCircleRemoteViews(
            context,
            isPlaying,
            progress,
            openAppPi,
            playPausePi,
            bitmap
        )

        val cardViews = buildCardRemoteViews(
            context,
            title,
            artist,
            isPlaying,
            openAppPi,
            prevPi,
            playPausePi,
            nextPi,
            bitmap,
            showPrevious = true,
            showNext = true
        )

        val cardNarrowViews = buildCardRemoteViews(
            context,
            title,
            artist,
            isPlaying,
            openAppPi,
            prevPi,
            playPausePi,
            nextPi,
            bitmap,
            showPrevious = false,
            showNext = true
        )

        val mediumViews = buildMediumRemoteViews(
            context,
            title,
            artist,
            isPlaying,
            openAppPi,
            prevPi,
            playPausePi,
            nextPi,
            bitmap,
            showMoreButtons = false,
            showPrevious = true,
            showNext = true,
            favoritePi = null,
            shufflePi = null,
            isFavorite = false,
            isShuffle = false
        )

        val mediumWideViews = buildMediumRemoteViews(
            context,
            title,
            artist,
            isPlaying,
            openAppPi,
            prevPi,
            playPausePi,
            nextPi,
            bitmap,
            showMoreButtons = true,
            showPrevious = true,
            showNext = true,
            favoritePi = favoritePi,
            shufflePi = shufflePi,
            isFavorite = isFavorite,
            isShuffle = isShuffle
        )

        val largeViews = buildLargeRemoteViews(
            context,
            title,
            artist,
            isPlaying,
            isFavorite = false,
            isShuffle = false,
            openAppPi = openAppPi,
            favoritePi = null,
            prevPi = prevPi,
            playPausePi = playPausePi,
            nextPi = nextPi,
            shufflePi = null,
            bitmap = bitmap,
            showMoreButtons = false,
            showPrevious = true,
            showNext = true
        )

        val largeWideViews = buildLargeRemoteViews(
            context,
            title,
            artist,
            isPlaying,
            isFavorite = isFavorite,
            isShuffle = isShuffle,
            openAppPi = openAppPi,
            favoritePi = favoritePi,
            prevPi = prevPi,
            playPausePi = playPausePi,
            nextPi = nextPi,
            shufflePi = shufflePi,
            bitmap = bitmap,
            showMoreButtons = true,
            showPrevious = true,
            showNext = true
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val viewMapping = mapOf(
                SizeF(40f, 40f) to pillSingleButtonViews,
                SizeF(100f, 40f) to pillViews,
                SizeF(190f, 40f) to cardViews,
                SizeF(90f, 90f) to circleViews,
                SizeF(180f, 80f) to mediumViews,
                SizeF(280f, 80f) to mediumWideViews,
                SizeF(180f, 170f) to largeViews,
                SizeF(280f, 170f) to largeWideViews
            )
            return RemoteViews(viewMapping)
        } else {
            val options = try {
                appWidgetManager.getAppWidgetOptions(appWidgetId)
            } catch (e: Exception) {
                null
            }
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0

            return when {
                minHeight < 75 && minWidth in 1..99 -> pillSingleButtonViews
                minHeight < 75 && minWidth in 100..189 -> pillViews
                minHeight < 75 && minWidth >= 190 -> cardViews
                minWidth in 75..220 && minHeight in 75..220 && abs(minWidth - minHeight) < 50 -> circleViews
                minHeight in 75..165 && minWidth >= 190 -> {
                    if (minWidth >= 280) mediumWideViews else mediumViews
                }
                minHeight >= 165 && minWidth >= 190 -> {
                    if (minWidth >= 280) largeWideViews else largeViews
                }
                minWidth < 190 && minHeight < 75 -> pillViews
                minWidth < 190 -> circleViews
                else -> cardViews
            }
        }
    }

    private fun getRoundedBitmap(src: Bitmap, cornerRadiusPx: Float): Bitmap {
        return try {
            val width = src.width.coerceAtLeast(1)
            val height = src.height.coerceAtLeast(1)
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(src, 0f, 0f, paint)
            output
        } catch (e: Throwable) {
            src
        }
    }

    private fun getCircularBitmap(src: Bitmap): Bitmap {
        return try {
            val size = minOf(src.width, src.height).coerceAtLeast(1)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val radius = size / 2f
            canvas.drawCircle(radius, radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val left = (size - src.width) / 2f
            val top = (size - src.height) / 2f
            canvas.drawBitmap(src, left, top, paint)
            output
        } catch (e: Throwable) {
            src
        }
    }

    private fun buildPillRemoteViews(
        context: Context,
        isPlaying: Boolean,
        openAppPi: PendingIntent,
        playPausePi: PendingIntent,
        bitmap: Bitmap?,
        showCover: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_pill).apply {
            setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
            )
            setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (isPlaying) R.string.pause else R.string.play)
            )
            setOnClickPendingIntent(R.id.widget_card_root, openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, openAppPi)
            setOnClickPendingIntent(R.id.widget_play_pause, playPausePi)

            if (showCover) {
                setViewVisibility(R.id.widget_cover, View.VISIBLE)
                if (bitmap != null) {
                    setImageViewBitmap(R.id.widget_cover, getCircularBitmap(bitmap))
                } else {
                    setImageViewResource(R.id.widget_cover, R.drawable.ic_default_cover)
                }
            } else {
                setViewVisibility(R.id.widget_cover, View.GONE)
            }
        }
    }

    private fun buildCircleRemoteViews(
        context: Context,
        isPlaying: Boolean,
        progress: Float,
        openAppPi: PendingIntent,
        playPausePi: PendingIntent,
        bitmap: Bitmap?
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_circle).apply {
            setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
            )
            setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (isPlaying) R.string.pause else R.string.play)
            )
            setOnClickPendingIntent(R.id.widget_card_root, openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, openAppPi)
            setOnClickPendingIntent(R.id.widget_play_pause, playPausePi)
            if (bitmap != null) {
                setImageViewBitmap(R.id.widget_cover, getCircularBitmap(bitmap))
            } else {
                setImageViewResource(R.id.widget_cover, R.drawable.ic_default_cover)
            }

            val progressBitmap = generateCircularProgressBar(
                context,
                128.dpToPx(context),
                progress,
                4.dpToPx(context).toFloat()
            )
            setImageViewBitmap(R.id.widget_progress, progressBitmap)
        }
    }

    private fun buildCardRemoteViews(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        openAppPi: PendingIntent,
        prevPi: PendingIntent,
        playPausePi: PendingIntent,
        nextPi: PendingIntent,
        bitmap: Bitmap?,
        showPrevious: Boolean = true,
        showNext: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget).apply {
            setTextViewText(R.id.widget_title, title)
            setTextViewText(R.id.widget_artist, artist)
            setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
            )
            setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (isPlaying) R.string.pause else R.string.play)
            )
            setOnClickPendingIntent(R.id.widget_card_root, openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, openAppPi)
            setOnClickPendingIntent(R.id.widget_play_pause, playPausePi)

            if (showPrevious) {
                setViewVisibility(R.id.widget_previous, View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_previous, prevPi)
            } else {
                setViewVisibility(R.id.widget_previous, View.GONE)
            }

            if (showNext) {
                setViewVisibility(R.id.widget_next, View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_next, nextPi)
            } else {
                setViewVisibility(R.id.widget_next, View.GONE)
            }

            if (bitmap != null) {
                setImageViewBitmap(R.id.widget_cover, getRoundedBitmap(bitmap, 12.dpToPx(context).toFloat()))
            } else {
                setImageViewResource(R.id.widget_cover, R.drawable.ic_default_cover)
            }
        }
    }

    private fun buildMediumRemoteViews(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        openAppPi: PendingIntent,
        prevPi: PendingIntent,
        playPausePi: PendingIntent,
        nextPi: PendingIntent,
        bitmap: Bitmap?,
        showMoreButtons: Boolean,
        showPrevious: Boolean = true,
        showNext: Boolean = true,
        favoritePi: PendingIntent?,
        shufflePi: PendingIntent?,
        isFavorite: Boolean,
        isShuffle: Boolean
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_medium).apply {
            setTextViewText(R.id.widget_title, title)
            setTextViewText(R.id.widget_artist, artist)
            setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
            )
            setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (isPlaying) R.string.pause else R.string.play)
            )
            setOnClickPendingIntent(R.id.widget_card_root, openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, openAppPi)
            setOnClickPendingIntent(R.id.widget_play_pause, playPausePi)

            if (showMoreButtons && favoritePi != null && shufflePi != null) {
                setViewVisibility(R.id.widget_favorite, View.VISIBLE)
                setViewVisibility(R.id.widget_shuffle, View.VISIBLE)
                setImageViewResource(
                    R.id.widget_favorite,
                    if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
                )
                setInt(R.id.widget_favorite, "setImageAlpha", if (isFavorite) 255 else 180)
                setImageViewResource(
                    R.id.widget_shuffle,
                    R.drawable.ic_shuffle
                )
                setInt(R.id.widget_shuffle, "setImageAlpha", if (isShuffle) 255 else 90)
                setOnClickPendingIntent(R.id.widget_favorite, favoritePi)
                setOnClickPendingIntent(R.id.widget_shuffle, shufflePi)
            } else {
                setViewVisibility(R.id.widget_favorite, View.GONE)
                setViewVisibility(R.id.widget_shuffle, View.GONE)
            }

            if (showPrevious) {
                setViewVisibility(R.id.widget_previous, View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_previous, prevPi)
            } else {
                setViewVisibility(R.id.widget_previous, View.GONE)
            }

            if (showNext) {
                setViewVisibility(R.id.widget_next, View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_next, nextPi)
            } else {
                setViewVisibility(R.id.widget_next, View.GONE)
            }

            if (bitmap != null) {
                setImageViewBitmap(R.id.widget_cover, getRoundedBitmap(bitmap, 12.dpToPx(context).toFloat()))
            } else {
                setImageViewResource(R.id.widget_cover, R.drawable.ic_default_cover)
            }
        }
    }

    private fun buildLargeRemoteViews(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        isFavorite: Boolean,
        isShuffle: Boolean,
        openAppPi: PendingIntent,
        favoritePi: PendingIntent?,
        prevPi: PendingIntent,
        playPausePi: PendingIntent,
        nextPi: PendingIntent,
        shufflePi: PendingIntent?,
        bitmap: Bitmap?,
        showMoreButtons: Boolean,
        showPrevious: Boolean = true,
        showNext: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_large).apply {
            setTextViewText(R.id.widget_title, title)
            setTextViewText(R.id.widget_artist, artist)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val isCentered = prefs.getBoolean("centered_title", false)
                val gravity = if (isCentered) Gravity.CENTER else (Gravity.START or Gravity.CENTER_VERTICAL)
                setInt(R.id.widget_title, "setGravity", gravity)
                setInt(R.id.widget_artist, "setGravity", gravity)
            }

            setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
            )
            setContentDescription(
                R.id.widget_play_pause,
                context.getString(if (isPlaying) R.string.pause else R.string.play)
            )

            setOnClickPendingIntent(R.id.widget_card_root, openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, openAppPi)
            setOnClickPendingIntent(R.id.widget_play_pause, playPausePi)

            if (showMoreButtons && favoritePi != null && shufflePi != null) {
                setViewVisibility(R.id.widget_favorite, View.VISIBLE)
                setViewVisibility(R.id.widget_shuffle, View.VISIBLE)
                setImageViewResource(
                    R.id.widget_favorite,
                    if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
                )
                setInt(R.id.widget_favorite, "setImageAlpha", if (isFavorite) 255 else 180)
                setImageViewResource(
                    R.id.widget_shuffle,
                    R.drawable.ic_shuffle
                )
                setInt(R.id.widget_shuffle, "setImageAlpha", if (isShuffle) 255 else 90)
                setOnClickPendingIntent(R.id.widget_favorite, favoritePi)
                setOnClickPendingIntent(R.id.widget_shuffle, shufflePi)
            } else {
                setViewVisibility(R.id.widget_favorite, View.GONE)
                setViewVisibility(R.id.widget_shuffle, View.GONE)
            }

            if (showPrevious) {
                setViewVisibility(R.id.widget_previous, View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_previous, prevPi)
            } else {
                setViewVisibility(R.id.widget_previous, View.VISIBLE)
            }

            if (showNext) {
                setViewVisibility(R.id.widget_next, View.VISIBLE)
                setOnClickPendingIntent(R.id.widget_next, nextPi)
            } else {
                setViewVisibility(R.id.widget_next, View.GONE)
            }

            if (bitmap != null) {
                setImageViewBitmap(R.id.widget_cover, getRoundedBitmap(bitmap, 12.dpToPx(context).toFloat()))
            } else {
                setImageViewResource(R.id.widget_cover, R.drawable.ic_default_cover)
            }
        }
    }

    private fun generateCircularProgressBar(
        context: Context,
        size: Int,
        progress: Float,
        strokeWidth: Float
    ): Bitmap {
        val safeSize = size.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }

        val strokeHalf = strokeWidth / 2f
        val arcRect = RectF(strokeHalf, strokeHalf, safeSize - strokeHalf, safeSize - strokeHalf)

        paint.color = ContextCompat.getColor(context, R.color.widget_track)
        canvas.drawArc(arcRect, 0f, 360f, false, paint)

        if (progress > 0f) {
            paint.color = ContextCompat.getColor(context, R.color.widget_primary)
            canvas.drawArc(arcRect, -90f, 360f * progress.coerceIn(0f, 1f), false, paint)
        }

        return bitmap
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
