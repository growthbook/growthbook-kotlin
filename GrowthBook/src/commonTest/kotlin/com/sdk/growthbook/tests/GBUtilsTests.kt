package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GBUtilsTests {

    @Test
    fun testHash(){
        val evalConditions = GBTestHelper.getFNVHashData()
        var failedScenarios : ArrayList<String> = ArrayList()
        var passedScenarios : ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val testContext = item[0].jsonPrimitive.content
                val experiment = item[1].jsonPrimitive.content

                val result = GBUtils.hash(testContext)

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

        assertTrue(failedScenarios.size == 0)

    }

    @Test
    fun testBucketRange(){
        val evalConditions = GBTestHelper.getBucketRangeData()
        var failedScenarios : ArrayList<String> = ArrayList()
        var passedScenarios : ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val numVariations = item[1].jsonArray[0].jsonPrimitive.content.toIntOrNull()
                val coverage = item[1].jsonArray[1].jsonPrimitive.content.toFloatOrNull()
                var weights : List<Float>? = null
                if (item[1].jsonArray[2] != JsonNull) {
                    weights =
                        (item[1].jsonArray[2].jsonArray.toList() as? List<String>)?.map { value -> value.toFloat() }
                }


                val bucketRange = GBUtils.getBucketRanges(numVariations ?: 1, coverage ?: 1F, weights ?: ArrayList())


                val status = item[0].toString() + "\nExpected Result - " +item[2].jsonArray.toString() + "\nActual result - " + bucketRange.toJsonElement().jsonArray.toString() + "\n"

                if (compareBucket(item[2].jsonArray.toList() as List<List<Float>>, bucketRange)) {
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

    fun compareBucket(expectedResults : List<List<Float>>, calaculatedResults: List<GBBucketRange>) : Boolean {

        var pairExpectedResults = getPairedData(expectedResults)

        if (pairExpectedResults.size != expectedResults.size) {
            return false
        }

        var result = true
        for (i in 0..pairExpectedResults.size-1) {
            var source = pairExpectedResults[i]
            var target = calaculatedResults[i]

            if (source.first != target.first || source.second != target.second) {
                result = false
                break
            }
        }

        return result

    }

    fun getPairedData(items :  List<List<Float>>) : List<GBBucketRange> {
        var pairExpectedResults : ArrayList<Pair<Float, Float>> = ArrayList()

        for (item in items) {
            val pair = item.zipWithNext().single()
            pairExpectedResults.add(Pair((pair.first as JsonPrimitive).content.toFloat(), (pair.second as JsonPrimitive).content.toFloat()))
        }
        return pairExpectedResults
    }

    @Test
    fun testChooseVariation(){
        val evalConditions = GBTestHelper.getChooseVariationData()
        var failedScenarios : ArrayList<String> = ArrayList()
        var passedScenarios : ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val hash = item[1].jsonPrimitive.content.toFloatOrNull()
                val rangeData = getPairedData(item[2].jsonArray.toList() as List<List<Float>>)

                val result = GBUtils.chooseVariation(hash ?: 0F, rangeData)

                val status = item[0].toString() + "\nExpected Result - " +item[3].toString() + "\nActual result - " + result.toString() + "\n"

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
    fun testInNameSpace(){
        val evalConditions = GBTestHelper.getInNameSpaceData()
        var failedScenarios : ArrayList<String> = ArrayList()
        var passedScenarios : ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val userId = item[1].jsonPrimitive.content
                val jsonArray = item[2].jsonArray
                val namespace = GBUtils.getGBNameSpace(jsonArray)

                val result = namespace?.let { GBUtils.inNamespace(userId, it) }

                val status = item[0].toString() + "\nExpected Result - " +item[3].toString() + "\nActual result - " + result + "\n"


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
    fun testEqualWeights(){
        val evalConditions = GBTestHelper.getEqualWeightsData()
        var failedScenarios : ArrayList<String> = ArrayList()
        var passedScenarios : ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val numVariations = item[0].jsonPrimitive.content.toInt()

                val result = GBUtils.getEqualWeights(numVariations)

                val status =  "Expected Result - " +item[1].toString() + "\nActual result - " + result + "\n"

                var resultTest = true

                if (item[1].jsonArray.size != result.size) {
                    resultTest = false
                } else{
                    for (i in 0..result.size-1) {
                        var source = item[1].jsonArray[i].jsonPrimitive.float
                        var target = result[i]

                        if (source != target) {
                            resultTest = false
                            break
                        }
                    }
                }

                if (resultTest) {
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
    fun testEdgeCases() {

        GBUtils()
        Constants()

        assertFalse(GBUtils.inNamespace("4242", GBNameSpace("",0F,0F)))

        val items = ArrayList<JsonPrimitive>()
        items.add(JsonPrimitive(1))

        assertTrue(GBUtils.getGBNameSpace(JsonArray(items)) == null)
    }

}