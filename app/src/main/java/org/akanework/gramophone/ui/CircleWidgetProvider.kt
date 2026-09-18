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
import android.widget.RemoteViews
import org.akanework.gramophone.ui.widget.BaseWidgetProvider
import org.akanework.gramophone.ui.widget.CardWidgetActions
import org.akanework.gramophone.ui.widget.CardWidgetPlaybackState
import org.akanework.gramophone.ui.widget.CardWidgetViewsBuilder

class CircleWidgetProvider : BaseWidgetProvider() {

    override val isCircleFamily: Boolean = true

    override fun buildViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        state: CardWidgetPlaybackState,
        actions: CardWidgetActions
    ): RemoteViews {
        return CardWidgetViewsBuilder.buildCircleResponsiveRemoteViews(
            context, appWidgetManager, appWidgetId, state, actions
        )
    }
}
