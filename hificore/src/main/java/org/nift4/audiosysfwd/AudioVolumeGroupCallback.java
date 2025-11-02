package org.nift4.audiosysfwd;

public interface AudioVolumeGroupCallback {
    /** Called when the index applied by the AudioPolicyManager changes */
    void onAudioVolumeGroupChanged(AudioVolumeGroupChangeEvent volumeChangeEvent);
}
