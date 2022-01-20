package com.sdk.growthbook.model

import com.sdk.growthbook.Utils.GBFeatures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
Defines the GrowthBook context.
 */
class GBContext(
    val apiKey: String,
    val hostURL : String,
    /// Switch to globally disable all experiments. Default true.
    val enabled : Boolean,
    /// Map of user attributes that are used to assign variations
    val attributes: HashMap<String, Any>,
    ///  Force specific experiments to always assign a specific variation (used for QA)
    val forcedVariations: HashMap<String, Int>,
    ///  If true, random assignment is disabled and only explicitly forced variations are used.
    val qaMode : Boolean,
    /// A function that takes experiment and result as arguments.
    val trackingCallback : (GBExperiment, GBExperimentResult) -> Unit
){

    /// Keys are unique identifiers for the features and the values are Feature objects.
    /// Feature definitions - To be pulled from API / Cache
    internal var features: GBFeatures = HashMap()
}