/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef ANDROID_AUDIO_UTILS_INTRINSIC_UTILS_H
#define ANDROID_AUDIO_UTILS_INTRINSIC_UTILS_H

#include <array>  // std::size
#include <type_traits>
#include "template_utils.h"

/*
  The intrinsics utility library contain helper functions for wide width DSP support.
  We use templated types to allow testing from scalar to vector values.

  See the Eigen project for general abstracted linear algebra acceleration.
  http://eigen.tuxfamily.org/
*/

// We conditionally include neon optimizations for ARM devices
#pragma push_macro("USE_NEON")
#undef USE_NEON

#if defined(__ARM_NEON__) || defined(__aarch64__)
#include <arm_neon.h>
#define USE_NEON
#endif

// We use macros to hide intrinsic methods that do not exist for
// incompatible target architectures; otherwise we have a
// "use of undeclared identifier" compilation error when
// we invoke our templated method.
//
// For example, we pass in DN_(vadd_f32) into implement_arg2().
// For ARM compilation, this works as expected, vadd_f32 is used.
// For x64 compilation, the macro converts vadd_f32 to a nullptr
// (so there is no undeclared identifier) and the calling site is safely
// ifdef'ed out in implement_arg2() for non ARM architectures.
//
// DN_(x) replaces x with nullptr for non-ARM arch
// DN64_(x) replaces x with nullptr for non-ARM64 arch
#pragma push_macro("DN_")
#pragma push_macro("DN64_")
#undef DN_
#undef DN64_

#ifdef USE_NEON
#if defined(__aarch64__)
#define DN_(x) x
#define DN64_(x) x
#else
#define DN_(x) x
#define DN64_(x) nullptr
#endif
#else
#define DN_(x) nullptr
#define DN64_(x) nullptr
#endif // USE_NEON

namespace android::audio_utils::intrinsics {

// For static assert(false) we need a template version to avoid early failure.
// See: https://stackoverflow.com/questions/51523965/template-dependent-false
	template <typename T>
	inline constexpr bool dependent_false_v = false;

// Detect if the value is directly addressable as an array.
// This is more advanced than std::is_array and works with neon intrinsics.
	template<typename T>
	concept is_array_like = requires(T a) {
	a[0];  // can index first element
};

template<typename F, typename T>
concept takes_identical_parameter_pair_v = requires(F f, T a) {
f(a, a);
};

/**
 * Applies a functional or a constant to an intrinsic struct.
 *
 * The vapply method has no return value, but can modify an input intrinsic struct
 * through element-wise application of a functional.
 * Compare the behavior with veval which returns a struct result.
 *
 * Using vector terminology:
 *   if f is a constant: v[i] = f;
 *   if f is a void method that takes an element value: f(v[i]);
 *   if f returns an element value but takes no arg: v[i] = f();
 *   if f returns an element value but takes an element value: v[i] = f(v[i]);
 */
template <typename V, typename F>
constexpr void vapply(const F& f, V& v) {
	if constexpr (std::is_same_v<V, float> || std::is_same_v<V, double>) {
		using E = std::decay_t<decltype(v)>;
		if constexpr (std::is_invocable_r_v<void, F, E>) {
			f(v);
		} else if constexpr (std::is_invocable_r_v<E, F, E>) {
			v = f(v);
		} else if constexpr (std::is_invocable_r_v<E, F>) {
			v = f();
		} else /* constexpr */ {
			v = f;
		}
	} else if constexpr (is_array_like<V>) {
		// this vector access within a neon object prevents constexpr.
		using E = std::decay_t<decltype(v[0])>;
#pragma unroll
		for (size_t i = 0; i < sizeof(v) / sizeof(v[0]); ++i) {
			if constexpr (std::is_invocable_r_v<void, F, E>) {
				f(v[i]);
			} else if constexpr (std::is_invocable_r_v<E, F, E>) {
				v[i] = f(v[i]);
			} else if constexpr (std::is_invocable_r_v<E, F>) {
				v[i] = f();
			} else /* constexpr */ {
				v[i] = f;
			}
		}
	} else /* constexpr */ {
		auto& [vv] = v;
		// for constexpr purposes, non-const references can't bind to array elements.
		using VT = decltype(vv);
		// automatically generated from tests/generate_constexpr_constructible.cpp
		if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type
		>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23, v24,
					v25, v26, v27, v28, v29, v30, v31, v32] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
			vapply(f, v24);
			vapply(f, v25);
			vapply(f, v26);
			vapply(f, v27);
			vapply(f, v28);
			vapply(f, v29);
			vapply(f, v30);
			vapply(f, v31);
			vapply(f, v32);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23, v24,
					v25, v26, v27, v28, v29, v30, v31] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
			vapply(f, v24);
			vapply(f, v25);
			vapply(f, v26);
			vapply(f, v27);
			vapply(f, v28);
			vapply(f, v29);
			vapply(f, v30);
			vapply(f, v31);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23, v24,
					v25, v26, v27, v28, v29, v30] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
			vapply(f, v24);
			vapply(f, v25);
			vapply(f, v26);
			vapply(f, v27);
			vapply(f, v28);
			vapply(f, v29);
			vapply(f, v30);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23, v24,
					v25, v26, v27, v28, v29] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
			vapply(f, v24);
			vapply(f, v25);
			vapply(f, v26);
			vapply(f, v27);
			vapply(f, v28);
			vapply(f, v29);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23, v24,
					v25, v26, v27, v28] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
			vapply(f, v24);
			vapply(f, v25);
			vapply(f, v26);
			vapply(f, v27);
			vapply(f, v28);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23, v24,
					v25, v26, v27] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
			vapply(f, v24);
			vapply(f, v25);
			vapply(f, v26);
			vapply(f, v27);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23, v24,
					v25, v26] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
			vapply(f, v24);
			vapply(f, v25);
			vapply(f, v26);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23, v24,
					v25] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
			vapply(f, v24);
			vapply(f, v25);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type
		>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23, v24] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
			vapply(f, v24);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22, v23] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
			vapply(f, v23);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21, v22] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
			vapply(f, v22);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20, v21] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
			vapply(f, v21);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19, v20] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
			vapply(f, v20);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18, v19] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
			vapply(f, v19);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17, v18] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
			vapply(f, v18);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16,
					v17] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
			vapply(f, v17);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type
		>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15, v16] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
			vapply(f, v16);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14, v15] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
			vapply(f, v15);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13, v14] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
			vapply(f, v14);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12, v13] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
			vapply(f, v13);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11, v12] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
			vapply(f, v12);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10, v11] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
			vapply(f, v11);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9, v10] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
			vapply(f, v10);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type,
				any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8,
					v9] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
			vapply(f, v9);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type, any_type
		>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7, v8] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
			vapply(f, v8);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6, v7] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
			vapply(f, v7);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5, v6] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
			vapply(f, v6);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4, v5] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
			vapply(f, v5);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type, any_type>()) {
			auto& [v1, v2, v3, v4] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
			vapply(f, v4);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type, any_type>()) {
			auto& [v1, v2, v3] = vv;
			vapply(f, v1);
			vapply(f, v2);
			vapply(f, v3);
		} else if constexpr (is_braces_constructible<VT,
				any_type, any_type>()) {
			auto& [v1, v2] = vv;
			vapply(f, v1);
			vapply(f, v2);
		} else if constexpr (is_braces_constructible<VT,
				any_type>()) {
			auto& [v1] = vv;
			vapply(f, v1);
		} else {
			static_assert(false, "Currently supports up to 32 members only.");
		}
	}
}

