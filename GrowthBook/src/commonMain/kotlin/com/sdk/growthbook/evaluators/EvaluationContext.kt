package com.sdk.growthbook.evaluators

import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.GBEventLogger
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.plugin.tracking.PluginRegistry
import com.sdk.growthbook.model.GBContextualBandit
import com.sdk.growthbook.model.StackContext
import com.sdk.growthbook.model.StickyBucketAssignmentDocsType
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import com.sdk.growthbook.utils.GBStickyAttributeKey

internal data class EvaluationContext(
    val enabled: Boolean,
    var features: GBFeatures,
    val userContext: UserContext,
    val loggingEnabled: Boolean,
    val savedGroups: Map<String, GBValue>?,
    var forcedVariations: Map<String, Any>,
    val trackingCallback: GBTrackingCallback,
    val gbExperimentHelper: GBExperimentHelper,
    // User-scoped, unlike the instance-scoped gbExperimentHelper above: usage is reported on a value
    // change rather than on every evaluation, so the state that decides it must outlive the per-call
    // context but not the user. It comes off the same EvalSnapshot as userContext.attributes, which
    // is what keeps an evaluation that started before a user change from writing into the next
    // user's history (see EvalSnapshot.featureUsageHelper).
    val gbFeatureUsageHelper: GBFeatureUsageHelper,
    val stickyBucketService: GBStickyBucketService?,
    val onFeatureUsage: ((String, GBFeatureResult) -> Unit)?,
    // Invoked at the point a new sticky-bucket assignment doc is generated during evaluation, so the
    // single changed key can be merged back into the shared context atomically (see
    // GBContext.mergeStickyAssignmentDoc). Replaces the previous wholesale write-back of the whole
    // docs map after evaluation, which could clobber a concurrent background refresh.
    val onStickyAssignmentChanged: ((GBStickyAttributeKey, GBStickyAssignmentsDocument) -> Unit)? = null,
    val stackContext: StackContext,
    val pluginRegistry: PluginRegistry?,
    // Structured event sink for the SDK's own events, alongside the typed plugin callbacks above.
    // Carried on the snapshot like every other evaluation input so a sink registered at build time
    // cannot be read halfway through an evaluation.
    val eventLogger: GBEventLogger? = null,
    val contextualBandits: Map<String, GBContextualBandit>? = null
)

internal data class UserContext(
    val qaMode: Boolean,
    internal val attributes: Map<String, GBValue>,
    internal var stickyBucketAssignmentDocs: StickyBucketAssignmentDocsType?,
)
