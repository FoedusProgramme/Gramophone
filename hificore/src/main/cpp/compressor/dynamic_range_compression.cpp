/*
 * Copyright (C) 2013 The Android Open Source Project
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
//#define LOG_NDEBUG 0

#include <android/log_macros.h>
#include "dynamic_range_compression.h"
#include <jni.h>

// fixed channel count: limit
#define FCC_LIMIT 28

namespace le_fx {
	// GRAMOPHONE: remove target_gain_to_knee_threshold_

	// GRAMOPHONE: added more knobs
	void AdaptiveDynamicRangeCompression::Initialize(
			float sampling_rate, float tau_attack, float tau_release, float compression_ratio) {
		sampling_rate_ = sampling_rate;
		state_ = 0.0f;
		slope_ = 1.0f / compression_ratio - 1.0f;
		compressor_gain_ = 1.0f;
		if (tau_attack > 0.0f) {
			const float taufs = tau_attack * sampling_rate_;
			alpha_attack_ = std::exp(-1.0f / taufs);
		} else {
			alpha_attack_ = 0.0f;
		}
		if (tau_release > 0.0f) {
			const float taufs = tau_release * sampling_rate_;
			alpha_release_ = std::exp(-1.0f / taufs);
		} else {
			alpha_release_ = 0.0f;
		}
	}

// Instantiate Compress for supported channel counts.
#define INSTANTIATE_COMPRESS(CHANNEL_COUNT) \
case CHANNEL_COUNT: \
    if constexpr (CHANNEL_COUNT <= FCC_LIMIT) { \
        Compress(inputAmp, kneeThreshold, postAmp, \
                reinterpret_cast<internal_array_t<float, CHANNEL_COUNT>*>(in), \
				reinterpret_cast<internal_array_t<float, CHANNEL_COUNT>*>(out), frameCount); \
        return; \
    } \
    break;

	void AdaptiveDynamicRangeCompression::Compress(size_t channelCount, float inputAmp, float kneeThreshold,
												   float postAmp, float* in, float* out, size_t frameCount) {
		using android::audio_utils::intrinsics::internal_array_t;
		switch (channelCount) {
			INSTANTIATE_COMPRESS(1)
			INSTANTIATE_COMPRESS(2)
			INSTANTIATE_COMPRESS(3)
			INSTANTIATE_COMPRESS(4)
			INSTANTIATE_COMPRESS(5)
			INSTANTIATE_COMPRESS(6)
			INSTANTIATE_COMPRESS(7)
			INSTANTIATE_COMPRESS(8)
			INSTANTIATE_COMPRESS(9)
			INSTANTIATE_COMPRESS(10)
			INSTANTIATE_COMPRESS(11)
			INSTANTIATE_COMPRESS(12)
			INSTANTIATE_COMPRESS(13)
			INSTANTIATE_COMPRESS(14)
			INSTANTIATE_COMPRESS(15)
			INSTANTIATE_COMPRESS(16)
			INSTANTIATE_COMPRESS(17)
			INSTANTIATE_COMPRESS(18)
			INSTANTIATE_COMPRESS(19)
			INSTANTIATE_COMPRESS(20)
			INSTANTIATE_COMPRESS(21)
			INSTANTIATE_COMPRESS(22)
			INSTANTIATE_COMPRESS(23)
			INSTANTIATE_COMPRESS(24)
			INSTANTIATE_COMPRESS(25)
			INSTANTIATE_COMPRESS(26)
			INSTANTIATE_COMPRESS(27)
			INSTANTIATE_COMPRESS(28)
			default:
				break;
		}
		ALOGE("%s: channelCount: %zu not supported", __func__, channelCount);
		abort();
	}

}  // namespace le_fx


extern "C"
JNIEXPORT jlong JNICALL
Java_org_nift4_gramophone_hificore_AdaptiveDynamicRangeCompression_create(JNIEnv *,
                                                                          jobject) {
	return (intptr_t) new le_fx::AdaptiveDynamicRangeCompression();
}

extern "C"
JNIEXPORT void JNICALL
Java_org_nift4_gramophone_hificore_AdaptiveDynamicRangeCompression_releaseNative(JNIEnv *,
                                                                                 jobject,
                                                                                 jlong ptr) {
	auto obj = (le_fx::AdaptiveDynamicRangeCompression*) ptr;
	delete obj;
}

extern "C"
JNIEXPORT void JNICALL
Java_org_nift4_gramophone_hificore_AdaptiveDynamicRangeCompression_initNative(JNIEnv *,
                                                                              jobject,
                                                                              jlong ptr,
                                                                              jfloat sampling_rate,
                                                                              jfloat tau_attack,
                                                                              jfloat tau_release,
                                                                              jfloat compression_ratio) {
	auto obj = (le_fx::AdaptiveDynamicRangeCompression*) ptr;
	obj->Initialize(sampling_rate, tau_attack, tau_release, compression_ratio);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_nift4_gramophone_hificore_AdaptiveDynamicRangeCompression_compressNative(JNIEnv *env,
                                                                                  jobject,
                                                                                  jlong ptr,
                                                                                  jint channel_count,
                                                                                  jfloat input_amp,
                                                                                  jfloat knee_threshold,
                                                                                  jfloat post_amp,
                                                                                  jobject in_buf,
                                                                                  jobject out_buf,
                                                                                  jint frame_count) {
	auto obj = (le_fx::AdaptiveDynamicRangeCompression*) ptr;
	auto in = (float*) env->GetDirectBufferAddress(in_buf);
	auto out = (float*) env->GetDirectBufferAddress(out_buf);
	obj->Compress(channel_count, input_amp, knee_threshold,
				  post_amp, in, out, frame_count);
}