// Type of array embedded in a struct that is usable in the Neon template functions below.
// This type must satisfy std::is_array_v<>.
template<typename T, size_t N>
struct internal_array_t {
	T v[N];
	static constexpr size_t size() { return N; }
	using element_t = T;
	constexpr bool operator==(const internal_array_t<T, N> other) const {
		for (size_t i = 0; i < N; ++i) {
			if (v[i] != other.v[i]) return false;
		}
		return true;
	}
	constexpr internal_array_t<T, N>& operator=(T value) {
		for (size_t i = 0; i < N; ++i) {
			v[i] = value;
		}
		return *this;
	}
	constexpr internal_array_t() = default;
	// explicit: disallow internal_array_t<float, 3> x  = 10.f;
	constexpr explicit internal_array_t(T value) {
		*this = value;
	}
	// allow internal_array_t<float, 3> x  = { 10.f };
	constexpr internal_array_t(std::initializer_list<T> value) {
		size_t i = 0;
		auto vptr = value.begin();
		for (; i < std::min(N, value.size()); ++i) {
			v[i] = *vptr++;
		}
		for (; i < N; ++i) {
			v[i] = {};
		}
	}
};

// assert our structs are trivially copyable so we can use memcpy freely.
static_assert(std::is_trivially_copyable_v<internal_array_t<float, 31>>);
static_assert(std::is_trivially_copyable_v<internal_array_t<double, 31>>);

// Vector convert between type T to type S.
template <typename S, typename T>
constexpr inline S vconvert(const T& in) {
	S out;

	if constexpr (is_array_like<S>) {
		if constexpr (is_array_like<T>) {
#pragma unroll
			// neon intrinsics need sizeof.
			for (size_t i = 0; i < sizeof(in) / sizeof(in[0]); ++i) {
				out[i] = in[i];
			}
		} else { /* constexpr */
			const auto& [inv] = in;
#pragma unroll
			for (size_t i = 0; i < T::size(); ++i) {
				out[i] = inv[i];
			}
		}
	} else { /* constexpr */
		auto& [outv] = out;
		if constexpr (is_array_like<T>) {
#pragma unroll
			// neon intrinsics need sizeof.
			for (size_t i = 0; i < sizeof(in) / sizeof(in[0]); ++i) {
				outv[i] = in[i];
			}
		} else { /* constexpr */
			const auto& [inv] = in;
#pragma unroll
			for (size_t i = 0; i < T::size(); ++i) {
				outv[i] = inv[i];
			}
		}
	}
	return out;
}

