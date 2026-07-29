package com.sdk.growthbook.ext

import com.sdk.growthbook.GrowthBookSDK
import kotlin.properties.ReadOnlyProperty

/**
 * A read-only Kotlin property delegate backed by the boolean state of feature [key].
 *
 * The flag is re-evaluated on **every** property read, so a delegated property always
 * reflects the current configuration — a freshly fetched/refreshed payload is picked up
 * without re-declaring the property:
 *
 * ```kotlin
 * val newHome by sdk.featureFlag("new-home")   // Boolean
 *
 * if (newHome) renderNewHome() else renderOldHome()
 * ```
 *
 * Pure sugar over [isOn]: the delegate adds no evaluation logic of its own. For an
 * explicit fail-open/fail-closed policy on unknown features use the [FallbackStrategy]
 * overload; for a typed value carrying its own default use the [Flag] overload.
 *
 * Note the [Flag] overload reads the raw boolean *value* (like [value]), whereas this one
 * uses [isOn]'s truthiness. For a feature whose stored value is not a boolean the two can
 * differ — e.g. a string-valued feature is "on" here but reads as `null`/default through a
 * `Flag<Boolean>`.
 *
 * @param key unique feature identifier
 */
fun GrowthBookSDK.featureFlag(key: String): ReadOnlyProperty<Any?, Boolean> {
    val sdk = this
    return ReadOnlyProperty {_, _ -> sdk.isOn(key)}
}

/**
 * A read-only property delegate backed by the boolean state of feature [key], applying
 * [fallbackStrategy] only when the feature is unknown (missing from the loaded config /
 * empty cache). See [isEnabled] for the exact semantics — a known feature always returns
 * its real evaluated value regardless of the strategy.
 *
 * ```kotlin
 * val betaCheckout by sdk.featureFlag("beta-checkout", FallbackStrategy.FAIL_CLOSED)
 * ```
 *
 * @param key unique feature identifier
 * @param fallbackStrategy strategy for the unknown-feature case
 */
fun GrowthBookSDK.featureFlag(key: String, fallbackStrategy: FallbackStrategy): ReadOnlyProperty<Any?, Boolean> {
    val sdk = this
    return ReadOnlyProperty {_, _ -> sdk.isEnabled(key, fallbackStrategy)}
}

/**
 * A read-only property delegate backed by the typed [flag], returning its value on every
 * read or the flag's [Flag.default] when the feature is missing or its stored value cannot
 * be read as [T].
 *
 * Declaring the flag once keeps its key, type and default together and removes magic
 * strings from call sites:
 *
 * ```kotlin
 * val MAX_ITEMS = Flag("max-items", default = 10)   // Flag<Int>
 *
 * val maxItems by sdk.featureFlag(MAX_ITEMS)         // Int, falls back to 10
 * ```
 *
 * Mirrors [value]: supported [Flag] value types are [Boolean], [String], [Int], [Long],
 * [Float], [Double]. The type is resolved from the runtime type of [Flag.default]; any
 * other type throws — decode custom or `@Serializable` types via the
 * `GrowthBookKotlinxSerialization` module instead.
 *
 * @param flag typed feature flag bundling key, value type and per-feature default
 * @throws IllegalArgumentException on first read if the flag's value type is unsupported
 */
fun <T: Any> GrowthBookSDK.featureFlag(flag: Flag<T>): ReadOnlyProperty<Any?, T> {
    val sdk = this
    return ReadOnlyProperty { _, _ -> sdk.resolveFlag(flag) }
}