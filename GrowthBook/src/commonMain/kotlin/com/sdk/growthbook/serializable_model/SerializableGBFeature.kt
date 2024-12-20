package com.sdk.growthbook.serializable_model

import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SerializableGBFeature(

    /**
     * The default value (should use null if not specified)
     */
    val defaultValue: JsonElement? = null,

    /**
     * Array of Rule objects that determine when and how the defaultValue gets overridden
     */
    val rules: List<SerializableGBFeatureRule>? = null
)

fun SerializableGBFeature.gbDeserialize(): GBFeature =
    GBFeature(
        defaultValue = defaultValue?.let { GBValue.from(it) },
        rules = rules?.map { it.gbDeserialize() }
    )