/*
  Generalized template functions for the Neon instruction set.

  See here for some general comments from ARM.
  https://developer.arm.com/documentation/dht0004/a/neon-support-in-compilation-tools/automatic-vectorization/floating-point-vectorization

  Notes:
  1) We provide scalar equivalents which are compilable even on non-ARM processors.
  2) We use recursive calls to decompose array types, e.g. float32x4x4_t -> float32x4_t
  3) NEON double SIMD acceleration is only available on 64 bit architectures.
     On Pixel 3XL, NEON double x 2 SIMD is actually slightly slower than the FP unit.

  We create a generic Neon acceleration to be applied to a composite type.

  The type follows the following compositional rules for simplicity:
      1) must be a primitive floating point type.
      2) must be a NEON data type.
      3) must be a struct with one member, either
           a) an array of types 1-3.
           b) a cons-pair struct of 2 possibly different members of types 1-3.

  Examples of possible struct definitions:
  using alternative_2_t = struct { struct { float a; float b; } s; };
  using alternative_9_t = struct { struct { float32x4x2_t a; float b; } s; };
  using alternative_15_t = struct { struct { float32x4x2_t a; struct { float v[7]; } b; } s; };
*/

#ifdef USE_NEON

// This will be specialized later to hold different types.
template<int N>
struct vfloat_struct {};

// Helper method to extract type contained in the struct.
template<int N>
using vfloat_t = typename vfloat_struct<N>::t;

// Create vfloat_extended_t to add helper methods.
//
// It is preferable to use vector_hw_t instead, which
// chooses between vfloat_extended_t and internal_array_t
// based on type support.
//
// Note: Adding helper methods will not affect std::is_trivially_copyable_v.
template<size_t N>
struct vfloat_extended_t : public vfloat_t<N> {
    static constexpr size_t size() { return N; }
    using element_t = float;
    constexpr bool operator==(const vfloat_extended_t<N>& other) const {
        return veq(*this, other);
    }
    vfloat_extended_t<N>& operator=(float value) {
        vapply(value, *this);
        return *this;
    }
    constexpr vfloat_extended_t(const vfloat_extended_t<N>& other) = default;
    vfloat_extended_t() = default;
    // explicit: disallow vfloat_extended_t<float, 3> x  = 10.f;
    explicit vfloat_extended_t(float value) {
        *this = value;
    }
    // allow internal_array_t<float, 3> x  = { 10.f };
    vfloat_extended_t(std::initializer_list<float> value) {
        size_t i = 0;
        auto vptr = value.begin();
        float v[N];
        for (; i < std::min(N, value.size()); ++i) {
            v[i] = *vptr++;
        }
        for (; i < N; ++i) {
            v[i] = {};
        }
        static_assert(sizeof(*this) == sizeof(v));
        static_assert(sizeof(*this) == N * sizeof(float));
        memcpy(this, v, sizeof(*this));
    }
    vfloat_extended_t(internal_array_t<float, N> value) {
        static_assert(sizeof(*this) == sizeof(value.v));
        static_assert(sizeof(*this) == N * sizeof(float));
        memcpy(this, value.v, sizeof(*this));
    }
};

// Create type alias vector_hw_t as platform independent SIMD intrinsic
// type for hardware support.

template<typename F, size_t N>
using vector_hw_t = std::conditional_t<
        std::is_same_v<F, float>, vfloat_extended_t<N>, internal_array_t<F, N>>;

// Recursively define structs containing the NEON intrinsic types for a given vector size.
// intrinsic_utils.h allows structurally recursive type definitions based on
// pairs of types (much like Lisp list cons pairs).
//
// For unpacking these type pairs, we use structured binding, so the naming of the
// element members is irrelevant.  Hence, it is possible to use pragma pack and
// std::pair<> to define these structs as follows:
//
// #pragma pack(push, 1)
// struct vfloat_struct<3> { using t = struct {
//     std::pair<vfloat_t<2>, vfloat_t<1>> p; }; };
// #pragma pack(pop)
//
// But due to ctor requirements, the resulting struct composed of std::pair is
// no longer considered trivially copyable.
//
template<>
struct vfloat_struct<1> { using t = struct { float v[1]; }; };
template<>
struct vfloat_struct<2> { using t = struct { float32x2_t v[1]; }; };
template<>
struct vfloat_struct<3> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<2> a; vfloat_t<1> b; } s; }; };
template<>
struct vfloat_struct<4> { using t = struct { float32x4_t v[1]; }; };
template<>
struct vfloat_struct<5> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<4> a; vfloat_t<1> b; } s; }; };
template<>
struct vfloat_struct<6> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<4> a; vfloat_t<2> b; } s; }; };
template<>
struct vfloat_struct<7> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<4> a; vfloat_t<3> b; } s; }; };
template<>
struct vfloat_struct<8> { using t = float32x4x2_t; };
template<>
struct vfloat_struct<9> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<8> a; vfloat_t<1> b; } s; }; };
template<>
struct vfloat_struct<10> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<8> a; vfloat_t<2> b; } s; }; };
template<>
struct vfloat_struct<11> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<8> a; vfloat_t<3> b; } s; }; };
template<>
struct vfloat_struct<12> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<8> a; vfloat_t<4> b; } s; }; };
template<>
struct vfloat_struct<13> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<8> a; vfloat_t<5> b; } s; }; };
template<>
struct vfloat_struct<14> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<8> a; vfloat_t<6> b; } s; }; };
template<>
struct vfloat_struct<15> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<8> a; vfloat_t<7> b; } s; }; };
template<>
struct vfloat_struct<16> { using t = float32x4x4_t; };
template<>
struct vfloat_struct<17> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<1> b; } s; }; };
template<>
struct vfloat_struct<18> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<2> b; } s; }; };
template<>
struct vfloat_struct<19> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<3> b; } s; }; };
template<>
struct vfloat_struct<20> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<4> b; } s; }; };
template<>
struct vfloat_struct<21> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<5> b; } s; }; };
template<>
struct vfloat_struct<22> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<6> b; } s; }; };
template<>
struct vfloat_struct<23> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<7> b; } s; }; };
template<>
struct vfloat_struct<24> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<8> b; } s; }; };
template<>
struct vfloat_struct<25> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<9> b; } s; }; };
template<>
struct vfloat_struct<26> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<10> b; } s; }; };
template<>
struct vfloat_struct<27> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<11> b; } s; }; };
template<>
struct vfloat_struct<28> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<12> b; } s; }; };
template<>
struct vfloat_struct<29> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<13> b; } s; }; };
template<>
struct vfloat_struct<30> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<14> b; } s; }; };
template<>
struct vfloat_struct<31> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<15> b; } s; }; };
template<>
struct vfloat_struct<32> { using t = struct { struct __attribute__((packed)) {
    vfloat_t<16> a; vfloat_t<16> b; } s; }; };

