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

/**
 * The evaluation surface of [GrowthBookSDK], extracted so application code can depend on an
 * interface rather than the concrete SDK — and swap in `FakeGrowthBook` from the `GrowthBookTest`
 * artifact in unit tests, with no network and no cache.
 *
 * Deliberately narrow: it covers evaluating features and experiments and pointing the SDK at a
 * user, not lifecycle (building, refreshing, streaming, closing), which only the real SDK can do.
 *
 * Implementors beware: every member here is abstract, and Kotlin interfaces export to
 * Objective-C with all members `@required`, so a default body would not save a Swift conformance.
 * Adding a member is therefore breaking — see the note in CLAUDE.md before extending this.
 */
interface IGrowthBookSDK {

    /**
     * Whether feature [featureId] evaluates to a truthy value for the current attributes.
     * False for an unknown feature, so it cannot distinguish "off" from "not there" — use
     * [feature] when that difference matters.
     */
    fun isOn(featureId: String): Boolean

    /**
     * Evaluates feature [id] against the current attributes and returns the full result: the
     * value, whether it came from a default, a force, an experiment or an unknown feature
     * ([com.sdk.growthbook.model.GBFeatureSource]), and the experiment result when one ran.
     *
     * Non-suspending and never blocks: it evaluates whatever definitions are loaded at this
     * instant, which before the first payload arrives means code defaults. Use [suspendFeature]
     * when the call must wait for real definitions.
     */
    fun feature(id: String): GBFeatureResult

    /**
     * Like [feature], but waits for the feature definitions to be available, retrying the fetch
     * with backoff when the initial one failed. Use it where a wrong first answer is worse than a
     * late one (paywalls, onboarding forks); prefer [feature] on hot paths and in UI code that can
     * re-render.
     */
    suspend fun suspendFeature(id: String): GBFeatureResult

    /**
     * Runs [experiment] directly — inline experimentation, without a feature flag wrapping it.
     * Assignment is deterministic in the hashed attribute, so repeated calls with unchanged
     * attributes return the same variation, and the tracking callback fires only on first exposure.
     */
    fun run(experiment: GBExperiment): GBExperimentResult

    /**
     * Replaces the targeting attributes wholesale — the map passed here becomes the attributes,
     * keys absent from it are dropped.
     *
     * Returns immediately; any sticky-bucket reload the new attributes imply runs in the
     * background, so an evaluation right after this call may still use the previous assignments.
     * On login/logout/user-switch use [setAttributesSync] instead.
     *
     * To add or change a few keys without rebuilding the map, the concrete SDK offers
     * [GrowthBookSDK.updateAttributes].
     */
    fun setAttributes(attributes: Map<String, GBValue>)

    /**
     * [setAttributes] that suspends until the sticky-bucket documents for the new attributes are
     * loaded, so the first evaluation after it cannot race the reload. This is the variant to use
     * whenever the user identity changes.
     */
    suspend fun setAttributesSync(attributes: Map<String, GBValue>)
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
