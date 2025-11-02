package org.nift4.audiofxfwd;

import android.media.audiofx.AudioEffect;

import java.lang.reflect.Method;

@SuppressWarnings("unused")
/* package */ class OnParameterChangeListenerAdapter implements AudioEffect.OnParameterChangeListener {
	private final OnParameterChangeListener delegate;

	public OnParameterChangeListenerAdapter(OnParameterChangeListener delegate) {
		this.delegate = delegate;
	}

	@Override
	public void onParameterChange(AudioEffect effect, int status, byte[] param, byte[] value) {
		delegate.onParameterChange(effect, status, param, value);
	}

	@SuppressWarnings("BlockedPrivateApi")
	public static Method getSetter() throws NoSuchMethodException {
		return AudioEffect.class.getDeclaredMethod("setParameterListener",
				AudioEffect.OnParameterChangeListener.class);
	}
}
