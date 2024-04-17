package com.sdk.growthbook.model

import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import com.sdk.growthbook.utils.GBStickyAttributeKey
import com.sdk.growthbook.evaluators.GBExperimentHelper
import com.sdk.growthbook.stickybucket.GBStickyBucketService

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
    val hostURL: String,
    /**
     * Switch to globally disable all experiments. Default true.
     */
    val enabled: Boolean,
    /**
     * Encryption key for encrypted feature
     */
    val encryptionKey: String?,
    /**
     * Map of user attributes that are used to assign variations
     */
    internal var attributes: Map<String, Any>,
    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    var forcedVariations: Map<String, Any>,
    /**
     *
     */
    var stickyBucketAssignmentDocs: Map<GBStickyAttributeKey, GBStickyAssignmentsDocument>? = null,
    /**
     *
     */
    var stickyBucketIdentifierAttributes: List<String>? = null,
    /**
     *
     */
    val stickyBucketService: GBStickyBucketService? = null,
    /**
     * If true, random assignment is disabled and only explicitly forced variations are used.
     */
    val qaMode: Boolean,
    /**
     * A function that takes experiment and result as arguments.
     */
    val trackingCallback: (GBExperiment, GBExperimentResult) -> Unit,
    /**
     *
     */
    val remoteEval: Boolean = false,
) {

    // Keys are unique identifiers for the features and the values are Feature objects.
    // Feature definitions - To be pulled from API / Cache
    internal var features: GBFeatures = HashMap()

    internal val experimentHelper: GBExperimentHelper = GBExperimentHelper()
}

data class FeatureEvalContext(
    val id: String?,
    val evaluatedFeatures: MutableSet<String>
)