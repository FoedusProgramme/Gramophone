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
