package com.sdk.growthbook.ext

import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBJson

/**
 * Returns whether the feature [id] is enabled (on).
 *
 * Alias for [GrowthBookSDK.isOn] using the more conventional `isEnabled` naming.
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.isEnabled(id: String): Boolean =
    isOn(id)

/**
 * Returns whether the feature [id] is enabled (on), applying [fallback] only when the feature
 * is unknown — i.e. genuinely absent from the loaded configuration.
 *
 * Unlike [isOn], this distinguishes "feature unknown" from "feature known but off":
 * a known feature always returns its real evaluated value, and [fallback] decides only the unknown case.
 *
 * **The fallback also covers the startup window.** Feature definitions are fetched
 * asynchronously, so until the first payload (or cached payload) is applied *every*
 * feature is unknown — [FallbackStrategy.FAIL_OPEN] then reports all of them as enabled,
 * permanently so if the fetch fails and no cache exists. Use
 * [com.sdk.growthbook.GrowthBookSDK.suspendFeature] (or seed
 * `GBSDKBuilder.setInitialFeatures`) when a flag must not be read before the SDK is ready.
 *
 * This is the ad-hoc, string-id counterpart to the declared-flag style: for a typed
 * flag carrying its own per-feature default see [Flag] with [value]/[isOn]. Note the
 * semantics differ — [fallback] fires only for an unknown feature, whereas a flag's
 * default also covers a present-but-wrong-typed value.
 *
 * @param id unique feature identifier
 * @param fallback strategy for the unknown-feature case
 */
fun GrowthBookSDK.isEnabled(id: String, fallback: FallbackStrategy): Boolean {
    val result = feature(id)
    // `unknownFeature` is also what the evaluator reports when evaluating a *loaded* feature
    // threw (malformed condition, prerequisite/sticky-bucket failure), so the source alone
    // cannot decide this: consulting the loaded definitions keeps an evaluation error on the
    // real evaluated value (off) instead of letting FAIL_OPEN flip a kill switch on.
    val known = result.source != GBFeatureSource.unknownFeature || getFeatures().containsKey(id)
    return if (known) {
        result.on
    } else {
        when (fallback) {
            FallbackStrategy.FAIL_OPEN -> true
            FallbackStrategy.FAIL_CLOSED -> false
        }
    }
}

/**
 * Returns whether the feature [id] is disabled — the negation of [isEnabled].
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.isDisabled(id: String): Boolean =
    !isOn(id)

/**
 * Returns whether the feature [id] actually exists in the loaded configuration.
 *
 * Useful for fallback strategies: it distinguishes "feature missing" from
 * "feature present but evaluated to a falsy/empty value" — and, unlike a bare
 * `source != unknownFeature` check, from "feature present but its evaluation threw",
 * which the evaluator also reports as [GBFeatureSource.unknownFeature].
 *
 * Note that a feature is unknown until the first payload is fetched or restored from
 * cache; see [isEnabled] for the startup-window caveat.
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.isFeatureKnown(id: String): Boolean =
    // Checked first so a loaded feature short-circuits without evaluating it — evaluation
    // is observable (feature-usage callback, experiment tracking) and merely asking whether
    // a feature exists should not trigger it.
    getFeatures().containsKey(id) || feature(id).source != GBFeatureSource.unknownFeature

/**
 * Returns the [String] value of the feature [id],
 * or `null` when the feature is missing or its value is not a String.
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.getStringOrNull(id: String): String? =
    featureValue<String>(id)

/**
 * Returns the [String] value of the feature [id], or the result of [default]
 * when the feature is missing or its value is not a String.
 *
 * [default] is evaluated lazily — only when no usable value is present.
 *
 * @param id unique feature identifier
 * @param default lazily-computed fallback value
 */
inline fun GrowthBookSDK.getStringOrElse(id: String, default: () -> String): String =
    getStringOrNull(id) ?: default()

/**
 * Returns the [String] value of the feature [id],
 * or [default] when the feature is missing or its value is not a String.
 *
 * @param id unique feature identifier
 * @param default value returned when no usable String is present
 */
fun GrowthBookSDK.getString(id: String, default: String): String =
    getStringOrNull(id) ?: default

/**
 * Returns the [Boolean] value of the feature [id],
 * or `null` when the feature is missing or its value is not a Boolean.
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.getBooleanOrNull(id: String): Boolean? =
    featureValue<Boolean>(id)

/**
 * Returns the [Boolean] value of the feature [id],
 * or [default] when the feature is missing or its value is not a Boolean.
 *
 * @param id unique feature identifier
 * @param default value returned when no usable Boolean is present
 */
fun GrowthBookSDK.getBoolean(id: String, default: Boolean): Boolean =
    getBooleanOrNull(id) ?: default

/**
 * Returns the [Boolean] value of the feature [id], or the result of [default]
 * when the feature is missing or its value is not a Boolean.
 *
 * [default] is evaluated lazily — only when no usable value is present.
 *
 * @param id unique feature identifier
 * @param default lazily-computed fallback value
 */
fun GrowthBookSDK.getBooleanOrElse(id: String, default: () -> Boolean): Boolean =
    getBooleanOrNull(id) ?: default()

