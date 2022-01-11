package com.comllc.growthbook.model

import com.comllc.growthbook.Utils.Constants
import kotlinx.serialization.Serializable


/*
    A Feature object consists of possible values plus rules for how to assign values to users.
 */
@Serializable
class GBFeature(
    /// The default value (should use null if not specified)
    // TODO Handle Any
    val defaultValue : String? = null,
    /// Array of Rule objects that determine when and how the defaultValue gets overridden
    val rules: List<GBFeatureRule>
)

@Serializable
class GBFeatureRule(
    /// Optional targeting condition
    val condition : GBCondition,
    /// What percent of users should be included in the experiment (between 0 and 1, inclusive)
    val coverage : Float? = null,
    ///  Immediately force a specific value (ignore every other option besides condition and coverage)
    // TODO Handle Any
    val force : String? = null,
    /// Run an experiment (A/B test) and randomly choose between these variations
    // TODO Handle Any
    val variations: ArrayList<String>,
    /// The globally unique tracking key for the experiment (default to the feature key)
    val trackingKey: String? = null,
    /// How to weight traffic between variations. Must add to 1.
    val weights: List<Float>,
    /// A tuple that contains the namespace identifier, plus a range of coverage for the experiment.
    val namespace : GBNameSpace? = null,
    /// What user attribute should be used to assign variations (defaults to id)
    val hashAttribute: String = Constants.idAttributeKey
)




enum class GBFeatureSource {
    unknownFeature, defaultValue, force, experiment
}

class GBFeatureResult(
    /// The assigned value of the feature
    val value : Any?,
    /// The assigned value cast to a boolean
    val on : Boolean? = null,
    /// The assigned value cast to a boolean and then negated
    val off: Boolean? = null,
    /// One of "unknownFeature", "defaultValue", "force", or "experiment"
    val source: GBFeatureSource,
    ///  When source is "experiment", this will be the Experiment object used
    val experiment: GBExperiment? = null
)