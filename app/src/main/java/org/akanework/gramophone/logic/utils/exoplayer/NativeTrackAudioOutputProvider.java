/*
 * Copyright (C) 2025 The Android Open Source Project
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
package org.akanework.gramophone.logic.utils.exoplayer;

import static android.os.Build.VERSION.SDK_INT;
import static androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES;
import static androidx.media3.exoplayer.audio.DefaultAudioSink.DEFAULT_PLAYBACK_SPEED;
import static androidx.media3.exoplayer.audio.DefaultAudioSink.MAX_PLAYBACK_SPEED;
import static androidx.media3.exoplayer.audio.DefaultAudioSink.OUTPUT_MODE_OFFLOAD;
import static androidx.media3.exoplayer.audio.DefaultAudioSink.OUTPUT_MODE_PASSTHROUGH;
import static androidx.media3.exoplayer.audio.DefaultAudioSink.OUTPUT_MODE_PCM;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Looper;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ListenerSet;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.AudioCapabilities;
import androidx.media3.exoplayer.audio.AudioCapabilitiesReceiver;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioOutputProvider;
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink.AudioOffloadSupportProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink.AudioTrackBufferSizeProvider;
import androidx.media3.exoplayer.audio.DefaultAudioSink.OutputMode;
import androidx.media3.extractor.DtsUtil;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.akanework.gramophone.logic.utils.AudioFormatDetector;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.nift4.gramophone.hificore.NativeTrack;

import java.util.Objects;

/** A default implementation of {@link AudioOutputProvider}. */
public final class NativeTrackAudioOutputProvider implements AudioOutputProvider {

  private static final String TAG = "ATAudioOutputProvider";

  private static final Supplier<Boolean> COMPRESSED_OFFLOAD_EXPLICIT_AAC_ENABLED =
      Suppliers.memoize(NativeTrackAudioOutputProvider::isCompressedOffloadExplicitAacEnabled);

  /** A builder to create {@link NativeTrackAudioOutputProvider} instances. */
  public static final class Builder {

    @NonNull private final Context context;

    private @MonotonicNonNull AudioOffloadSupportProvider audioOffloadSupportProvider;
    private AudioTrackBufferSizeProvider bufferSizeProvider;
    private float maxPlaybackSpeed;

    /**
     * Creates a new builder.
     *
     * @param context The {@link Context}.
     */
    public Builder(@NonNull Context context) {
      this.context = context.getApplicationContext();
      bufferSizeProvider = AudioTrackBufferSizeProvider.DEFAULT;
      maxPlaybackSpeed = MAX_PLAYBACK_SPEED;
    }

    /**
     * Sets an {@link AudioOffloadSupportProvider} to provide the provider's offload support
     * capabilities for a given {@link AudioOutputProvider.FormatConfig} for calls to {@link
     * #getFormatSupport(FormatConfig)}.
     *
     * <p>The default is an instance of {@link DefaultAudioOffloadSupportProvider}.
     *
     * @param audioOffloadSupportProvider The {@link AudioOffloadSupportProvider} to use.
     * @return This builder.
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setAudioOffloadSupportProvider(
        AudioOffloadSupportProvider audioOffloadSupportProvider) {
      this.audioOffloadSupportProvider = audioOffloadSupportProvider;
      return this;
    }

    /**
     * Sets an {@link AudioTrackBufferSizeProvider} to compute the buffer size when {@link
     * DefaultAudioSink#configure} is called with {@code specifiedBufferSize == 0}.
     *
     * <p>The default value is {@link AudioTrackBufferSizeProvider#DEFAULT}.
     *
     * @param bufferSizeProvider The {@link AudioTrackBufferSizeProvider} to use.
     * @return This builder.
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setAudioTrackBufferSizeProvider(
        AudioTrackBufferSizeProvider bufferSizeProvider) {
      this.bufferSizeProvider = bufferSizeProvider;
      return this;
    }

    /**
     * Sets the maximum playback speed that an {@link NativeTrackAudioOutput} provided by this
     * instance is going to be configured for. This is also used to allocate buffers that are big
     * enough to not underrun at the maximum playback speed. This value has no effect if {@code
     * useAudioOutputPlaybackParams} is disabled.
     *
     * <p>The default value is {@link DefaultAudioSink#MAX_PLAYBACK_SPEED}.
     *
     * @param maxPlaybackSpeed The maximum playback speed to use. Must be at least {@code 1f}.
     * @return This builder.
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setMaxPlaybackSpeed(float maxPlaybackSpeed) {
      checkArgument(maxPlaybackSpeed >= 1f);
      this.maxPlaybackSpeed = maxPlaybackSpeed;
      return this;
    }

    /** Builds the {@link NativeTrackAudioOutputProvider}. */
    public NativeTrackAudioOutputProvider build() {
      if (audioOffloadSupportProvider == null) {
        audioOffloadSupportProvider = new DefaultAudioOffloadSupportProvider(context);
      }

      return new NativeTrackAudioOutputProvider(this);
    }
  }

