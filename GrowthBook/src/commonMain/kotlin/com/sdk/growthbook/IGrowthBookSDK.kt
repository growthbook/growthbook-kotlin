package com.sdk.growthbook

import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.GBFeatures

/**
 * The SDK surface application code should depend on, so a `GrowthBookSDK` can be swapped for a
 * double — `FakeGrowthBook` in the `GrowthBookTest` artifact — without touching call sites.
 *
 * It is not an extension point for third-party implementations: the implementations are SDK-owned,
 * which is why members may be added here in a minor release. If you do implement it yourself,
 * override every member rather than relying on a default body — a default is a compatibility
 * measure for the implementations we ship, not a sensible behaviour to inherit.
 */
interface IGrowthBookSDK {

    /**
     * Whether the feature [featureId] is on — the [GBFeatureResult.on] of evaluating it.
     *
     * A feature that is absent from the loaded definitions is off, which makes this
     * indistinguishable from a feature that is present and evaluates to a falsy value. Use
     * [getFeatures] when that difference matters.
     *
     * @param featureId unique feature identifier
     */
    fun isOn(featureId: String): Boolean

    /**
     * Evaluates the feature [id] against the current attributes and returns the full result —
     * the value, its source, and the rule or experiment that produced it.
     *
     * A best-effort read of the state loaded at call time. Definitions arrive asynchronously, so a
     * call made before the first payload has been applied evaluates against no definitions at all
     * and reports every feature as unknown; [suspendFeature] waits instead.
     *
     * Evaluation is observable: it reports through the feature-usage callback and plugins, and
     * fires experiment tracking when the feature enrols the user. Do not call it merely to test
     * whether a feature exists.
     *
     * @param id unique feature identifier
     */
    fun feature(id: String): GBFeatureResult

    /**
     * Awaiting counterpart of [feature]: suspends until feature definitions have been loaded from
     * the network (retrying with backoff), then evaluates.
     *
     * Not a guarantee of fresh data — once the retries are exhausted it evaluates against whatever
     * is loaded, which may be nothing. Use it where a first read would otherwise land in the
     * startup window and silently return defaults.
     *
     * @param id unique feature identifier
     */
    suspend fun suspendFeature(id: String): GBFeatureResult

    /**
     * Assigns the user to a variation of [experiment] and returns the result, reporting the
     * exposure through the tracking callback when the user is enrolled.
     *
     * For experiments defined in the GrowthBook payload prefer [feature] — this is for experiments
     * declared inline in code.
     */
    fun run(experiment: GBExperiment): GBExperimentResult

    /**
     * Replaces the attributes used for targeting and variation assignment.
     *
     * Returns before sticky-bucket assignments have been reloaded — that runs in the background.
     * Use [setAttributesSync] when an evaluation follows immediately and sticky bucketing is in
     * play, otherwise the evaluation can race the reload.
     */
    fun setAttributes(attributes: Map<String, GBValue>)

    /**
     * [setAttributes] that awaits the sticky-bucket reload before returning, so an evaluation
     * right after it sees the new user's assignments. Prefer it on login, logout and user switch.
     *
     * Despite the name it suspends rather than blocking a thread; "Sync" here means synchronized
     * with the reload.
     */
    suspend fun setAttributesSync(attributes: Map<String, GBValue>)

    /**
     * The currently loaded feature definitions.
     *
     * Implementations that hold definitions should override this. The default exists only so
     * that adding this member stays binary-compatible for existing Kotlin/Java implementors —
     * it reports "no definitions loaded", which callers cannot distinguish from a genuinely
     * empty payload, so an implementation that keeps the default silently degrades any caller
     * using this to tell "feature absent" from "feature present but off". Note the default does
     * **not** rescue Swift conformances: Kotlin interfaces export to Objective-C with every
     * member `@required`.
     */
    fun getFeatures(): GBFeatures = emptyMap()
}

/**
 * Maps a resolved feature value onto the requested type [V], or `null` when there is no value.
 *
 * This is the single implementation behind both [GrowthBookSDK.featureValue] and the
 * [IGrowthBookSDK.featureValue] extension below. Keeping one body matters: a member always wins
 * over an extension in Kotlin, so any difference between the two would make the result depend on
 * the *declared* type of the variable rather than on the value — and switching a field from
 * `GrowthBookSDK` to `IGrowthBookSDK` is exactly the refactor this interface exists to enable.
 *
 * The shape follows the reference (TypeScript) SDK's `getFeatureValue`, which hands back the
 * decoded JSON value as-is with no supported-type gate; its value type (`JSONValue`) includes
 * `Array<JSONValue>`, so arrays are ordinary values there. The `as? V` casts are a deliberate
 * Kotlin-side addition: a type mismatch yields `null` here, whereas TS would return a value of
 * the wrong runtime type.
 */
@PublishedApi
internal inline fun <reified V> GBFeatureResult.extractValue(): V? =
    when (val value = gbValue) {
        is GBBoolean -> value.value as? V
        is GBString -> value.value as? V
        is GBNumber -> value.value as? V
        is GBJson -> value as? V
        is GBArray -> value as? V
        is GBNull, is GBValue.Unknown, null -> null
    }

/**
 * Reads the value of feature [id] typed as [V], or `null` if the feature has no value or its
 * value is not a [V].
 */
inline fun <reified V> IGrowthBookSDK.featureValue(id: String): V? =
    feature(id).extractValue()

/**
 * Awaiting counterpart of [featureValue]: suspends until feature definitions have been loaded
 * (see [IGrowthBookSDK.suspendFeature]) and only then reads feature [id] as [V].
 *
 * Use it where reading too early would silently mean "unknown feature" — at startup, before the
 * first payload lands, every feature is unknown and [featureValue] returns `null` for all of them.
 *
 * This is not a guarantee of fresh data: when every fetch attempt fails, [suspendFeature] gives up
 * and returns the locally evaluated result, so this can still yield `null`.
 *
 * It exists so callers outside this module can await *and* map a value without reimplementing
 * [extractValue], which is `@PublishedApi internal` and therefore out of reach from another module
 * — a second copy of the mapping rules would drift from this one.
 */
suspend inline fun <reified V> IGrowthBookSDK.suspendFeatureValue(id: String): V? =
    suspendFeature(id).extractValue()
