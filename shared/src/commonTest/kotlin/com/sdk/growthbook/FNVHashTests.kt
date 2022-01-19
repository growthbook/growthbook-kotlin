package com.sdk.growthbook

import com.sdk.growthbook.Utils.FNV
import kotlinx.serialization.json.*
import kotlin.test.BeforeTest
import kotlin.test.Test

class FNVHashTests {

    lateinit var evalConditions : JsonArray

    @BeforeTest
    fun setUp() {
        evalConditions = TestData.getFNVHashData()

    }


    @Test
    fun testHash(){
        var failedScenarios : ArrayList<String> = ArrayList()
        var passedScenarios : ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val testContext = item[0].jsonPrimitive.content
                val experiment = item[1].jsonPrimitive.content

                val evaluator = FNV()
                val result = evaluator.hashValue(testContext)

                val status = item[0].toString() + "\nExpected Result - " +item[1].toString() + "\nActual result - " + result + "\n"

                if (experiment == result.toString()) {
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

//        assertTrue(failedScenarios.size == 0)

    }

    @Test
    fun testFNVHash(){

    }
}