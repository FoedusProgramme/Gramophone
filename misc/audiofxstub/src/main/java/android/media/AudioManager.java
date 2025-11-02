package android.media;

import java.util.concurrent.Executor;

@SuppressWarnings("unused")
public class AudioManager {
    public AudioManager() {
        throw new UnsupportedOperationException("Stub!");
    }

    public void registerVolumeGroupCallback(Executor executor,
                                            VolumeGroupCallback callback) {
        throw new UnsupportedOperationException("Stub!");
    }
    public void unregisterVolumeGroupCallback(VolumeGroupCallback callback) {
        throw new UnsupportedOperationException("Stub!");
    }
    public abstract static class VolumeGroupCallback {
        public void onAudioVolumeGroupChanged(int group, int flags) {
            throw new UnsupportedOperationException("Stub!");
        }
    }
}