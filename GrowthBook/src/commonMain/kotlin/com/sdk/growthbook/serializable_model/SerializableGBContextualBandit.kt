package com.sdk.growthbook.serializable_model

import com.sdk.growthbook.model.GBBanditContext
import com.sdk.growthbook.model.GBContextualBandit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire (JSON) shape of a contextual bandit definition, keyed by bandit ref in the payload.
 * The per-variation [SerializableGBBanditContext.weights] are recomputed server-side; the SDK
 * only routes a user to the first matching context (leaf) and buckets by that leaf's weights.
 */
@Serializable
@ConsistentCopyVisibility
data class SerializableGBContextualBandit internal constructor(

    /**
     * Monotonic marker of which weight generation this definition represents.
     */
    val banditVersion: Int? = null,

    /**
     * Ordered list of contexts (leaves); the first whose condition passes wins.
     */
    val contexts: List<SerializableGBBanditContext>? = null
)

/**
 * A single bandit context (leaf): a targeting condition plus its own variation weights.
 */
@Serializable
@ConsistentCopyVisibility
data class SerializableGBBanditContext internal constructor(

    /**
     * Server-assigned id of this leaf (used for tracking which leaf a user landed in).
     */
    val leafId: Int,

    /**
     * Targeting condition evaluated against user attributes to route users into this leaf.
     */
    val condition: JsonElement? = null,

    /**
     * How to weight traffic between variations for users in this leaf. Must add to 1.
     */
    val weights: List<Float>? = null
)

internal fun SerializableGBContextualBandit.gbDeserialize(): GBContextualBandit =
    GBContextualBandit(
        banditVersion = banditVersion,
        contexts = contexts?.map { it.gbDeserialize() }
    )

internal fun SerializableGBBanditContext.gbDeserialize(): GBBanditContext =
    GBBanditContext(
        leafId = leafId,
        condition = condition,
        weights = weights
    )
