package com.sdk.growthbook.test

import com.sdk.growthbook.IGrowthBookSDK
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.serializable_model.SerializableGBFeature
import com.sdk.growthbook.serializable_model.gbDeserialize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A deterministic, in-memory [IGrowthBookSDK] for unit tests.
 *
 * [FakeGrowthBook] never touches the network or cache: feature values are the
 * ones you set explicitly. Unset features evaluate as unknown (off, `null` value),
 * exactly like the real SDK when a key is missing.
 *
 * Scope: the fake is a pure override map. It does **not** evaluate rules,
 * conditions, or attribute targeting — that is intentional, so tests are
 * deterministic. To test real targeting logic, use the real SDK with a mock
 * network dispatcher instead.
 *
 * Example:
 * ```kotlin
 * val gb = FakeGrowthBook().enable("new-home")
 *
 * assertTrue(gb.isOn("new-home"))
 * assertFalse(gb.isOn("checkout-v2")) // never configured -> off
 * ```
 *
 * Configuration methods return `this`, so setup can be chained:
 * ```kotlin
 * val gb = FakeGrowthBook()
 *     .enable("new-home")
 *     .setValue("welcome-copy", "Hello")
 *     .setValue("max-items", 25)
 * ```
 *
 * Reusable scenarios are just factory functions that return a preconfigured
 * instance; fork one per test with [copy] so tests never share mutable state:
 * ```kotlin
 * object GBScenarios {
 *     fun checkoutV2() = FakeGrowthBook().enable("checkout-v2").setValue("max-items", 25)
 * }
 * val gb = GBScenarios.checkoutV2().copy().disable("promo-banner")
 * ```
 */
class FakeGrowthBook : IGrowthBookSDK {

    private val features: MutableMap<String, GBValue> = mutableMapOf()
    private val forcedVariations: MutableMap<String, Int> = mutableMapOf()
    private var attributes: Map<String, GBValue> = emptyMap()
    private val queried: MutableList<String> = mutableListOf()

    /** Forces [id] on by assigning it a boolean `true`. */
    fun enable(id: String): FakeGrowthBook = setValue(id, GBBoolean(true))

    /** Forces [id] off by assigning it a boolean `false`. */
    fun disable(id: String): FakeGrowthBook = setValue(id, GBBoolean(false))

    /** Assigns a raw [GBValue] to [id]. */
    fun setValue(id: String, value: GBValue): FakeGrowthBook {
        features[id] = value
        return this
    }

    /** Convenience overload for boolean feature values. */
    fun setValue(id: String, value: Boolean): FakeGrowthBook = setValue(id, GBBoolean(value))

    /** Convenience overload for string feature values. */
    fun setValue(id: String, value: String): FakeGrowthBook = setValue(id, GBString(value))

    /** Convenience overload for numeric feature values. */
    fun setValue(id: String, value: Number): FakeGrowthBook = setValue(id, GBNumber(value))

    /**
     * Bulk-seeds feature values, overwriting any existing entries with the same
     * key. Useful for loading a predefined fixture in one call.
     */
    fun setFeatures(values: Map<String, GBValue>): FakeGrowthBook {
        features.putAll(values)
        return this
    }

    /**
     * Forces [experimentKey] to resolve to variation [index] on [run], with
     * `inExperiment = true`. Mirrors the real SDK's forced-variation QA behavior.
     */
    fun setForcedVariation(experimentKey: String, index: Int): FakeGrowthBook {
        forcedVariations[experimentKey] = index
        return this
    }

    /** Removes any configured value for [id], so it evaluates as unknown again. */
    fun clear(id: String): FakeGrowthBook {
        features.remove(id)
        return this
    }

    /** Removes all configured feature values. */
    fun clearAll(): FakeGrowthBook {
        features.clear()
        return this
    }

