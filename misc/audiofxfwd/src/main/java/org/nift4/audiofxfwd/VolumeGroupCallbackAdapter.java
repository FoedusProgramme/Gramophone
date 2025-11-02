package org.nift4.audiofxfwd;

import android.media.AudioManager;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/* package */ class VolumeGroupCallbackAdapter extends AudioManager.VolumeGroupCallback {
    private final VolumeGroupCallback delegate;

    public VolumeGroupCallbackAdapter(VolumeGroupCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onAudioVolumeGroupChanged(int group, int flags) {
        delegate.onAudioVolumeGroupChanged(group, flags);
    }

    @SuppressWarnings("PrivateApi")
    public static Method getAdd() throws NoSuchMethodException {
        return AudioManager.class.getDeclaredMethod("registerVolumeGroupCallback",
                Executor.class, AudioManager.VolumeGroupCallback.class);
    }

    @SuppressWarnings("PrivateApi")
    public static Method getRemove() throws NoSuchMethodException {
        return AudioManager.class.getDeclaredMethod("unregisterVolumeGroupCallback",
                AudioManager.VolumeGroupCallback.class);
    }
}
