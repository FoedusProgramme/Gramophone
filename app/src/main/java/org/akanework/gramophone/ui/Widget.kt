/*
 *     Copyright (C) 2024 nift4
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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService

private inline val service
    get() = GramophonePlaybackService.instanceForWidgetAndLyricsOnly

class LyricWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val seekPi = PendingIntent.getBroadcast(
            context,
            GramophonePlaybackService.PENDING_INTENT_WIDGET_ID,
            Intent(context.packageName + ".SEEK_TO").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        for (appWidgetId in appWidgetIds) {
            val views =
                RemoteViews(context.packageName, R.layout.lyric_widget).apply {
                    setPendingIntentTemplate(R.id.list_view, seekPi)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val factory = LyricRemoteViewsFactory(context, appWidgetId)
                        setRemoteAdapter(
                            R.id.list_view,
                            RemoteViews.RemoteCollectionItems.Builder()
                                .setViewTypeCount(factory.viewTypeCount)
                                .setHasStableIds(factory.hasStableIds())
                                .apply {
                                    for (i in 0..<factory.count) {
                                        addItem(
                                            factory.getItemId(i),
                                            factory.getViewAt(i)!!
                                        )
                                    }
                                }
                                .build())
                    } else {
                        @Suppress("Deprecation")
                        setRemoteAdapter(
                            R.id.list_view,
                            Intent(context, LyricWidgetService::class.java).apply {
                                this.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                // Intents are compared using filterEquals() which ignores extras, so encode extras
                                // in data to enforce comparison noticing the difference between different Intents.
                                this.data = toUri(Intent.URI_INTENT_SCHEME).toUri()
                            }
                        )
                    }
                    setEmptyView(R.id.list_view, R.id.empty_view)
                    val li = service?.getCurrentLyricIndex(true)
                    if (li != null)
                        setScrollPosition(R.id.list_view, li)
                }
            // setting null first fixes outdated data related bugs but causes flicker. hence we
            // sparingly update the entire adapter.
            if (Build.VERSION.SDK_INT < 36)
                appWidgetManager.updateAppWidget(appWidgetId, null)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    companion object {
        fun update(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            if (awm != null)
                LyricWidgetProvider().onUpdate(context, awm, awm.appWidgetIds(context))
        }

        fun adapterUpdate(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                update(context)
            } else {
                CoroutineScope(Dispatchers.Default).launch {
                    val awm = AppWidgetManager.getInstance(context)
                    if (awm != null) {
                        for (appWidgetId in awm.appWidgetIds(context)) {
                            @Suppress("deprecation")
                            awm.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list_view)
                        }
                    }
                }
            }
        }

        fun hasWidget(context: Context): Boolean {
            val awm = AppWidgetManager.getInstance(context)
            return awm?.getAppWidgetIds(
                ComponentName(
                    context,
                    LyricWidgetProvider::class.java
                )
            )?.isNotEmpty() ?: false
        }

        private fun AppWidgetManager.appWidgetIds(context: Context) =
            getAppWidgetIds(ComponentName(context, LyricWidgetProvider::class.java))
    }
}

class LyricWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        if (!intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
            throw IllegalStateException("where is EXTRA_APPWIDGET_ID?")
        /*val size = if (intent.hasExtra("width") || intent.hasExtra("height")) {
            if (!intent.hasExtra("width"))
                throw IllegalStateException("where is width? we have height")
            if (!intent.hasExtra("height"))
                throw IllegalStateException("where is height? we have width")
            SizeF(intent.getFloatExtra("width", -1f), intent.getFloatExtra("height", -1f))
        } else null*/
        return LyricRemoteViewsFactory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        )
    }
}

