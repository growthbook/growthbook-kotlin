package com.sdk.growthbook.ext

import com.sdk.growthbook.GrowthBookSDK

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
 * @throws IllegalArgumentException if [T] is not one of the supported types
 */
inline fun <reified T> GrowthBookSDK.value(flag: Flag<T>): T {
    val raw: Any? = when (T::class) {
        Boolean::class -> getBooleanOrNull(flag.key)
        String::class -> getStringOrNull(flag.key)
        Int::class -> getIntOrNull(flag.key)
        Long::class -> getLongOrNull(flag.key)
        Float::class -> getFloatOrNull(flag.key)
        Double::class -> getDoubleOrNull(flag.key)
        else -> throw IllegalArgumentException(
            "Unsupported Flag type ${T::class.simpleName}; " +
                "decode custom types via GrowthBookKotlinxSerialization"
        )
    }
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
fun GrowthBookSDK.isOn(flag: Flag<Boolean>): Boolean = value(flag)
