package com.sdk.growthbook.tests

import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.diffFeatures
import com.sdk.growthbook.serializable_model.SerializableGBExperiment
import com.sdk.growthbook.serializable_model.SerializableGBExperimentResult
import com.sdk.growthbook.serializable_model.SerializableGBFeatureRule
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.utils.GBUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GBExactPrecisionTests {

    private val json = GBTestHelper.jsonParser

    private val ruleJson = """
        {
          "key": "exp",
          "coverage": 0.123456789,
          "weights": [0.3333333333333333, 0.6666666666666667],
          "range": [0.1, 0.30000000000000004],
          "ranges": [[0, 0.3333333333333333], [0.3333333333333333, 1]],
          "filters": [{"seed": "s", "ranges": [[0, 0.6000000000000001]]}]
        }
    """.trimIndent()

    @Test
    fun payloadDoublesSurviveDeserializationAndCacheRoundTrip() {
        val rule = json.decodeFromString(SerializableGBFeatureRule.serializer(), ruleJson).gbDeserialize()

        assertEquals(0.123456789, rule.coverage)
        assertEquals(listOf(0.3333333333333333, 0.6666666666666667), rule.weights)
        assertEquals(0.1 to 0.30000000000000004, rule.range)
        assertEquals(listOf(0.0 to 0.3333333333333333, 0.3333333333333333 to 1.0), rule.ranges)
        assertEquals(listOf(0.0 to 0.6000000000000001), rule.filters?.single()?.ranges)

        val encoded = json.encodeToString(SerializableGBFeatureRule.serializer(), rule.gbSerialize())
        val again = json.decodeFromString(SerializableGBFeatureRule.serializer(), encoded).gbDeserialize()
        assertEquals(rule.coverage, again.coverage)
        assertEquals(rule.weights, again.weights)
        assertEquals(rule.range, again.range)
        assertEquals(rule.ranges, again.ranges)
        assertEquals(rule.filters?.single()?.ranges, again.filters?.single()?.ranges)
    }

    @Test
    fun equalityIncludesDigitsBeyondFloatPrecision() {
        fun rule(weights: String) = json.decodeFromString(
            SerializableGBFeatureRule.serializer(),
            """{"key": "exp", "variations": [0, 1], "weights": $weights}"""
        ).gbDeserialize()

        val base = rule("[0.123456789, 0.876543211]")
        val same = rule("[0.123456789, 0.876543211]")
        val beyondFloat = rule("[0.123456788, 0.876543212]")

        assertEquals(base, same)
        assertEquals(base.hashCode(), same.hashCode())
        assertNotEquals(base, beyondFloat)

        val old = mapOf("f" to GBFeature(rules = listOf(base)))
        assertEquals(setOf("f"), diffFeatures(old, mapOf("f" to GBFeature(rules = listOf(beyondFloat)))).changed.keys)
        assertEquals(emptySet(), diffFeatures(old, mapOf("f" to GBFeature(rules = listOf(same)))).changedKeys)
    }

    @Test
    fun copyKeepsAssignment() {
        val experiment = json.decodeFromString(
            SerializableGBExperiment.serializer(),
            """{"key": "e", "variations": [0, 1], "ranges": [[0, 0.22000000000000003], [0.5, 0.7]]}"""
        ).gbDeserialize()
        val copy = experiment.copy(key = "variant-b")

        assertEquals(experiment.ranges, copy.ranges)
        assertEquals(0, GBUtils.chooseVariation(0.22, copy.ranges!!))
    }

    @Test
    fun experimentResultSurvivesSerializationRoundTrip() {
        val result = GBExperimentResult(
            key = "1",
            value = GBNumber(1),
            variationId = 1,
            inExperiment = true,
            bucket = 0.2201,
            leafId = 3,
            variationWeights = listOf(0.3333333333333333, 0.6666666666666667),
        )
        val encoded = json.encodeToString(SerializableGBExperimentResult.serializer(), result.gbSerialize())
        val again = json.decodeFromString(SerializableGBExperimentResult.serializer(), encoded).gbDeserialize()

        assertEquals(listOf(0.3333333333333333, 0.6666666666666667), again.variationWeights)
        assertEquals(0.2201, again.bucket)
        assertEquals(result, again)
    }
}
