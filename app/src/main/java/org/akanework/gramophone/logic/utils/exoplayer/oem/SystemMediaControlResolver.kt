package org.akanework.gramophone.logic.utils.exoplayer.oem

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.media.MediaRouter2
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.exoplayer.oem.MiPlayAudioSupport.supportMiPlay

object SystemMediaControlResolver {
    fun intentSystemMediaDialog(context: Context) {
//        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            supportMiPlay(context) -> {
                val intent = Intent().apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    setClassName(
                        "miui.systemui.plugin",
                        "miui.systemui.miplay.MiPlayDetailActivity"
                    )
                }
                if (!startIntent(intent,context = context,)) {
                    startSystemMediaControl(context = context)
                }
            }
            (getOneUIVersionReadable() != null) -> {
                val intent = Intent().apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    setClassName(
                        "com.samsung.android.mdx.quickboard",
                        "com.samsung.android.mdx.quickboard.view.MediaActivity"
                    )
                }
                if (!startIntent(intent,context = context)) {
                    startSystemMediaControl(context = context,)
                }
            }

            else -> {
                startSystemMediaControl(context = context,)
            }
        }
    }

    private fun startSystemMediaControl(context: Context){
        if (Build.VERSION.SDK_INT >= 34) {
            // zh: Android 14 及以上
            // en:Android 14 and above
            val tag = startNativeMediaDialogForAndroid14(context)
            if (!tag) {
                Toast.makeText(context, R.string.media_control_text_error, Toast.LENGTH_SHORT).show()
            }
        } else if (Build.VERSION.SDK_INT >= 31) {
            // zh: Android 12 及以上
            // en: Android 14 and above
            val intent = Intent().apply {
                action = "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"
                setPackage("com.android.systemui")
                putExtra("package_name", context.packageName)
            }
            val tag =  startNativeMediaDialog(context = context,intent)
            if (!tag) {
                Toast.makeText(context, R.string.media_control_text_error, Toast.LENGTH_SHORT).show()
            }
        } else{
            // zh: Android 11 及以下
            // en: Android 11 and below
            val tag = startNativeMediaDialogForAndroid11(context)
            if (!tag) {
                Toast.makeText(context, R.string.media_control_text_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startNativeMediaDialog(context: Context,intent: Intent): Boolean {
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

    private fun startIntent(intent: Intent,context: Context): Boolean {
       return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * zh: 获取 One UI 版本字符串（如 6.0.0），非三星或无此属性则返回 null
     * en: Get One UI version string (e.g. 6.0.0), return null if not Samsung or no such property
     */
    @SuppressLint("PrivateApi")
    private fun getOneUIVersionReadable(): String? {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            val value = (get.invoke(null, "ro.build.version.oneui") as String).trim()
            if (value.isEmpty()) return null
            val code = value.toIntOrNull() ?: return null
            val major = code / 10000
            val minor = (code / 100) % 100
            val patch = code % 100
            "$major.$minor.$patch"
        } catch (e: Exception) {
            null
        }
    }

    fun isMediaOutputPanelSupported(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 34 -> {
                // Android 14+ is support
                true
            }
            Build.VERSION.SDK_INT >= 31 -> {
                // Android 12~13
                val intent = Intent().apply {
                    action = "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"
                    setPackage("com.android.systemui")
                    putExtra("package_name", context.packageName)
                }
                isSystemIntentAvailable(context, intent)
            }
            Build.VERSION.SDK_INT == 30 -> {
                // Android 11
                val intent = Intent().apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    action = "com.android.settings.panel.action.MEDIA_OUTPUT"
                    putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                }
                isSystemIntentAvailable(context, intent)
            }
            else -> {
                // Android 10 and below
                val intent = Intent().apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    action = "com.android.settings.panel.action.MEDIA_OUTPUT"
                    putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                }
                isSystemIntentAvailable(context, intent)
            }
        }
    }

    private fun isSystemIntentAvailable(context: Context, intent: Intent): Boolean {
        val resolveInfoList = context.packageManager.queryIntentActivities(intent, 0)
        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo
            val applicationInfo: ApplicationInfo? = activityInfo?.applicationInfo
            if (applicationInfo != null && (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                return true
            }
        }
        return false
    }
}