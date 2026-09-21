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
import androidx.media3.common.Player
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getBooleanStrict

enum class CardLayoutVariant(val minWidth: Float, val minHeight: Float) {
    LARGE_WIDE(220f, 180f),
    LARGE(180f, 180f),
    MEDIUM_WIDE(220f, 75f),
    MEDIUM(180f, 75f),
    CARD(180f, 50f)
}

enum class CircleLayoutVariant(val minWidth: Float, val minHeight: Float) {
    CIRCLE(110f, 110f),
    PILL(110f, 56f),
    PILL_SINGLE(56f, 56f)
}

object CardWidgetViewsBuilder {

    const val PREF_CENTERED_TITLE = "centered_title"

    fun selectCardVariant(widthDp: Int, heightDp: Int): CardLayoutVariant =
        CardLayoutVariant.entries
            .filter { widthDp >= it.minWidth && heightDp >= it.minHeight }
            .maxByOrNull { it.minWidth * it.minHeight }
            ?: CardLayoutVariant.CARD

    fun selectCircleVariant(widthDp: Int, heightDp: Int): CircleLayoutVariant =
        CircleLayoutVariant.entries
            .filter { widthDp >= it.minWidth && heightDp >= it.minHeight }
            .maxByOrNull { it.minWidth * it.minHeight }
            ?: CircleLayoutVariant.PILL_SINGLE

    fun getCardVariantViewsMap(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        colors: CardWidgetColors = CardWidgetColorResolver.resolve(context, state.artworkBitmap)
    ): Map<CardLayoutVariant, RemoteViews> {
        val card = buildCardViews(context, state, actions, colors, showPrevious = true, showNext = true)
        val medium = buildMediumViews(context, state, actions, colors, showMoreButtons = false, showFavorite = false)
        val mediumWide = buildMediumViews(context, state, actions, colors, showMoreButtons = true, showFavorite = true)
        val large = buildLargeViews(context, state, actions, colors, showMoreButtons = false, showFavorite = true)
        val largeWide = buildLargeViews(context, state, actions, colors, showMoreButtons = true, showFavorite = true)

        return mapOf(
            CardLayoutVariant.LARGE_WIDE to largeWide,
            CardLayoutVariant.LARGE to large,
            CardLayoutVariant.MEDIUM_WIDE to mediumWide,
            CardLayoutVariant.MEDIUM to medium,
            CardLayoutVariant.CARD to card
        )
    }

    fun getCircleVariantViewsMap(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        colors: CardWidgetColors = CardWidgetColorResolver.resolve(context, state.artworkBitmap)
    ): Map<CircleLayoutVariant, RemoteViews> {
        return mapOf(
            CircleLayoutVariant.CIRCLE to buildCircleViews(context, state, actions, colors),
            CircleLayoutVariant.PILL to buildPillViews(context, state, actions, colors, showCover = true),
            CircleLayoutVariant.PILL_SINGLE to buildPillViews(context, state, actions, colors, showCover = false)
        )
    }

