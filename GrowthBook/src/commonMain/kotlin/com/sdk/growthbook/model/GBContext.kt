@file:OptIn(ExperimentalAtomicApi::class)

package com.sdk.growthbook.model

import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import com.sdk.growthbook.utils.GBStickyAttributeKey
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal typealias FeatureUsageFuncCallback = (String, GBFeatureResult) -> Unit
internal typealias StickyBucketAssignmentDocsType = Map<GBStickyAttributeKey, GBStickyAssignmentsDocument>

/**
 * Immutable snapshot of the evaluation-input state shared across threads.
 *
 * These fields are written by the background payload pipeline (network fetch + sticky-bucket refresh)
 * and read synchronously by feature()/run(). They are published together as a single immutable value
 * behind an AtomicReference, so any reader observes a fully consistent set — there is no window where,
 * e.g., new [features] are visible while [stickyBucketAssignmentDocs] are still stale.
 */
internal data class EvalSnapshot(
    val features: GBFeatures = HashMap(),
    val attributes: Map<String, GBValue> = emptyMap(),
    val forcedVariations: Map<String, Number> = emptyMap(),
    val forcedFeatures: Map<String, GBValue> = emptyMap(),
    val attributeOverrides: Map<String, GBValue> = emptyMap(),
    val stickyBucketAssignmentDocs: StickyBucketAssignmentDocsType? = null,
    val stickyBucketIdentifierAttributes: List<String>? = null,
    val savedGroups: Map<String, GBValue>? = null,
)

/**
 * Defines the GrowthBook context.
 *
 * The cross-thread-shared evaluation inputs (features, attributes, forced variations, sticky-bucket
 * state, saved groups) are kept in a single [EvalSnapshot] behind an [AtomicReference]. Reads return
 * the current snapshot; writes atomically swap in a copy with the changed field (CAS loop, so
 * concurrent writers never lose each other's updates). For a consistent multi-field read during
 * evaluation, use [evalSnapshot] rather than reading the individual properties one by one.
 */
