package com.sdk.growthbook.tests

import com.sdk.growthbook.model.GBArray
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.utils.Constants
import com.sdk.growthbook.utils.GBBucketRange
import com.sdk.growthbook.utils.GBNameSpace
import com.sdk.growthbook.utils.GBUtils
import com.sdk.growthbook.utils.toJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GBUtilsTests {

    @Test
    fun testHash() {
        val evalConditions = GBTestHelper.getFNVHashData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val testContext = item[1].jsonPrimitive.content
                val expected = item[3].jsonPrimitive.doubleOrNull
                val hashVersion = item[2].jsonPrimitive.content
                val seed = item[0].jsonPrimitive.content

                val result = GBUtils.hash(testContext, hashVersion.toInt(), seed)

                val status =
                    item[0].toString() + "\nExpected Result - " + item[1].toString() +
                        "\nActual result - " + result + "\n"

                if (expected == result) {
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
    fun testBucketRange() {
        val evalConditions = GBTestHelper.getBucketRangeData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val numVariations = item[1].jsonArray[0].jsonPrimitive.content.toIntOrNull()
                val coverage = item[1].jsonArray[1].jsonPrimitive.doubleOrNull
                var weights: List<Double>? = null
                if (item[1].jsonArray[2] != JsonNull) {
                    weights = item[1].jsonArray[2].jsonArray.map { it.jsonPrimitive.double }
                }

                val bucketRange = GBUtils.getBucketRanges(
                    numVariations ?: 1,
                    coverage ?: 1.0,
                    weights ?: ArrayList()
                )

                val status =
                    item[0].toString() + "\nExpected Result - " + item[2].jsonArray.toString() +
                        "\nActual result - " +
                        bucketRange.toJsonElement().jsonArray.toString() + "\n"

                if (compareBucket(item[2].jsonArray, bucketRange)) {
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

    private fun compareBucket(
        expectedResults: JsonArray,
        calaculatedResults: List<GBBucketRange>
    ): Boolean {

        val pairExpectedResults = getPairedData(expectedResults)

        if (pairExpectedResults.size != expectedResults.size) {
            return false
        }

        var result = true
        for (i in 0..pairExpectedResults.size - 1) {
            val source = pairExpectedResults[i]
            val target = calaculatedResults[i]

            if (roundLikeJsSpec(source.first) != roundLikeJsSpec(target.first) ||
                roundLikeJsSpec(source.second) != roundLikeJsSpec(target.second)
            ) {
                result = false
                break
            }
        }

        return result
    }

    private fun getPairedData(items: JsonArray): List<GBBucketRange> =
        items.map {
            val pair = it.jsonArray
            GBBucketRange(pair[0].jsonPrimitive.double, pair[1].jsonPrimitive.double)
        }

    // The spec's getBucketRange/getEqualWeights expectations are rounded to 8 decimals
    // (the JS harness rounds both sides), so those cases are compared the same way.
    private fun roundLikeJsSpec(n: Double): Double = kotlin.math.round(n * 1e8) / 1e8

    // Unrounded output of the JS SDK's getBucketRanges for the same inputs.
    @Test
    fun testBucketRangesMatchJsExactly() {
        val cases = listOf(
            Triple(
                Pair(5, 0.1),
                listOf(0.2, 0.2, 0.2, 0.2, 0.2),
                listOf(
                    0.0 to 0.020000000000000004,
                    0.2 to 0.22000000000000003,
                    0.4 to 0.42000000000000004,
                    0.6000000000000001 to 0.6200000000000001,
                    0.8 to 0.8200000000000001,
                ),
            ),
            Triple(
                Pair(5, 0.72),
                listOf(0.2, 0.2, 0.2, 0.2, 0.2),
                listOf(
                    0.0 to 0.144,
                    0.2 to 0.344,
                    0.4 to 0.544,
                    0.6000000000000001 to 0.7440000000000001,
                    0.8 to 0.9440000000000001,
                ),
            ),
            Triple(
                Pair(2, 0.01),
                listOf(0.1, 0.9),
                listOf(
                    0.0 to 0.001,
                    0.1 to 0.10900000000000001,
                ),
            ),
            Triple(
                Pair(2, 0.74),
                listOf(0.3, 0.7),
                listOf(
                    0.0 to 0.222,
                    0.3 to 0.8180000000000001,
                ),
            ),
            Triple(
                Pair(4, 0.36),
                listOf(0.1, 0.2, 0.3, 0.4),
                listOf(
                    0.0 to 0.036,
                    0.1 to 0.172,
                    0.30000000000000004 to 0.40800000000000003,
                    0.6000000000000001 to 0.7440000000000001,
                ),
            ),
            Triple(
                Pair(3, 0.29),
                listOf(0.2, 0.3, 0.5),
                listOf(
                    0.0 to 0.057999999999999996,
                    0.2 to 0.28700000000000003,
                    0.5 to 0.645,
                ),
            ),
            Triple(
                Pair(3, 0.48),
                listOf(0.2, 0.3, 0.5),
                listOf(
                    0.0 to 0.096,
                    0.2 to 0.344,
                    0.5 to 0.74,
                ),
            ),
        )
        for ((args, weights, expected) in cases) {
            assertEquals(expected, GBUtils.getBucketRanges(args.first, args.second, weights))
        }
    }

    @Test
    fun testEqualWeightsMatchJsExactly() {
        assertEquals(List(3) { 0.3333333333333333 }, GBUtils.getEqualWeights(3))
    }

    @Test
    fun testChooseVariation() {
        val evalConditions = GBTestHelper.getChooseVariationData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val hash = item[1].jsonPrimitive.doubleOrNull
                val rangeData =
                    getPairedData(item[2].jsonArray)

                val result = GBUtils.chooseVariation(hash ?: 0.0, rangeData)

                val status =
                    item[0].toString() + "\nExpected Result - " + item[3].toString() +
                        "\nActual result - " + result.toString() + "\n"

                if (item[3].toString() == result.toString()) {
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
    fun testInNameSpace() {
        val evalConditions = GBTestHelper.getInNameSpaceData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val userId = item[1].jsonPrimitive.content
                val jsonArray = item[2].jsonArray
                val namespace = GBUtils.getGBNameSpace(jsonArray)

                val result = namespace?.let { GBUtils.inNamespace(userId, it) }

                val status =
                    item[0].toString() + "\nExpected Result - " + item[3].toString() +
                        "\nActual result - " + result + "\n"


                if (item[3].toString() == result.toString()) {
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
    fun testEqualWeights() {
        val evalConditions = GBTestHelper.getEqualWeightsData()
        val failedScenarios: ArrayList<String> = ArrayList()
        val passedScenarios: ArrayList<String> = ArrayList()
        for (item in evalConditions) {
            if (item is JsonArray) {

                val numVariations = item[0].jsonPrimitive.content.toInt()

                val result = GBUtils.getEqualWeights(numVariations)

                val status =
                    "Expected Result - " + item[1].toString() + "\nActual result - " + result + "\n"

                var resultTest = true

                if (item[1].jsonArray.size != result.size) {
                    resultTest = false
                } else {
                    for (i in 0..result.size - 1) {
                        val source = item[1].jsonArray[i].jsonPrimitive.double
                        val target = result[i]

                        if (roundLikeJsSpec(source) != roundLikeJsSpec(target)) {
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

        print("\nTOTAL TESTS - " + evalConditions.size)
        print("\nPassed TESTS - " + passedScenarios.size)
        print("\nFailed TESTS - " + failedScenarios.size)
        print("\n")
        print(failedScenarios)

        assertTrue(failedScenarios.size == 0)
    }

    @Test
    fun testHashMixesWholeUtf16CodeUnits() {
        // 'ā' (U+0101) and U+0001 share a low byte, so they only differ if the whole unit is mixed in
        assertTrue(GBUtils.hash("\u0101", 1, "") != GBUtils.hash("\u0001", 1, ""))
        // An astral character hashes as its two surrogate code units
        assertEquals(GBUtils.hash("\uD83D\uDE00", 2, "s"), GBUtils.hash("😀", 2, "s"))
    }

    @Test
    fun testHashAttributeUsesOnlyNonEmptyStringsAndNonZeroNumbers() {
        fun hashValue(id: GBValue?, fallback: GBValue? = null): Pair<String, String> {
            val attributes = HashMap<String, GBValue>()
            id?.let { attributes["id"] = it }
            fallback?.let { attributes["deviceId"] = it }
            return GBUtils.getHashAttribute("id", "deviceId", attributes, emptyMap())
        }
        val device = GBString("d1")
        for (missing in listOf(GBBoolean(true), GBBoolean(false), GBNumber(0), GBNumber(0.0), GBString(""), GBNull,
            GBArray(listOf(GBString("1"))), GBArray(emptyList()), GBJson(emptyMap()), GBNumber(Double.NaN), null)) {
            assertEquals("deviceId" to "d1", hashValue(missing, device), "id $missing falls back")
            assertEquals("id" to "", hashValue(missing), "id $missing is missing")
        }
        assertEquals("id" to "1", hashValue(GBNumber(1.0), device))
        assertEquals("id" to "1.5", hashValue(GBNumber(1.5), device))
        assertEquals("id" to "-3", hashValue(GBNumber(-3), device))
        assertEquals("id" to "abc", hashValue(GBString("abc"), device))
    }

    @Test
    fun testEdgeCases() {

        GBUtils()
        Constants()

        assertFalse(GBUtils.inNamespace("4242", GBNameSpace("", 0.0, 0.0)))

        val items = ArrayList<JsonPrimitive>()
        items.add(JsonPrimitive(1))

        assertTrue(GBUtils.getGBNameSpace(JsonArray(items)) == null)
    }
}