package com.sdk.growthbook.local

import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.model.GBFeatureResult
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class GrowthBookLocalSDK(
    val attributes: Map<String, Any>,
    val enabled: Boolean,
    val forcedVariations: Map<String, Int>,
    val qaMode: Boolean,
    private val growthBookFeatures: String
) {

    private val json: Json by lazy {
        Json {
            prettyPrint = true; isLenient = true; ignoreUnknownKeys = true
        }
    }

    fun feature(featureKey: String): GBFeatureResult? {
        val features = json.decodeFromString<FeaturesDataModel>(growthBookFeatures)
        val feature = features.features[featureKey] ?: return null

        return GBFeatureEvaluator().evaluateFeature(
            feature,
            attributes,
            featureKey,
            enabled,
            forcedVariations,
            qaMode
        )
    }
}