  @NonNull private final Context context;

  private final AudioTrackBufferSizeProvider audioTrackBufferSizeProvider;
  private final AudioOffloadSupportProvider audioOffloadSupportProvider;
  @Nullable private final CapabilityChangeListener capabilityChangeListener;
  private final float maxPlaybackSpeed;

  private @MonotonicNonNull ListenerSet<Listener> listeners;
  private Clock clock;
  private @MonotonicNonNull AudioCapabilities audioCapabilities;
  private @MonotonicNonNull AudioCapabilitiesReceiver audioCapabilitiesReceiver;
  @Nullable private Looper playbackLooper;
  @Nullable private Context contextWithDeviceId;

  private NativeTrackAudioOutputProvider(Builder builder) {
    this.context = builder.context;
    this.audioOffloadSupportProvider = checkNotNull(builder.audioOffloadSupportProvider);
    this.audioTrackBufferSizeProvider = builder.bufferSizeProvider;
    this.capabilityChangeListener = new CapabilityChangeListener();
    this.maxPlaybackSpeed = builder.maxPlaybackSpeed;
    this.clock = Clock.DEFAULT;
  }

  @Override
  public FormatSupport getFormatSupport(FormatConfig formatConfig) {
    updateAudioCapabilitiesReceiver(formatConfig);
    AudioOffloadSupport offloadSupport =
        audioOffloadSupportProvider.getAudioOffloadSupport(
            formatConfig.format, formatConfig.audioAttributes);
    return new FormatSupport.Builder()
        .setFormatSupportLevel(getFormatSupportLevel(formatConfig))
        .setIsFormatSupportedForOffload(offloadSupport.isFormatSupported)
        .setIsGaplessSupportedForOffload(offloadSupport.isGaplessSupported)
        .setIsSpeedChangeSupportedForOffload(offloadSupport.isSpeedChangeSupported)
        .build();
  }

