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
internal typealias GBFeatures = Map<String, GBFeature>

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

/**
 * Object used for mutual exclusion and filtering users out of experiments based on random hashes. Has the following properties
 */
@Serializable
class GBFilter(
    /**
     * The seed used in the hash
     */
    var seed: String,
    /**
     * Array of ranges that are included
     */
    var ranges: List<GBBucketRange>,
    /**
     * The attribute to use (default to "id")
     */
    var attribute: String?,
    /**
     * The hash version to use (default to 2)
     */
    var hashVersion: Int?
) {}

/**
 * Meta info about an experiment variation. Has the following properties
 */
@Serializable
class GBVariationMeta(
    /**
     * A unique key for this variation
     */
    var key: String?,
    /**
     * A human-readable name for this variation
     */
    var name: String?,
    /**
     * Used to implement holdout groups
     */
    var passthrough: Boolean?
) {}

/**
 * Used for remote feature evaluation to trigger the TrackingCallback. An object with 2 properties
 */
class GBTrackData(
    /**
     * experiment - Experiment
     */
    var experiment: GBExperiment,
    /**
     * result - ExperimentResult
     */
    var experimentResult: GBExperimentResult
) {}