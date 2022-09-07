package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBExperimentEvaluator
import com.sdk.growthbook.utils.toHashMap
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBExperiment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GBExperimentRunTests {

    lateinit var evalConditions : JsonArray

    @BeforeTest
    fun setUp() {
        evalConditions = GBTestHelper.getRunExperimentData()
    }

    @Test
    fun testExperiments(){
        var failedScenarios : ArrayList<String> = ArrayList()
        var passedScenarios : ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val testContext = GBTestHelper.jsonParser.decodeFromJsonElement<GBContextTest>(item[1])
                val experiment = GBTestHelper.jsonParser.decodeFromJsonElement<GBExperiment>(item[2])

                val attributes = testContext.attributes.jsonObject.toHashMap()

                val gbContext = GBContext("", hostURL = "",
                    enabled = testContext.enabled, attributes = attributes, forcedVariations = testContext.forcedVariations ?: HashMap(),
                    qaMode = testContext.qaMode, trackingCallback = { _, _ ->

                    })

                val evaluator = GBExperimentEvaluator()
                val result = evaluator.evaluateExperiment(gbContext, experiment)

                val status = item[0].toString() + "\nExpected Result - " +item[3] + " & " + item[4] + "\nActual result - " + result.value.toString() + " & " + result.inExperiment+"\n\n"

                if (item[3].toString() == result.value.toString() && item[4].toString() == result.inExperiment.toString()) {
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