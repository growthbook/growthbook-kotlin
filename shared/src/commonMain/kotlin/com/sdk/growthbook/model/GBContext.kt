package com.sdk.growthbook.model

import com.sdk.growthbook.Utils.GBFeatures
import com.sdk.growthbook.Utils.GBOverrides

/*
Defines the GrowthBook context.
 */
class GBContext(
    val apiKey: String,
    /// Switch to globally disable all experiments. Default true.
    val enabled : Boolean,
    /// Map of user attributes that are used to assign variations
    val attributes: HashMap<String, Any>,
    val url : String,
    ///  Force specific experiments to always assign a specific variation (used for QA)
    val forcedVariations: HashMap<String, Int> = HashMap(),
    ///  If true, random assignment is disabled and only explicitly forced variations are used.
    val qaMode : Boolean,
    /// A function that takes experiment and result as arguments.
    val trackingCallback : (GBExperiment, GBExperimentResult) -> Unit
){

    /// Keys are unique identifiers for the features and the values are Feature objects.
    /// Feature definitions - To be pulled from API / Cache
    internal var features: GBFeatures = HashMap()
    /// Keys are experiment trackingKeys and the value is an ExperimentOverride object
    /// Experiment overrides - To be pulled from API / Cache
    internal var overrides: GBOverrides = HashMap()
}