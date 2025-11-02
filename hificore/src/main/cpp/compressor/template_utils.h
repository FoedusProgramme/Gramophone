/*
 * Copyright 2024 The Android Open Source Project
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

#pragma once

#ifdef __cplusplus

#include <optional>
#include <string>
#include <tuple>
#include <type_traits>
#include <utility>

namespace android::audio_utils {

// Determine if a type is a specialization of a templated type
// Example: is_specialization_v<T, std::vector>
	template <typename Test, template <typename...> class Ref>
	struct is_specialization : std::false_type {};

	template <template <typename...> class Ref, typename... Args>
	struct is_specialization<Ref<Args...>, Ref> : std::true_type {};

	template <typename Test, template <typename...> class Ref>
	inline constexpr bool is_specialization_v = is_specialization<Test, Ref>::value;

// For static assert(false) we need a template version to avoid early failure.
	template <typename T>
	inline constexpr bool dependent_false_v = false;

// Determine the number of arguments required for structured binding.
// See the discussion here and follow the links:
// https://isocpp.org/blog/2016/08/cpp17-structured-bindings-convert-struct-to-a-tuple-simple-reflection
// Example: is_braces_constructible<T, any_type>()
	struct any_type {
		template <class T>
		constexpr operator T();  // non explicit
	};

	template <typename T, typename... TArgs>
	decltype(void(T{std::declval<TArgs>()...}), std::true_type{})
	test_is_braces_constructible(int);

	template <typename, typename...>
	std::false_type test_is_braces_constructible(...);

	template <typename T, typename... TArgs>
	using is_braces_constructible =
			decltype(test_is_braces_constructible<T, TArgs...>(0));

	template <typename T, typename... Args>
	constexpr bool is_braces_constructible_v =
			is_braces_constructible<T, Args...>::value;

// Define a concept to check if a class has a member type `Tag` and `getTag()` method
	template <typename T>
	concept has_tag_and_get_tag = requires(T t) {
{ t.getTag() } -> std::same_as<typename T::Tag>;
};

template <typename T>
inline constexpr bool has_tag_and_get_tag_v = has_tag_and_get_tag<T>;

/**
 * Concept to identify primitive types, includes fundamental types, enums, and
 * std::string.
 */
template <typename T>
concept PrimitiveType = std::is_arithmetic_v<T> || std::is_enum_v<T> ||
                        std::is_same_v<T, std::string>;

// Helper to access elements by runtime index
template <typename Tuple, typename Func, size_t... Is>
void op_tuple_elements_by_index(Tuple&& tuple, size_t index, Func&& func,
                                std::index_sequence<Is...>) {
	((index == Is ? func(std::get<Is>(tuple)) : void()), ...);
}

template <typename Tuple, typename Func>
void op_tuple_elements(Tuple&& tuple, size_t index, Func&& func) {
	constexpr size_t tuple_size = std::tuple_size_v<std::decay_t<Tuple>>;
	op_tuple_elements_by_index(std::forward<Tuple>(tuple), index,
	                           std::forward<Func>(func),
	                           std::make_index_sequence<tuple_size>{});
}

/**
 * The maximum structure members supported in the structure.
 * If this utility is used for a structure with more than `N` members, the
 * compiler will fail. In that case, `structure_to_tuple` must be extended.
 *
 */
constexpr size_t kMaxStructMember = 20;

/**
 * @brief Converts a structure to a tuple.
 *
 * This function uses structured bindings to decompose the input structure `t`
 * into individual elements, and then returns a `std::tuple` containing those
 * elements.
 *
 * Example:
 * ```cpp
 * struct Point3D {
 *     int x;
 *     int y;
 *     int z;
 * };
 * Point3D point{1, 2, 3};
 * auto tuple = structure_to_tuple(point);  // tuple will be std::make_tuple(1,
 * 2, 3)
 * ```
 *
 * @tparam T The type of the structure to be converted.
 * @param t The structure to be converted to a tuple.
 * @return A `std::tuple` containing all members of the input structure.
 *
 * @note The maximum number of members supported in a structure is
 * `kMaxStructMember`. If the input structure has more than `kMaxStructMember`
 * members, a compile-time error will occur.
 */
