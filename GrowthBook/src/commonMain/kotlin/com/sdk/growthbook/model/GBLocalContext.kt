package com.sdk.growthbook.model

/**
 * Defines the GrowthBook local properties.
 * The properties whit describe a user and a user environment
 *
 * The local content is a Context without network settings (host and api entities)
 */

class GBLocalContext(
    /**
     * Switch to globally disable all experiments. Default true.
     */
    val enabled: Boolean,
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
    val qaMode: Boolean,
)
