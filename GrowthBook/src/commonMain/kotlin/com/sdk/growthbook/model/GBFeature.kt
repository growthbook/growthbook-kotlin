package com.sdk.growthbook.model

import com.sdk.growthbook.utils.GBBucketRange
import com.sdk.growthbook.utils.GBCondition
import com.sdk.growthbook.utils.GBFilter
import com.sdk.growthbook.utils.GBTrackData
import com.sdk.growthbook.utils.GBVariationMeta
import com.sdk.growthbook.utils.GBParentConditionInterface
import com.sdk.growthbook.utils.RangeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * A Feature object consists of possible values plus rules for how to assign values to users.
 */
@Serializable
class GBFeature(
    /**
     * The default value (should use null if not specified)
     */
    val defaultValue: JsonElement? = null,
    /**
     * Array of Rule objects that determine when and how the defaultValue gets overridden
     */
    val rules: List<GBFeatureRule>? = null
)

/**
 * Rule object consists of various definitions to apply to calculate feature value
 */
@Serializable
class GBFeatureRule(
    /**
     * Optional targeting condition
     */
    val condition: GBCondition? = null,
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
    val force: JsonElement? = null,
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
     * An sticky bucket version number that can be used to force a re-bucketing of users (default to 0)
     */
    val bucketVersion: Int? = null,
    /**
     * Any users with a sticky bucket version less than this will be excluded from the experiment
     */
    val minBucketVersion: Int? = null,
    /**
     * Array of tracking calls to fire
     */
    val tracks: ArrayList<GBTrackData>? = null
)

/**
 * Enum For defining feature value source
 */
@Suppress("EnumEntryName")
enum class GBFeatureSource {
    /**
     * Queried Feature doesn't exist in GrowthBook
     */
    unknownFeature,

    /**
     * Default Value for the Feature is being processed
     */
    defaultValue,

    /**
     * Forced Value for the Feature is being processed
     */
    force,

    /**
     * Experiment Value for the Feature is being processed
     */
    experiment,

    /**
     * CyclicPrerequisite Value for the Feature is being processed
     */
    cyclicPrerequisite,

    /**
     * Prerequisite Value for the Feature is being processed
     */
    prerequisite
}

/**
 * Result for Feature
 */
class GBFeatureResult(
    /**
     * The assigned value of the feature
     */
    val value: JsonElement?,
    /**
     * The assigned value cast to a boolean
     */
    val on: Boolean = false,
    /**
     * The assigned value cast to a boolean and then negated
     */
    val off: Boolean = true,
    /**
     * One of "unknownFeature", "defaultValue", "force", or "experiment"
     */
    val source: GBFeatureSource,
    /**
     * When source is "experiment", this will be the Experiment object used
     */
    val experiment: GBExperiment? = null,
    /**
     * When source is "experiment", this will be an ExperimentResult object
     */
    val experimentResult: GBExperimentResult? = null
)
