package com.comllc.growthbook.model


/*
    A Feature object consists of possible values plus rules for how to assign values to users.
 */
class GBFeature<T>(
    /// The default value (should use null if not specified)
    val defaultValue : T? = null,
    /// Array of Rule objects that determine when and how the defaultValue gets overridden
    val rules: List<GBFeatureRule<T>>
)

class GBFeatureRule<T>(
    /// Optional targeting condition
    val condition : GBCondition,
    /// What percent of users should be included in the experiment (between 0 and 1, inclusive)
    val coverage : Float?,
    ///  Immediately force a specific value (ignore every other option besides condition and coverage)
    val force : T?,
    /// Run an experiment (A/B test) and randomly choose between these variations
    val variations: ArrayList<T>,
    /// The globally unique tracking key for the experiment (default to the feature key)
    val trackingKey: String,
    /// How to weight traffic between variations. Must add to 1.
    val weights: List<Float>,
    /// A tuple that contains the namespace identifier, plus a range of coverage for the experiment.
    val namespace : GBNameSpace,
    /// What user attribute should be used to assign variations (defaults to id)
    val hashAttribute: String
)

/*
    TODO - Targeting condition based on MongoDB query syntax.
    For details on parsing and evaluating these conditions, view the reference Typescript implementation
    https://github.com/growthbook/growthbook/tree/main/packages/sdk-js/src/mongrule.ts
 */
class GBCondition()

enum class GBFeatureSource {
    unknownFeature, defaultValue, force, experiment
}

class GBFeatureResult<T>(
    /// The assigned value of the feature
    val value : T?,
    /// The assigned value cast to a boolean
    val on : Boolean? = null,
    /// The assigned value cast to a boolean and then negated
    val off: Boolean? = null,
    /// One of "unknownFeature", "defaultValue", "force", or "experiment"
    val source: GBFeatureSource,
    ///  When source is "experiment", this will be the Experiment object used
    val experiment: GBExperiment<T>? = null
)