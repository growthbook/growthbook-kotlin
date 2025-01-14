package com.sdk.growthbook.serializable_model

import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement

/**
 * The result of running an Experiment given a specific Context
 */
@Serializable
data class SerializableGBExperimentResult(

    /**
     * Whether or not the user is part of the experiment
     */
    val inExperiment: Boolean = false,

    /**
     * The array index of the assigned variation
     */
    val variationId: Int = 0,

    /**
     * The array value of the assigned variation
     */
    val value: JsonElement = JsonObject(HashMap()),

    /**
     * The user attribute used to assign a variation
     */
    val hashAttribute: String? = null,

    /**
     * The value of that attribute
     */
    val hashValue: String? = null,

    //new properties v0.4.0
    /**
     * The unique key for the assigned variation
     */
    val key: String = "",

    /**
     * The human-readable name of the assigned variation
     */
    var name: String? = null,

    /**
     * The hash value used to assign a variation (float from 0 to 1)
     */
    var bucket: Float? = null,

    /**
     * Used for holdout groups
     */
    var passthrough: Boolean? = null,

    /**
     * If a hash was used to assign a variation
     */
    val hashUsed: Boolean? = null,

    /**
     * The id of the feature (if any) that the experiment came from
     */
    val featureId: String? = null,

    /**
     * If sticky bucketing was used to assign a variation
     */
    val stickyBucketUsed: Boolean? = null
)

internal fun SerializableGBExperimentResult.gbDeserialize() =
    GBExperimentResult(
        key = key,
        name = name,
        bucket = bucket,
        hashUsed = hashUsed,
        hashValue = hashValue,
        featureId = featureId,
        variationId = variationId,
        passthrough = passthrough,
        value = GBValue.from(value),
        inExperiment = inExperiment,
        hashAttribute = hashAttribute,
        stickyBucketUsed = stickyBucketUsed,
    )
