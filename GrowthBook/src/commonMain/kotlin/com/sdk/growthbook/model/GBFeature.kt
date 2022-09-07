package com.sdk.growthbook.model

import com.sdk.growthbook.utils.GBCondition
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
    val defaultValue : JsonElement? = null,
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
    val condition : GBCondition? = null,
    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    val coverage : Float? = null,
    /**
     * Immediately force a specific value (ignore every other option besides condition and coverage)
     */
    val force : JsonElement? = null,
    /**
     * Run an experiment (A/B test) and randomly choose between these variations
     */
    val variations: ArrayList<JsonElement>? = null,
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
    val namespace : JsonArray? = null,
    /**
     * What user attribute should be used to assign variations (defaults to id)
     */
    val hashAttribute: String? = null
)

/**
 * Enum For defining feature value source
 */
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
    experiment
}

/**
 * Result for Feature
 */
class GBFeatureResult(
    /**
     * The assigned value of the feature
     */
    val value : Any?,
    /**
     * The assigned value cast to a boolean
     */
    val on : Boolean = false,
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