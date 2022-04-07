package com.sdk.growthbook.Utils

import com.sdk.growthbook.model.GBFeature
import kotlinx.serialization.json.JsonElement

/**
 * Constants Class - GrowthBook
 */
internal class Constants {

    companion object {

        /**
         * ID Attribute Key
         */
        val idAttributeKey = "id"

        /**
         * Identifier for Caching Feature Data in Internal Storage File
         */
        val featureCache = "FeatureCache"

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
typealias GBCacheRefreshHandler = (Boolean) -> Unit

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