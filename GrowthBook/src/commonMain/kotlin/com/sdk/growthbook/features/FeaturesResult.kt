package com.sdk.growthbook.features

import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import kotlinx.serialization.json.JsonObject

internal sealed interface FeaturesResult {
    data class Applied(
        val features: GBFeatures,
        val savedGroups: JsonObject?
    ) : FeaturesResult

    data class Failed(val error: GBError) : FeaturesResult
}

/**
 * Adapter nullable [DecodedPayload] -> [FeaturesResult]
 */
internal fun FeaturePayloadDecoder.decodeToResult(model: FeaturesDataModel): FeaturesResult =
    try {
        val payload = decode(model)
        payload.features
            ?.let { FeaturesResult.Applied(it, payload.savedGroups) }
            ?: FeaturesResult.Failed(GBError(Exception("Failed to decode features payload")))
    } catch (e: Throwable) {
        FeaturesResult.Failed(GBError(e))
    }
