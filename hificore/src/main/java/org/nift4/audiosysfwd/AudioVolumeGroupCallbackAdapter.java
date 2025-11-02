package org.nift4.audiosysfwd;

import android.media.AudioSystem;
import android.media.INativeAudioVolumeGroupCallback;
import android.media.audio.common.AudioVolumeGroupChangeEvent;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Objects;

/* package */ class AudioVolumeGroupCallbackAdapter extends INativeAudioVolumeGroupCallback.Stub {
    private final AudioVolumeGroupCallback delegate;

    public AudioVolumeGroupCallbackAdapter(AudioVolumeGroupCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onAudioVolumeGroupChanged(AudioVolumeGroupChangeEvent volumeChangeEvent) {
        org.nift4.audiosysfwd.AudioVolumeGroupChangeEvent event;
        try {
            Class<?> clazz = volumeChangeEvent.getClass();
            event = new org.nift4.audiosysfwd.AudioVolumeGroupChangeEvent();
            event.flags = (int) Objects.requireNonNull(clazz.getField("flags").get(volumeChangeEvent));
            event.groupId = (int) Objects.requireNonNull(clazz.getField("groupId").get(volumeChangeEvent));
            event.muted = (boolean) Objects.requireNonNull(clazz.getField("muted").get(volumeChangeEvent));
            event.volumeIndex = (int) Objects.requireNonNull(clazz.getField("volumeIndex").get(volumeChangeEvent));
        } catch (Throwable t) {
            Log.e("AVolumeGroupCAdapter", "failed to convert", t);
            return;
        }
        delegate.onAudioVolumeGroupChanged(event);
    }

    @SuppressWarnings("PrivateApi")
    public static Method getAdd() throws NoSuchMethodException {
        return AudioSystem.class.getDeclaredMethod("registerAudioVolumeGroupCallback",
                INativeAudioVolumeGroupCallback.class);
    }

    @SuppressWarnings("PrivateApi")
    public static Method getRemove() throws NoSuchMethodException {
        return AudioSystem.class.getDeclaredMethod("unregisterAudioVolumeGroupCallback",
                INativeAudioVolumeGroupCallback.class);
    }
}
