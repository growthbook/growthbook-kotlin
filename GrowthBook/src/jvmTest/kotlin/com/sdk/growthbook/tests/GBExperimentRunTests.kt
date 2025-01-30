package com.sdk.growthbook.tests

import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.sdk.growthbook.integration.buildSDK
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.utils.toHashMap
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.evaluators.GBExperimentEvaluator
import com.sdk.growthbook.evaluators.EvaluationContext
import com.sdk.growthbook.evaluators.UserContext

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
                val experiment =
                    GBTestHelper.jsonParser.decodeFromJsonElement(
                        GBExperiment.serializer(),
                        item[2]
                    )

                val attributes = testContext.attributes.jsonObject.toHashMap()

                val gbContext = GBContext(
                    apiKey = "",
                    hostURL = "",
                    enabled = testContext.enabled,
                    attributes = attributes,
                    forcedVariations = testContext.forcedVariations ?: HashMap(),
                    qaMode = testContext.qaMode,
                    trackingCallback = { _, _ ->

                    },
                    encryptionKey = "",
                    savedGroups = testContext.savedGroups.jsonObject.toHashMap()
                )

                gbContext.features = testContext.features
                    .mapValues { it.value.gbDeserialize() }

                val testScopeEvaluationContext = EvaluationContext(
                    enabled = gbContext.enabled,
                    features = gbContext.features,
                    loggingEnabled = true,
                    savedGroups = gbContext.savedGroups,
                    forcedVariations = gbContext.forcedVariations,
                    trackingCallback = gbContext.trackingCallback,
                    stickyBucketService = gbContext.stickyBucketService,
                    onFeatureUsage = gbContext.onFeatureUsage,
                    userContext = UserContext(
                        qaMode = gbContext.qaMode,
                        attributes = gbContext.attributes,
                        stickyBucketAssignmentDocs = gbContext.stickyBucketAssignmentDocs,
                    )
                )

                val evaluator = GBExperimentEvaluator(testScopeEvaluationContext)
                val result = evaluator.evaluateExperiment(
                    experiment = experiment,
                    attributeOverrides = attributes
                )

                val status =
                    item[0].toString() + "\nExpected Result - " + item[3] + " & " + item[4] +
                        "\nActual result - " + result.value.toString() + " & " +
                        result.inExperiment + "\n\n"

                if (item[3].toString() == result.value.toString()
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
            val experiment =
                GBTestHelper.jsonParser.decodeFromJsonElement(
                    GBExperiment.serializer(),
                    item[2]
                )
            val attributes = testContext.attributes.jsonObject.toHashMap()

            val testScopeEvalContext = EvaluationContext(
                enabled = testContext.enabled,
                features = testContext.features.mapValues { it.value.gbDeserialize() },
                loggingEnabled = true,
                savedGroups = null,
                forcedVariations = testContext.forcedVariations ?: emptyMap(),
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
            val evaluator = GBExperimentEvaluator(testScopeEvalContext)

            evaluator.evaluateExperiment(
                experiment = experiment,
                attributeOverrides = attributes
            )

            evaluator.evaluateExperiment(
                experiment = experiment,
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
            attributes = mapOf("id" to 1)
        )
        val experimentKey = "key-576"
        val experiment = GBExperiment(
            key = experimentKey,
            variations = listOf(JsonPrimitive(0), JsonPrimitive(1))
        )

        val result1 = gb.run(experiment)
        assertTrue(result1.inExperiment)
        assertTrue(result1.hashUsed == true)
        assertEquals(result1.value, JsonPrimitive(1))

        gb.setForcedVariations(
            mapOf(experimentKey to 0)
        )

        val result2 = gb.run(experiment)
        assertTrue(result2.inExperiment)
        assertTrue(result2.hashUsed == false)
        assertEquals(result2.value, JsonPrimitive(0))
    }
}