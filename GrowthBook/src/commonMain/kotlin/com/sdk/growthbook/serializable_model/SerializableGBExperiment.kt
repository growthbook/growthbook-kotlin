package com.sdk.growthbook.serializable_model

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import com.sdk.growthbook.utils.GBFilter
import com.sdk.growthbook.utils.GBCondition
import com.sdk.growthbook.utils.GBBucketRange
import com.sdk.growthbook.utils.RangeSerializer
import com.sdk.growthbook.utils.GBVariationMeta
import com.sdk.growthbook.utils.GBParentConditionInterface
import com.sdk.growthbook.kotlinx.serialization.from

/*
    Defines a single experiment
 */
@Serializable
data class SerializableGBExperiment(

    /**
     * The globally unique tracking key for the experiment
     */
    val key: String,

    /**
     * The different variations to choose between
     */
    val variations: List<JsonElement> = ArrayList(),

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
)

internal fun SerializableGBExperiment.gbDeserialize() =
    GBExperiment(
        key = key,
        seed = seed,
        meta = meta,
        name = name,
        force = force,
        phase = phase,
        active = active,
        ranges = ranges,
        weights = weights,
        filters = filters,
        coverage = coverage,
        namespace = namespace,
        condition = condition,
        hashVersion = hashVersion,
        bucketVersion = bucketVersion,
        hashAttribute = hashAttribute,
        minBucketVersion = minBucketVersion,
        parentConditions = parentConditions,
        fallBackAttribute = fallBackAttribute,
        disableStickyBucketing = disableStickyBucketing,
        variations = variations.map { GBValue.from(it) },
    )
