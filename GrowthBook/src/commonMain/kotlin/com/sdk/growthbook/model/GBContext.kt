package com.sdk.growthbook.model

import com.sdk.growthbook.Utils.GBFeatures

/**
 * Defines the GrowthBook context.
 */
class GBContext(
    /**
     * Registered API Key for GrowthBook SDK
     */
    val apiKey: String,
    /**
     * Host URL for GrowthBook
     */
    val hostURL : String,
    /**
     * Switch to globally disable all experiments. Default true.
     */
    val enabled : Boolean,
    /**
     * Map of user attributes that are used to assign variations
     */
    val attributes: Map<String, Any>,
    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    val forcedVariations: Map<String, Int>,
    /**
     * If true, random assignment is disabled and only explicitly forced variations are used.
     */
    val qaMode : Boolean,
    /**
     * A function that takes experiment and result as arguments.
     */
    val trackingCallback : (GBExperiment, GBExperimentResult) -> Unit
){

    // Keys are unique identifiers for the features and the values are Feature objects.
    // Feature definitions - To be pulled from API / Cache
    internal var features: GBFeatures = HashMap()
}