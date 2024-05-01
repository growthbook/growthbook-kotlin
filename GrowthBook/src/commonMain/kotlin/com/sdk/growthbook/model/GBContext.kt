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
     * Map of Sticky Bucket documents
     */
    var stickyBucketAssignmentDocs: Map<GBStickyAttributeKey, GBStickyAssignmentsDocument>? = null,

    /**
     * List of user's attributes keys
     */
    var stickyBucketIdentifierAttributes: List<String>? = null,

    /**
     * Service that provide functionality of Sticky Bucketing
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
     * Flag which defines whether to use Remote Evaluation
     */
    val remoteEval: Boolean = false,
) {

    // Keys are unique identifiers for the features and the values are Feature objects.
    // Feature definitions - To be pulled from API / Cache
    internal var features: GBFeatures = HashMap()

    internal val experimentHelper: GBExperimentHelper = GBExperimentHelper()
}

/**
 * Model consist already evaluated features
 */
data class FeatureEvalContext(

    /**
     * Unique feature identifier
     */
    val id: String?,

    /**
     * Collection of unique feature identifier that used for handle recursion
     * in evaluate feature method
     */
    val evaluatedFeatures: MutableSet<String>
)