package com.sdk.growthbook.features

import kotlinx.serialization.json.JsonObject
import com.sdk.growthbook.model.gbSerialize
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.serializable_model.SerializableFeaturesDataModel

/**
 * Data Object for Feature API Response
 */
data class FeaturesDataModel(
    val features: GBFeatures? = null,
    val encryptedFeatures: String? = null,
    val savedGroups: JsonObject? = null,
    val encryptedSavedGroups: String? = null
)

internal fun FeaturesDataModel.gbSerialize() =
    SerializableFeaturesDataModel(
        features = features?.mapValues { it.value.gbSerialize() },
        encryptedFeatures = encryptedFeatures,
        savedGroups = savedGroups,
        encryptedSavedGroups = encryptedSavedGroups,
    )
