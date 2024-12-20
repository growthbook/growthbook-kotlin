package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.toHashMap
import com.sdk.growthbook.evaluators.GBExperimentEvaluator
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.serializable_model.gbDeserialize
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

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

                val evaluator = GBExperimentEvaluator()
                val result = evaluator.evaluateExperiment(
                    context = gbContext,
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
            val gbContext = GBContext(
                apiKey = "",
                hostURL = "",
                enabled = testContext.enabled,
                attributes = attributes,
                forcedVariations = testContext.forcedVariations ?: HashMap(),
                qaMode = testContext.qaMode,
                trackingCallback = { _, _ ->
                    countTrackingCallback += 1
                }, encryptionKey = ""
            )
            val evaluator = GBExperimentEvaluator()

            evaluator.evaluateExperiment(
                context = gbContext,
                experiment = experiment,
                attributeOverrides = attributes
            )

            evaluator.evaluateExperiment(
                context = gbContext,
                experiment = experiment,
                attributeOverrides = attributes
            ) // second time for test count of callbacks

            println("Count of calls TrackingCallback - $countTrackingCallback")
            assertTrue(countTrackingCallback == 1)
        }
    }
}