private class LyricRemoteViewsFactory(private val context: Context, private val appWidgetId: Int) :
    RemoteViewsService.RemoteViewsFactory {
    override fun onCreate() {
        // do nothing
    }

    override fun onDataSetChanged() {
        Handler(Looper.getMainLooper()).postDelayed({
            val li = service?.getCurrentLyricIndex(true)
            val awm = AppWidgetManager.getInstance(context)
            if (li != null) {
                awm.partiallyUpdateAppWidget(
                    appWidgetId,
                    RemoteViews(context.packageName, R.layout.lyric_widget).apply {
                        setScrollPosition(R.id.list_view, li)
                    }
                )
            }
        }, 100)
    }

    override fun onDestroy() {
        // do nothing
    }

    override fun getCount(): Int {
        return service?.lyrics?.unsyncedText?.size ?: 0
    }

    private val themeContext = ContextThemeWrapper(context, R.style.Theme_Gramophone)
    private val span =
        ForegroundColorSpan(ContextCompat.getColor(themeContext, R.color.sl_lyric_active))

    override fun getViewAt(position: Int): RemoteViews? {
        val cPos = runBlocking {
            withContext(Dispatchers.Main.immediate) {
                service?.endedWorkaroundPlayer?.contentPosition
            }
        }
        val item = service?.syncedLyrics?.text?.getOrNull(position)
        val itemUnsynced =
            if (item == null) service?.lyrics?.unsyncedText?.getOrNull(position) else null
        if (item == null && itemUnsynced == null) return null
        val isTranslation = item?.isTranslated == true
        val isBackground = (item?.speaker ?: itemUnsynced?.second)?.isBackground == true
        val isVoice2 = (item?.speaker ?: itemUnsynced?.second)?.isVoice2 == true
        val isGroup = (item?.speaker ?: itemUnsynced?.second)?.isGroup == true
        val startTs = item?.start?.toLong() ?: -1L
        val endTs = item?.end?.toLong() ?: Long.MAX_VALUE
        val isActive = startTs == -1L || cPos != null && cPos >= startTs && cPos <= endTs
        return RemoteViews(
            context.packageName, when {
                isGroup && isTranslation && isBackground -> R.layout.lyric_widget_text_center_tlbg
                isGroup && isTranslation -> R.layout.lyric_widget_text_center_tl
                isGroup && isBackground -> R.layout.lyric_widget_text_center_bg
                isGroup -> R.layout.lyric_widget_text_center
                isVoice2 && isTranslation && isBackground -> R.layout.lyric_widget_text_right_tlbg
                isVoice2 && isTranslation -> R.layout.lyric_widget_text_right_tl
                isVoice2 && isBackground -> R.layout.lyric_widget_text_right_bg
                isVoice2 -> R.layout.lyric_widget_text_right
                isTranslation && isBackground -> R.layout.lyric_widget_text_left_tlbg
                isTranslation -> R.layout.lyric_widget_text_left_tl
                isBackground -> R.layout.lyric_widget_text_left_bg
                else -> R.layout.lyric_widget_text_left
            }
        ).apply {
            val sb = SpannableString(item?.text ?: itemUnsynced!!.first)
            if (isActive) {
                val hlChar = if (item?.words != null) item.words.findLast {
                    it.timeRange.first <=
                            cPos!!.toULong()
                }?.charRange?.last?.plus(1) ?: 0 else sb.length
                if (hlChar > 0)
                    sb.setSpan(span, 0, hlChar, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            }
            setTextViewText(R.id.lyric_widget_item, sb)
            if (startTs >= 0L && item?.isClickable != false)
                setOnClickFillInIntent(R.id.lyric_widget_item, Intent().apply {
                    putExtras(Bundle().apply {
                        putLong("seekTo", startTs)
                    })
                })
        }
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 12
    }

    override fun getItemId(position: Int): Long {
        val item = service?.syncedLyrics?.text?.getOrNull(position)
        val itemUnsynced =
            if (item == null) service?.lyrics?.unsyncedText?.getOrNull(position) else null
        if (item == null && itemUnsynced == null) return 0L
        return ((item?.text ?: itemUnsynced!!.first).hashCode()).toLong()
            .shl(32) or
                (item?.start?.hashCode() ?: -1L).toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }
}
