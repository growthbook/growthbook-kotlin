package com.sdk.growthbook.model

import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import com.sdk.growthbook.utils.GBStickyAttributeKey
import com.sdk.growthbook.stickybucket.GBStickyBucketService

internal typealias FeatureUsageFuncCallback = (String, GBFeatureResult) -> Unit
internal typealias StickyBucketAssignmentDocsType = Map<GBStickyAttributeKey, GBStickyAssignmentsDocument>

/**
 * Defines the GrowthBook context.
 */
data class GBContext(

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
    var forcedVariations: Map<String, Number>,

    /**
     * Map of Sticky Bucket documents
     */
    var stickyBucketAssignmentDocs: StickyBucketAssignmentDocsType? = null,

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
    val trackingCallback: GBTrackingCallback,

    /**
     * A callback that will be invoked every time a feature is viewed. Listen for feature usage events
     */
    val onFeatureUsage: FeatureUsageFuncCallback? = null,

    /**
     * Flag which defines whether to use Remote Evaluation
     */
    val remoteEval: Boolean = false,

    /**
     * If true, prints logging statements to stdout
     */
    val enableLogging: Boolean = false,

    var savedGroups: Map<String, Any>? = null
) {

    // Keys are unique identifiers for the features and the values are Feature objects.
    // Feature definitions - To be pulled from API / Cache
    internal var features: GBFeatures = HashMap()
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
