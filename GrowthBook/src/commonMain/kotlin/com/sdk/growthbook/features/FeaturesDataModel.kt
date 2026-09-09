package com.sdk.growthbook.features

import com.sdk.growthbook.model.GBContextualBandit
import kotlinx.serialization.json.JsonObject
import com.sdk.growthbook.model.gbSerialize
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.serializable_model.SerializableFeaturesDataModel
import com.sdk.growthbook.serializable_model.gbDeserialize

/**
 * Data Object for Feature API Response.
 *
 * SDK-owned: instances come from the payload pipeline, never from consumer code, so the constructor
 * (and `copy()` with it) is internal — new payload fields can then be added without breaking
 * already-compiled consumers. Public only because it appears in an overridden delegate member.
 */
@ConsistentCopyVisibility
data class FeaturesDataModel internal constructor(
    val features: GBFeatures? = null,
    val encryptedFeatures: String? = null,
    val savedGroups: JsonObject? = null,
    val encryptedSavedGroups: String? = null,
    val contextualBandits: Map<String, GBContextualBandit>? = null,
    val encryptedContextualBandits: String? = null
)

internal fun FeaturesDataModel.gbSerialize() =
    SerializableFeaturesDataModel(
        features = features?.mapValues { it.value.gbSerialize() },
        encryptedFeatures = encryptedFeatures,
        savedGroups = savedGroups,
        encryptedSavedGroups = encryptedSavedGroups,
        contextualBandits = contextualBandits?.mapValues { it.value.gbSerialize() },
        encryptedContextualBandits = encryptedContextualBandits
    )