// assert our structs are trivially copyable so we can use memcpy freely.
static_assert(std::is_trivially_copyable_v<vfloat_struct<31>>);
static_assert(std::is_trivially_copyable_v<vfloat_t<31>>);

#else

// x64 or risc-v, use loop vectorization if no HW type exists.
template<typename F, int N>
using vector_hw_t = internal_array_t<F, N>;

#endif // USE_NEON

/**
 * Returns the first element of the intrinsic struct.
 */
template <typename T>
constexpr auto first_element_of(const T& t) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		return t;
	} else if constexpr (is_array_like<T>) {
		return first_element_of(t[0]);
	} else /* constexpr */ {
		const auto& [tval] = t;  // single-member struct
		if constexpr (std::is_array_v<decltype(tval)>) {
			return first_element_of(tval[0]);
		} else /* constexpr */ {
			const auto& [p1, p2] = tval;
			return first_element_of(p1);
		}
	}
}

/**
 * Evaluate f(v1 [, v2 [, v3]]) and return an intrinsic struct result.
 *
 * The veval method returns the vector result by element-wise
 * evaulating a functional f to one or more intrinsic struct inputs.
 * Compare this method with the single argument vapply,
 * which can modify a single struct argument in-place.
 */
template <typename F, typename V>
constexpr V veval(const F& f, const V& v1) {
	if constexpr (std::is_same_v<V, float> || std::is_same_v<V, double>) {
		return f(v1);
	} else if constexpr (is_array_like<V>) {
		V out;
#pragma unroll
		// neon intrinsics need sizeof.
		for (size_t i = 0; i < sizeof(v1) / sizeof(v1[0]); ++i) {
			out[i] = f(v1[i]);
		}
		return out;
	} else /* constexpr */ {
		V ret;
		auto& [retval] = ret;  // single-member struct
		const auto& [v1val] = v1;
		if constexpr (std::is_array_v<decltype(v1val)>) {
#pragma unroll
			for (size_t i = 0; i < std::size(v1val); ++i) {
				retval[i] = veval(f, v1val[i]);
			}
			return ret;
		} else /* constexpr */ {
			auto& [r1, r2] = retval;
			const auto& [p1, p2] = v1val;
			r1 = veval(f, p1);
			r2 = veval(f, p2);
			return ret;
		}
	}
}

template <typename F, typename V>
constexpr V veval(const F& f, const V& v1, const V& v2) {
	if constexpr (std::is_same_v<V, float> || std::is_same_v<V, double>) {
		return f(v1, v2);
	} else if constexpr (is_array_like<V>) {
		V out;
#pragma unroll
		// neon intrinsics need sizeof.
		for (size_t i = 0; i < sizeof(v1) / sizeof(v1[0]); ++i) {
			out[i] = f(v1[i], v2[i]);
		}
		return out;
	} else /* constexpr */ {
		V ret;
		auto& [retval] = ret;  // single-member struct
		const auto& [v1val] = v1;
		const auto& [v2val] = v2;
		if constexpr (std::is_array_v<decltype(v1val)>) {
#pragma unroll
			for (size_t i = 0; i < std::size(v1val); ++i) {
				retval[i] = veval(f, v1val[i], v2val[i]);
			}
			return ret;
		} else /* constexpr */ {
			auto& [r1, r2] = retval;
			const auto& [p11, p12] = v1val;
			const auto& [p21, p22] = v2val;
			r1 = veval(f, p11, p21);
			r2 = veval(f, p12, p22);
			return ret;
		}
	}
}

