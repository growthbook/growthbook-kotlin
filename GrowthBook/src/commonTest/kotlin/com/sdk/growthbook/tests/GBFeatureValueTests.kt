package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.toHashMap
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.model.GBContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GBFeatureValueTests {

    private lateinit var evalConditions: JsonArray

    @BeforeTest
    fun setUp() {
        evalConditions = GBTestHelper.getFeatureData()
    }

    @Test
    fun testFeatures() {
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val testData =
                    GBTestHelper.jsonParser.decodeFromJsonElement(
                        GBFeaturesTest.serializer(),
                        item[1]
                    )

                val attributes = testData.attributes.jsonObject.toHashMap()

                val gbContext = GBContext(
                    apiKey = "", hostURL = "",
                    enabled = true, attributes = attributes, forcedVariations = HashMap(),
                    qaMode = false, trackingCallback = { _, _ ->

                    }, encryptionKey = ""
                )
                if (testData.features != null) {
                    gbContext.features = testData.features
                }

                val evaluator = GBFeatureEvaluator()
                val result = evaluator.evaluateFeature(
                    context = gbContext,
                    featureKey = item[2].jsonPrimitive.content,
                    attributeOverrides = attributes
                )

                val expectedResult =
                    GBTestHelper.jsonParser.decodeFromJsonElement(
                        GBFeatureResultTest.serializer(),
                        item[3]
                    )

                val status = item[0].toString() +
                    "\nExpected Result - " +
                    "\nValue - " + expectedResult.value.toString() +
                    "\nOn - " + expectedResult.on.toString() +
                    "\nOff - " + expectedResult.off.toString() +
                    "\nSource - " + expectedResult.source +
                    "\nExperiment - " + expectedResult.experiment?.key +
                    "\nExperiment Result - " + expectedResult.experimentResult?.variationId +
                    "\nActual result - " +
                    "\nValue - " + result.value.toString() +
                    "\nOn - " + result.on.toString() +
                    "\nOff - " + result.off.toString() +
                    "\nSource - " + result.source +
                    "\nExperiment - " + result.experiment?.key +
                    "\nExperiment Result - " + result.experimentResult?.variationId + "\n\n"

                if (result.value.toString() == expectedResult.value.toString() &&
                    result.on.toString() == expectedResult.on.toString() &&
                    result.off.toString() == expectedResult.off.toString() &&
                    result.source.toString() == expectedResult.source &&
                    result.experiment?.key == expectedResult.experiment?.key &&
                    result.experimentResult?.variationId == expectedResult.experimentResult?.variationId
                ) {
                    passedScenarios.add(status)
                } else {
                    failedScenarios.add(status)
                }
            }
        }

        print("\nTOTAL TESTS - " + evalConditions.size)
        print("\nPassed TESTS - " + passedScenarios.size)
        print("\nFailed TESTS - " + failedScenarios.size)
        print("\n")
        print(failedScenarios)

        assertTrue(failedScenarios.size == 0)
    }
}