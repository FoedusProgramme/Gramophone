# reflection in ReflectionAudioEffect
-keep class org.nift4.audiofxfwd.OnParameterChangeListenerAdapter { *; }
-dontwarn android.media.audiofx.AudioEffect$OnParameterChangeListener

# reflection in AudioSystemHiddenApi
-keep class org.nift4.audiofxfwd.VolumeGroupCallbackAdapter { *; }
-dontwarn android.media.AudioManager$VolumeGroupCallback
-keep class org.nift4.audiosysfwd.AudioVolumeGroupCallbackAdapter { *; }
-dontwarn android.media.AudioSystem
-dontwarn android.media.INativeAudioVolumeGroupCallback
-dontwarn android.media.INativeAudioVolumeGroupCallback$Stub
-dontwarn android.media.audio.common.AudioVolumeGroupChangeEvent