template <typename F, typename V>
constexpr V veval(const F& f, const V& v1, const V& v2, const V& v3) {
	if constexpr (std::is_same_v<V, float> || std::is_same_v<V, double>) {
		return f(v1, v2, v3);
	} else if constexpr (is_array_like<V>) {
		V out;
#pragma unroll
		// neon intrinsics need sizeof.
		for (size_t i = 0; i < sizeof(v1) / sizeof(v1[0]); ++i) {
			out[i] = f(v1[i], v2[i], v3[i]);
		}
		return out;
	} else /* constexpr */ {
		V ret;
		auto& [retval] = ret;  // single-member struct
		const auto& [v1val] = v1;
		const auto& [v2val] = v2;
		const auto& [v3val] = v3;
		if constexpr (std::is_array_v<decltype(v1val)>) {
#pragma unroll
			for (size_t i = 0; i < std::size(v1val); ++i) {
				retval[i] = veval(f, v1val[i], v2val[i], v3val[i]);
			}
			return ret;
		} else /* constexpr */ {
			auto& [r1, r2] = retval;
			const auto& [p11, p12] = v1val;
			const auto& [p21, p22] = v2val;
			const auto& [p31, p32] = v3val;
			r1 = veval(f, p11, p21, p31);
			r2 = veval(f, p12, p22, p32);
			return ret;
		}
	}
}

/**
 * Compare two intrinsic structs and return true iff equal.
 *
 * As opposed to memcmp, this handles floating point equality
 * which is different due to signed 0 and NaN, etc.
 */
template<typename T>
inline bool veq(T a, T b) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		return a == b;
	} else if constexpr (is_array_like<T>) {
#pragma unroll
		for (size_t i = 0; i < sizeof(a) / sizeof(a[0]); ++i) {
			if (!veq(a[i], b[i])) return false;
		}
		return true;
	} else /* constexpr */ {
		const auto& [aval] = a;
		const auto& [bval] = b;
		if constexpr (std::is_array_v<decltype(aval)>) {
#pragma unroll
			for (size_t i = 0; i < std::size(aval); ++i) {
				if (!veq(aval[i], bval[i])) return false;
			}
			return true;
		} else /* constexpr */ {
			const auto& [a1, a2] = aval;
			const auto& [b1, b2] = bval;
			return veq(a1, b1) && veq(a2, b2);
		}
	}
}

// --------------------------------------------------------------------

template<typename F, typename FN1, typename FN2, typename FN3, typename T>
inline T implement_arg1(const F& f, const FN1& fn1, const FN2& fn2, const FN3& fn3, T a) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		return f(a);
#ifdef USE_NEON
		} else if constexpr (std::is_same_v<T, float32x2_t>) {
        return fn1(a);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return fn2(a);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return fn3(a);
#endif
#endif // USE_NEON

	} else /* constexpr */ {
		T ret;
		auto& [retval] = ret;  // single-member struct
		const auto& [aval] = a;
		if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
			for (size_t i = 0; i < std::size(aval); ++i) {
				retval[i] = implement_arg1(f, fn1, fn2, fn3, aval[i]);
			}
			return ret;
		} else /* constexpr */ {
			auto& [r1, r2] = retval;
			const auto& [a1, a2] = aval;
			r1 = implement_arg1(f, fn1, fn2, fn3, a1);
			r2 = implement_arg1(f, fn1, fn2, fn3, a2);
			return ret;
		}
	}
}

template<typename F, typename FN1, typename FN2, typename FN3, typename T>
inline auto implement_arg1v(const F& f, const FN1& fn1, const FN2& fn2, const FN3& fn3, T a) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		return a;

#if defined(USE_NEON) && defined(__aarch64__)
		} else if constexpr (std::is_same_v<T, float32x2_t>) {
        return fn1(a);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return fn2(a);
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return fn3(a);
#endif // defined(USE_NEON) && defined(__aarch64__)
	} else if constexpr (is_array_like<T>) {
		using ret_t = std::decay_t<decltype(a[0])>;

		ret_t ret = a[0];
		// array_like is not the same as an array, so we use sizeof here
		// to handle neon instrinsics.
#pragma unroll
		for (size_t i = 1; i < sizeof(a) / sizeof(a[0]); ++i) {
			ret = f(ret, a[i]);
		}
		return ret;
	} else /* constexpr */ {
		const auto &[aval] = a;
		if constexpr (std::is_array_v<decltype(aval)>) {
			using ret_t = std::decay_t<decltype(first_element_of(aval[0]))>;
			ret_t ret = implement_arg1v(f, fn1, fn2, fn3, aval[0]);
#pragma unroll
			for (size_t i = 1; i < std::size(aval); ++i) {
				ret = f(ret, implement_arg1v(f, fn1, fn2, fn3, aval[i]));
			}
			return ret;
		} else /* constexpr */ {
			using ret_t = std::decay_t<decltype(first_element_of(a))>;
			const auto& [a1, a2] = aval;
			ret_t ret = implement_arg1v(f, fn1, fn2, fn3, a1);
			ret = f(ret, implement_arg1v(f, fn1, fn2, fn3, a2));
			return ret;
		}
	}
}

template<typename T, typename F>
inline T vdupn(F f);

/**
 * Invoke vector intrinsic with a vector argument T and a scalar argument S.
 *
 * If the vector intrinsic does not support vector-scalar operation, we dup the scalar
 * argument.
 */
