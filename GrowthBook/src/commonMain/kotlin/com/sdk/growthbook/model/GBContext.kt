@file:OptIn(ExperimentalAtomicApi::class)

package com.sdk.growthbook.model

import com.sdk.growthbook.GBEventLogger
import com.sdk.growthbook.GBTrackingCallback
import com.sdk.growthbook.evaluators.GBFeatureUsageHelper
import com.sdk.growthbook.plugin.tracking.GrowthBookPlugin
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
    val contextualBandits: Map<String, GBContextualBandit>? = null,
    /**
     * Per-feature usage history for the user these [attributes] describe.
     *
     * The only mutable object on the snapshot, and it is here rather than beside it on [GBContext]
     * for the reason the whole snapshot exists: an evaluation must not pair one user's attributes
     * with another user's history. A user change swaps in a *fresh* helper through the same CAS
     * that publishes the new attributes (see [GBContext.mutateReportingAttributeChange]), so the
     * two can never be read apart.
     *
     * Swapping rather than resetting in place is what makes a slow evaluation safe. An evaluation
     * that started before the user changed still holds the previous helper through its
     * [com.sdk.growthbook.evaluators.EvaluationContext]; it reports against — and writes into —
     * that now-orphaned history, which no one reads again. Resetting a shared helper instead let
     * the late write land in the *new* user's history and suppress their first report of the same
     * value, for as long as that value stayed put. Deliberately unlike the reference SDK, which
     * keeps one instance-wide map (`trackedFeatureUsage`, GrowthBook.ts) and compensates in its
     * tracking plugin with `dedupeKeyAttributes`.
     *
     * [com.sdk.growthbook.evaluators.GBExperimentHelper] stays off the snapshot on purpose: its key
     * already carries `hashAttribute`/`hashValue`, so it distinguishes users by itself, and handing
     * it a fresh instance per user change would re-fire an exposure the previous history correctly
     * remembered (A -> B -> A).
     */
    val featureUsageHelper: GBFeatureUsageHelper = GBFeatureUsageHelper()
)

/**
 * Defines the GrowthBook context.
 *
 * The cross-thread-shared evaluation inputs (features, attributes, forced variations, sticky-bucket
 * state, saved groups) are kept in a single [EvalSnapshot] behind an [AtomicReference]. Reads return
 * the current snapshot; writes atomically swap in a copy with the changed field (CAS loop, so
 * concurrent writers never lose each other's updates). For a consistent multi-field read during
 * evaluation, use [evalSnapshot] rather than reading the individual properties one by one.
 *
 * SDK-owned: assembled by [com.sdk.growthbook.GBSDKBuilder], never by consumer code — which is why
 * the constructor is internal. The type itself stays public because [com.sdk.growthbook.GrowthBookSDK.getGBContext]
 * returns it for reading. With no external caller bound to the constructor's JVM signature, new
 * options can be added here directly instead of being smuggled in as body properties.
 */
