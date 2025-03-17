package com.sdk.growthbook.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import com.sdk.growthbook.utils.GBFilter
import com.sdk.growthbook.utils.GBCondition
import com.sdk.growthbook.utils.GBBucketRange
import com.sdk.growthbook.utils.GBVariationMeta
import com.sdk.growthbook.utils.RangeSerializer
import com.sdk.growthbook.utils.GBParentConditionInterface
import com.sdk.growthbook.kotlinx.serialization.gbSerialize
import com.sdk.growthbook.serializable_model.SerializableGBExperiment
import com.sdk.growthbook.serializable_model.SerializableGBExperimentResult

/*
    Defines a single experiment
 */
data class GBExperiment(

    /**
     * The globally unique tracking key for the experiment
     */
    val key: String,

    /**
     * The different variations to choose between
     */
    val variations: List<GBValue> = emptyList(),

    /**
     * A tuple that contains the namespace identifier, plus a range of coverage for the experiment
     */
    val namespace: JsonArray? = null,

    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    val hashAttribute: String? = null,

    /**
     * How to weight traffic between variations. Must add to 1.
     */
    var weights: List<Float>? = null,

    /**
     * If set to false, always return the control (first variation)
     */
    var active: Boolean? = true,

    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    var coverage: Float? = null,

    /**
     * Optional targeting condition
     */
    var condition: GBCondition? = null,

    /**
     * Each item defines a prerequisite where a `condition` must evaluate against
     * a parent feature's value (identified by `id`). If `gate` is true, then this is a blocking
     * feature-level prerequisite; otherwise it applies to the current rule only.
     */
    val parentConditions: ArrayList<GBParentConditionInterface>? = null,

    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    var force: Int? = null,

    //new properties v0.4.0
    /**
     * The hash version to use (default to 1)
     */
    var hashVersion: Int? = null,

    /**
     * Array of ranges, one per variation
     */
    @Serializable(with = RangeSerializer.GBBucketRangeListSerializer::class)
    var ranges: List<GBBucketRange>? = null,

    /**
     * Meta info about the variations
     */
    var meta: ArrayList<GBVariationMeta>? = null,

    /**
     * Array of filters to apply
     */
    var filters: ArrayList<GBFilter>? = null,

    /**
     * The hash seed to use
     */
    var seed: String? = null,

    /**
     * Human-readable name for the experiment
     */
    var name: String? = null,

    /**
     * Id of the current experiment phase
     */
    var phase: String? = null,

    /**
     * When using sticky bucketing, can be used as a fallback to assign variations
     */
    val fallBackAttribute: String? = null,

    /**
     * If true, sticky bucketing will be disabled for this experiment.
     * (Note: sticky bucketing is only available
     * if a StickyBucketingService is provided in the Context)
     */
    val disableStickyBucketing: Boolean? = null,

    /**
     * An sticky bucket version number that can be used to force a re-bucketing
     * of users (default to 0)
     */
    val bucketVersion: Int? = null,

    /**
     * Any users with a sticky bucket version less than this will be excluded from the experiment
     */
    val minBucketVersion: Int? = null
) {
    internal fun gbSerialize() =
        SerializableGBExperiment(
            key = key,
            meta = meta,
            seed = seed,
            name = name,
            force = force,
            phase = phase,
            active = active,
            ranges = ranges,
            filters = filters,
            weights = weights,
            coverage = coverage,
            condition = condition,
            namespace = namespace,
            hashVersion = hashVersion,
            bucketVersion = bucketVersion,
            hashAttribute = hashAttribute,
            minBucketVersion = minBucketVersion,
            parentConditions = parentConditions,
            fallBackAttribute = fallBackAttribute,
            disableStickyBucketing = disableStickyBucketing,
            variations = variations.map { it.gbSerialize() },
        )
}

/**
 * The result of running an Experiment given a specific Context
 */
data class GBExperimentResult(

    /**
     * Whether or not the user is part of the experiment
     */
    val inExperiment: Boolean = false,

    /**
     * The array index of the assigned variation
     */
    val variationId: Int = 0,

    /**
     * The array value of the assigned variation
     */
    val value: GBValue,

    /**
     * The user attribute used to assign a variation
     */
    val hashAttribute: String? = null,

    /**
     * The value of that attribute
     */
    val hashValue: String? = null,

    //new properties v0.4.0
    /**
     * The unique key for the assigned variation
     */
    val key: String = "",

    /**
     * The human-readable name of the assigned variation
     */
    var name: String? = null,

    /**
     * The hash value used to assign a variation (float from 0 to 1)
     */
    var bucket: Float? = null,

    /**
     * Used for holdout groups
     */
    var passthrough: Boolean? = null,

    /**
     * If a hash was used to assign a variation
     */
    val hashUsed: Boolean? = null,

    /**
     * The id of the feature (if any) that the experiment came from
     */
    val featureId: String? = null,

    /**
     * If sticky bucketing was used to assign a variation
     */
    val stickyBucketUsed: Boolean? = null
) {
    internal fun gbSerialize() =
        SerializableGBExperimentResult(
            key = key,
            name = name,
            bucket = bucket,
            hashUsed = hashUsed,
            hashValue = hashValue,
            featureId = featureId,
            passthrough = passthrough,
            variationId = variationId,
            value = value.gbSerialize(),
            inExperiment = inExperiment,
            hashAttribute = hashAttribute,
            stickyBucketUsed = stickyBucketUsed,
        )
}
