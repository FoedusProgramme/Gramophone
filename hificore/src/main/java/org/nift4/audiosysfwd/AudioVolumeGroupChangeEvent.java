package org.nift4.audiosysfwd;

import java.util.Objects;

@SuppressWarnings("unused")
public class AudioVolumeGroupChangeEvent {
    /**
     * Shall show a toast containing the current volume.
     */
    public static final int VOLUME_FLAG_SHOW_UI = 1;
    /**
     * Whether to include ringer modes as possible options when changing volume..
     */
    public static final int VOLUME_FLAG_ALLOW_RINGER_MODES = 1 << 1;
    /**
     * Whether to play a sound when changing the volume.
     */
    public static final int VOLUME_FLAG_PLAY_SOUND = 1 << 2;
    /**
     * Removes any sounds/vibrate that may be in the queue, or are playing.
     */
    public static final int VOLUME_FLAG_REMOVE_SOUND_AND_VIBRATE = 1 << 3;
    /**
     * Whether to vibrate if going into the vibrate ringer mode.
     */
    public static final int VOLUME_FLAG_VIBRATE = 1 << 4;
    /**
     * Indicates to VolumePanel that the volume slider should be disabled as user cannot
     * change the volume.
     */
    public static final int VOLUME_FLAG_FIXED_VOLUME = 1 << 5;
    /**
     * Indicates the volume set/adjust call is for Bluetooth absolute volume.
     */
    public static final int VOLUME_FLAG_BLUETOOTH_ABS_VOLUME = 1 << 6;
    /**
     * Adjusting the volume was prevented due to silent mode, display a hint in the UI.
     */
    public static final int VOLUME_FLAG_SHOW_SILENT_HINT = 1 << 7;
    /**
     * Indicates the volume call is for Hdmi Cec system audio volume.
     */
    public static final int VOLUME_FLAG_HDMI_SYSTEM_AUDIO_VOLUME = 1 << 8;
    /**
     * Indicates that this should only be handled if media is actively playing.
     */
    public static final int VOLUME_FLAG_ACTIVE_MEDIA_ONLY = 1 << 9;
    /**
     * Like FLAG_SHOW_UI, but only dialog warnings and confirmations, no sliders.
     */
    public static final int VOLUME_FLAG_SHOW_UI_WARNINGS = 1 << 10;
    /**
     * Adjusting the volume down from vibrated was prevented, display a hint in the UI.
     */
    public static final int VOLUME_FLAG_SHOW_VIBRATE_HINT = 1 << 11;
    /**
     * Adjusting the volume due to a hardware key press.
     */
    public static final int VOLUME_FLAG_FROM_KEY = 1 << 12;
    /**
     * Indicates that an absolute volume controller is notifying AudioService of a change in the
     * volume or mute status of an external audio system..
     */
    public static final int VOLUME_FLAG_ABSOLUTE_VOLUME = 1 << 13;

    /** Unique identifier of the volume group. */
    public int groupId;
    /** Index in UI applied. */
    public int volumeIndex;
    /** Muted attribute, orthogonal to volume index. */
    public boolean muted;
    /**
     * Bitmask indicating a suggested UI behavior or characterising the volume event.
     * The bit masks are defined in the constants prefixed by VOLUME_FLAG_*.
     */
    public int flags;

    @Override
    public String toString() {
        return "AudioVolumeGroupChangeEvent{" +
                "groupId=" + groupId +
                ", volumeIndex=" + volumeIndex +
                ", muted=" + muted +
                ", flags=" + flags +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AudioVolumeGroupChangeEvent that)) return false;
        return groupId == that.groupId && volumeIndex == that.volumeIndex && muted == that.muted && flags == that.flags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, volumeIndex, muted, flags);
    }
}
