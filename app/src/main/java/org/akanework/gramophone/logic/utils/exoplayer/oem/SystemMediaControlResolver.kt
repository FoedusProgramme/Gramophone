package org.akanework.gramophone.logic.utils.exoplayer.oem

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.media.MediaRouter2
import android.os.Build
import androidx.annotation.RequiresApi
import org.akanework.gramophone.logic.utils.exoplayer.oem.MiPlayAudioSupport.supportMiPlay

class SystemMediaControlResolver(val context: Context) {
    @OptIn(UnstableMediaKitApi::class)
    fun intentSystemMediaDialog() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            manufacturer.contains("xiaomi") -> {
                if (supportMiPlay(context)){
                    val intent = Intent().apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        setClassName(
                            "miui.systemui.plugin",
                            "miui.systemui.miplay.MiPlayDetailActivity"
                        )
                    }
                    startIntent(intent)
                }
            }

            manufacturer.contains("samsung") -> {
                val intent = Intent().apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    setClassName(
                        "com.samsung.android.mdx.quickboard",
                        "com.samsung.android.mdx.quickboard.view.MediaActivity"
                    )
                }
                startIntent(intent)
            }

            else -> {
                if (Build.VERSION.SDK_INT >= 34) {
                    // zh: Android 14 及以上
                    startNativeMediaDialogForAndroid14(context)
                } else if (Build.VERSION.SDK_INT >= 31) {
                    // zh: Android 12 及以上
                    val intent = Intent().apply {
                        action = "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"
                        setPackage("com.android.systemui")
                        putExtra("package_name", context.packageName)
                    }
                    startNativeMediaDialog(intent)
                } else if (Build.VERSION.SDK_INT == 30) {
                    // Android 11
                    startNativeMediaDialogForAndroid11(context)
                } else {
                    val intent = Intent().apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        action = "com.android.settings.panel.action.MEDIA_OUTPUT"
                        putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                    }
                    startNativeMediaDialog(intent)
                }
            }
        }
    }

    private fun startNativeMediaDialog(intent: Intent): Boolean {
        val resolveInfoList: List<ResolveInfo> =
            context.packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo
            val applicationInfo: ApplicationInfo? = activityInfo?.applicationInfo
            if (applicationInfo != null && (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    private fun startNativeMediaDialogForAndroid11(context: Context): Boolean {
        val intent = Intent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            action = "com.android.settings.panel.action.MEDIA_OUTPUT"
            putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
        }
        val resolveInfoList: List<ResolveInfo> =
            context.packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo
            val applicationInfo: ApplicationInfo? = activityInfo?.applicationInfo
            if (applicationInfo != null && (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startNativeMediaDialogForAndroid14(context: Context): Boolean {
        val mediaRouter2 = MediaRouter2.getInstance(context)
        return mediaRouter2.showSystemOutputSwitcher()
    }

    private fun startIntent(intent: Intent): Boolean {
       return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}