template <typename F, typename T, typename S>
auto invoke_intrinsic_with_dup_as_needed(const F& f, T a, S b) {
	if constexpr (takes_identical_parameter_pair_v<F, T>) {
		return f(a, vdupn<T>(b));
	} else /* constexpr */ {
		return f(a, b);
	}
}

// arg2 with a vector and scalar parameter.
template<typename F, typename FN1, typename FN2, typename FN3, typename T, typename S>
inline auto implement_arg2(const F& f, const FN1& fn1, const FN2& fn2, const FN3& fn3, T a, S b) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		if constexpr (std::is_same_v<S, float> || std::is_same_v<S, double>) {
			return f(a, b);
		} else /* constexpr */ {
			return implement_arg2(f, fn1, fn2, fn3, b, a); // we prefer T to be the vector/struct.
		}
	} else if constexpr (std::is_same_v<S, float> || std::is_same_v<S, double>) {
		// handle the lane variant
#ifdef USE_NEON
		if constexpr (std::is_same_v<T, float32x2_t>) {
            return invoke_intrinsic_with_dup_as_needed(fn1, a, b);
        } else if constexpr (std::is_same_v<T, float32x4_t>) {
            return invoke_intrinsic_with_dup_as_needed(fn2, a, b);
#if defined(__aarch64__)
        } else if constexpr (std::is_same_v<T, float64x2_t>) {
            return invoke_intrinsic_with_dup_as_needed(fn3, a, b);
#endif
        } else
#endif // USE_NEON
		{
			T ret;
			auto &[retval] = ret;  // single-member struct
			const auto &[aval] = a;
			if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
				for (size_t i = 0; i < std::size(aval); ++i) {
					retval[i] = implement_arg2(f, fn1, fn2, fn3, aval[i], b);
				}
				return ret;
			} else /* constexpr */ {
				auto& [r1, r2] = retval;
				const auto& [a1, a2] = aval;
				r1 = implement_arg2(f, fn1, fn2, fn3, a1, b);
				r2 = implement_arg2(f, fn1, fn2, fn3, a2, b);
				return ret;
			}
		}
	} else {
		// Both types T and S are non-primitive and they are not equal.
		static_assert(dependent_false_v<T>);
	}
}

template<typename F, typename FN1, typename FN2, typename FN3, typename T>
inline T implement_arg2(const F& f, const FN1& fn1, const FN2& fn2, const FN3& fn3, T a, T b) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		return f(a, b);

#ifdef USE_NEON
		} else if constexpr (std::is_same_v<T, float32x2_t>) {
        return fn1(a, b);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return fn2(a, b);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return fn3(a, b);
#endif
#endif // USE_NEON

	} else /* constexpr */ {
		T ret;
		auto& [retval] = ret;  // single-member struct
		const auto& [aval] = a;
		const auto& [bval] = b;
		if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
			for (size_t i = 0; i < std::size(aval); ++i) {
				retval[i] = implement_arg2(f, fn1, fn2, fn3, aval[i], bval[i]);
			}
			return ret;
		} else /* constexpr */ {
			auto& [r1, r2] = retval;
			const auto& [a1, a2] = aval;
			const auto& [b1, b2] = bval;
			r1 = implement_arg2(f, fn1, fn2, fn3, a1, b1);
			r2 = implement_arg2(f, fn1, fn2, fn3, a2, b2);
			return ret;
		}
	}
}

template<typename F, typename FN1, typename FN2, typename FN3, typename T, typename S, typename R>
inline auto implement_arg3(
		const F& f, const FN1& fn1, const FN2& fn2, const FN3& fn3, R a, T b, S c) {
	// Arbitrary support is not allowed.
	(void) f;
	(void) fn1;
	(void) fn2;
	(void) fn3;
	(void) a;
	(void) b;
	(void) c;
	static_assert(dependent_false_v<T>);
}

template<typename F, typename FN1, typename FN2, typename FN3, typename T, typename S>
inline auto implement_arg3(
		const F& f, const FN1& fn1, const FN2& fn2, const FN3& fn3, T a, T b, S c) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		if constexpr (std::is_same_v<S, float> || std::is_same_v<S, double>) {
			return f(a, b, c);
		} else {
			static_assert(dependent_false_v<T>);
		}
	} else if constexpr (std::is_same_v<S, float> || std::is_same_v<S, double>) {
		// handle the lane variant
#ifdef USE_NEON
		if constexpr (std::is_same_v<T, float32x2_t>) {
            return fn1(a, b, c);
        } else if constexpr (std::is_same_v<T, float32x4_t>) {
            return fn2(a, b, c);
#if defined(__aarch64__)
        } else if constexpr (std::is_same_v<T, float64x2_t>) {
            return fn3(a, b, c);
#endif
        } else
#endif // USE_NEON
		{
			T ret;
			auto &[retval] = ret;  // single-member struct
			const auto &[aval] = a;
			const auto &[bval] = b;
			if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
				for (size_t i = 0; i < std::size(aval); ++i) {
					retval[i] = implement_arg3(f, fn1, fn2, fn3, aval[i], bval[i], c);
				}
				return ret;
			} else /* constexpr */ {
				auto &[r1, r2] = retval;
				const auto &[a1, a2] = aval;
				const auto &[b1, b2] = bval;
				r1 = implement_arg3(f, fn1, fn2, fn3, a1, b1, c);
				r2 = implement_arg3(f, fn1, fn2, fn3, a2, b2, c);
				return ret;
			}
		}
	} else {
		// Both types T and S are non-primitive and they are not equal.
		static_assert(dependent_false_v<T>);
	}
}

