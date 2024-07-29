package com.sdk.growthbook.features

import com.sdk.growthbook.utils.GBFeatures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Data Object for Feature API Response
 */
@Serializable
data class FeaturesDataModel(
    val features: GBFeatures? = null,
    val encryptedFeatures: String? = null,
    val savedGroups: JsonObject? = null,
    val encryptedSavedGroups: String? = null
)