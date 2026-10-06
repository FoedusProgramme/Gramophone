# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
# -renamesourcefileattribute SourceFile

# enabling obfuscation would break some self-reflection in the app
-dontobfuscate

# strip Kotlin null checks inserted by the compiler to reduce method count and overhead
-processkotlinnullchecks remove

# reflection by lyric getter xposed
-keep class androidx.media3.common.util.Util {
    public static void setForegroundServiceNotification(...);
}

# JNI
-keep class org.nift4.gramophone.hificore.NativeTrack {
    onAudioDeviceUpdate(...);
    onUnderrun(...);
    onMarker(...);
    onNewPos(...);
    onStreamEnd(...);
    onNewIAudioTrack(...);
    onNewTimestamp(...);
    onLoopEnd(...);
    onBufferEnd(...);
    onMoreData(...);
    onCanWriteMoreData(...);
}

# Koin looks definitions up by class. -dontobfuscate does not stop R8 from merging classes (a
# release build once merged UacManager into an androidx class), and two merged types bound in
# Koin would share one key, so one definition would silently replace the other. A keep rule
# without allowoptimization pins the class against merging; allowshrinking still drops an unused
# one. Keep this in sync with the definitions in org.akanework.gramophone.di.
-keep,allowshrinking class org.akanework.gramophone.logic.ApplicationScope
-keep,allowshrinking class org.akanework.gramophone.logic.AppUiEvents
-keep,allowshrinking class org.akanework.gramophone.logic.library.LibraryReadiness
-keep,allowshrinking class org.akanework.gramophone.logic.library.LibraryRefresher
-keep,allowshrinking class org.akanework.gramophone.logic.library.LibraryWriteRepository
-keep,allowshrinking class org.akanework.gramophone.logic.library.MediaConsentRequester
-keep,allowshrinking class org.akanework.gramophone.logic.settings.LibraryFilterSettings
-keep,allowshrinking class org.akanework.gramophone.ui.MediaControllerViewModel
-keep,allowshrinking class org.akanework.gramophone.ui.intent.DefaultPlayIntentExecutor
-keep,allowshrinking class org.akanework.gramophone.ui.intent.PlayIntentExecutor
-keep,allowshrinking class org.akanework.gramophone.ui.intent.PlayIntentViewModel
-keep,allowshrinking class org.akanework.gramophone.ui.nav.NavViewModel
-keep,allowshrinking class org.akanework.gramophone.ui.state.HomeViewModel
-keep,allowshrinking class org.nift4.gramophone.hificore.UacManager
-keep,allowshrinking class uk.akane.libphonograph.reader.FlowReader