template<typename F, typename FN1, typename FN2, typename FN3, typename T>
inline T implement_arg3(
		const F& f, const FN1& fn1, const FN2& fn2, const FN3& fn3, T a, T b, T c) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		return f(a, b, c);

#ifdef USE_NEON
		} else if constexpr (std::is_same_v<T, float32x2_t>) {
        return fn1(a, b, c);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return fn2(a, b, c);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return fn3(a, b, c);
#endif
#endif // USE_NEON

	} else /* constexpr */ {
		T ret;
		auto& [retval] = ret;  // single-member struct
		const auto& [aval] = a;
		const auto& [bval] = b;
		const auto& [cval] = c;
		if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
			for (size_t i = 0; i < std::size(aval); ++i) {
				retval[i] = implement_arg3(f, fn1, fn2, fn3, aval[i], bval[i], cval[i]);
			}
			return ret;
		} else /* constexpr */ {
			auto& [r1, r2] = retval;
			const auto& [a1, a2] = aval;
			const auto& [b1, b2] = bval;
			const auto& [c1, c2] = cval;
			r1 = implement_arg3(f, fn1, fn2, fn3, a1, b1, c1);
			r2 = implement_arg3(f, fn1, fn2, fn3, a2, b2, c2);
			return ret;
		}
	}
}

// absolute value
template<typename T>
static inline T vabs(T a) {
	return implement_arg1([](const auto& x) { return std::abs(x); },
	                      DN_(vabs_f32), DN_(vabsq_f32), DN64_(vabsq_f64), a);
}

template<typename T>
inline T vadd(T a, T b) {
	return implement_arg2([](const auto& x, const auto& y) { return x + y; },
	                      DN_(vadd_f32), DN_(vaddq_f32), DN64_(vaddq_f64), a, b);
}

// add internally
template<typename T>
inline auto vaddv(const T& a) {
	return implement_arg1v([](const auto& x, const auto& y) { return x + y; },
	                       DN64_(vaddv_f32), DN64_(vaddvq_f32), DN64_(vaddvq_f64), a);
}

// duplicate float into all elements.
template<typename T, typename F>
inline T vdupn(F f) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		return f;

#ifdef USE_NEON
		} else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vdup_n_f32(f);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vdupq_n_f32(f);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vdupq_n_f64(f);
#endif
#endif // USE_NEON

	} else /* constexpr */ {
		T ret;
		auto &[retval] = ret;  // single-member struct
		if constexpr (std::is_array_v<decltype(retval)>) {
#pragma unroll
			for (auto& val : retval) {
				val = vdupn<std::decay_t<decltype(val)>>(f);
			}
			return ret;
		} else /* constexpr */ {
			auto &[r1, r2] = retval;
			using r1_type = std::decay_t<decltype(r1)>;
			using r2_type = std::decay_t<decltype(r2)>;
			r1 = vdupn<r1_type>(f);
			r2 = vdupn<r2_type>(f);
			return ret;
		}
	}
}

// load from float pointer.
template<typename T, typename F>
static inline T vld1(const F *f) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		return *f;

#ifdef USE_NEON
		} else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vld1_f32(f);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vld1q_f32(f);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vld1q_f64(f);
#endif
#endif // USE_NEON

	} else /* constexpr */ {
		T ret;
		auto &[retval] = ret;  // single-member struct
		if constexpr (std::is_array_v<decltype(retval)>) {
			using element_type = std::decay_t<decltype(retval[0])>;
			constexpr size_t subelements = sizeof(element_type) / sizeof(F);
#pragma unroll
			for (size_t i = 0; i < std::size(retval); ++i) {
				retval[i] = vld1<element_type>(f);
				f += subelements;
			}
			return ret;
		} else /* constexpr */ {
			auto &[r1, r2] = retval;
			using r1_type = std::decay_t<decltype(r1)>;
			using r2_type = std::decay_t<decltype(r2)>;
			r1 = vld1<r1_type>(f);
			f += sizeof(r1) / sizeof(F);
			r2 = vld1<r2_type>(f);
			return ret;
		}
	}
}

template<typename T, typename F>
inline auto vmax(T a, F b) {
	return implement_arg2([](const auto& x, const auto& y) { return std::max(x, y); },
	                      DN_(vmax_f32), DN_(vmaxq_f32), DN64_(vmaxq_f64), a, b);
}

template<typename T>
inline T vmax(T a, T b) {
	return implement_arg2([](const auto& x, const auto& y) { return std::max(x, y); },
	                      DN_(vmax_f32), DN_(vmaxq_f32), DN64_(vmaxq_f64), a, b);
}

