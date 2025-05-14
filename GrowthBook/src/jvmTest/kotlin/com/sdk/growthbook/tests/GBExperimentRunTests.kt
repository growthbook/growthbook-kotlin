package com.sdk.growthbook.tests

import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import com.sdk.growthbook.integration.buildSDK
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.evaluators.GBExperimentEvaluator
import com.sdk.growthbook.evaluators.EvaluationContext
import com.sdk.growthbook.evaluators.GBExperimentHelper
import com.sdk.growthbook.evaluators.UserContext
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.model.toGbNumber
import com.sdk.growthbook.serializable_model.SerializableGBExperiment
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.kotlinx.serialization.gbSerialize

class GBExperimentRunTests {

    private lateinit var evalConditions: JsonArray

    @BeforeTest
    fun setUp() {
        evalConditions = GBTestHelper.getRunExperimentData()
    }

    @Test
    fun testExperiments() {
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {
                val testContext =
                    GBTestHelper.jsonParser.decodeFromJsonElement(
                        GBContextTest.serializer(),
                        item[1]
                    )
                val serializableGbExperiment =
                    GBTestHelper.jsonParser.decodeFromJsonElement(
                        SerializableGBExperiment.serializer(),
                        item[2]
                    )

                val attributes = testContext
                    .attributes.jsonObject
                    .mapValues { GBValue.from(it.value) }

                val gbContext = GBContext(
                    apiKey = "",
                    enabled = testContext.enabled,
                    attributes = attributes,
                    forcedVariations = testContext.forcedVariations ?: HashMap(),
                    qaMode = testContext.qaMode,
                    trackingCallback = { _, _ ->

                    },
                    encryptionKey = "",
                    savedGroups = testContext.savedGroups.jsonObject.mapValues { GBValue.from(it.value) },
                )

                gbContext.features = testContext.features
                    .mapValues { it.value.gbDeserialize() }

                val testScopeEvaluationContext = EvaluationContext(
                    enabled = gbContext.enabled,
                    features = gbContext.features,
                    loggingEnabled = true,
                    savedGroups = gbContext.savedGroups,
                    gbExperimentHelper = GBExperimentHelper(),
                    onFeatureUsage = gbContext.onFeatureUsage,
                    forcedVariations = gbContext.forcedVariations,
                    trackingCallback = gbContext.trackingCallback,
                    stickyBucketService = gbContext.stickyBucketService,
                    userContext = UserContext(
                        qaMode = gbContext.qaMode,
                        attributes = gbContext.attributes,
                        stickyBucketAssignmentDocs = gbContext.stickyBucketAssignmentDocs,
                    )
                )

                val evaluator = GBExperimentEvaluator(testScopeEvaluationContext)
                val result = evaluator.evaluateExperiment(
                    attributeOverrides = attributes,
                    experiment = serializableGbExperiment.gbDeserialize(),
                )

                val resultJsonElement = result.value.gbSerialize()
                val status =
                    item[0].toString() + "\nExpected Result - " + item[3] + " & " + item[4] +
                        "\nActual result - " + resultJsonElement.toString() + " & " +
                        result.inExperiment + "\n\n"

                if (item[3].toString() == resultJsonElement.toString()
                    && item[4].toString() == result.inExperiment.toString()
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
    fun testOneTimeInvokeTrackingCallback() {
        var countTrackingCallback = 0
        val item = evalConditions.first()
        if (item is JsonArray) {
            val testContext =
                GBTestHelper.jsonParser.decodeFromJsonElement(
                    GBContextTest.serializer(),
                    item[1]
                )
            val serializableGbExperiment =
                GBTestHelper.jsonParser.decodeFromJsonElement(
                    SerializableGBExperiment.serializer(),
                    item[2]
                )
            val attributes = testContext
                .attributes.jsonObject
                .mapValues { GBValue.from(it.value) }

            val testScopeEvalContext = EvaluationContext(
                savedGroups = null,
                loggingEnabled = true,
                enabled = testContext.enabled,
                gbExperimentHelper = GBExperimentHelper(),
                forcedVariations = testContext.forcedVariations ?: emptyMap(),
                features = testContext.features.mapValues { it.value.gbDeserialize() },
                trackingCallback = { _, _ ->
                    countTrackingCallback += 1
                },
                stickyBucketService = null,
                onFeatureUsage = null,
                userContext = UserContext(
                    attributes = attributes,
                    qaMode = testContext.qaMode,
                    stickyBucketAssignmentDocs = null,
                )
            )

            val gbExperiment = serializableGbExperiment.gbDeserialize()
            val evaluator = GBExperimentEvaluator(testScopeEvalContext)
            evaluator.evaluateExperiment(
                experiment = gbExperiment,
                attributeOverrides = attributes
            )

            evaluator.evaluateExperiment(
                experiment = gbExperiment,
                attributeOverrides = attributes
            ) // second time for test count of callbacks

            println("Count of calls TrackingCallback - $countTrackingCallback")
            assertTrue(countTrackingCallback == 1)
        }
    }

    @Test
    fun `forcing example`() {
        val gb = buildSDK(
            json = "",
            attributes = mapOf("id" to 1.toGbNumber())
        )
        val experimentKey = "key-576"
        val experiment = GBExperiment(
            key = experimentKey,
            variations = listOf(GBNumber(0), GBNumber(1)),
        )

        val result1 = gb.run(experiment)
        assertTrue(result1.inExperiment)
        assertTrue(result1.hashUsed == true)
        assertEquals(result1.value, GBNumber(1))

        gb.setForcedVariations(
            mapOf(experimentKey to 0)
        )

        val result2 = gb.run(experiment)
        assertTrue(result2.inExperiment)
        assertTrue(result2.hashUsed == false)
        assertEquals(result2.value, GBNumber(0))
    }
}