template <typename T>
auto structure_to_tuple(const T& t) {
	if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15,
				t16, t17, t18, t19, t20] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12,
		                       t13, t14, t15, t16, t17, t18, t19, t20);
	} else if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15,
				t16, t17, t18, t19] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12,
		                       t13, t14, t15, t16, t17, t18, t19);
	} else if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15,
				t16, t17, t18] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12,
		                       t13, t14, t15, t16, t17, t18);
	} else if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15,
				t16, t17] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12,
		                       t13, t14, t15, t16, t17);
	} else if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15,
				t16] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12,
		                       t13, t14, t15, t16);
	} else if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type,
			any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14, t15] =
				t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12,
		                       t13, t14, t15);
	} else if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13, t14] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12,
		                       t13, t14);
	} else if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12,
		                       t13);
	} else if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type, any_type,
			any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12);
	} else if constexpr (is_braces_constructible<T, any_type, any_type, any_type,
			any_type, any_type, any_type,
			any_type, any_type, any_type,
			any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11);
	} else if constexpr (is_braces_constructible<T, any_type, any_type, any_type,
			any_type, any_type, any_type,
			any_type, any_type, any_type,
			any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9, t10] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10);
	} else if constexpr (is_braces_constructible<
			T, any_type, any_type, any_type, any_type, any_type,
			any_type, any_type, any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8, t9] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8, t9);
	} else if constexpr (is_braces_constructible<T, any_type, any_type, any_type,
			any_type, any_type, any_type,
			any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7, t8] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7, t8);
	} else if constexpr (is_braces_constructible<T, any_type, any_type, any_type,
			any_type, any_type, any_type,
			any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6, t7] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6, t7);
	} else if constexpr (is_braces_constructible<T, any_type, any_type, any_type,
			any_type, any_type,
			any_type>()) {
		auto&& [t1, t2, t3, t4, t5, t6] = t;
		return std::make_tuple(t1, t2, t3, t4, t5, t6);
	} else if constexpr (is_braces_constructible<T, any_type, any_type, any_type,
			any_type, any_type>()) {
		auto&& [t1, t2, t3, t4, t5] = t;
		return std::make_tuple(t1, t2, t3, t4, t5);
	} else if constexpr (is_braces_constructible<T, any_type, any_type, any_type,
			any_type>()) {
		auto&& [t1, t2, t3, t4] = t;
		return std::make_tuple(t1, t2, t3, t4);
	} else if constexpr (is_braces_constructible<T, any_type, any_type,
			any_type>()) {
		auto&& [t1, t2, t3] = t;
		return std::make_tuple(t1, t2, t3);
	} else if constexpr (is_braces_constructible<T, any_type, any_type>()) {
		auto&& [t1, t2] = t;
		return std::make_tuple(t1, t2);
	} else if constexpr (is_braces_constructible<T, any_type>()) {
		auto&& [t1] = t;
		return std::make_tuple(t1);
	} else {
		static_assert(false, "Currently supports up to 20 members only.");
	}
}

/**
 * @brief Applies a ternary operation element-wise to structs of type T and
 * transform the results into a new struct of the same type.
 *
 * This function template takes three structs of the same type `T` and an
 * ternary operation `op`, then applies `op` element-wise to the corresponding
 * members of the input structs. If all the results of `op` are valid (i.e., not
 * `std::nullopt`), it constructs a new struct `T` with these results and
 * returns it as `std::optional<T>`. Otherwise, it returns `std::nullopt`.
 *
 * @example
 * struct Point3D {
 *   int x;
 *   int y;
 *   int z;
 * };
 *
 * std::vector<int> v1{10, 2, 1};
 * std::vector<int> v2{4, 5, 8};
 * std::vector<int> v3{1, 8, 6};
 *
 * const auto addOp3 = [](const auto& a, const auto& b, const auto& c) {
 *   return {a + b + c};
 * };
 * auto result = op_aggregate(addOp3, v1, v2, v3);
 * The value of `result` will be std::vector<int>({15, 15, 15})
 *
 * @tparam TernaryOp The ternary operation apply to parameters `a`, `b`, and
 * `c`.
 * @tparam T The type of `a`, `b`, and `c`.
 * @param op The ternary operation to apply.
 * @param a The first input struct.
 * @param b The second input struct.
 * @param c The third input struct.
 * @return A new struct with the aggregated results if all results are valid,
 *         `std::nullopt` otherwise.
 */
