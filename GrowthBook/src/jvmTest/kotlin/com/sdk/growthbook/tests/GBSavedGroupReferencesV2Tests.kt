package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBConditionEvaluator
import com.sdk.growthbook.evaluators.GBExperimentEvaluator
import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.kotlinx.serialization.gbSerialize
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.serializable_model.SerializableGBExperiment
import com.sdk.growthbook.serializable_model.gbDeserialize
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shared spec's `savedGroupReferencesV2` section: resolving a saved group of any type by
 * reference through the `$savedGroup` operator.
 *
 * These cases live under their own key rather than in `evalCondition` / `feature` / `run`, so an
 * SDK without the capability skips them wholesale — the same arrangement as `stickyBucket` and
 * `contextualBandit`. They are kept in one class here for the same reason: the section is a unit,
 * and splitting it across the three existing suites would hide which spec version each one tracks.
 *
 * The `feature` and `run` sections are not redundant with `evalCondition`. Both carry a case whose
 * reference sits inside a prerequisite gate, and a gate that forgets to pass `savedGroups` down
 * resolves every reference to "matches nobody" while every condition-level case still passes.
 */
class GBSavedGroupReferencesV2Tests {

    private lateinit var conditionCases: JsonArray
    private lateinit var featureCases: JsonArray
    private lateinit var runCases: JsonArray

    @BeforeTest
    fun setUp() {
        conditionCases = GBTestHelper.getSavedGroupReferencesV2ConditionData()
        featureCases = GBTestHelper.getSavedGroupReferencesV2FeatureData()
        runCases = GBTestHelper.getSavedGroupReferencesV2RunData()
    }

    @Test
    fun testConditions() {
        val failed = ArrayList<String>()

        for (item in conditionCases) {
            if (item !is JsonArray) continue

            val name = item[0].jsonPrimitive.content
            val condition = GBValue.from(item[1]) as? GBJson ?: GBJson(emptyMap())
            val attributes = (GBValue.from(item[2]) as? GBJson)?.toMap() ?: emptyMap()
            val expected = item[3].jsonPrimitive.booleanOrNull
            val savedGroups = if (item.size > 4) {
                (GBValue.from(item[4]) as? GBJson)?.toMap()
            } else {
                null
            }

            val actual = GBConditionEvaluator().evalCondition(attributes, condition, savedGroups)
            if (actual != expected) {
                failed.add("$name — expected $expected, got $actual")
            }
        }

        report("evalCondition", conditionCases.size, failed)
    }

    @Test
    fun testFeatures() {
        val failed = ArrayList<String>()

        for (item in featureCases) {
            if (item !is JsonArray) continue

            val name = item[0].jsonPrimitive.content
            val testData = GBTestHelper.jsonParser
                .decodeFromJsonElement(GBFeaturesTest.serializer(), item[1])
            val attributes = testData.attributes.jsonObject.mapValues { GBValue.from(it.value) }

            val evaluator = GBFeatureEvaluator(
                GBTestHelper.createTestScopeEvaluationContext(
                    features = testData.features?.mapValues { it.value.gbDeserialize() }
                        ?: emptyMap(),
                    attributes = attributes,
                    savedGroups = testData.savedGroups?.jsonObject
                        ?.mapValues { GBValue.from(it.value) },
                    forcedVariations = testData.forcedVariations
                        ?.mapValues { it.value.jsonPrimitive.intOrNull ?: 0 } ?: emptyMap(),
                    qaMode = testData.qaMode,
                    enabled = testData.enabled,
                )
            )

            val actual = evaluator.evaluateFeature(
                featureKey = item[2].jsonPrimitive.content,
                attributeOverrides = attributes,
            )
            val expected = GBTestHelper.jsonParser
                .decodeFromJsonElement(GBFeatureResultTest.serializer(), item[3])
            val actualValue = (actual.gbValue?.gbSerialize() as? JsonPrimitive)?.content

            if (
                actualValue != expected.value.content ||
                actual.on != expected.on ||
                actual.off != expected.off ||
                actual.source.toString() != expected.source ||
                actual.ruleId != expected.ruleId
            ) {
                failed.add(
                    "$name — expected value=${expected.value.content} on=${expected.on} " +
                        "source=${expected.source} ruleId=${expected.ruleId}, " +
                        "got value=$actualValue on=${actual.on} " +
                        "source=${actual.source} ruleId=${actual.ruleId}"
                )
            }
        }

        report("feature", featureCases.size, failed)
    }

    @Test
    fun testExperiments() {
        val failed = ArrayList<String>()

        for (item in runCases) {
            if (item !is JsonArray) continue

            val name = item[0].jsonPrimitive.content
            val testContext = GBTestHelper.jsonParser
                .decodeFromJsonElement(GBContextTest.serializer(), item[1])
            val experiment = GBTestHelper.jsonParser
                .decodeFromJsonElement(SerializableGBExperiment.serializer(), item[2])

            val attributes = testContext.attributes.jsonObject.mapValues { GBValue.from(it.value) }

            val evaluator = GBExperimentEvaluator(
                GBTestHelper.createTestScopeEvaluationContext(
                    features = testContext.features.mapValues { it.value.gbDeserialize() },
                    attributes = attributes,
                    savedGroups = testContext.savedGroups.jsonObject
                        .mapValues { GBValue.from(it.value) },
                    forcedVariations = testContext.forcedVariations ?: emptyMap(),
                    qaMode = testContext.qaMode,
                    enabled = testContext.enabled,
                )
            )

            val actual = evaluator.evaluateExperiment(
                experiment = experiment.gbDeserialize(),
                attributeOverrides = attributes,
            )
            val actualValue = actual.value.gbSerialize().toString()

            if (
                actualValue != item[3].toString() ||
                actual.inExperiment != item[4].jsonPrimitive.booleanOrNull ||
                actual.hashUsed != item[5].jsonPrimitive.booleanOrNull
            ) {
                failed.add(
                    "$name — expected value=${item[3]} inExperiment=${item[4]} " +
                        "hashUsed=${item[5]}, got value=$actualValue " +
                        "inExperiment=${actual.inExperiment} hashUsed=${actual.hashUsed}"
                )
            }
        }

        report("run", runCases.size, failed)
    }

    private fun report(section: String, total: Int, failed: List<String>) {
        println("savedGroupReferencesV2.$section — $total cases, ${failed.size} failed")
        failed.forEach { println("  $it") }
        assertTrue(failed.isEmpty(), "${failed.size} of $total $section cases failed")
    }
}
