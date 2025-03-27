package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.model.StickyBucketAssignmentDocsType
import com.sdk.growthbook.stickybucket.GBStickyBucketService

internal data class EvaluationContext(
    val enabled: Boolean,
    var features: GBFeatures,
    val userContext: UserContext,
    val loggingEnabled: Boolean,
    val savedGroups: Map<String, GBValue>?,
    var forcedVariations: Map<String, Any>,
    val trackingCallback: GBTrackingCallback,
    val stickyBucketService: GBStickyBucketService?,
    val onFeatureUsage: ((String, GBFeatureResult) -> Unit)?,
) {
    internal val experimentHelper: GBExperimentHelper = GBExperimentHelper()
}

internal data class UserContext(
    val qaMode: Boolean,
    internal val attributes: Map<String, GBValue>,
    internal var stickyBucketAssignmentDocs: StickyBucketAssignmentDocsType?,
)
