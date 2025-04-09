package com.sdk.growthbook.serializable_model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.utils.GBFilter
import com.sdk.growthbook.utils.GBBucketRange
import com.sdk.growthbook.utils.GBVariationMeta
import com.sdk.growthbook.utils.RangeSerializer
import com.sdk.growthbook.utils.OptionalProperty
import com.sdk.growthbook.utils.GBParentConditionInterface
import com.sdk.growthbook.utils.OptionalPropertySerializer
import com.sdk.growthbook.kotlinx.serialization.from

/**
 * Rule object consists of various definitions to apply to calculate feature value
 */
@Serializable
data class SerializableGBFeatureRule(
    /**
     * Unique feature rule id
     */
    val id: String? = null,
    /**
     * Optional targeting condition
     */
    val condition: JsonElement? = null,

    /**
     * Each item defines a prerequisite where a `condition` must evaluate against
     * a parent feature's value (identified by `id`). If `gate` is true, then this is a blocking
     * feature-level prerequisite; otherwise it applies to the current rule only.
     */
    val parentConditions: ArrayList<GBParentConditionInterface>? = null,

    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    val coverage: Float? = null,

    /**
     * Immediately force a specific value (ignore every other option besides condition and coverage)
     */
    @Serializable(with = OptionalPropertySerializer::class)
    val force: OptionalProperty<JsonElement?> = OptionalProperty.NotPresent,
    /**
     * Run an experiment (A/B test) and randomly choose between these variations
     */
    val variations: List<JsonElement>? = null,

    /**
     * The globally unique tracking key for the experiment (default to the feature key)
     */
    val key: String? = null,

    /**
     * How to weight traffic between variations. Must add to 1.
     */
    val weights: List<Float>? = null,

    /**
     * A tuple that contains the namespace identifier, plus a range of coverage for the experiment.
     */
    val namespace: JsonArray? = null,

    /**
     * What user attribute should be used to assign variations (defaults to id)
     */
    val hashAttribute: String? = null,

    // new properties v0.4.0
    /**
     * The hash version to use (default to 1)
     */
    val hashVersion: Int? = null,

    /**
     * A more precise version of coverage
     */
    @Serializable(with = RangeSerializer.GBBucketRangeSerializer::class)
    val range: GBBucketRange? = null,

    /**
     * Ranges for experiment variations
     */
    @Serializable(with = RangeSerializer.GBBucketRangeListSerializer::class)
    val ranges: List<GBBucketRange>? = null,

    /**
     * Meta info about the experiment variations
     */
    val meta: ArrayList<GBVariationMeta>? = null,

    /**
     * Array of filters to apply to the rule
     */
    val filters: ArrayList<GBFilter>? = null,

    /**
     * Seed to use for hashing
     */
    val seed: String? = null,

    /**
     * Human-readable name for the experiment
     */
    val name: String? = null,

    /**
     * The phase id of the experiment
     */
    val phase: String? = null,

    /**
     * When using sticky bucketing, can be used as a fallback to assign variations
     */
    val fallbackAttribute: String? = null,

    /**
     * If true, sticky bucketing will be disabled for this experiment.
     * (Note: sticky bucketing is only available
     * if a StickyBucketingService is provided in the Context)
     */
    val disableStickyBucketing: Boolean? = null,

    /**
     * An sticky bucket version number that can be used to force
     * a re-bucketing of users (default to 0)
     */
    val bucketVersion: Int? = null,

    /**
     * Any users with a sticky bucket version less than this will be excluded from the experiment
     */
    val minBucketVersion: Int? = null,

    /**
     * Array of tracking calls to fire
     */
    val tracks: ArrayList<SerializableGBTrackData>? = null
)

internal fun SerializableGBFeatureRule.gbDeserialize() =
    GBFeatureRule(
        id = id,
        key = key,
        meta = meta,
        seed = seed,
        name = name,
        range = range,
        phase = phase,
        ranges = ranges,
        tracks = tracks?.let {
            ArrayList(
                tracks.map { it.gbDeserialize() }
            )
        },
        filters = filters,
        weights = weights,
        coverage = coverage,
        namespace = namespace,
        condition = condition,
        hashVersion = hashVersion,
        bucketVersion = bucketVersion,
        hashAttribute = hashAttribute,
        parentConditions = parentConditions,
        minBucketVersion = minBucketVersion,
        fallbackAttribute = fallbackAttribute,
        force = when(force) {
            is OptionalProperty.Present -> {
                if (force.value == null) null
                else GBValue.from(force.value)
            }
            is OptionalProperty.NotPresent -> {
                null
            }
        },
        disableStickyBucketing = disableStickyBucketing,
        variations = variations?.map { GBValue.from(it) },
    )