  @Override
  public OutputConfig getOutputConfig(FormatConfig formatConfig) throws ConfigurationException {
    Format format = formatConfig.format;
    updateAudioCapabilitiesReceiver(formatConfig);

    @OutputMode int outputMode;
    @C.Encoding int outputEncoding;
    int outputSampleRate;
    int outputChannelConfig;
    int outputPcmFrameSize;
    boolean usePlaybackParameters;
    boolean useOffloadGapless = false;

    if (Objects.equals(format.sampleMimeType, MimeTypes.AUDIO_RAW)) {
      checkArgument(Util.isEncodingLinearPcm(format.pcmEncoding));
      outputMode = OUTPUT_MODE_PCM;
      outputEncoding = format.pcmEncoding;
      outputSampleRate = format.sampleRate;
      outputChannelConfig = getAudioOutputChannelConfig(format);
      outputPcmFrameSize = Util.getPcmFrameSize(outputEncoding, format.channelCount);
      usePlaybackParameters = formatConfig.enablePlaybackParameters;
    } else {
      outputSampleRate = format.sampleRate;
      outputPcmFrameSize = C.LENGTH_UNSET;
      AudioOffloadSupport audioOffloadSupport =
          formatConfig.enableOffload
              ? audioOffloadSupportProvider.getAudioOffloadSupport(
                  format, formatConfig.audioAttributes)
              : AudioOffloadSupport.DEFAULT_UNSUPPORTED;
      if (formatConfig.enableOffload && audioOffloadSupport.isFormatSupported) {
        outputMode = OUTPUT_MODE_OFFLOAD;
        outputEncoding = MimeTypes.getEncoding(checkNotNull(format.sampleMimeType), format.codecs);
        outputChannelConfig = getAudioOutputChannelConfig(format);
        if ((outputEncoding == C.ENCODING_AAC_HE_V1 || outputEncoding == C.ENCODING_AAC_HE_V2)
            && outputSampleRate >= 16000
            && !COMPRESSED_OFFLOAD_EXPLICIT_AAC_ENABLED.get()) {
          if (outputEncoding == C.ENCODING_AAC_HE_V2 && format.channelCount == 2) {
            outputChannelConfig = AudioFormat.CHANNEL_OUT_MONO;
          }
          outputEncoding = C.ENCODING_AAC_LC;
          outputSampleRate /= 2;
        }
        // Offload requires NativeTrack playback parameters to apply speed changes quickly.
        usePlaybackParameters = true;
        useOffloadGapless = audioOffloadSupport.isGaplessSupported;
      } else {
        outputMode = OUTPUT_MODE_PASSTHROUGH;
        @Nullable
        Pair<Integer, Integer> encodingAndChannelConfig =
            audioCapabilities.getEncodingAndChannelConfigForPassthrough(
                format, formatConfig.audioAttributes);
        if (encodingAndChannelConfig == null) {
          throw new ConfigurationException("Unable to configure passthrough for: " + format);
        }
        outputEncoding = encodingAndChannelConfig.first;
        outputChannelConfig = encodingAndChannelConfig.second;
        // Passthrough only supports audio output playback parameters, but we only enable it this
        // was specifically requested by the app.
        usePlaybackParameters = formatConfig.enablePlaybackParameters;
      }
    }

    // Replace unknown bitrate by maximum allowed bitrate for DTS Express to avoid allocating an
    // NativeTrack buffer for the much larger maximum bitrate of the underlying DTS-HD encoding.
    int bitrate = format.bitrate;
    if (Objects.equals(format.sampleMimeType, MimeTypes.AUDIO_DTS_EXPRESS)
        && bitrate == Format.NO_VALUE) {
      bitrate = DtsUtil.DTS_EXPRESS_MAX_RATE_BITS_PER_SECOND;
    }

    int bufferSize =
        formatConfig.preferredBufferSize != C.LENGTH_UNSET
            ? formatConfig.preferredBufferSize
            : audioTrackBufferSizeProvider.getBufferSizeInBytes(
                getNativeTrackMinBufferSize(outputSampleRate, outputChannelConfig, outputEncoding),
                outputEncoding,
                outputMode,
                outputPcmFrameSize != C.LENGTH_UNSET ? outputPcmFrameSize : 1,
                outputSampleRate,
                bitrate,
                usePlaybackParameters ? maxPlaybackSpeed : DEFAULT_PLAYBACK_SPEED);

    return new OutputConfig.Builder()
        .setSampleRate(outputSampleRate)
        .setChannelMask(outputChannelConfig)
        .setEncoding(outputEncoding)
        .setBufferSize(bufferSize)
        .setAudioSessionId(formatConfig.audioSessionId)
        .setAudioAttributes(formatConfig.audioAttributes)
        .setIsOffload(outputMode == OUTPUT_MODE_OFFLOAD)
        .setIsTunneling(formatConfig.enableTunneling)
        .setUsePlaybackParameters(usePlaybackParameters)
        .setUseOffloadGapless(useOffloadGapless)
        .setVirtualDeviceId(formatConfig.virtualDeviceId)
        .build();
  }

