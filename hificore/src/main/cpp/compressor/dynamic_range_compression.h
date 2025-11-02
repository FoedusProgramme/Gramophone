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
#ifndef LE_FX_ENGINE_DSP_CORE_DYNAMIC_RANGE_COMPRESSION_H_

#include "intrinsic_utils.h"

namespace le_fx {
	namespace math {
// taken from common/core/math.h
// A fast approximation to log2(.)
		inline float fast_log2(float val) {
			int* const exp_ptr = reinterpret_cast <int *> (&val);
			int x = *exp_ptr;
			const int log_2 = ((x >> 23) & 255) - 128;
			x &= ~(255 << 23);
			x += 127 << 23;
			*exp_ptr = x;
			val = ((-1.0f / 3) * val + 2) * val - 2.0f / 3;
			return static_cast<float>(val + log_2);
		}

// A fast approximation to log(.)
		inline float fast_log(float val) {
			return fast_log2(val) *
			       0.693147180559945286226763982995180413126945495605468750f;
		}

	}  // namespace math

// An adaptive dynamic range compression algorithm. The gain adaptation is made
// at the logarithmic domain and it is based on a Branching-Smooth compensated
// digital peak detector with different time constants for attack and release.
	class AdaptiveDynamicRangeCompression {
	public:
		AdaptiveDynamicRangeCompression() = default;
		AdaptiveDynamicRangeCompression(const AdaptiveDynamicRangeCompression&) = delete;
		AdaptiveDynamicRangeCompression& operator=(const AdaptiveDynamicRangeCompression&) = delete;

		// GRAMOPHONE: added more knobs
		void Initialize(float sampling_rate, float tau_attack, float tau_release, float compression_ratio);

		// optionally, in-place compression if in == out.
		void Compress(size_t channelCount, float inputAmp, float kneeThresholdDb, float postAmp,
					  float* in, float* out, size_t frameCount);

		// GRAMOPHONE: remove target_gain_to_knee_threshold_

	private:

		// Templated Compress routine.
		template <typename V>
		void Compress(float inputAmp, float kneeThreshold, float postAmp, V* in, V* out, size_t frameCount) {
			// GRAMOPHONE: move scale into Compress()
			constexpr float scale = 1 << 15; // power of 2 is lossless conversion to int16_t range
			constexpr float inverseScale = 1.f / scale;
			// Converts from dB to 1og-base
			float knee_threshold = 0.1151292546497023061569109358970308676362037658691406250f *
					kneeThreshold + 10.39717719035538401328722102334722876548767089843750f;
			for (size_t i = 0; i < frameCount; ++i) {
				auto v = android::audio_utils::intrinsics::vmul(in[i], inputAmp * scale);
				const float max_abs_x = android::audio_utils::intrinsics::vmaxv(
						android::audio_utils::intrinsics::vabs(v));
				const float max_abs_x_dB = math::fast_log(std::max(max_abs_x, kMinLogAbsValue));
				// Subtract Threshold from log-encoded input to get the amount of overshoot
				const float overshoot = max_abs_x_dB - knee_threshold;
				// Hard half-wave rectifier
				const float rect = std::max(overshoot, 0.0f);
				// Multiply rectified overshoot with slope
				const float cv = rect * slope_;
				const float prev_state = state_;
				const float alpha = (cv <= state_) ? alpha_attack_ : alpha_release_;
				state_ = alpha * state_ + (1.0f - alpha) * cv;
				compressor_gain_ *= expf(state_ - prev_state);
				// GRAMOPHONE: added post gain knob
				const auto x = android::audio_utils::intrinsics::vmul(v, compressor_gain_ * postAmp);
				v = android::audio_utils::intrinsics::vclamp(x, -kFixedPointLimit, kFixedPointLimit);
				out[i] = android::audio_utils::intrinsics::vmul(inverseScale, v);
			}
		}

		// The minimum accepted absolute input value to prevent numerical issues
		// when the input is close to zero.
		static constexpr float kMinLogAbsValue =
				0.032766999999999997517097227728299912996590137481689453125f;
		// Fixed-point arithmetic limits
		static constexpr float kFixedPointLimit = 32767.0f;

		float slope_;
		float sampling_rate_;
		// the internal state of the envelope detector
		float state_;
		// the latest gain factor that was applied to the input signal
		float compressor_gain_;
		// attack constant for exponential dumping
		float alpha_attack_;
		// release constant for exponential dumping
		float alpha_release_;
		// GRAMOPHONE: remove target_gain_to_knee_threshold_
	};

}  // namespace le_fx

#endif  // LE_FX_ENGINE_DSP_CORE_DYNAMIC_RANGE_COMPRESSION_H_
