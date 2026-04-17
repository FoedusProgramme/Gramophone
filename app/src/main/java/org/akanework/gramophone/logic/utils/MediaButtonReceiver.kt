package org.akanework.gramophone.logic.utils

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.Log
import androidx.media3.session.MediaButtonReceiver
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.NOTIFY_CHANNEL_ID
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.NOTIFY_ID
import org.akanework.gramophone.logic.GramophonePlaybackService.Companion.PENDING_INTENT_NOTIFY_ID
import org.akanework.gramophone.logic.hasNotificationPermission
import org.akanework.gramophone.logic.mayThrowForegroundServiceStartNotAllowed
import org.akanework.gramophone.logic.mayThrowForegroundServiceStartNotAllowedMiui
import org.akanework.gramophone.logic.supportsNotificationPermission
import org.akanework.gramophone.ui.MainActivity

class MediaButtonReceiver : MediaButtonReceiver() {

    companion object {
        private const val TAG = "MediaButtonReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "+onReceive(): $intent")
        super.onReceive(context, intent)
        Log.i(TAG, "-onReceive(): $intent")
    }

    override fun shouldStartForegroundService(context: Context, intent: Intent): Boolean {
        val prefs = context.getSharedPreferences("LastPlayedManager", 0)
        val ret = !prefs.getString("last_played_grp", null).isNullOrEmpty()
        Log.i(TAG, "shouldStartForegroundService()=$ret: $intent")
        return ret
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onForegroundServiceStartNotAllowedException(
        context: Context,
        intent: Intent,
        e: ForegroundServiceStartNotAllowedException
    ) {
        Log.w(TAG, "Failed to resume playback :/", e)
        if (mayThrowForegroundServiceStartNotAllowed()
            || mayThrowForegroundServiceStartNotAllowedMiui()
        ) {
            if (supportsNotificationPermission() && !context.hasNotificationPermission()) {
                Log.e(
                    TAG, Log.getThrowableString(
                        IllegalStateException(
                            "onForegroundServiceStartNotAllowedException shouldn't be called on T+"
                        )
                    )!!
                )
                return
            }
            val nm = NotificationManagerCompat.from(context)
            @SuppressLint("MissingPermission") // false positive
            nm.notify(NOTIFY_ID, NotificationCompat.Builder(context, NOTIFY_CHANNEL_ID).apply {
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setAutoCancel(true)
                setCategory(NotificationCompat.CATEGORY_ERROR)
                setSmallIcon(R.drawable.ic_error)
                setContentTitle(context.getString(R.string.fgs_failed_title))
                setContentText(context.getString(R.string.fgs_failed_text))
                setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        PENDING_INTENT_NOTIFY_ID,
                        Intent(context, MainActivity::class.java)
                            .putExtra(MainActivity.PLAYBACK_AUTO_START_FOR_FGS, true),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                setVibrate(longArrayOf(0L, 200L))
                setLights(0, 0, 0)
                setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                setSound(null)
            }.build())
        } else {
            Handler(Looper.getMainLooper()).post {
                throw IllegalStateException("onForegroundServiceStartNotAllowedException shouldn't be called on T+")
            }
        }
    }
}