/**
 * Returns the [Int] value of the feature [id],
 * or `null` when the feature is missing or its value is not numeric.
 *
 * The value is read as a [Number] and converted via [Number.toInt], so it is
 * robust to how the number was stored — JSON numbers deserialize to
 * Int/Long/Float/Double depending on their literal form.
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.getIntOrNull(id: String): Int? =
    featureValue<Number>(id)?.toInt()

/**
 * Returns the [Int] value of the feature [id],
 * or [default] when the feature is missing or its value is not numeric.
 *
 * See [getIntOrNull] for conversion details.
 *
 * @param id unique feature identifier
 * @param default value returned when no usable number is present
 */
fun GrowthBookSDK.getInt(id: String, default: Int): Int =
    getIntOrNull(id) ?: default

/**
 * Returns the [Int] value of the feature [id], or the result of [default]
 * when the feature is missing or its value is not numeric.
 *
 * [default] is evaluated lazily — only when no usable value is present.
 * See [getIntOrNull] for conversion details.
 *
 * @param id unique feature identifier
 * @param default lazily-computed fallback value
 */
fun GrowthBookSDK.getIntOrElse(id: String, default: () -> Int): Int =
    getIntOrNull(id) ?: default()

/**
 * Returns the [Long] value of the feature [id],
 * or `null` when the feature is missing or its value is not numeric.
 *
 * The value is read as a [Number] and converted via [Number.toLong], so it is
 * robust to how the number was stored — JSON numbers deserialize to
 * Int/Long/Float/Double depending on their literal form.
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.getLongOrNull(id: String): Long? =
    featureValue<Number>(id)?.toLong()

/**
 * Returns the [Long] value of the feature [id],
 * or [default] when the feature is missing or its value is not numeric.
 *
 * See [getLongOrNull] for conversion details.
 *
 * @param id unique feature identifier
 * @param default value returned when no usable number is present
 */
fun GrowthBookSDK.getLong(id: String, default: Long): Long =
    getLongOrNull(id) ?: default

/**
 * Returns the [Long] value of the feature [id], or the result of [default]
 * when the feature is missing or its value is not numeric.
 *
 * [default] is evaluated lazily — only when no usable value is present.
 * See [getLongOrNull] for conversion details.
 *
 * @param id unique feature identifier
 * @param default lazily-computed fallback value
 */
fun GrowthBookSDK.getLongOrElse(id: String, default: () -> Long): Long =
    getLongOrNull(id) ?: default()

/**
 * Returns the [Float] value of the feature [id],
 * or `null` when the feature is missing or its value is not numeric.
 *
 * The value is read as a [Number] and converted via [Number.toFloat], so it is
 * robust to how the number was stored — JSON numbers deserialize to
 * Int/Long/Float/Double depending on their literal form.
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.getFloatOrNull(id: String): Float? =
    featureValue<Number>(id)?.toFloat()

/**
 * Returns the [Float] value of the feature [id],
 * or [default] when the feature is missing or its value is not numeric.
 *
 * See [getFloatOrNull] for conversion details.
 *
 * @param id unique feature identifier
 * @param default value returned when no usable number is present
 */
fun GrowthBookSDK.getFloat(id: String, default: Float): Float =
    getFloatOrNull(id) ?: default

/**
 * Returns the [Float] value of the feature [id], or the result of [default]
 * when the feature is missing or its value is not numeric.
 *
 * [default] is evaluated lazily — only when no usable value is present.
 * See [getFloatOrNull] for conversion details.
 *
 * @param id unique feature identifier
 * @param default lazily-computed fallback value
 */
fun GrowthBookSDK.getFloatOrElse(id: String, default: () -> Float): Float =
    getFloatOrNull(id) ?: default()

/**
 * Returns the [Double] value of the feature [id],
 * or `null` when the feature is missing or its value is not numeric.
 *
 * The value is read as a [Number] and converted via [Number.toDouble], so it is
 * robust to how the number was stored — JSON numbers deserialize to
 * Int/Long/Float/Double depending on their literal form.
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.getDoubleOrNull(id: String): Double? =
    featureValue<Number>(id)?.toDouble()

/**
 * Returns the [Double] value of the feature [id],
 * or [default] when the feature is missing or its value is not numeric.
 *
 * See [getDoubleOrNull] for conversion details.
 *
 * @param id unique feature identifier
 * @param default value returned when no usable number is present
 */
fun GrowthBookSDK.getDouble(id: String, default: Double): Double =
    getDoubleOrNull(id) ?: default

/**
 * Returns the [Double] value of the feature [id], or the result of [default]
 * when the feature is missing or its value is not numeric.
 *
 * [default] is evaluated lazily — only when no usable value is present.
 * See [getDoubleOrNull] for conversion details.
 *
 * @param id unique feature identifier
 * @param default lazily-computed fallback value
 */
fun GrowthBookSDK.getDoubleOrElse(id: String, default: () -> Double): Double =
    getDoubleOrNull(id) ?: default()

/**
 * Returns the structured JSON value of the feature [id],
 * or `null` when the feature is missing or its value is not a JSON object.
 *
 * @param id unique feature identifier
 */
fun GrowthBookSDK.getJson(id: String): GBJson? =
    featureValue<GBJson>(id)

/**
 * Strategy for [isEnabled] when a feature is unknown (missing / empty cache).
 */
enum class FallbackStrategy {

    /** Unknown feature is treated as enabled (`true`) — permissive. */
    FAIL_OPEN,

    /** Unknown feature is treated as disabled (`false`) — restrictive. */
    FAIL_CLOSED
}
