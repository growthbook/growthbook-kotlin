package com.sdk.growthbook.Utils

import com.sdk.growthbook.model.GBExperimentOverride
import com.sdk.growthbook.model.GBFeature
import io.ktor.util.*

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

typealias GBCacheRefreshHandler = (Boolean) -> Unit

class GBError : Exception() {
    val errorMessage = this.message
    val stackTrace = this.stackTraceToString()
}