  @SuppressWarnings("CatchingUnchecked") // Catching generic Exception from NativeTrack.release
  @Override
  public NativeTrackAudioOutput getAudioOutput(OutputConfig config) throws InitializationException {
    NativeTrack nativeTrack;
    try {
      @NonNull Context contextForNativeTrack;
      int audioSessionId = config.audioSessionId;
      if (config.virtualDeviceId != C.INDEX_UNSET && SDK_INT >= 34) {
        if (contextWithDeviceId == null
            || contextWithDeviceId.getDeviceId() != config.virtualDeviceId) {
          contextWithDeviceId = context.createDeviceContext(config.virtualDeviceId);
        }
        contextForNativeTrack = contextWithDeviceId;
        audioSessionId = AudioManager.AUDIO_SESSION_ID_GENERATE;
      } else {
        contextForNativeTrack = context;
      }
      android.media.AudioAttributes nativeTrackAttributes =
          getNativeTrackAttributes(config.audioAttributes, config.isTunneling);
      AudioFormatDetector.Encoding format = AudioFormatDetector.Encoding.get(config.encoding);
      if (format == null) {
        throw new IllegalStateException("missing map for media3 format " + config.encoding);
      }
      int channelMask = 0x3; // TODO
      NativeTrack.Builder nativeTrackBuilder =
          NativeTrack.Builder.create(contextForNativeTrack, nativeTrackAttributes,
                  format.getNativeOrThrow(), channelMask);
      nativeTrackBuilder.setSampleRate(config.sampleRate);
      nativeTrackBuilder.setBufferSizeInBytes(config.bufferSize);
      nativeTrackBuilder.setSessionId(audioSessionId);
      nativeTrackBuilder.setMaxRequiredSpeed(maxPlaybackSpeed);
      // TODO: direct_pcm?
      nativeTrackBuilder.setTrackFlags(NativeTrack.AUDIO_OUTPUT_FLAG_DIRECT|NativeTrack.AUDIO_OUTPUT_FLAG_DIRECT_PCM);
      //TODO:if (SDK_INT >= 29) {
      //  nativeTrackBuilder.setOffloadedPlayback(config.isOffload);
      //}
      nativeTrack = nativeTrackBuilder.build();
      if ((nativeTrack.flags() & NativeTrack.AUDIO_OUTPUT_FLAG_DIRECT) == 0) {//TODO
        nativeTrack.release();
        throw new IllegalArgumentException("No direct output available");
      }
    } catch (UnsupportedOperationException | IllegalArgumentException e) {
      throw new InitializationException(e);
    }
    if (nativeTrack.getMyState() != NativeTrack.State.ALIVE) {
      try {
        nativeTrack.release();
      } catch (Exception e) {
        // The track has already failed to initialize, so it wouldn't be that surprising if
        // release were to fail too. Swallow the exception.
      }
      throw new InitializationException();
    }
    return new NativeTrackAudioOutput(
        nativeTrack, config, capabilityChangeListener, maxPlaybackSpeed, clock);
  }

  @Override
  public void addListener(Listener listener) {
    verifySinglePlaybackLooper();
    if (listeners == null) {
      listeners = new ListenerSet<>(Thread.currentThread());
    }
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    if (listeners != null) {
      listeners.remove(listener);
    }
  }

  @UnstableApi
  @Override
  public void setClock(Clock clock) {
    this.clock = clock;
  }

  @UnstableApi
  @Override
  public boolean hasPendingReleases() {
    return NativeTrackAudioOutput.hasPendingReleases();
  }

  @Override
  public void release() {
    if (listeners != null) {
      listeners.release();
    }
    if (audioCapabilitiesReceiver != null) {
      audioCapabilitiesReceiver.unregister();
    }
  }

  /** Returns the {@link AudioCapabilities}. */
  @UnstableApi
  @Override
  @Nullable
  public AudioCapabilities getAudioCapabilities() {
    return audioCapabilities;
  }

  private android.media.AudioAttributes getNativeTrackAttributes(
      AudioAttributes audioAttributes, boolean tunneling) {
    if (tunneling) {
      return getNativeTrackTunnelingAttributes();
    } else {
      return audioAttributes.getPlatformAudioAttributes();
    }
  }

  private android.media.AudioAttributes getNativeTrackTunnelingAttributes() {
    return new android.media.AudioAttributes.Builder()
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
        .setFlags(android.media.AudioAttributes.FLAG_HW_AV_SYNC)
        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .build();
  }

