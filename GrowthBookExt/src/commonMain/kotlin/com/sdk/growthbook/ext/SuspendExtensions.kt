package com.sdk.growthbook.ext

import com.sdk.growthbook.IGrowthBookSDK
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.suspendFeatureValue

/**
 * Awaiting counterparts of the accessors in `Extensions.kt`, for the startup window.
 *
 * Feature definitions are fetched asynchronously, so until the first payload (or cached payload) is
 * applied *every* feature is unknown and the synchronous accessors return their defaults. These
 * helpers suspend until definitions are available and only then evaluate, which is what you want on
 * a splash screen or anywhere a first read decides something the user will see.
 *
 * They are not a guarantee of fresh data: the underlying `suspendFeature` retries with backoff and,
 * once it runs out of attempts, evaluates against whatever is loaded — so a total fetch failure
 * still yields the default. What they remove is reading a *knowably* empty state.
 *
 * Naming: `await*` rather than overloads of the synchronous `get*`, because `suspend` does not
 * take part in signature resolution — `fun getBoolean(id, default)` and
 * `suspend fun getBoolean(id, default)` are conflicting overloads. The prefix also makes the
 * possible wait visible at the call site.
 *
 * Deliberately a narrower surface than the synchronous set: no `OrElse` variants (in a suspending
 * context `?: default()` reads fine on its own) and no `Float` accessor. Property delegates have no
 * counterpart here at all — `ReadOnlyProperty.getValue` is not `suspend`, so a property cannot wait.
 */

/**
 * Suspends until feature definitions are loaded, then returns whether [id] is enabled.
 *
 * @param id unique feature identifier
 */
suspend fun IGrowthBookSDK.awaitEnabled(id: String): Boolean =
    suspendFeature(id).on

/**
 * Suspends until feature definitions are loaded, then returns whether [id] is enabled, applying
 * [fallback] only when the feature is unknown — i.e. genuinely absent from the loaded payload.
 *
 * The awaiting form narrows what [fallback] covers, but does not eliminate it: once a payload has
 * been applied, unknown means the feature really is absent from it. It does **not** hold when every
 * fetch attempt failed — the underlying `suspendFeature` then gives up and evaluates against no
 * definitions at all, so every feature reads as unknown and [FallbackStrategy.FAIL_OPEN] reports
 * all of them as enabled. Choose [FallbackStrategy.FAIL_CLOSED] for anything that must not turn
 * itself on when the SDK cannot reach the network, a kill switch above all.
 *
 * @param id unique feature identifier
 * @param fallback strategy for the unknown-feature case
 */
suspend fun IGrowthBookSDK.awaitEnabled(id: String, fallback: FallbackStrategy): Boolean {
    val result = suspendFeature(id)
    // Same reasoning as the synchronous isEnabled: the evaluator also reports unknownFeature when
    // evaluating a *loaded* feature threw, so the source alone cannot decide this.
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
 * Suspends until feature definitions are loaded, then returns the value of [id] as a Boolean,
 * or [default] when no usable Boolean is present.
 */
suspend fun IGrowthBookSDK.awaitBoolean(id: String, default: Boolean): Boolean =
    suspendFeatureValue<Boolean>(id) ?: default

/**
 * Suspends until feature definitions are loaded, then returns the value of [id] as a String,
 * or [default] when no usable String is present.
 */
suspend fun IGrowthBookSDK.awaitString(id: String, default: String): String =
    suspendFeatureValue<String>(id) ?: default

/**
 * Suspends until feature definitions are loaded, then returns the value of [id] as an Int,
 * or [default] when no usable number is present.
 *
 * Like the synchronous accessors, any stored numeric type is accepted and narrowed, so a value
 * serialized as a floating-point number still reads as an Int.
 */
suspend fun IGrowthBookSDK.awaitInt(id: String, default: Int): Int =
    suspendFeatureValue<Number>(id)?.toInt() ?: default

/**
 * Suspends until feature definitions are loaded, then returns the value of [id] as a Long,
 * or [default] when no usable number is present.
 */
suspend fun IGrowthBookSDK.awaitLong(id: String, default: Long): Long =
    suspendFeatureValue<Number>(id)?.toLong() ?: default

/**
 * Suspends until feature definitions are loaded, then returns the value of [id] as a Double,
 * or [default] when no usable number is present.
 */
suspend fun IGrowthBookSDK.awaitDouble(id: String, default: Double): Double =
    suspendFeatureValue<Number>(id)?.toDouble() ?: default

/**
 * Suspends until feature definitions are loaded, then returns the value of [id] as a [GBJson]
 * object, or `null` when the value is absent or not a JSON object.
 */
suspend fun IGrowthBookSDK.awaitJson(id: String): GBJson? =
    suspendFeatureValue<GBJson>(id)