    fun buildCardResponsiveRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
    ): RemoteViews {
        val colors = CardWidgetColorResolver.resolve(context, state.artworkBitmap)
        val variantMap = getCardVariantViewsMap(context, state, actions, colors)
        return buildResponsiveRemoteViews(
            appWidgetManager,
            appWidgetId,
            variantMap,
            sizeOf = { SizeF(it.minWidth, it.minHeight) },
            selector = ::selectCardVariant
        )
    }

    fun buildCircleResponsiveRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
    ): RemoteViews {
        val colors = CardWidgetColorResolver.resolve(context, state.artworkBitmap)
        val variantMap = getCircleVariantViewsMap(context, state, actions, colors)
        return buildResponsiveRemoteViews(
            appWidgetManager,
            appWidgetId,
            variantMap,
            sizeOf = { SizeF(it.minWidth, it.minHeight) },
            selector = ::selectCircleVariant
        )
    }

    private fun <V> buildResponsiveRemoteViews(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        variantMap: Map<V, RemoteViews>,
        sizeOf: (V) -> SizeF,
        selector: (minWidth: Int, minHeight: Int) -> V
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val viewMapping = variantMap.mapKeys { (variant, _) -> sizeOf(variant) }
            return RemoteViews(viewMapping)
        } else {
            val options = try {
                appWidgetManager.getAppWidgetOptions(appWidgetId)
            } catch (_: Exception) {
                null
            }
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            return variantMap.getValue(selector(minWidth, minHeight))
        }
    }

    fun buildPillViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        colors: CardWidgetColors = CardWidgetColorResolver.resolve(context, state.artworkBitmap),
        showCover: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_pill).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setColorStateList(
                    R.id.widget_card_bg,
                    "setBackgroundTintList",
                    android.content.res.ColorStateList.valueOf(colors.background)
                )
            }
            setInt(R.id.widget_play_pause_bg, "setColorFilter", colors.primary)
            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi, iconColor = colors.onPrimary)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)

            if (showCover) {
                setViewVisibility(R.id.widget_cover, View.VISIBLE)
                applyArtwork(this, state.artworkBitmap, R.id.widget_cover)
            } else {
                setViewVisibility(R.id.widget_cover, View.GONE)
            }
        }
    }

    fun buildCircleViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        colors: CardWidgetColors = CardWidgetColorResolver.resolve(context, state.artworkBitmap)
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_circle).apply {
            setInt(R.id.widget_play_pause_bg, "setColorFilter", colors.primary)
            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi, iconColor = colors.onPrimary)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)
            applyArtwork(this, state.artworkBitmap, R.id.widget_cover)
        }
    }

    fun buildCardViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        colors: CardWidgetColors = CardWidgetColorResolver.resolve(context, state.artworkBitmap),
        showPrevious: Boolean = true,
        showNext: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget).apply {
            setInt(R.id.widget_card_bg, "setColorFilter", colors.background)
            setTextColor(R.id.widget_title, colors.onSurface)
            setTextColor(R.id.widget_artist, colors.onSurfaceVariant)
            setTextViewText(R.id.widget_title, state.title)
            setTextViewText(R.id.widget_artist, state.artist)
            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi, iconColor = colors.primary)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)

            applyNavControls(this, showPrevious, actions.prevPi, showNext, actions.nextPi, onSurfaceColor = colors.onSurface)
            applyArtwork(this, state.artworkBitmap, R.id.widget_cover)
        }
    }

    fun buildMediumViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        colors: CardWidgetColors = CardWidgetColorResolver.resolve(context, state.artworkBitmap),
        showMoreButtons: Boolean,
        showFavorite: Boolean = showMoreButtons,
        showPrevious: Boolean = true,
        showNext: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_medium).apply {
            setInt(R.id.widget_card_bg, "setColorFilter", colors.background)
            setTextColor(R.id.widget_title, colors.onSurface)
            setTextColor(R.id.widget_artist, colors.onSurfaceVariant)
            setTextViewText(R.id.widget_title, state.title)
            setTextViewText(R.id.widget_artist, state.artist)
            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi, iconColor = colors.primary)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)

            applyFavoriteControl(this, context, showFavorite, state.isFavorite, actions.favoritePi, colors)
            applyRepeatAndShuffleControls(this, context, showMoreButtons, state.repeatMode, actions.repeatPi, state.isShuffle, actions.shufflePi, colors)
            applyNavControls(this, showPrevious, actions.prevPi, showNext, actions.nextPi, onSurfaceColor = colors.onSurface)
            applyArtwork(this, state.artworkBitmap, R.id.widget_cover)
        }
    }

    fun buildLargeViews(
        context: Context,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions,
        colors: CardWidgetColors = CardWidgetColorResolver.resolve(context, state.artworkBitmap),
        showMoreButtons: Boolean,
        showFavorite: Boolean = true,
        showPrevious: Boolean = true,
        showNext: Boolean = true
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.card_widget_large).apply {
            setInt(R.id.widget_card_bg, "setColorFilter", colors.background)
            setTextColor(R.id.widget_title, colors.onSurface)
            setTextColor(R.id.widget_artist, colors.onSurfaceVariant)
            setTextViewText(R.id.widget_title, state.title)
            setTextViewText(R.id.widget_artist, state.artist)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val isCentered = prefs.getBooleanStrict(PREF_CENTERED_TITLE, false)
            // TextView.setGravity(int) is an allowed @RemotableViewMethod in Android SDK (TextView.java:5962).
            // Together with the geometric spacer on the left, it centers both the container bounds and text glyphs.
            setViewVisibility(R.id.widget_title_start_spacer, if (isCentered) View.VISIBLE else View.GONE)
            val gravity = if (isCentered) Gravity.CENTER_HORIZONTAL else Gravity.START
            setInt(R.id.widget_title, "setGravity", gravity)
            setInt(R.id.widget_artist, "setGravity", gravity)

            applyPlayPauseControl(this, context, state.isPlaying, actions.playPausePi, iconColor = colors.primary)
            setOnClickPendingIntent(R.id.widget_card_root, actions.openAppPi)
            setOnClickPendingIntent(R.id.widget_cover, actions.openAppPi)

            applyFavoriteControl(this, context, showFavorite, state.isFavorite, actions.favoritePi, colors)
            applyRepeatAndShuffleControls(this, context, showMoreButtons, state.repeatMode, actions.repeatPi, state.isShuffle, actions.shufflePi, colors)
            applyNavControls(this, showPrevious, actions.prevPi, showNext, actions.nextPi, onSurfaceColor = colors.onSurface)
            applyArtwork(this, state.artworkBitmap, R.id.widget_cover)
        }
    }

    private fun applyPlayPauseControl(
        views: RemoteViews,
        context: Context,
        isPlaying: Boolean,
        playPausePi: PendingIntent,
        iconColor: Int? = null
    ) {
        views.setImageViewResource(
            R.id.widget_play_pause,
            if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
        )
        if (iconColor != null) {
            views.setInt(R.id.widget_play_pause, "setColorFilter", iconColor)
        }
        views.setContentDescription(
            R.id.widget_play_pause,
            context.getString(if (isPlaying) R.string.pause else R.string.play)
        )
        views.setOnClickPendingIntent(R.id.widget_play_pause, playPausePi)
    }

    private fun applyFavoriteControl(
        views: RemoteViews,
        context: Context,
        showFavorite: Boolean,
        isFavorite: Boolean,
        favoritePi: PendingIntent,
        colors: CardWidgetColors
    ) {
        if (showFavorite) {
            views.setViewVisibility(R.id.widget_favorite, View.VISIBLE)
            views.setImageViewResource(
                R.id.widget_favorite,
                if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
            )
            views.setInt(R.id.widget_favorite, "setImageAlpha", 255)
            val color = if (isFavorite) colors.primary else colors.onSurface
            views.setInt(R.id.widget_favorite, "setColorFilter", color)
            views.setContentDescription(
                R.id.widget_favorite,
                context.getString(if (isFavorite) R.string.unfavorite else R.string.playlist_favourite)
            )
            views.setOnClickPendingIntent(R.id.widget_favorite, favoritePi)
        } else {
            views.setViewVisibility(R.id.widget_favorite, View.GONE)
        }
    }

    private fun applyRepeatAndShuffleControls(
        views: RemoteViews,
        context: Context,
        showMore: Boolean,
        repeatMode: Int,
        repeatPi: PendingIntent?,
        isShuffle: Boolean,
        shufflePi: PendingIntent?,
        colors: CardWidgetColors
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
            val repeatColor = if (isRepeatActive) colors.primary else colors.onSurface
            views.setInt(R.id.widget_repeat, "setColorFilter", repeatColor)
            views.setContentDescription(R.id.widget_repeat, context.getString(R.string.repeat_mode))
            views.setOnClickPendingIntent(R.id.widget_repeat, repeatPi)

            views.setImageViewResource(R.id.widget_shuffle, R.drawable.ic_shuffle)
            views.setInt(R.id.widget_shuffle, "setImageAlpha", if (isShuffle) 255 else 100)
            val shuffleColor = if (isShuffle) colors.primary else colors.onSurface
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
        viewId: Int
    ) {
        if (bitmap != null) {
            views.setImageViewBitmap(viewId, bitmap)
        } else {
            views.setImageViewResource(viewId, R.drawable.ic_default_cover)
        }
    }

    private fun applyNavControls(
        views: RemoteViews,
        showPrevious: Boolean,
        prevPi: PendingIntent,
        showNext: Boolean,
        nextPi: PendingIntent,
        onSurfaceColor: Int? = null
    ) {
        if (showPrevious) {
            views.setViewVisibility(R.id.widget_previous, View.VISIBLE)
            if (onSurfaceColor != null) {
                views.setInt(R.id.widget_previous, "setColorFilter", onSurfaceColor)
            }
            views.setOnClickPendingIntent(R.id.widget_previous, prevPi)
        } else {
            views.setViewVisibility(R.id.widget_previous, View.GONE)
        }

        if (showNext) {
            views.setViewVisibility(R.id.widget_next, View.VISIBLE)
            if (onSurfaceColor != null) {
                views.setInt(R.id.widget_next, "setColorFilter", onSurfaceColor)
            }
            views.setOnClickPendingIntent(R.id.widget_next, nextPi)
        } else {
            views.setViewVisibility(R.id.widget_next, View.GONE)
        }
    }
}
