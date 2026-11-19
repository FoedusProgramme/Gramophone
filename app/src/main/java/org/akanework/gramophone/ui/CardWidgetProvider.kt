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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.Player
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.ui.widget.BaseWidgetProvider
import org.akanework.gramophone.ui.widget.CardWidgetActions
import org.akanework.gramophone.ui.widget.CardWidgetPlaybackState
import org.akanework.gramophone.ui.widget.CardWidgetStore
import org.akanework.gramophone.ui.widget.CardWidgetViewsBuilder
import org.akanework.gramophone.ui.widget.DesktopWidgetManager

class CardWidgetProvider : BaseWidgetProvider() {

    override fun buildViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
    ): RemoteViews {
        return CardWidgetViewsBuilder.buildCardResponsiveRemoteViews(
            context, appWidgetManager, appWidgetId, state, actions
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        handleWidgetAction(context, action)
    }

    private fun handleWidgetAction(context: Context, action: String) {
        val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
        val player = service?.endedWorkaroundPlayer
        if (service != null && player != null) {
            when (action) {
                ACTION_REPEAT -> {
                    player.repeatMode = nextRepeatMode(player.repeatMode)
                }
                ACTION_SHUFFLE -> {
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                }
                ACTION_FAVORITE -> {
                    service.toggleCurrentItemFavorite()
                }
            }
        }
    }

    companion object {

        fun nextRepeatMode(currentMode: Int): Int = when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
    }
}