template<typename T>
inline auto vmaxv(const T& a) {
	return implement_arg1v([](const auto& x, const auto& y) { return std::max(x, y); },
	                       DN64_(vmaxv_f32), DN64_(vmaxvq_f32), DN64_(vmaxvq_f64), a);
}

template<typename T, typename F>
inline auto vmin(T a, F b) {
	return implement_arg2([](const auto& x, const auto& y) { return std::min(x, y); },
	                      DN_(vmin_f32), DN_(vminq_f32), DN64_(vminq_f64), a, b);
}

template<typename T>
inline T vmin(T a, T b) {
	return implement_arg2([](const auto& x, const auto& y) { return std::min(x, y); },
	                      DN_(vmin_f32), DN_(vminq_f32), DN64_(vminq_f64), a, b);
}

template<typename T>
inline auto vminv(const T& a) {
	return implement_arg1v([](const auto& x, const auto& y) { return std::min(x, y); },
	                       DN64_(vminv_f32), DN64_(vminvq_f32), DN64_(vminvq_f64), a);
}

/**
 * Returns c as follows:
 * c_i = a_i * b_i if a and b are the same vector type or
 * c_i = a_i * b if a is a vector and b is scalar or
 * c_i = a * b_i if a is scalar and b is a vector.
 */

// Workaround for missing method.
#if defined(USE_NEON) && defined(__aarch64__)
float64x2_t vmlaq_n_f64(float64x2_t __p0, float64x2_t __p1, float64_t __p2);
#endif

template<typename T, typename F>
static inline T vmla(T a, T b, F c) {
	return implement_arg3([](const auto& x, const auto& y, const auto& z) { return x + y * z; },
	                      DN_(vmla_n_f32), DN_(vmlaq_n_f32), DN64_(vmlaq_n_f64), a, b, c);
}

template<typename T, typename F>
static inline T vmla(T a, F b, T c) {
	return vmla(a, c, b);
}

// fused multiply-add a + b * c
template<typename T>
inline T vmla(const T& a, const T& b, const T& c) {
	return implement_arg3([](const auto& x, const auto& y, const auto& z) { return x + y * z; },
	                      DN_(vmla_f32), DN_(vmlaq_f32), DN64_(vmlaq_f64), a, b, c);
}

/**
 * Returns c as follows:
 * c_i = a_i * b_i if a and b are the same vector type or
 * c_i = a_i * b if a is a vector and b is scalar or
 * c_i = a * b_i if a is scalar and b is a vector.
 */
template<typename T, typename F>
static inline auto vmul(T a, F b) {
	return implement_arg2([](const auto& x, const auto& y) { return x * y; },
	                      DN_(vmul_n_f32), DN_(vmulq_n_f32), DN64_(vmulq_n_f64), a, b);
}

template<typename T>
inline T vmul(T a, T b) {
	return implement_arg2([](const auto& x, const auto& y) { return x * y; },
	                      DN_(vmul_f32), DN_(vmulq_f32), DN64_(vmulq_f64), a, b);
}

// negate
template<typename T>
inline T vneg(T a) {
	return implement_arg1([](const auto& x) { return -x; },
	                      DN_(vneg_f32), DN_(vnegq_f32), DN64_(vnegq_f64), a);
}

// store to float pointer.
template<typename T, typename F>
static inline void vst1(F *f, T a) {
	if constexpr (std::is_same_v<T, float> || std::is_same_v<T, double>) {
		*f = a;

#ifdef USE_NEON
		} else if constexpr (std::is_same_v<T, float32x2_t>) {
        return vst1_f32(f, a);
    } else if constexpr (std::is_same_v<T, float32x4_t>) {
        return vst1q_f32(f, a);
#if defined(__aarch64__)
    } else if constexpr (std::is_same_v<T, float64x2_t>) {
        return vst1q_f64(f, a);
#endif
#endif // USE_NEON

	} else /* constexpr */ {
		const auto &[aval] = a;
		if constexpr (std::is_array_v<decltype(aval)>) {
			constexpr size_t subelements = sizeof(std::decay_t<decltype(aval[0])>) / sizeof(F);
#pragma unroll
			for (size_t i = 0; i < std::size(aval); ++i) {
				vst1(f, aval[i]);
				f += subelements;
			}
		} else /* constexpr */ {
			const auto &[a1, a2] = aval;
			vst1(f, a1);
			f += sizeof(std::decay_t<decltype(a1)>) / sizeof(F);
			vst1(f, a2);
		}
	}
}

// subtract a - b
template<typename T>
inline T vsub(T a, T b) {
	return implement_arg2([](const auto& x, const auto& y) { return x - y; },
	                      DN_(vsub_f32), DN_(vsubq_f32), DN64_(vsubq_f64), a, b);
}

// Derived methods

/**
 * Clamps a value between the specified min and max.
 */
template<typename T, typename S, typename R>
static inline T vclamp(const T& value, const S& min_value, const R& max_value) {
	return vmin(vmax(value, min_value), max_value);
}

} // namespace android::audio_utils::intrinsics

#pragma pop_macro("DN64_")
#pragma pop_macro("DN_")
#pragma pop_macro("USE_NEON")

#endif // !ANDROID_AUDIO_UTILS_INTRINSIC_UTILS_H