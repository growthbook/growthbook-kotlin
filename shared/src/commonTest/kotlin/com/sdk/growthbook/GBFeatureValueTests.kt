package com.sdk.growthbook

import com.sdk.growthbook.Evaluators.GBFeatureEvaluator
import com.sdk.growthbook.Utils.toHashMap
import com.sdk.growthbook.model.GBContext
import kotlinx.serialization.json.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GBFeatureValueTests {

    lateinit var evalConditions : JsonArray

    @BeforeTest
    fun setUp() {
        evalConditions = TestData.getFeatureData()

    }

    @Test
    fun testFeatures(){
        var failedScenarios : ArrayList<String> = ArrayList()
        var passedScenarios : ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val testData = TestData.jsonParser.decodeFromJsonElement<GBFeaturesTest>(item[1])

                val attributes = testData.attributes.jsonObject.toHashMap()

                val gbContext = GBContext("",
                    enabled = true, attributes = attributes, "", forcedVariations = HashMap(), false, { _, _ ->

                    })
                if (testData.features != null) {
                    gbContext.features = testData.features
                }


                val evaluator = GBFeatureEvaluator()
                val result = evaluator.evaluateFeature(gbContext, item[2].jsonPrimitive.content)

                val expectedResult = TestData.jsonParser.decodeFromJsonElement<GBFeatureResultTest>(item[3])

                val status = item[0].toString() +
                        "\nExpected Result - " +
                        "\nValue - " + expectedResult.value.toString() +
                        "\nOn - " + expectedResult.on.toString() +
                        "\nOff - " + expectedResult.off.toString() +
                        "\nSource - " + expectedResult.source +
                        "\nActual result - " +
                        "\nValue - " + result.value.toString() +
                        "\nOn - " + result.on.toString() +
                        "\nOff - " + result.off.toString() +
                        "\nSource - " + result.source + "\n\n"

                if (result.value.toString() == expectedResult.value.toString() &&
                    result.on.toString() == expectedResult.on.toString() &&
                    result.off.toString() == expectedResult.off.toString() &&
                    result.source.toString() == expectedResult.source ) {
                    passedScenarios.add(status)
                } else {
                    failedScenarios.add(status)
                }

            }
        }

        print("\nTOTAL TESTS - "+ evalConditions.size)
        print("\nPassed TESTS - "+ passedScenarios.size)
        print("\nFailed TESTS - "+ failedScenarios.size)
        print("\n")
        print(failedScenarios)

        assertTrue(failedScenarios.size == 0)

    }
}