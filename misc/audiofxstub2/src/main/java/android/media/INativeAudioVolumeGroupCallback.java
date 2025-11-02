/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation --min_sdk_version current -pout/soong/.intermediates/system/hardware/interfaces/media/android.media.audio.common.types_interface/4/preprocessed.aidl --ninja -d out/soong/.intermediates/frameworks/av/media/libaudioclient/volumegroupcallback-aidl-java-source/gen/android/media/INativeAudioVolumeGroupCallback.java.d -o out/soong/.intermediates/frameworks/av/media/libaudioclient/volumegroupcallback-aidl-java-source/gen -Nframeworks/av/media/libaudioclient/aidl frameworks/av/media/libaudioclient/aidl/android/media/INativeAudioVolumeGroupCallback.aidl
 *
 * DO NOT CHECK THIS FILE INTO A CODE TREE (e.g. git, etc..).
 * ALWAYS GENERATE THIS FILE FROM UPDATED AIDL COMPILER
 * AS A BUILD INTERMEDIATE ONLY. THIS IS NOT SOURCE CODE.
 */
package android.media;

import android.annotation.SuppressLint;
import android.media.audio.common.AudioVolumeGroupChangeEvent;

/**
 * The INativeAudioVolumeGroupCallback interface is a callback associated to the
 * setVolumeGroupVolumeIndex API. The callback is used by the AudioPolicyManager
 * implementation in native audio server to communicate volume changes.
 */
@SuppressLint("NewApi")
@SuppressWarnings("unused")
public interface INativeAudioVolumeGroupCallback extends android.os.IInterface
{
    /** Default implementation for INativeAudioVolumeGroupCallback. */
    class Default implements android.media.INativeAudioVolumeGroupCallback
    {
        /** Called when the index applied by the AudioPolicyManager changes */
        @Override public void onAudioVolumeGroupChanged(android.media.audio.common.AudioVolumeGroupChangeEvent volumeChangeEvent) throws android.os.RemoteException
        {
        }
        @Override
        public android.os.IBinder asBinder() {
            return null;
        }
    }
    /** Local-side IPC implementation stub class. */
    abstract class Stub extends android.os.Binder implements android.media.INativeAudioVolumeGroupCallback
    {
        /** Construct the stub and attach it to the interface. */
        @SuppressWarnings("this-escape")
        public Stub()
        {
            this.attachInterface(this, DESCRIPTOR);
        }
        /**
         * Cast an IBinder object into an android.media.INativeAudioVolumeGroupCallback interface,
         * generating a proxy if needed.
         */
        public static android.media.INativeAudioVolumeGroupCallback asInterface(android.os.IBinder obj)
        {
            if (obj==null) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof INativeAudioVolumeGroupCallback) {
                return ((android.media.INativeAudioVolumeGroupCallback)iin);
            }
            return new android.media.INativeAudioVolumeGroupCallback.Stub.Proxy(obj);
        }
        @Override public android.os.IBinder asBinder()
        {
            return this;
        }
        @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
        {
            java.lang.String descriptor = DESCRIPTOR;
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
                data.enforceInterface(descriptor);
            }
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(descriptor);
                return true;
            }
            if (code == TRANSACTION_onAudioVolumeGroupChanged) {
                AudioVolumeGroupChangeEvent _arg0;
                _arg0 = data.readTypedObject(AudioVolumeGroupChangeEvent.CREATOR);
                data.enforceNoDataAvail();
                this.onAudioVolumeGroupChanged(_arg0);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
        private static class Proxy implements android.media.INativeAudioVolumeGroupCallback
        {
            private final android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote)
            {
                mRemote = remote;
            }
            @Override public android.os.IBinder asBinder()
            {
                return mRemote;
            }
            public java.lang.String getInterfaceDescriptor()
            {
                return DESCRIPTOR;
            }
            /** Called when the index applied by the AudioPolicyManager changes */
            @Override public void onAudioVolumeGroupChanged(android.media.audio.common.AudioVolumeGroupChangeEvent volumeChangeEvent) throws android.os.RemoteException
            {
                android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeTypedObject(volumeChangeEvent, 0);
                    mRemote.transact(Stub.TRANSACTION_onAudioVolumeGroupChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
                }
                finally {
                    _data.recycle();
                }
            }
        }
        static final int TRANSACTION_onAudioVolumeGroupChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION);
    }
    String DESCRIPTOR = "android.media.INativeAudioVolumeGroupCallback";
    /** Called when the index applied by the AudioPolicyManager changes */
    void onAudioVolumeGroupChanged(android.media.audio.common.AudioVolumeGroupChangeEvent volumeChangeEvent) throws android.os.RemoteException;
}