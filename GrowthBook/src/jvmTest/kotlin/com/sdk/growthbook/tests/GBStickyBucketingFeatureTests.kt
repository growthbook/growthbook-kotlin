package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.stickybucket.GBStickyBucketServiceImp
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import com.sdk.growthbook.utils.GBStickyAttributeKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue
import com.sdk.growthbook.kotlinx.serialization.from

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

                val attributes = testData
                    .attributes.jsonObject
                    .mapValues { GBValue.from(it.value) }

                val gbContext = GBContext(
                    apiKey = "",
                    hostURL = "",
                    enabled = true,
                    attributes = attributes,
                    forcedVariations = emptyMap(),
                    qaMode = false,
                    trackingCallback = { _, _ ->

                    },
                    encryptionKey = "",
                    stickyBucketService = service
                )

                if (testData.features != null) {
                    gbContext.features = testData.features
                        .mapValues { it.value.gbDeserialize() }
                }

                val listActualStickyAssignmentsDoc =
                    mutableListOf<GBStickyAssignmentsDocument>()

                item[2].jsonArray.forEach {
                    listActualStickyAssignmentsDoc.add(
                        Json.decodeFromJsonElement(GBStickyAssignmentsDocument.serializer(), it)
                    )
                }

                val mapOfDocForContext =
                    mutableMapOf<String, GBStickyAssignmentsDocument>()
                for (doc in listActualStickyAssignmentsDoc) {
                    val key = "${doc.attributeName}||${doc.attributeValue}"
                    mapOfDocForContext[key] = doc
                }

                gbContext.stickyBucketAssignmentDocs = mapOfDocForContext

                val expectedExperimentResult: GBExperimentResultTest? = if (item[4] is JsonNull) {
                    null
                } else {
                    GBTestHelper.jsonParser.decodeFromJsonElement(
                        GBExperimentResultTest.serializer(),
                        item[4]
                    )
                }

                val expectedStickyAssignmentDocs =
                    mutableMapOf<GBStickyAttributeKey, GBStickyAssignmentsDocument>()
                for (doc in item[5].jsonObject) {
                    expectedStickyAssignmentDocs[doc.key] =
                        Json.decodeFromJsonElement(GBStickyAssignmentsDocument.serializer(), doc.value)
                }

                val testScopeEvalContext =
                    GBTestHelper.createTestScopeEvaluationContext(
                        gbContext.features, attributes,
                        stickyBucketService = service,
                        stickyBucketAssignmentDocs = mapOfDocForContext,
                        savedGroups = gbContext.savedGroups,
                        forcedVariations = gbContext.forcedVariations,
                    )

                val evaluator = GBFeatureEvaluator(testScopeEvalContext)
                val actualExperimentResult = evaluator.evaluateFeature(
                    featureKey = item[3].jsonPrimitive.content,
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
                        "6) ${
                            actualExperimentResult?.stickyBucketUsed ?: "No stickybucketUsed"
                        }; " +
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
                        "6) ${
                            expectedExperimentResult?.stickyBucketUsed ?: "No stickybucketUsed"
                        }; " +
                        "7) ${expectedExperimentResult?.value ?: "No value"}; " +
                        "8) ${expectedExperimentResult?.variationId ?: "No variationId"} " +
                        "9) ${expectedExperimentResult?.featureId} END"
                )

                val status =
                    item[0].toString() +
                        "\nExpected Result - " + item[4] + " & " + expectedStickyAssignmentDocs + "\n\n" +
                        "\nActual result - " + actualExperimentResult.toString() + " & " +
                        gbContext.stickyBucketAssignmentDocs + "\n\n"

                val actualValue = actualExperimentResult?.value
                val expectedValue = expectedExperimentResult?.value?.let(GBValue::from)
                if (
                    expectedValue == actualValue
                    && expectedExperimentResult?.inExperiment == actualExperimentResult
                        ?.inExperiment
                    && expectedExperimentResult?.stickyBucketUsed == actualExperimentResult
                        ?.stickyBucketUsed
                    && expectedExperimentResult?.featureId == actualExperimentResult?.featureId
                    && expectedExperimentResult?.bucket == actualExperimentResult?.bucket
                    && expectedExperimentResult?.variationId == actualExperimentResult?.variationId
                    && expectedExperimentResult?.hashUsed == actualExperimentResult?.hashUsed
                    && expectedExperimentResult?.passthrough == actualExperimentResult?.passthrough

                    && expectedStickyAssignmentDocs == testScopeEvalContext.userContext.stickyBucketAssignmentDocs
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
