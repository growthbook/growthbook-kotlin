package com.sdk.growthbook.Utils

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Constants Class - GrowthBook
 */
internal class Constants {

    companion object {

        /**
         * ID Attribute Key
         */
        const val idAttributeKey = "id"

        /**
         * Identifier for Caching Feature Data in Internal Storage File
         */
        const val featureCache = "FeatureCache"
    }
}

/**
 * Type Alias for Feature in GrowthBook
 */
internal typealias GBFeatures = HashMap<String, GBFeature>

/**
 * Type Alias for Condition Element in GrowthBook Rules
 */
typealias GBCondition = JsonElement

/**
 * Handler for Refresh Cache Request
 * It updates back whether cache was refreshed or not
 */
typealias GBCacheRefreshHandler = (Boolean, GBError?) -> Unit

/**
 * Triple Tuple for GrowthBook Namespaces
 * It has ID, StartRange & EndRange
 */
typealias GBNameSpace = Triple<String, Float, Float>

/**
 * Double Tuple for GrowthBook Ranges
 */
typealias GBBucketRange = Pair<Float, Float>

/**
 * GrowthBook Error Class to handle any error / exception scenario
 */
class GBError(error: Throwable?) {

    /**
     * Error Message for the caught error / exception
     */
    lateinit var errorMessage: String

    /**
     * Error Stacktrace for the caught error / exception
     */
    lateinit var stackTrace: String

    /**
     * Constructor for initializing
     */
    init {
        if (error != null) {
            errorMessage = error.message ?: ""
            stackTrace = error.stackTraceToString()
        }
    }
}

@Serializable
class GBFilter(
    var seed: String,
    var ranges: List<GBBucketRange>,
    var attribute: String,
    var hashVersion: Int
) {}

@Serializable
class GBVariationMeta(
    var key: String?,
    var name: String?,
    var passthrough: Boolean?
) {}

class GBTrackData(
    var experiment: GBExperiment,
    var experimentResult: GBExperimentResult
) {}