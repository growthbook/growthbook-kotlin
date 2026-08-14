package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.model.GBJson
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.serializable_model.SerializableGBFeature
import com.sdk.growthbook.serializable_model.gbDeserialize
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that large `$in` conditions stay fast after load-time conversion to [GBJson].
 * Hit and full-scan miss are both timed (miss walks the whole array with List.contains).
 */
class GBConditionPerfTests {

    @Test
    fun largeInConditionHitAndMissAreFast() {
        val listSize = 5_000
        val iterations = 10_000
        val warmup = 200
        // Generous vs CI noise; unpatched (convert every eval) is typically multi-second.
        val maxMsPerCase = 2_000.0

        val inItems = buildJsonArray {
            for (i in 0 until listSize) {
                add(JsonPrimitive("item-$i"))
            }
        }
        val featureJson = buildJsonObject {
            put(
                "defaultValue",
                JsonPrimitive(false),
            )
            put(
                "rules",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "condition",
                                buildJsonObject {
                                    put(
                                        "id",
                                        buildJsonObject {
                                            put("\$in", inItems)
                                        },
                                    )
                                },
                            )
                            put("force", JsonPrimitive(true))
                        },
                    )
                },
            )
        }

        val serializable = Json.decodeFromJsonElement(
            SerializableGBFeature.serializer(),
            featureJson,
        )
        val feature = serializable.gbDeserialize()
        assertTrue(
            feature.rules!!.first().condition is GBJson,
            "condition should be converted to GBJson at deserialize",
        )

        val features = mapOf(FEATURE_KEY to feature)

        fun evalWith(id: String): Boolean {
            val attributes: Map<String, GBValue> = mapOf("id" to GBString(id))
            val evaluator = GBFeatureEvaluator(
                GBTestHelper.createTestScopeEvaluationContext(features, attributes),
            )
            return evaluator.evaluateFeature(
                featureKey = FEATURE_KEY,
                attributeOverrides = attributes,
            ).on
        }

        assertEquals(true, evalWith("item-2500"), "mid-list hit should match")
        assertEquals(false, evalWith("item-missing"), "missing id should miss")

        fun timeEvals(id: String, expectedOn: Boolean): Double {
            repeat(warmup) {
                assertEquals(expectedOn, evalWith(id))
            }
            // Mirror GrowthBookSDK.feature(): fresh EvaluationContext each call so the
            // evaluatedFeatures stack does not trip cyclic-prerequisite detection.
            // Features (with load-time GBJson conditions) are reused across calls.
            val ns = measureNanoTime {
                repeat(iterations) {
                    val on = evalWith(id)
                    if (on != expectedOn) {
                        error("unexpected result for id=$id")
                    }
                }
            }
            return ns / 1_000_000.0
        }

        val hitMs = timeEvals("item-2500", expectedOn = true)
        val missMs = timeEvals("item-missing", expectedOn = false)

        println(
            "GBConditionPerfTests: $iterations evals, $listSize-item \$in — " +
                "hit=${"%.1f".format(hitMs)}ms miss=${"%.1f".format(missMs)}ms",
        )

        assertTrue(
            hitMs < maxMsPerCase,
            "hit too slow: ${hitMs}ms (limit ${maxMsPerCase}ms)",
        )
        assertTrue(
            missMs < maxMsPerCase,
            "miss too slow: ${missMs}ms (limit ${maxMsPerCase}ms)",
        )
    }

    companion object {
        private const val FEATURE_KEY = "large-in-flag"
    }
}
