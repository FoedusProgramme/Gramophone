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
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx

object CardWidgetViewsBuilder {

    fun buildCardResponsiveRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
    ): RemoteViews {
        val card = buildCardViews(context, state, actions, showPrevious = true, showNext = true)
        val medium = buildMediumViews(context, state, actions, showMoreButtons = false)
        val mediumWide = buildMediumViews(context, state, actions, showMoreButtons = true)
        val large = buildLargeViews(context, state, actions, showMoreButtons = false)
        val largeWide = buildLargeViews(context, state, actions, showMoreButtons = true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val viewMapping = mapOf(
                SizeF(180f, 60f) to card,
                SizeF(180f, 80f) to medium,
                SizeF(260f, 80f) to mediumWide,
                SizeF(180f, 160f) to large,
                SizeF(260f, 160f) to largeWide
            )
            return RemoteViews(viewMapping)
        } else {
            return selectPreSCardLayout(appWidgetManager, appWidgetId, card, medium, mediumWide, large, largeWide)
        }
    }

    fun buildCircleResponsiveRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
    ): RemoteViews {
        val pillSingle = buildPillViews(context, state, actions, showCover = false)
        val pill = buildPillViews(context, state, actions, showCover = true)
        val circle = buildCircleViews(context, state, actions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val viewMapping = mapOf(
                SizeF(40f, 40f) to pillSingle,
                SizeF(100f, 40f) to pill,
                SizeF(80f, 80f) to circle
            )
            return RemoteViews(viewMapping)
        } else {
            return selectPreSCircleLayout(appWidgetManager, appWidgetId, pillSingle, pill, circle)
        }
    }

    fun buildResponsiveRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
    ): RemoteViews = buildCardResponsiveRemoteViews(context, appWidgetManager, appWidgetId, state, actions)

    private fun selectPreSCardLayout(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        card: RemoteViews,
        medium: RemoteViews,
        mediumWide: RemoteViews,
        large: RemoteViews,
        largeWide: RemoteViews
    ): RemoteViews {
        val options = try {
            appWidgetManager.getAppWidgetOptions(appWidgetId)
        } catch (_: Exception) {
            null
        }
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0

        return when {
            minHeight < 75 -> card
            minHeight in 75..165 -> if (minWidth >= 260) mediumWide else medium
            minHeight >= 165 -> if (minWidth >= 260) largeWide else large
            else -> card
        }
    }

    private fun selectPreSCircleLayout(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        pillSingle: RemoteViews,
        pill: RemoteViews,
        circle: RemoteViews
    ): RemoteViews {
        val options = try {
            appWidgetManager.getAppWidgetOptions(appWidgetId)
        } catch (_: Exception) {
            null
        }
        val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0

        return when {
            minHeight < 75 && minWidth in 1..99 -> pillSingle
            minHeight < 75 -> pill
            else -> circle
        }
    }

    fun buildPillViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        showCover: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_pill).apply {
            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)

            if (showCover) {
                setViewVisibility(R.id.widget_cover, View.VISIBLE)
                applyArtwork(this, state.artworkBitmap, R.id.widget_cover, isCircular = true)
            } else {
                setViewVisibility(R.id.widget_cover, View.GONE)
            }
        }
    }

    fun buildCircleViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_circle).apply {
            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)
            applyArtwork(this, state.artworkBitmap, R.id.widget_cover, isCircular = true)
        }
    }

    fun buildCardViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        showPrevious: Boolean = true,
        showNext: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget).apply {
            setTextViewText(R.id.widget_title, state.title)
            setTextViewText(R.id.widget_artist, state.artist)
            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)

            applyFavoriteControl(this, context, state.isFavorite, actions.favoritePi)
            applyNavControls(this, showPrevious, actions.prevPi, showNext, actions.nextPi)
            applyArtwork(this, state.artworkBitmap, R.id.widget_cover, cornerRadiusPx = 12.dpToPx(context).toFloat())
        }
    }

    fun buildMediumViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        showMoreButtons: Boolean,
        showPrevious: Boolean = true,
        showNext: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_medium).apply {
            setTextViewText(R.id.widget_title, state.title)
            setTextViewText(R.id.widget_artist, state.artist)
            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)

            applyFavoriteControl(this, context, state.isFavorite, actions.favoritePi)
            applyRepeatAndShuffleControls(this, context, showMoreButtons, state.repeatMode, actions.repeatPi, state.isShuffle, actions.shufflePi)
            applyNavControls(this, showPrevious, actions.prevPi, showNext, actions.nextPi)
            applyArtwork(this, state.artworkBitmap, R.id.widget_cover, cornerRadiusPx = 12.dpToPx(context).toFloat())
        }
    }

    fun buildLargeViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        showMoreButtons: Boolean,
        showPrevious: Boolean = true,
        showNext: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_large).apply {
            setTextViewText(R.id.widget_title, state.title)
            setTextViewText(R.id.widget_artist, state.artist)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val isCentered = prefs.getBoolean("centered_title", false)
                val gravity = if (isCentered) Gravity.CENTER else (Gravity.START or Gravity.CENTER_VERTICAL)
                setInt(R.id.widget_title, "setGravity", gravity)
                setInt(R.id.widget_artist, "setGravity", gravity)
            }

            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)

            applyFavoriteControl(this, context, state.isFavorite, actions.favoritePi)
            applyRepeatAndShuffleControls(this, context, showMoreButtons, state.repeatMode, actions.repeatPi, state.isShuffle, actions.shufflePi)
            applyNavControls(this, showPrevious, actions.prevPi, showNext, actions.nextPi)
            applyArtwork(this, state.artworkBitmap, R.id.widget_cover, cornerRadiusPx = 12.dpToPx(context).toFloat())
        }
    }

    private fun applyPlayPauseControl(
        views: RemoteViews,
        context: Context,
        isPlaying: Boolean,
        playPausePi: PendingIntent
    ) {
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
        )
        views.setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (isPlaying) R.string.pause else R.string.play)
        )
        views.setOnClickPendingIntent(R.id.widget_play_pause, playPausePi)
    }

    private fun applyFavoriteControl(
        views: RemoteViews,
        context: Context,
        isFavorite: Boolean,
        favoritePi: PendingIntent
    ) {
        views.setImageViewResource(
            R.id.widget_favorite,
            if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
        )
        views.setInt(R.id.widget_favorite, "setImageAlpha", 255)
        val color = ContextCompat.getColor(
            context,
            if (isFavorite) R.color.widget_primary else R.color.widget_on_surface
        )
        views.setInt(R.id.widget_favorite, "setColorFilter", color)
        views.setContentDescription(
            R.id.widget_favorite,
            context.getString(if (isFavorite) R.string.unfavorite else R.string.playlist_favourite)
        )
        views.setOnClickPendingIntent(R.id.widget_favorite, favoritePi)
    }

    private fun applyRepeatAndShuffleControls(
        views: RemoteViews,
        context: Context,
        showMore: Boolean,
        repeatMode: Int,
        repeatPi: PendingIntent?,
        isShuffle: Boolean,
        shufflePi: PendingIntent?
    ) {
        if (showMore && repeatPi != null && shufflePi != null) {
            views.setViewVisibility(R.id.widget_repeat, View.VISIBLE)
            views.setViewVisibility(R.id.widget_shuffle, View.VISIBLE)

            val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) {
                R.drawable.ic_repeat_one
            } else {
                R.drawable.ic_repeat
            }
            views.setImageViewResource(R.id.widget_repeat, repeatIcon)
            val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
            views.setInt(R.id.widget_repeat, "setImageAlpha", if (isRepeatActive) 255 else 100)
            val repeatColor = ContextCompat.getColor(
                context,
                if (isRepeatActive) R.color.widget_primary else R.color.widget_on_surface
            )
            views.setInt(R.id.widget_repeat, "setColorFilter", repeatColor)
            views.setContentDescription(R.id.widget_repeat, context.getString(R.string.repeat_mode))
            views.setOnClickPendingIntent(R.id.widget_repeat, repeatPi)

            views.setImageViewResource(R.id.widget_shuffle, R.drawable.ic_shuffle)
            views.setInt(R.id.widget_shuffle, "setImageAlpha", if (isShuffle) 255 else 100)
            val shuffleColor = ContextCompat.getColor(
                context,
                if (isShuffle) R.color.widget_primary else R.color.widget_on_surface
            )
            views.setInt(R.id.widget_shuffle, "setColorFilter", shuffleColor)
            views.setContentDescription(R.id.widget_shuffle, context.getString(R.string.shuffle))
            views.setOnClickPendingIntent(R.id.widget_shuffle, shufflePi)
        } else {
            views.setViewVisibility(R.id.widget_repeat, View.GONE)
            views.setViewVisibility(R.id.widget_shuffle, View.GONE)
        }
    }

    private fun applyArtwork(
        views: RemoteViews,
        bitmap: Bitmap?,
        viewId: Int,
        cornerRadiusPx: Float = 0f,
        isCircular: Boolean = false
    ) {
        if (bitmap != null) {
            val processed = if (isCircular) {
                CardWidgetBitmapUtils.getCircularBitmap(bitmap)
            } else {
                CardWidgetBitmapUtils.getRoundedBitmap(bitmap, cornerRadiusPx)
            }
            views.setImageViewBitmap(viewId, processed)
        } else {
            views.setImageViewResource(viewId, R.drawable.ic_default_cover)
        }
    }

    private fun applyNavControls(
        views: RemoteViews,
        showPrevious: Boolean,
        prevPi: PendingIntent,
        showNext: Boolean,
        nextPi: PendingIntent
    ) {
        if (showPrevious) {
            views.setViewVisibility(R.id.widget_previous, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_previous, prevPi)
        } else {
            views.setViewVisibility(R.id.widget_previous, View.GONE)
        }

        if (showNext) {
            views.setViewVisibility(R.id.widget_next, View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_next, nextPi)
        } else {
            views.setViewVisibility(R.id.widget_next, View.GONE)
        }
    }
}
