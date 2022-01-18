package com.sdk.growthbook

import com.sdk.growthbook.model.GBConditionEvaluator
import kotlinx.serialization.json.JsonArray
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GBConditionTests {

    lateinit var evalConditions : JsonArray

    @BeforeTest
    fun setUp() {
        evalConditions = TestData.getEvalConditionData()
    }

    @Test
    fun testConditions(){
        var failedScenarios : ArrayList<String> = ArrayList()
        var passedScenarios : ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {
                val evaluator = GBConditionEvaluator()
                val result = evaluator.evalCondition(item[2], item[1])

                val status = item[0].toString() + "\nExpected Result - " +item[3] + "\nActual result - " + result+"\n\n"

                if (item[3].toString() == result.toString()) {
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
    }
}