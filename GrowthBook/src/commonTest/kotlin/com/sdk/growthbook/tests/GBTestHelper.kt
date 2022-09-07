package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.model.GBExperiment
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class GBTestHelper {

    companion object {

        val jsonParser = Json { ignoreUnknownKeys = true }
        val testData = jsonParser.decodeFromString<JsonElement>(gbTestCases)

        fun getEvalConditionData(): JsonArray {
            val array = testData.jsonObject.get("evalCondition") as JsonArray
            return array
        }

        fun getRunExperimentData(): JsonArray {
            val array = testData.jsonObject.get("run") as JsonArray
            return array
        }

        fun getFNVHashData(): JsonArray {
            val array = testData.jsonObject.get("hash") as JsonArray
            return array
        }

        fun getFeatureData(): JsonArray {

            val array = testData.jsonObject.get("feature") as JsonArray
            return array
        }

        fun getBucketRangeData(): JsonArray {
            val array = testData.jsonObject.get("getBucketRange") as JsonArray
            return array
        }

        fun getInNameSpaceData(): JsonArray {
            val array = testData.jsonObject.get("inNamespace") as JsonArray
            return array
        }

        fun getChooseVariationData(): JsonArray {
            val array = testData.jsonObject.get("chooseVariation") as JsonArray
            return array
        }

        fun getEqualWeightsData(): JsonArray {
            val array = testData.jsonObject.get("getEqualWeights") as JsonArray
            return array
        }
    }
}

@Serializable
class GBContextTest (
    val attributes: JsonElement = JsonObject(HashMap()),
    val qaMode: Boolean = false,
    val enabled: Boolean = true,
    val forcedVariations: HashMap<String, Int>? = null
)

@Serializable
class GBFeaturesTest(
    val features : GBFeatures? = null,
    val attributes: JsonElement = JsonObject(HashMap())
)

@Serializable
class GBFeatureResultTest(
    val value: JsonPrimitive,
    val on : Boolean,
    val off: Boolean,
    val source: String,
    val experiment: GBExperiment? = null,
    val experimentResult: GBExperimentResultTest? = null
)

@Serializable
class GBExperimentResultTest(
    /// Whether or not the user is part of the experiment
    val inExperiment: Boolean,
    /// The array index of the assigned variation
    val variationId: Int,
    /// The array value of the assigned variation
    val value: JsonElement,
    /// The user attribute used to assign a variation
    val hashAttribute: String? = null,
    ///  The value of that attribute
    val hashValue: String? = null

)
