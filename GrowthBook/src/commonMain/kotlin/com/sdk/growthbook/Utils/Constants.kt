package com.sdk.growthbook.Utils

import com.sdk.growthbook.model.GBFeature
import kotlinx.serialization.json.JsonElement

internal class Constants {

    companion object {
        val idAttributeKey = "id"

        val featureCache = "FeatureCache"

        val featurePath = "api/features/"
    }

}

internal typealias GBFeatures = HashMap<String, GBFeature>

typealias GBCondition = JsonElement

typealias GBCacheRefreshHandler = (Boolean) -> Unit

typealias GBNameSpace = Triple<String, Float, Float>

typealias GBBucketRange = Pair<Float, Float>

class GBError(error: Throwable?) {
    lateinit var errorMessage: String
    lateinit var stackTrace: String

    init {
        if (error != null) {
            errorMessage = error.message ?: ""
            stackTrace = error.stackTraceToString()
        }
    }
}