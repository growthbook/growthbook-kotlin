package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.toHashMap
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class GBStickyBucketingFeatureTests {
    private lateinit var evalConditions: JsonArray
    private lateinit var service: GBStickyBucketServiceImp

    @Before
    fun setUp() {
        evalConditions = GBTestHelper.getStickyBucketingData()
        service = GBStickyBucketServiceImp()
    }

    @Test
    fun testEvaluateFeatureWithStickyBucketingFeature() {
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
                    apiKey = "",
                    hostURL = "",
                    enabled = true,
                    attributes = attributes,
                    forcedVariations = testData.forcedVariations ?: HashMap(),
                    qaMode = false,
                    trackingCallback = { _, _ ->

                    },
                    encryptionKey = "",
                    stickyBucketService = service
                )

                if (testData.features != null) {
                    gbContext.features = testData.features
                }
                if (testData.forcedVariations != null) {
                    gbContext.forcedVariations = testData.forcedVariations.toHashMap()
                }
                if (testData.stickyBucketAssignmentDocs != null) {
                    gbContext.stickyBucketAssignmentDocs = testData.stickyBucketAssignmentDocs
                }
                val expectedExperimentResult: GBExperimentResultTest? = if (item[3] is JsonNull) {
                    null
                } else {
                    GBTestHelper.jsonParser.decodeFromJsonElement(
                        GBExperimentResultTest.serializer(),
                        item[3]
                    )
                }

                val stickyAssigmentDocs = item[4].jsonObject.toHashMap()

                val evaluator = GBFeatureEvaluator()
                val actualExperimentResult = evaluator.evaluateFeature(
                    context = gbContext,
                    featureKey = item[2].jsonPrimitive.content,
                    attributeOverrides = attributes
                ).experimentResult

                if (actualExperimentResult == null) {
                    println("ACTUAL RESULT IS NULL")
                }
                if (expectedExperimentResult == null) {
                    println("EXPECTED RESULT IS NULL")
                }
                println(
                    "ACTUAL RESULT: 1) ${actualExperimentResult?.bucket ?: "No bucket"}; " +
                        "2) ${actualExperimentResult?.hashAttribute ?: "No hasAttribute"}; " +
                        "3) ${actualExperimentResult?.hashValue ?: "No hashValue"}; " +
                        "4) ${actualExperimentResult?.inExperiment ?: "No in experiment"}; " +
                        "5) ${actualExperimentResult?.key ?: "No key"}; " +
                        "6) ${actualExperimentResult?.stickyBucketUsed ?: "No stickybucketUsed"}; " +
                        "7) ${actualExperimentResult?.value ?: "No value"}; " +
                        "8) ${actualExperimentResult?.variationId ?: "No variationId"} " +
                        "9) ${actualExperimentResult?.featureId} END"
                )

                println(
                    "EXPECTED RESULT: 1) ${expectedExperimentResult?.bucket ?: "No bucket"}; " +
                        "2) ${expectedExperimentResult?.hashAttribute ?: "No hasAttribute"}; " +
                        "3) ${expectedExperimentResult?.hashValue ?: "No hashValue"}; " +
                        "4) ${expectedExperimentResult?.inExperiment ?: "No in experiment"}; " +
                        "5) ${expectedExperimentResult?.key ?: "No key"}; " +
                        "6) ${expectedExperimentResult?.stickyBucketUsed ?: "No stickybucketUsed"}; " +
                        "7) ${expectedExperimentResult?.value ?: "No value"}; " +
                        "8) ${expectedExperimentResult?.variationId ?: "No variationId"} " +
                        "9) ${expectedExperimentResult?.featureId} END"
                )


                println()
                val status =
                    item[0].toString() +
                        "\nExpected Result - " + item[3] + " & " + stickyAssigmentDocs + "\n\n" +
                        "\nActual result - " + actualExperimentResult.toString() + " & " + gbContext.stickyBucketAssignmentDocs + "\n\n"

                if (expectedExperimentResult?.value.toString() == actualExperimentResult?.value.toString()
                    && stickyAssigmentDocs.size == gbContext.stickyBucketAssignmentDocs?.size) {
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
