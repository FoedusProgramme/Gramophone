/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java --structured --version 4 --hash af71e6ae2c6861fc2b09bb477e7285e6777cd41c --stability vintf --min_sdk_version 29 --ninja -d out/soong/.intermediates/system/hardware/interfaces/media/android.media.audio.common.types-V4-java-source/gen/android/media/audio/common/AudioVolumeGroupChangeEvent.java.d -o out/soong/.intermediates/system/hardware/interfaces/media/android.media.audio.common.types-V4-java-source/gen -Nsystem/hardware/interfaces/media/aidl_api/android.media.audio.common.types/4 system/hardware/interfaces/media/aidl_api/android.media.audio.common.types/4/android/media/audio/common/AudioVolumeGroupChangeEvent.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.media.audio.common;

import android.annotation.SuppressLint;

@SuppressLint("NewApi")
public class AudioVolumeGroupChangeEvent implements android.os.Parcelable
{
    public int groupId = 0;
    public int volumeIndex = 0;
    public boolean muted = false;
    public int flags = 0;
    public static final android.os.Parcelable.Creator<AudioVolumeGroupChangeEvent> CREATOR = new android.os.Parcelable.Creator<AudioVolumeGroupChangeEvent>() {
        @Override
        public AudioVolumeGroupChangeEvent createFromParcel(android.os.Parcel _aidl_source) {
            AudioVolumeGroupChangeEvent _aidl_out = new AudioVolumeGroupChangeEvent();
            _aidl_out.readFromParcel(_aidl_source);
            return _aidl_out;
        }
        @Override
        public AudioVolumeGroupChangeEvent[] newArray(int _aidl_size) {
            return new AudioVolumeGroupChangeEvent[_aidl_size];
        }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
        int _aidl_start_pos = _aidl_parcel.dataPosition();
        _aidl_parcel.writeInt(0);
        _aidl_parcel.writeInt(groupId);
        _aidl_parcel.writeInt(volumeIndex);
        _aidl_parcel.writeBoolean(muted);
        _aidl_parcel.writeInt(flags);
        int _aidl_end_pos = _aidl_parcel.dataPosition();
        _aidl_parcel.setDataPosition(_aidl_start_pos);
        _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
        _aidl_parcel.setDataPosition(_aidl_end_pos);
    }
    public final void readFromParcel(android.os.Parcel _aidl_parcel)
    {
        int _aidl_start_pos = _aidl_parcel.dataPosition();
        int _aidl_parcelable_size = _aidl_parcel.readInt();
        try {
            if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
            if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
            groupId = _aidl_parcel.readInt();
            if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
            volumeIndex = _aidl_parcel.readInt();
            if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
            muted = _aidl_parcel.readBoolean();
            if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
            flags = _aidl_parcel.readInt();
        } finally {
            if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
                throw new android.os.BadParcelableException("Overflow in the size of parcelable");
            }
            _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
        }
    }
    @Override
    public String toString() {
        java.util.StringJoiner _aidl_sj = new java.util.StringJoiner(", ", "{", "}");
        _aidl_sj.add("groupId: " + (groupId));
        _aidl_sj.add("volumeIndex: " + (volumeIndex));
        _aidl_sj.add("muted: " + (muted));
        _aidl_sj.add("flags: " + (flags));
        return "AudioVolumeGroupChangeEvent" + _aidl_sj;
    }
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (!(other instanceof AudioVolumeGroupChangeEvent that)) return false;
        if (!java.util.Objects.deepEquals(groupId, that.groupId)) return false;
        if (!java.util.Objects.deepEquals(volumeIndex, that.volumeIndex)) return false;
        if (!java.util.Objects.deepEquals(muted, that.muted)) return false;
        return java.util.Objects.deepEquals(flags, that.flags);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.deepHashCode(java.util.Arrays.asList(groupId, volumeIndex, muted, flags).toArray());
    }
    @Override
    public int describeContents() {
        return 0;
    }
}