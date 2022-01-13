package com.sdk.growthbook.Utils

import com.sdk.growthbook.model.GBExperimentOverride
import com.sdk.growthbook.model.GBFeature
import io.ktor.util.*
import kotlinx.serialization.json.JsonElement

internal class Constants {

    companion object {
        val idAttributeKey = "id"

        val configCache = "ConfigCache"
        val featureCache = "FeatureCache"

        val configPath = "config/"

        val featurePath = "api/features/"
    }

}

typealias GBOverrides = HashMap<String, GBExperimentOverride>

typealias GBFeatures = HashMap<String, GBFeature>

typealias GBCondition = HashMap<String, JsonElement>

typealias GBCacheRefreshHandler = (Boolean) -> Unit

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