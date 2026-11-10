package com.sdk.growthbook.ext

import com.sdk.growthbook.IGrowthBookSDK
import com.sdk.growthbook.suspendFeatureValue

/**
 * A typed, self-describing feature flag: bundles the feature [key], its value
 * type [T], and a per-feature [default] returned when the feature is absent or
 * its value cannot be read as [T].
 *
 * Declaring flags in one place removes magic strings from business code and keeps
 * the key, type and default together:
 *
 * ```kotlin
 * object Flags {
 *     val DARK_MODE = Flag("dark-mode", default = false)  // Flag<Boolean>
 *     val MAX_ITEMS = Flag("max-items", default = 10)     // Flag<Int>
 * }
 *
 * val items = sdk.value(Flags.MAX_ITEMS)   // Int, falls back to 10
 * ```
 *
 * @param key unique feature identifier
 * @param default value returned by [value] when the feature is missing or its
 *   value is not a [T]
 */
data class Flag<out T>(val key: String, val default: T)

/**
 * Returns the value of [flag] typed as [T], or its [Flag.default] when the
 * feature is missing or its stored value cannot be read as [T].
 *
 * Delegates to the primitive `getXOrNull` helpers rather than reading the value
 * directly, so numeric flags are robust to how the number was stored — a value
 * serialized as `Float` is still read correctly by a `Flag<Int>`.
 *
 * Supported types: [Boolean], [String], [Int], [Long], [Float], [Double]. Any
 * other type throws — decode custom or `@Serializable` types via the
 * `GrowthBookKotlinxSerialization` module instead.
 *
 * @throws IllegalArgumentException if the flag's value type is not supported
 */
fun <T : Any> IGrowthBookSDK.value(flag: Flag<T>): T = resolveFlag(flag)

/**
 * Shared resolution behind [value] and the `featureFlag(Flag)` property delegate, so both
 * dispatch identically. Reads the feature keyed on the runtime type of [Flag.default] and
 * falls back to [Flag.default] when the feature is missing or its stored value cannot be
 * read as that type.
 *
 * Supported types: [Boolean], [String], [Int], [Long], [Float], [Double].
 *
 * @throws IllegalArgumentException if the flag's value type is not supported
 */
internal fun <T : Any> IGrowthBookSDK.resolveFlag(flag: Flag<T>): T {
    val raw: Any? = when (flag.default) {
        is Boolean -> getBooleanOrNull(flag.key)
        is String -> getStringOrNull(flag.key)
        is Int -> getIntOrNull(flag.key)
        is Long -> getLongOrNull(flag.key)
        is Float -> getFloatOrNull(flag.key)
        is Double -> getDoubleOrNull(flag.key)
        else -> throw IllegalArgumentException(
            "Unsupported Flag type ${flag.default::class.simpleName}; " +
                "decode custom types via GrowthBookKotlinxSerialization"
        )
    }
    @Suppress("UNCHECKED_CAST")
    return (raw as? T) ?: flag.default
}

/**
 * Awaiting counterpart of [value]: suspends until feature definitions are loaded (see
 * `SuspendExtensions.kt`) and only then resolves [flag], so a first read at startup returns the
 * real value instead of [Flag.default].
 *
 * Supported types: [Boolean], [String], [Int], [Long], [Float], [Double].
 *
 * @throws IllegalArgumentException if the flag's value type is not supported
 */
suspend fun <T : Any> IGrowthBookSDK.await(flag: Flag<T>): T = resolveFlagSuspending(flag)

/**
 * Suspending twin of [resolveFlag], kept adjacent to it on purpose: the two dispatch tables must
 * stay in step, and Kotlin cannot abstract over suspend-ness, so one function cannot serve both.
 * The alternative — awaiting once and then delegating to [resolveFlag] — would evaluate the feature
 * twice and so report it twice through the feature-usage callback and plugins.
 *
 * @throws IllegalArgumentException if the flag's value type is not supported
 */
internal suspend fun <T : Any> IGrowthBookSDK.resolveFlagSuspending(flag: Flag<T>): T {
    val raw: Any? = when (flag.default) {
        is Boolean -> suspendFeatureValue<Boolean>(flag.key)
        is String -> suspendFeatureValue<String>(flag.key)
        is Int -> suspendFeatureValue<Number>(flag.key)?.toInt()
        is Long -> suspendFeatureValue<Number>(flag.key)?.toLong()
        is Float -> suspendFeatureValue<Number>(flag.key)?.toFloat()
        is Double -> suspendFeatureValue<Number>(flag.key)?.toDouble()
        else -> throw IllegalArgumentException(
            "Unsupported Flag type ${flag.default::class.simpleName}; " +
                "decode custom types via GrowthBookKotlinxSerialization"
        )
    }
    @Suppress("UNCHECKED_CAST")
    return (raw as? T) ?: flag.default
}

/**
 * Convenience for boolean flags: returns the value of [flag], or its
 * [Flag.default] when the feature is missing or its value is not a boolean.
 * Equivalent to `value(flag)`.
 *
 * For ad-hoc access by string id with an explicit fail-open/fail-closed policy,
 * use `isEnabled(id, FallbackStrategy)` instead. Note the semantics differ: that
 * strategy applies only to an unknown feature, whereas this flag's default also
 * covers a present-but-non-boolean value.
 */
fun IGrowthBookSDK.isOn(flag: Flag<Boolean>): Boolean = value(flag)