template <typename TernaryOp, typename T, size_t... Is>
std::optional<T> op_aggregate_helper(TernaryOp op, const T& a, const T& b,
                                     const T& c, std::index_sequence<Is...>) {
	const auto aTuple = structure_to_tuple<T>(a);
	const auto bTuple = structure_to_tuple<T>(b);
	const auto cTuple = structure_to_tuple<T>(c);

	T result;
	auto resultTuple = structure_to_tuple<T>(result);

	bool success = true;
	(
			[&]() {
				if (!success) return;  // stop op with any previous error
				auto val = op(std::get<Is>(aTuple), std::get<Is>(bTuple),
				              std::get<Is>(cTuple));
				if (!val) {
					success = false;
					return;
				}
				std::get<Is>(resultTuple) = *val;
			}(),
			...);

	if (!success) {
		return std::nullopt;
	}

	return std::apply([](auto&&... args) { return T{args...}; }, resultTuple);
}

template <typename TernaryOp, typename T>
std::optional<T> op_aggregate(TernaryOp op, const T& a, const T& b,
                              const T& c) {
	constexpr size_t tuple_size =
			std::tuple_size_v<std::decay_t<decltype(structure_to_tuple(a))>>;
	return op_aggregate_helper<TernaryOp, T>(
			op, a, b, c, std::make_index_sequence<tuple_size>{});
}

/**
 * @brief Applies a binary operation element-wise to structs of type T and
 * transform the results into a new struct of the same type.
 *
 * This function template takes three structs of the same type `T` and an
 * operation `op`, and applies `op` element-wise to the corresponding members of
 * the input structs. If all the results of `op` are valid (i.e., not
 * `std::nullopt`), it constructs a new struct `T` with these results and
 * returns it as `std::optional<T>`. Otherwise, it returns `std::nullopt`.
 *
 * @example
 * struct Point3D {
 *   int x;
 *   int y;
 *   int z;
 * };
 *
 * std::vector<int> v1{10, 2, 1};
 * std::vector<int> v2{4, 5, 8};
 * const auto addOp2 = [](const auto& a, const auto& b) {
 *   return {a + b};
 * };
 * auto result = op_aggregate(addOp2, v1, v2);
 * The value of `result` will be std::vector<int>({14, 7, 9})
 *
 * @tparam BinaryOp The binary operation to apply to parameters `a` and `b`.
 * @tparam T The type of `a` and `b`.
 * @param op The ternary operation to apply.
 * @param a The first input struct.
 * @param b The second input struct.
 * @return A new struct with the aggregated results if all results are valid,
 *         `std::nullopt` otherwise.
 */
template <typename BinaryOp, typename T, size_t... Is>
std::optional<T> op_aggregate_helper(BinaryOp op, const T& a, const T& b,
                                     std::index_sequence<Is...>) {
	const auto aTuple = structure_to_tuple<T>(a);
	const auto bTuple = structure_to_tuple<T>(b);

	T result;
	auto resultTuple = structure_to_tuple<T>(result);

	bool success = true;
	(
			[&]() {
				if (!success) return;  // stop op with any previous error
				auto val = op(std::get<Is>(aTuple), std::get<Is>(bTuple));
				if (!val) {
					success = false;
					return;
				}
				std::get<Is>(resultTuple) = *val;
			}(),
			...);

	if (!success) {
		return std::nullopt;
	}

	return std::apply([](auto&&... args) { return T{args...}; }, resultTuple);
}

template <typename BinaryOp, typename T>
std::optional<T> op_aggregate(BinaryOp op, const T& a, const T& b) {
	constexpr size_t tuple_size =
			std::tuple_size_v<std::decay_t<decltype(structure_to_tuple(a))>>;
	return op_aggregate_helper<BinaryOp, T>(
			op, a, b, std::make_index_sequence<tuple_size>{});
}

}  // namespace android::audio_utils

#endif  // __cplusplus
