package com.sdk.growthbook.model

import com.sdk.growthbook.serializable_model.SerializableGBBanditContext
import com.sdk.growthbook.serializable_model.SerializableGBContextualBandit
import com.sdk.growthbook.utils.GBCondition

/**
 * A contextual bandit definition, as delivered in the feature payload and keyed by bandit ref.
 *
 * The per-variation weights inside each context are recomputed server-side (Thompson sampling); the
 * SDK does not learn or update them. At evaluation time it simply routes a user to the first matching
 * context (leaf) and buckets by that leaf's weights using the ordinary experiment machinery.
 */
@ConsistentCopyVisibility
data class GBContextualBandit internal constructor(

    /**
     * Monotonic marker of which weight generation this definition represents. Surfaced on the
     * experiment result so tracking can attribute an exposure to the weights that produced it.
     */
    val banditVersion: Int? = null,

    /**
     * Ordered list of contexts (leaves). The first one whose [GBBanditContext.condition] passes for
     * the user's attributes wins; if none match, the rule's aggregate weights are used instead.
     */
    val contexts: List<GBBanditContext>? = null
)

/**
 * A single bandit context (leaf): a targeting condition plus its own per-variation weights.
 */
@ConsistentCopyVisibility
data class GBBanditContext internal constructor(

    /**
     * Server-assigned id of this leaf, surfaced on the experiment result so an exposure can be
     * attributed to the exact leaf the user was routed into.
     */
    val leafId: Int,

    /**
     * Targeting condition evaluated against the user's attributes to decide whether the user is
     * routed into this leaf (same MongoDB-style condition format as feature/experiment targeting).
     */
    val condition: GBCondition? = null,

    /**
     * How to weight traffic between variations for users in this leaf. Must add to 1.
     */
    val weights: List<Float>? = null
)

/**
 * Per-user resolved bandit context: the leaf the user was routed into plus the weights used.
 * Attached transiently to a [com.sdk.growthbook.model.GBExperiment] during evaluation and surfaced
 * on the experiment result for tracking. Not part of the payload.
 */
internal data class CBContext(
    val leafId: Int,
    val variationWeights: List<Float>,
    val banditVersion: Int? = null
)

internal const val CONTEXTUAL_BANDIT_FALLBACK_LEAF_ID = -1

internal fun GBContextualBandit.gbSerialize(): SerializableGBContextualBandit =
    SerializableGBContextualBandit(
        banditVersion = banditVersion,
        contexts = contexts?.map { it.gbSerialize() }
    )

internal fun GBBanditContext.gbSerialize(): SerializableGBBanditContext =
    SerializableGBBanditContext(
        leafId = leafId,
        condition = condition,
        weights = weights
    )
