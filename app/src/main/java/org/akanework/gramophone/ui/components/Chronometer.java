/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.akanework.gramophone.ui.components;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.DeprecatedSinceApi;
import androidx.appcompat.widget.AppCompatTextView;

import java.util.Formatter;
import java.util.IllegalFormatException;
import java.util.Locale;

/**
 * Class that implements a simple timer.
 * <p>
 * You can give it a start time in the {@link SystemClock#elapsedRealtime} timebase,
 * and it counts up from that, or if you don't give it a base time, it will use the
 * time at which you call {@link #start}.
 *
 * <p>The timer can also count downward towards the base time by
 * setting {@link #setCountDown(boolean)} to true.
 *
 *  <p>By default, it will display the current
 * timer value in the form "MM:SS" or "H:MM:SS", or you can use {@link #setFormat}
 * to format the timer value into an arbitrary string.
 *
 * @attr ref android.R.styleable#Chronometer_format
 * @attr ref android.R.styleable#Chronometer_countDown
 */
@SuppressWarnings("unused")
@DeprecatedSinceApi(api = Build.VERSION_CODES.N, message = "Use android.widget.Chronometer instead")
public class Chronometer extends AppCompatTextView {
    private static final String TAG = "Chronometer";

    /**
     * A callback that notifies when the chronometer has incremented on its own.
     */
    public interface OnChronometerTickListener {

        /**
         * Notification that the chronometer has changed.
         */
        void onChronometerTick(Chronometer chronometer);

    }

    private long mBase;
    private long mNow; // the currently displayed time
    private boolean mVisible;
    private boolean mStarted;
    private boolean mRunning;
    private boolean mLogged;
    private String mFormat;
    private Formatter mFormatter;
    private Locale mFormatterLocale;
    private final Object[] mFormatterArgs = new Object[1];
    private StringBuilder mFormatBuilder;
    private OnChronometerTickListener mOnChronometerTickListener;
    private final StringBuilder mRecycle = new StringBuilder(8);
    private boolean mCountDown;

    /**
     * Initialize this Chronometer object.
     * Sets the base to the current time.
     */
    public Chronometer(Context context) {
        this(context, null, 0);
    }

    /**
     * Initialize with standard view layout information.
     * Sets the base to the current time.
     */
    public Chronometer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Initialize with standard view layout information and style.
     * Sets the base to the current time.
     */
    public Chronometer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mBase = SystemClock.elapsedRealtime();
        updateText(mBase);
    }

    /**
     * Set this view to count down to the base instead of counting up from it.
     *
     * @param countDown whether this view should count down
     *
     * @see #setBase(long)
     */
    public void setCountDown(boolean countDown) {
        mCountDown = countDown;
        updateText(SystemClock.elapsedRealtime());
    }

    /**
     * @return whether this view counts down
     *
     * @see #setCountDown(boolean)
     */
    public boolean isCountDown() {
        return mCountDown;
    }

    /**
     * @return whether this is the final countdown
     */
    public boolean isTheFinalCountDown() {
        try {
            getContext().startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/9jK-NcRmVcw"))
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Set the time that the count-up timer is in reference to.
     *
     * @param base Use the {@link SystemClock#elapsedRealtime} time base.
     */
    public void setBase(long base) {
        mBase = base;
        dispatchChronometerTick();
        updateText(SystemClock.elapsedRealtime());
    }

    /**
     * Return the base time as set through {@link #setBase}.
     */
    public long getBase() {
        return mBase;
    }

    /**
     * Sets the format string used for display.  The Chronometer will display
     * this string, with the first "%s" replaced by the current timer value in
     * "MM:SS" or "H:MM:SS" form.
     * <p>
     * If the format string is null, or if you never call setFormat(), the
     * Chronometer will simply display the timer value in "MM:SS" or "H:MM:SS"
     * form.
     *
     * @param format the format string.
     */
    public void setFormat(String format) {
        mFormat = format;
        if (format != null && mFormatBuilder == null) {
            mFormatBuilder = new StringBuilder(format.length() * 2);
        }
    }

    /**
     * Returns the current format string as set through {@link #setFormat}.
     */
    public String getFormat() {
        return mFormat;
    }

    /**
     * Sets the listener to be called when the chronometer changes.
     *
     * @param listener The listener.
     */
    public void setOnChronometerTickListener(OnChronometerTickListener listener) {
        mOnChronometerTickListener = listener;
    }

    /**
     * @return The listener (may be null) that is listening for chronometer change
     *         events.
     */
    public OnChronometerTickListener getOnChronometerTickListener() {
        return mOnChronometerTickListener;
    }

    /**
     * Start counting up.  This does not affect the base as set from {@link #setBase}, just
     * the view display.
     * <p>
     * Chronometer works by regularly scheduling messages to the handler, even when the
     * Widget is not visible.  To make sure resource leaks do not occur, the user should
     * make sure that each start() call has a reciprocal call to {@link #stop}.
     */
    public void start() {
        mStarted = true;
        updateRunning();
    }

    /**
     * Stop counting up.  This does not affect the base as set from {@link #setBase}, just
     * the view display.
     * <p>
     * This stops the messages to the handler, effectively releasing resources that would
     * be held as the chronometer is running, via {@link #start}.
     */
    public void stop() {
        mStarted = false;
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = visibility == VISIBLE;
        updateRunning();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateRunning();
    }

    private synchronized void updateText(long now) {
        mNow = now;
        long seconds = Math.round((mCountDown ? mBase - now - 499 : now - mBase) / 1000f);
        boolean negative = false;
        if (seconds < 0) {
            seconds = -seconds;
            negative = true;
        }
        String text = DateUtils.formatElapsedTime(mRecycle, seconds);
        if (negative) {
            text = "-" + text;
        }

        if (mFormat != null) {
            Locale loc = Locale.getDefault();
            if (mFormatter == null || !loc.equals(mFormatterLocale)) {
                mFormatterLocale = loc;
                mFormatter = new Formatter(mFormatBuilder, loc);
            }
            mFormatBuilder.setLength(0);
            mFormatterArgs[0] = text;
            try {
                mFormatter.format(mFormat, mFormatterArgs);
                text = mFormatBuilder.toString();
            } catch (IllegalFormatException ex) {
                if (!mLogged) {
                    Log.w(TAG, "Illegal format string: " + mFormat);
                    mLogged = true;
                }
            }
        }
        setText(text);
    }

    private void updateRunning() {
        boolean running = mVisible && mStarted && isShown();
        if (running != mRunning) {
            if (running) {
                updateText(SystemClock.elapsedRealtime());
                dispatchChronometerTick();
                postTickOnNextSecond();
            } else {
                removeCallbacks(mTickRunnable);
            }
            mRunning = running;
        }
    }

    private final Runnable mTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRunning) {
                updateText(SystemClock.elapsedRealtime());
                dispatchChronometerTick();
                postTickOnNextSecond();
            }
        }
    };

    private void postTickOnNextSecond() {
        long nowMillis = mNow;
        long delayMillis;
        if (mCountDown) {
            delayMillis = (mBase - nowMillis) % 1000;
            if (delayMillis <= 0) {
                delayMillis += 1000;
            }
        } else {
            delayMillis = 1000 - (Math.abs(nowMillis - mBase) % 1000);
        }
        // Aim for 1 millisecond into the next second so we don't update exactly on the second
        delayMillis++;
        postDelayed(mTickRunnable, delayMillis);
    }

    void dispatchChronometerTick() {
        if (mOnChronometerTickListener != null) {
            mOnChronometerTickListener.onChronometerTick(this);
        }
    }
}
