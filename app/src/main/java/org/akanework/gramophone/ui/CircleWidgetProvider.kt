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
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import org.akanework.gramophone.ui.widget.CardWidgetViewsBuilder

class CircleWidgetProvider : AppWidgetProvider() {

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
        val state = CardWidgetProvider.buildCurrentPlaybackState(context)
        val actions = CardWidgetProvider.buildWidgetActions(context, state)

        for (appWidgetId in appWidgetIds) {
            val views = CardWidgetViewsBuilder.buildCircleResponsiveRemoteViews(
                context, appWidgetManager, appWidgetId, state, actions
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)

            if (state.artworkUri != null && (state.artworkUri != CardWidgetProvider.cachedArtworkUri || CardWidgetProvider.cachedArtworkBitmap == null)) {
                CardWidgetProvider.loadArtworkAndRefresh(
                    context, appWidgetManager, appWidgetId, state, actions, isCircle = true
                )
            }
        }
    }

    companion object {
        fun hasWidget(context: Context): Boolean {
            val awm = AppWidgetManager.getInstance(context) ?: return false
            return awm.getAppWidgetIds(ComponentName(context, CircleWidgetProvider::class.java)).isNotEmpty()
        }

        fun update(context: Context) {
            val awm = AppWidgetManager.getInstance(context) ?: return
            val ids = awm.getAppWidgetIds(ComponentName(context, CircleWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                CircleWidgetProvider().onUpdate(context, awm, ids)
            }
        }
    }
}