class GBContext internal constructor(

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

    /**
     * Saved Groups let you target the same group of users across multiple features and experiments.
     */
    savedGroups: Map<String, GBValue>? = null,

    /**
     * Plugins registered with the GrowthBook. See
     * [GrowthBookPlugin] and
     * [com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin].
     *
     * Normally set via [com.sdk.growthbook.GBSDKBuilder.setPlugins]. The SDK reads it once while
     * initialising its plugin registry (the reference JS SDK consumes `options.plugins` in its
     * constructor the same way); as a `val` here that read-once contract is structural rather than
     * merely documented.
     */
    val plugins: List<GrowthBookPlugin>? = null,

    /**
     * Structured sink for every event the SDK produces — the two evaluation events and explicit
     * [com.sdk.growthbook.GrowthBookSDK.logEvent] calls. Set via
     * [com.sdk.growthbook.GBSDKBuilder.setEventLogger].
     */
    val eventLogger: GBEventLogger? = null,
) {

    // Single source of truth for all cross-thread-shared evaluation inputs.
    private val state = AtomicReference(
        EvalSnapshot(
            attributes = attributes,
            forcedVariations = forcedVariations,
            stickyBucketAssignmentDocs = stickyBucketAssignmentDocs,
            stickyBucketIdentifierAttributes = stickyBucketIdentifierAttributes,
            savedGroups = savedGroups
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
     * [mutate] for the attribute writes, returning the attributes this write published, or null
     * when it changed nothing.
     *
     * Both the verdict and the map come off the same CAS iteration that won, so they describe the
     * write that happened rather than a state sampled around it: two concurrent writers would
     * otherwise each compare against — and hand observers — a map neither of them published.
     * Callers notify observers only on a real change (see
     * [com.sdk.growthbook.GrowthBookSDK.setAttributes]); re-setting the same map must stay silent.
     *
     * A real change also starts the next user's [EvalSnapshot.featureUsageHelper], in this same
     * swap: the new attributes and the empty history they belong to become visible to readers as
     * one value, so no evaluation can pair the two users. The helper is built inside the loop
     * because it has to be part of the snapshot the CAS publishes — a losing iteration throws its
     * instance away, which costs an allocation only under write contention on a user change.
     */
    private fun mutateReportingAttributeChange(
        transform: (EvalSnapshot) -> EvalSnapshot
    ): Map<String, GBValue>? {
        while (true) {
            val current = state.load()
            val transformed = transform(current)
            val changed = current.attributes != transformed.attributes
            val next = if (changed) {
                transformed.copy(featureUsageHelper = GBFeatureUsageHelper())
            } else {
                transformed
            }
            if (state.compareAndSet(current, next)) {
                return if (changed) next.attributes else null
            }
        }
    }

    /**
     * Drops the feature-usage history, so every feature reports again on its next evaluation.
     *
     * Only [com.sdk.growthbook.GrowthBookSDK.close] needs this — a user change gets a fresh history
     * from [mutateReportingAttributeChange] as part of publishing the new attributes. Swaps in a new
     * helper rather than clearing the current one for the same reason: an evaluation still in flight
     * holds the old instance and must not be able to write into the replacement.
     */
    internal fun resetFeatureUsageHistory() =
        mutate { it.copy(featureUsageHelper = GBFeatureUsageHelper()) }

    /**
     * Map of user attributes that are used to assign variations.
     *
     * Read-only on purpose. A plain setter here would write through [mutate], which publishes the
     * new attributes while leaving the previous user's [EvalSnapshot.featureUsageHelper] and
     * skipping the change report the plugins need — the exact pair of bugs the dedicated writers
     * below exist to prevent. Every attribute write goes through one of them.
     */
    internal val attributes: Map<String, GBValue>
        get() = state.load().attributes

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
            stickyBucketAssignmentDocs = (it.stickyBucketAssignmentDocs
                ?: emptyMap()) + (key to doc)
        )
    }

    /**
     * Atomically replace the user [attributes] and clear the sticky-bucket docs in a single update.
     * Done as one swap (not two separate writes) so a concurrent reader never observes the new
     * attributes paired with the previous user's stale sticky docs.
     *
     * @return the attributes this write published, or null when nothing changed.
     */
    internal fun setAttributesClearingStickyDocs(attributes: Map<String, GBValue>) =
        mutateReportingAttributeChange {
            it.copy(attributes = attributes, stickyBucketAssignmentDocs = null)
        }

    /**
     * Atomically replace the user [attributes], leaving the sticky-bucket docs alone — the caller
     * (`setAttributesSync`) awaits a sticky refresh of its own right after.
     *
     * @return the attributes this write published, or null when nothing changed.
     */
    internal fun setAttributesReportingChange(attributes: Map<String, GBValue>) =
        mutateReportingAttributeChange { it.copy(attributes = attributes) }

    /**
     * Atomically shallow-merge [attributes] into the current attributes (new keys added, existing
     * overwritten, others preserved). The read-merge-write happens inside the CAS loop, so two
     * concurrent merges can never lose each other's keys — unlike a read-then-[attributes]-set from
     * the caller. Mirrors the intent of [setAttributesClearingStickyDocs] for the merge case.
     *
     * @return the attributes this write published, or null when nothing changed.
     */
    internal fun mergeAttributesClearingStickyDocs(attributes: Map<String, GBValue>) =
        mutateReportingAttributeChange {
            it.copy(attributes = it.attributes + attributes, stickyBucketAssignmentDocs = null)
        }

    /**
     * Atomically shallow-merge [attributes] into the current attributes without touching the
     * sticky-bucket docs (mirrors the plain [attributes] setter used by `setAttributesSync`). The
     * merge is inside the CAS loop, so concurrent merges never lose keys.
     *
     * @return the attributes this write published, or null when nothing changed.
     */
    internal fun mergeAttributes(attributes: Map<String, GBValue>) =
        mutateReportingAttributeChange { it.copy(attributes = it.attributes + attributes) }

    /**
     * Atomically publish a freshly decoded payload: [features], [savedGroups] and [contextualBandits]
     * land in a single swap. Done as one write (not three separate setters) because payload
     * application runs on a background dispatcher while feature()/run() read from the app thread — a
     * reader landing between the writes would otherwise see new features paired with the previous
     * generation's bandit definitions or saved groups.
     *
     * A null argument means "absent from this payload, keep the current value", matching the
     * per-field dispatch it replaces.
     */
    internal fun applyPayload(
        features: GBFeatures?,
        savedGroups: Map<String, GBValue>?,
        contextualBandits: Map<String, GBContextualBandit>?,
    ) = mutate {
        it.copy(
            features = features ?: it.features,
            savedGroups = savedGroups ?: it.savedGroups,
            contextualBandits = contextualBandits ?: it.contextualBandits,
        )
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

    /**
     * Contextual bandit definitions keyed by bandit ref, pulled from the payload alongside
     * [features]. Internal-only: consumers never need the raw definitions — only the leaf metadata
     * (leafId / weights / version) surfaced on the experiment result. Written by the payload
     * pipeline, read atomically via [evalSnapshot] during evaluation.
     */
    internal var contextualBandits: Map<String, GBContextualBandit>?
        get() = state.load().contextualBandits
        set(value) = mutate { it.copy(contextualBandits = value) }
}

/**
 * Model consist already evaluated features
 */
@ConsistentCopyVisibility
data class StackContext internal constructor(

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
