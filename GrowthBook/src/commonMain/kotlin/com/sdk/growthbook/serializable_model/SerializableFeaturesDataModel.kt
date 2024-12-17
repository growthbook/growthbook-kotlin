package com.sdk.growthbook.serializable_model

import com.sdk.growthbook.features.FeaturesDataModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Data class for Feature API Response
 */
@Serializable
internal data class SerializableFeaturesDataModel(
    val features: Map<String, SerializableGBFeature>? = null,
    val encryptedFeatures: String? = null,
    val savedGroups: JsonObject? = null,
    val encryptedSavedGroups: String? = null
)

internal fun SerializableFeaturesDataModel.gbDeserialize() =
    FeaturesDataModel(
        features = features?.mapValues { it.value.gbDeserialize() },
        encryptedFeatures = encryptedFeatures,
        savedGroups = savedGroups,
        encryptedSavedGroups = encryptedSavedGroups,
    )
