package com.sdk.growthbook.local

import com.sdk.growthbook.Utils.TrackingCallback
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBLocalContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class GrowthBookLocalSDK(
    private val localContext: GBLocalContext,
    private val growthBookFeatures: String,
    private val trackingCallback: TrackingCallback = { _, _ -> }
) {

    private val gBFeatureEvaluator: GBFeatureEvaluator by lazy { GBFeatureEvaluator() }

    private val json: Json by lazy {
        Json {
            prettyPrint = true; isLenient = true; ignoreUnknownKeys = true
        }
    }

    fun feature(featureKey: String): GBFeatureResult? {
        val features = json.decodeFromString<FeaturesDataModel>(growthBookFeatures)
        val feature = features.features[featureKey] ?: return null

        return gBFeatureEvaluator.evaluateFeature(
            feature,
            featureKey,
            localContext,
            trackingCallback
        )
    }
}
