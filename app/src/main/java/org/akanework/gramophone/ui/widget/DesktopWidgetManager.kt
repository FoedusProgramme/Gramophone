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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import org.akanework.gramophone.ui.CardWidgetProvider
import org.akanework.gramophone.ui.CircleWidgetProvider

object DesktopWidgetManager {

    private val providers: List<BaseWidgetProvider> = listOf(
        CardWidgetProvider(),
        CircleWidgetProvider()
    )

    /**
     * Deep entry point for service playback callbacks.
     * Persists the latest playback snapshot to disk and refreshes all active widget instances.
     */
    fun refreshFromPlayback(context: Context) {
        BaseWidgetProvider.savePlaybackSnapshot(context)
        updateAllWidgets(context)
    }

    fun updateAllWidgets(context: Context) {
        val awm = AppWidgetManager.getInstance(context) ?: return
        for (provider in providers) {
            val ids = awm.getAppWidgetIds(ComponentName(context, provider::class.java))
            if (ids.isNotEmpty()) {
                provider.onUpdate(context, awm, ids)
            }
        }
    }
}