  void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
    verifySinglePlaybackLooper();
    if (this.audioCapabilities != null && !audioCapabilities.equals(this.audioCapabilities)) {
      this.audioCapabilities = audioCapabilities;
      if (listeners != null) {
        listeners.sendEvent(Listener::onFormatSupportChanged);
      }
    }
  }

  private int getAudioOutputChannelConfig(Format format) {
    return Util.getAudioTrackChannelConfig(format);
  }

  private int getNativeTrackMinBufferSize(int sampleRateInHz, int channelConfig, int encoding) {
    return NativeTrack.getMinBufferSize(sampleRateInHz, channelConfig, encoding);
  }

  @EnsuresNonNull("audioCapabilities")
  private void updateAudioCapabilitiesReceiver(FormatConfig formatConfig) {
    verifySinglePlaybackLooper();
    if (audioCapabilitiesReceiver == null) {
      // Must be lazily initialized to receive audio capabilities receiver listener event on the
      // current (playback) thread as the constructor is not called in the playback thread.
      audioCapabilitiesReceiver =
          new AudioCapabilitiesReceiver(
              context,
              this::onAudioCapabilitiesChanged,
              formatConfig.audioAttributes,
              formatConfig.preferredDevice);
      audioCapabilities = audioCapabilitiesReceiver.register();
    } else {
      if (formatConfig.preferredDevice != null) {
        audioCapabilitiesReceiver.setRoutedDevice(formatConfig.preferredDevice);
      }
      audioCapabilitiesReceiver.setAudioAttributes(formatConfig.audioAttributes);
    }
    checkNotNull(audioCapabilities);
  }

  private void verifySinglePlaybackLooper() {
    @Nullable Looper myLooper = Looper.myLooper();
    checkState(
        playbackLooper == null || playbackLooper == myLooper,
        "NativeTrackAudioOutputProvider accessed on multiple threads: %s and %s",
        getLooperThreadName(playbackLooper),
        getLooperThreadName(myLooper));
    playbackLooper = myLooper;
  }

  @RequiresNonNull("audioCapabilities")
  private @SupportLevel int getFormatSupportLevel(FormatConfig formatConfig) {
    Format format = formatConfig.format;
    if (Objects.equals(format.sampleMimeType, MimeTypes.AUDIO_RAW)) {
      if (format.channelCount != 2) {
        // TODO add support for this
        return FORMAT_UNSUPPORTED;
      }
      if (format.pcmEncoding != C.ENCODING_PCM_16BIT) {
        if (!formatConfig.enableHighResolutionPcmOutput) {
          // Other PCM formats explicitly disabled, so claim no support.
          return FORMAT_UNSUPPORTED;
        }
      }
      if (!Util.isEncodingLinearPcm(format.pcmEncoding)) {
        Log.w(TAG, "Invalid PCM encoding: " + format.pcmEncoding);
        return FORMAT_UNSUPPORTED;
      }
      AudioFormatDetector.Encoding encoding = AudioFormatDetector.Encoding.get(format.pcmEncoding);
      if (encoding == null) {
        // Format not yet supported by AudioFormatDetector, this should be fixed...
        Log.e(TAG, "Missing PCM encoding: " + format.pcmEncoding);
        return FORMAT_UNSUPPORTED;
      }
      if (!encoding.isSupportedAsNative()) {
        // Format not yet supported by NativeTrack on this SDK level.
        return FORMAT_UNSUPPORTED;
      }
      // NativeTrack can play this PCM format. It may internally resample to other PCM formats, but
      // this is outside of our control and knowledge.
      return FORMAT_SUPPORTED_DIRECTLY;
    }
    if (audioCapabilities.isPassthroughPlaybackSupported(format, formatConfig.audioAttributes)) {
      //TODO return FORMAT_SUPPORTED_DIRECTLY;
    }

    return FORMAT_UNSUPPORTED;
  }

  private static String getLooperThreadName(@Nullable Looper looper) {
    return looper == null ? "null" : looper.getThread().getName();
  }

  private final class CapabilityChangeListener
      implements NativeTrackAudioOutput.CapabilityChangeListener {

    @Override
    public void onRecoverableWriteError() {
      if (audioCapabilitiesReceiver != null) {
        // Change to the audio capabilities supported by all the devices during the error recovery.
        audioCapabilities = DEFAULT_AUDIO_CAPABILITIES;
        audioCapabilitiesReceiver.overrideCapabilities(DEFAULT_AUDIO_CAPABILITIES);
      }
    }

    @Override
    public void onRoutedDeviceChanged(AudioDeviceInfo routedDevice) {
      if (audioCapabilitiesReceiver != null) {
        audioCapabilitiesReceiver.setRoutedDevice(routedDevice);
      }
    }
  }

  private static boolean isCompressedOffloadExplicitAacEnabled() {
    return SDK_INT > 37
        && Objects.equals(
            Util.getSystemProperty("persist.audio.compressed_offload_implicit_aac"), "false");
  }
}