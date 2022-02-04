package com.sdk.growthbook.tests

import com.sdk.growthbook.Evaluators.GBAttributeType
import com.sdk.growthbook.Evaluators.GBConditionEvaluator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GBConditionTests {

    lateinit var evalConditions : JsonArray

    @BeforeTest
    fun setUp() {
        evalConditions = GBTestHelper.getEvalConditionData()
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

        assertTrue(failedScenarios.size == 0)

    }

    @Test
    fun testInValidConditionObj(){
        val evaluator = GBConditionEvaluator()
        assertFalse(evaluator.evalCondition(JsonObject(HashMap()), JsonArray(ArrayList())))

        assertFalse(evaluator.isOperatorObject(JsonObject(HashMap())))

        assertTrue(evaluator.getType(null).toString() == GBAttributeType.gbUnknown.toString())

        assertTrue(evaluator.getPath(JsonPrimitive("test"), "key") == null)

        assertTrue(evaluator.evalConditionValue(JsonObject(HashMap()), null) == false)

        assertTrue(evaluator.evalOperatorCondition("${"$"}lte", JsonPrimitive("abc"), JsonPrimitive("abc")))

        assertTrue(evaluator.evalOperatorCondition("${"$"}gte", JsonPrimitive("abc"), JsonPrimitive("abc")))
    }
}