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

    /**
     * A configured value together with the [GBFeatureSource] the real SDK would report for it.
     * Tracking the origin keeps `GBFeatureResult.source` honest: code and observability hooks
     * that branch on it (`setFeatureUsageCallback`, `GrowthBookPlugin`) are then exercised
     * against a source production actually produces.
     */
    private data class Entry(val value: GBValue, val source: GBFeatureSource)

    private val features: MutableMap<String, Entry> = mutableMapOf()
    private val forcedVariations: MutableMap<String, Int> = mutableMapOf()
    private var attributes: Map<String, GBValue> = emptyMap()
    private val queried: MutableList<String> = mutableListOf()

    /** Forces [id] on by assigning it a boolean `true`. */
    fun enable(id: String): FakeGrowthBook = setValue(id, GBBoolean(true))

    /** Forces [id] off by assigning it a boolean `false`. */
    fun disable(id: String): FakeGrowthBook = setValue(id, GBBoolean(false))

    /**
     * Assigns a raw [GBValue] to [id].
     *
     * The value is reported with `source = override`, which is what the real SDK reports for a
     * value supplied from code via `setForcedFeatures`.
     */
    fun setValue(id: String, value: GBValue): FakeGrowthBook {
        features[id] = Entry(value, GBFeatureSource.override)
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
     *
     * Seeded values stand in for the payload, so they are reported with
     * `source = defaultValue` — unlike the single-key [setValue], which reports `override`.
     */
    fun setFeatures(values: Map<String, GBValue>): FakeGrowthBook {
        values.forEach { (id, value) -> features[id] = Entry(value, GBFeatureSource.defaultValue) }
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
        val entry = features[id]
            ?: return GBFeatureResult(
                gbValue = null,
                on = false,
                off = true,
                source = GBFeatureSource.unknownFeature,
            )
        val isFalse = isFalsy(entry.value)
        return GBFeatureResult(
            gbValue = entry.value,
            on = !isFalse,
            off = isFalse,
            source = entry.source,
        )
    }

    override suspend fun suspendFeature(id: String): GBFeatureResult = feature(id)

    override fun isOn(featureId: String): Boolean = feature(featureId).on

    /**
     * Deterministically resolves the experiment. If a variation was forced for
     * this key via [setForcedVariation] (or [GBExperiment.force] is set), returns
     * that variation with `inExperiment = true`; otherwise returns the control
     * (variation 0) with `inExperiment = false`. No hashing or bucketing occurs.
     *
     * Everything else about the returned result mirrors the real SDK's
     * `GBExperimentEvaluator.getExperimentResult`, so assertions written against the fake hold
     * against production:
     *  - an index outside `experiment.variations` falls back to the baseline (index 0) and
     *    `inExperiment = false`, instead of reporting an impossible in-experiment result;
     *  - [GBExperimentResult.key] is the **variation** key — `meta[index].key`, or the index as
     *    a string — not the experiment key;
     *  - [GBExperimentResult.value] is [GBValue.Unknown] when the index cannot be reached at all
     *    (an experiment with no variations).
     */
    override fun run(experiment: GBExperiment): GBExperimentResult {
        val forced = forcedVariations[experiment.key] ?: experiment.force

        var index = forced ?: 0
        var inExperiment = forced != null
        if (index < 0 || index >= experiment.variations.size) {
            index = 0
            inExperiment = false
        }

        val meta = experiment.meta?.getOrNull(index)
        return GBExperimentResult(
            inExperiment = inExperiment,
            variationId = index,
            value = experiment.variations.getOrNull(index) ?: GBValue.Unknown,
            key = meta?.key ?: "$index",
            name = meta?.name,
            passthrough = meta?.passthrough,
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
     * mirrors the reference (TypeScript) SDK's `off = !value`. A value is "off" when
     * it is `null`, [GBNull] (JSON `null`), [GBValue.Unknown], boolean `false`, zero
     * of any numeric type (including `-0.0` and `NaN`), or the empty string. Empty
     * arrays and objects, and the string `"0"`, are truthy — as they are in JS.
     */
    private fun isFalsy(value: GBValue?): Boolean =
        value == null ||
            value is GBNull ||
            value is GBValue.Unknown ||
            (value is GBBoolean && !value.value) ||
            (value is GBNumber && value.value.toDouble().let { it == 0.0 || it.isNaN() }) ||
            (value is GBString && value.value.isEmpty())

    companion object {
        private val json = Json { isLenient = true; ignoreUnknownKeys = true }

        /** The keys that mark an object as a feature definition; a definition carries at least one. */
        private val FEATURE_KEYS = setOf("defaultValue", "rules")

        /**
         * Whether this object is a *map of features* rather than a features response: every value
         * must look like a feature definition, i.e. an object carrying at least one of
         * [FEATURE_KEYS].
         *
         * This is what separates a bare map that happens to contain a flag named `features` from
         * a real response whose `features` key holds the map — checking for the key alone would
         * silently load the wrong object in the first case.
         *
         * "At least one of", not "only": the decoder runs with `ignoreUnknownKeys`, so a feature
         * carrying extra fields (`id`, `description`, …) decodes fine and must not be rejected
         * here. A features *response* is excluded anyway, since its `features` value is a map of
         * feature names and carries neither marker key.
         */
        private fun JsonObject.isFeatureMap(): Boolean =
            values.all { it is JsonObject && it.keys.any(FEATURE_KEYS::contains) }

        /**
         * Builds a fake from a GrowthBook features payload (e.g. exported from the
         * dashboard). Accepts either a full response `{"features": { ... }}` or a
         * bare features object `{ "my-flag": { "defaultValue": true }, ... }`.
         *
         * Only each feature's `defaultValue` is loaded — the fake stores values,
         * not rules (see the class note on scope). Features without a
         * `defaultValue` are skipped, and loaded values report `source = defaultValue`.
         *
         * @throws IllegalArgumentException if the JSON is not an object, is encrypted, or holds
         * no recognisable features — rather than failing later with an opaque decoding error.
         */
        fun fromFeaturesJson(featuresJson: String): FakeGrowthBook {
            val root = json.parseToJsonElement(featuresJson) as? JsonObject
                ?: throw IllegalArgumentException(
                    "fromFeaturesJson expects a JSON object: either a features response " +
                        "({\"features\": { ... }}) or a bare features map " +
                        "({\"my-flag\": {\"defaultValue\": true}})."
                )

            val featuresObj = when {
                root.isFeatureMap() -> root
                root["features"] is JsonObject -> root["features"] as JsonObject
                root.containsKey("encryptedFeatures") -> throw IllegalArgumentException(
                    "fromFeaturesJson cannot read an encrypted payload: it does not decrypt. " +
                        "Pass the decrypted features instead."
                )
                else -> throw IllegalArgumentException(
                    "fromFeaturesJson found no features in this JSON. Expected a \"features\" " +
                        "object or a bare features map, but got keys: ${root.keys.joinToString()}."
                )
            }

            val fake = FakeGrowthBook()
            for ((key, element) in featuresObj) {
                val feature = try {
                    json.decodeFromJsonElement(SerializableGBFeature.serializer(), element)
                        .gbDeserialize()
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "fromFeaturesJson could not read \"$key\" as a feature definition: " +
                            "${e.message}",
                        e,
                    )
                }
                val value = feature.defaultValue ?: continue
                fake.features[key] = Entry(value, GBFeatureSource.defaultValue)
            }
            return fake
        }
    }
}