    /**
     * Returns an independent copy with the same features, forced variations, and
     * attributes. Interaction tracking ([queriedFeatures]) starts fresh. Use this
     * to fork a shared fixture per test without mutating the original.
     */
    fun copy(): FakeGrowthBook {
        val forked = FakeGrowthBook()
        forked.features.putAll(this.features)
        forked.forcedVariations.putAll(this.forcedVariations)
        forked.attributes = this.attributes
        return forked
    }

    override fun feature(id: String): GBFeatureResult {
        queried.add(id)
        val value = features[id]
            ?: return GBFeatureResult(
                gbValue = null,
                on = false,
                off = true,
                source = GBFeatureSource.unknownFeature,
            )
        val isFalse = isFalsy(value)
        return GBFeatureResult(
            gbValue = value,
            on = !isFalse,
            off = isFalse,
            source = GBFeatureSource.force,
        )
    }

    override suspend fun suspendFeature(id: String): GBFeatureResult = feature(id)

    override fun isOn(featureId: String): Boolean = feature(featureId).on

    /**
     * Deterministically resolves the experiment. If a variation was forced for
     * this key via [setForcedVariation] (or [GBExperiment.force] is set), returns
     * that variation with `inExperiment = true`; otherwise returns the control
     * (variation 0) with `inExperiment = false`. No hashing or bucketing occurs.
     */
    override fun run(experiment: GBExperiment): GBExperimentResult {
        val forced = forcedVariations[experiment.key] ?: experiment.force
        val index = forced ?: 0
        val value = experiment.variations.getOrNull(index) ?: GBNull
        return GBExperimentResult(
            inExperiment = forced != null,
            variationId = index,
            value = value,
            key = experiment.key,
        )
    }

    override fun setAttributes(attributes: Map<String, GBValue>) {
        this.attributes = attributes
    }

    override suspend fun setAttributesSync(attributes: Map<String, GBValue>) {
        this.attributes = attributes
    }

    /** The attributes last set via [setAttributes]/[setAttributesSync]. */
    fun attributes(): Map<String, GBValue> = attributes

    /** Feature keys queried via [feature]/[isOn]/[suspendFeature], in call order. */
    fun queriedFeatures(): List<String> = queried.toList()

    /** Whether [id] was ever queried via [feature]/[isOn]/[suspendFeature]. */
    fun wasQueried(id: String): Boolean = id in queried

    /**
     * Truthiness rule matching the core SDK's `GBFeatureEvaluator`, which in turn
     * mirrors the reference (TypeScript) SDK's `off = !value`. A value is "off"
     * when it is `null`, [GBNull] (JSON `null`), boolean `false`, numeric `0`, or
     * the empty string.
     */
    private fun isFalsy(value: GBValue?): Boolean =
        value == null ||
            value is GBNull ||
            (value is GBBoolean && !value.value) ||
            (value is GBNumber && value.value == 0) ||
            (value is GBString && value.value.isEmpty())

    companion object {
        private val json = Json { isLenient = true; ignoreUnknownKeys = true }

        /**
         * Builds a fake from a GrowthBook features payload (e.g. exported from the
         * dashboard). Accepts either a full response `{"features": { ... }}` or a
         * bare features object `{ "my-flag": { "defaultValue": true }, ... }`.
         *
         * Only each feature's `defaultValue` is loaded — the fake stores values,
         * not rules (see the class note on scope). Features without a
         * `defaultValue` are skipped.
         */
        fun fromFeaturesJson(featuresJson: String): FakeGrowthBook {
            val root = json.parseToJsonElement(featuresJson).jsonObject
            val featuresObj = (root["features"] as? JsonObject) ?: root
            val fake = FakeGrowthBook()
            for ((key, element) in featuresObj) {
                val feature = json
                    .decodeFromJsonElement(SerializableGBFeature.serializer(), element)
                    .gbDeserialize()
                val value = feature.defaultValue ?: continue
                fake.setValue(key, value)
            }
            return fake
        }
    }
}