class GBContext(

    /**
     * Registered API Key for GrowthBook SDK
     */
    val apiKey: String,

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
    attributes: Map<String, GBValue>,

    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    forcedVariations: Map<String, Number>,

    /**
     * Map of Sticky Bucket documents
     */
    stickyBucketAssignmentDocs: StickyBucketAssignmentDocsType? = null,

    /**
     * List of user's attributes keys
     */
    stickyBucketIdentifierAttributes: List<String>? = null,

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

    savedGroups: Map<String, GBValue>? = null,
) {

    // Single source of truth for all cross-thread-shared evaluation inputs.
    private val state = AtomicReference(
        EvalSnapshot(
            attributes = attributes,
            forcedVariations = forcedVariations,
            stickyBucketAssignmentDocs = stickyBucketAssignmentDocs,
            stickyBucketIdentifierAttributes = stickyBucketIdentifierAttributes,
            savedGroups = savedGroups,
        )
    )

    /**
     * Atomically read the full evaluation-input snapshot. Evaluation must use this (one read) instead
     * of reading the individual properties separately, so it operates on a single consistent state.
     */
    internal fun evalSnapshot(): EvalSnapshot = state.load()

    private fun mutate(transform: (EvalSnapshot) -> EvalSnapshot) {
        while (true) {
            val current = state.load()
            if (state.compareAndSet(current, transform(current))) return
        }
    }

    /**
     * Map of user attributes that are used to assign variations
     */
    internal var attributes: Map<String, GBValue>
        get() = state.load().attributes
        set(value) = mutate { it.copy(attributes = value) }

    /**
     * Force specific experiments to always assign a specific variation (used for QA)
     */
    var forcedVariations: Map<String, Number>
        get() = state.load().forcedVariations
        set(value) = mutate { it.copy(forcedVariations = value) }

    /**
     * Map of Sticky Bucket documents
     */
    var stickyBucketAssignmentDocs: StickyBucketAssignmentDocsType?
        get() = state.load().stickyBucketAssignmentDocs
        set(value) = mutate { it.copy(stickyBucketAssignmentDocs = value) }

    /**
     * Atomically merge a single freshly-generated sticky-bucket assignment doc into the current
     * docs, keyed by its attribute. Unlike assigning [stickyBucketAssignmentDocs] wholesale, this
     * only adds/replaces the one [key], so it composes with a concurrent background refresh that
     * replaces the whole map: the CAS loop re-reads the latest state, so neither write clobbers the
     * other's unrelated keys. Mirrors the TypeScript SDK, which mutates the live docs object by a
     * single key at the point of generation.
     */
    internal fun mergeStickyAssignmentDoc(
        key: GBStickyAttributeKey,
        doc: GBStickyAssignmentsDocument,
    ) = mutate {
        it.copy(
            stickyBucketAssignmentDocs = (it.stickyBucketAssignmentDocs ?: emptyMap()) + (key to doc)
        )
    }

    /**
     * Atomically replace the user [attributes] and clear the sticky-bucket docs in a single update.
     * Done as one swap (not two separate writes) so a concurrent reader never observes the new
     * attributes paired with the previous user's stale sticky docs.
     */
    internal fun setAttributesClearingStickyDocs(attributes: Map<String, GBValue>) = mutate {
        it.copy(attributes = attributes, stickyBucketAssignmentDocs = null)
    }

    /**
     * Atomically shallow-merge [attributes] into the current attributes (new keys added, existing
     * overwritten, others preserved). The read-merge-write happens inside the CAS loop, so two
     * concurrent merges can never lose each other's keys — unlike a read-then-[attributes]-set from
     * the caller. Mirrors the intent of [setAttributesClearingStickyDocs] for the merge case.
     */
    internal fun mergeAttributesClearingStickyDocs(attributes: Map<String, GBValue>) = mutate {
        it.copy(attributes = it.attributes + attributes, stickyBucketAssignmentDocs = null)
    }

    /**
     * Atomically shallow-merge [attributes] into the current attributes without touching the
     * sticky-bucket docs (mirrors the plain [attributes] setter used by `setAttributesSync`). The
     * merge is inside the CAS loop, so concurrent merges never lose keys.
     */
    internal fun mergeAttributes(attributes: Map<String, GBValue>) = mutate {
        it.copy(attributes = it.attributes + attributes)
    }

    /**
     * Forced feature values, published atomically alongside the other evaluation inputs so a reader
     * on another thread always sees the latest write (previously a plain field on GrowthBookSDK with
     * no happens-before guarantee).
     */
    internal var forcedFeatures: Map<String, GBValue>
        get() = state.load().forcedFeatures
        set(value) = mutate { it.copy(forcedFeatures = value) }

    /**
     * Attribute overrides used for Sticky Bucketing, published atomically alongside the other
     * evaluation inputs (previously a plain field on GrowthBookSDK with no happens-before guarantee).
     */
    internal var attributeOverrides: Map<String, GBValue>
        get() = state.load().attributeOverrides
        set(value) = mutate { it.copy(attributeOverrides = value) }

    /**
     * List of user's attributes keys
     */
    var stickyBucketIdentifierAttributes: List<String>?
        get() = state.load().stickyBucketIdentifierAttributes
        set(value) = mutate { it.copy(stickyBucketIdentifierAttributes = value) }

    /**
     * Saved groups used by feature/experiment conditions
     */
    var savedGroups: Map<String, GBValue>?
        get() = state.load().savedGroups
        set(value) = mutate { it.copy(savedGroups = value) }

    // Keys are unique identifiers for the features and the values are Feature objects.
    // Feature definitions - To be pulled from API / Cache.
    internal var features: GBFeatures
        get() = state.load().features
        set(value) = mutate { it.copy(features = value) }
}

/**
 * Model consist already evaluated features
 */
data class StackContext(

    /**
     * Unique feature identifier
     */
    val id: String?,

    /**
     * Collection of unique feature identifier that used for handle recursion
     * in evaluate feature method
     */
    var evaluatedFeatures: MutableSet<String>
)
