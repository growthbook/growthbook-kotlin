package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.serializable_model.gbDeserialize
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

                val attributes = testData
                    .attributes.jsonObject
                    .mapValues { GBValue.from(it.value) }

                val gbContext = GBContext(
                    apiKey = "", hostURL = "",
                    enabled = true, attributes = attributes, forcedVariations = HashMap(),
                    qaMode = false, trackingCallback = { _, _ ->

                    }, encryptionKey = "",
                )
                if (testData.features != null) {
                    gbContext.features = testData.features
                        .mapValues { it.value.gbDeserialize() }
                }
                if (testData.forcedVariations != null) {
                    gbContext.forcedVariations = testData.forcedVariations
                        .mapValues {
                            it.value.jsonPrimitive.intOrNull ?: 0
                        }
                }

                if (testData.savedGroups != null) {
                    gbContext.savedGroups = testData
                        .savedGroups.jsonObject
                        .mapValues { GBValue.from(it.value) }
                }

                val evaluator = GBFeatureEvaluator(
                    GBTestHelper.createTestScopeEvaluationContext(
                        gbContext.features, attributes,
                        savedGroups = gbContext.savedGroups,
                        forcedVariations = gbContext.forcedVariations,
                    )
                )
                val result = evaluator.evaluateFeature(
                    featureKey = item[2].jsonPrimitive.content,
                    attributeOverrides = attributes
                )

                val expectedResult =
                    GBTestHelper.jsonParser.decodeFromJsonElement(
                        GBFeatureResultTest.serializer(),
                        item[3]
                    )

                val resultJsonElement: JsonElement? = result.gbValue?.gbSerialize()
                val resultJsonPrimitive =  resultJsonElement as? JsonPrimitive?
                val status = item[0].toString() +
                    "\nExpected Result - " +
                    "\nValue - " + expectedResult.value.content +
                    "\nOn - " + expectedResult.on.toString() +
                    "\nOff - " + expectedResult.off.toString() +
                    "\nSource - " + expectedResult.source +
                    "\nExperiment - " + expectedResult.experiment?.key +
                    "\nExperiment Result - " + expectedResult.experimentResult?.variationId +
                    "\nActual result - " +
                    "\nValue - " + resultJsonPrimitive?.content.toString() +
                    "\nOn - " + result.on.toString() +
                    "\nOff - " + result.off.toString() +
                    "\nSource - " + result.source +
                    "\nExperiment - " + result.experiment?.key +
                    "\nExperiment Result - " + result.experimentResult?.variationId + "\n\n"

                if (
                    resultJsonPrimitive?.content.toString() == expectedResult.value.content &&
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

    @Test
    fun `whether featureUsageCallback is called`() {
        val expectedNumberOfOnFeatureUsageCalls = 1
        var actualNumberOfOnFeatureUsageCalls = 0

        val builder = GBSDKBuilder(
            apiKey = "",
            hostURL = "",
            networkDispatcher = MockNetworkClient(
                successResponse = null,
                error = null,
            ),
            attributes = emptyMap(),
            trackingCallback = { _, _ -> },
        )

        builder.setFeatureUsageCallback { _, _ ->
            actualNumberOfOnFeatureUsageCalls++
        }

        val sdk: GrowthBookSDK = builder.javaCompatibleInitialize()

        for (item in evalConditions) {
            if (item is JsonArray) {
                sdk.feature(id = item[2].jsonPrimitive.content)
                break
            }
        }

        assertEquals(expectedNumberOfOnFeatureUsageCalls, actualNumberOfOnFeatureUsageCalls)
    }

    @Test
    fun `whether featureUsageCallback is called on context level`() {
        val expectedNumberOfOnFeatureUsageCalls = 1
        var actualNumberOfOnFeatureUsageCalls = 0

        for (item in evalConditions) {
            if (item is JsonArray) {
                val testData =
                    GBTestHelper.jsonParser.decodeFromJsonElement(
                        GBFeaturesTest.serializer(),
                        item[1]
                    )

                val attributes = testData
                    .attributes.jsonObject
                    .mapValues { GBValue.from(it.value) }

                val testScopeEvalContext = GBTestHelper.createTestScopeEvaluationContext(
                    attributes = attributes,
                    features = if (testData.features != null) {
                        testData.features
                            .mapValues { it.value.gbDeserialize() }
                    } else {
                        emptyMap()
                    },
                    onFeatureUsage = { _, _ ->
                        actualNumberOfOnFeatureUsageCalls++
                    }
                )

                val evaluator = GBFeatureEvaluator(testScopeEvalContext)
                evaluator.evaluateFeature(
                    featureKey = item[2].jsonPrimitive.content,
                    attributeOverrides = attributes
                )

                assertEquals(expectedNumberOfOnFeatureUsageCalls, actualNumberOfOnFeatureUsageCalls)
                break
            }
        }
    }
}
