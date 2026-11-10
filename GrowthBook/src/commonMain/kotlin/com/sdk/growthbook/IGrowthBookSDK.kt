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

interface IGrowthBookSDK {
    fun isOn(featureId: String): Boolean
    fun feature(id: String): GBFeatureResult
    suspend fun suspendFeature(id: String): GBFeatureResult
    fun run(experiment: GBExperiment): GBExperimentResult
    fun setAttributes(attributes: Map<String, GBValue>)
    suspend fun setAttributesSync(attributes: Map<String, GBValue>)

    /**
     * Registers [callback] to be notified when an experiment assignment changes. See
     * [GrowthBookSDK.subscribe] for the full contract and for how it differs from the tracking
     * callback.
     *
     * Defaulted to a no-op so that adding it stays source- and binary-compatible for existing Kotlin
     * and Java implementors. Note that Kotlin interfaces export to Objective-C with every member
     * `@required`, so a Swift type conforming to this interface does **not** inherit the default and
     * must implement it.
     */
    fun subscribe(callback: GBExperimentRunCallback): GBSubscription = GBSubscription {}
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
