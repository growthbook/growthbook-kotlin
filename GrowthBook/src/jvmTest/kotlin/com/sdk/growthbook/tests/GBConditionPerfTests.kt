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
 * Smoke/perf check: a 1000-item `$in` rule stays correct and finishes quickly
 * after load-time condition conversion (+ membership Set on [com.sdk.growthbook.model.GBArray]).
 */
class GBConditionPerfTests {

    @Test
    fun largeInConditionOf1000ItemsEvaluatesCorrectlyAndQuickly() {
        val listSize = 1_000
        // Unpatched (convert every eval + linear scan) is typically hundreds of ms+.
        val maxMs = 100.0

        val inItems = buildJsonArray {
            for (i in 0 until listSize) {
                add(JsonPrimitive("item-$i"))
            }
        }
        val featureJson = buildJsonObject {
            put("defaultValue", JsonPrimitive(false))
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
                                        buildJsonObject { put("\$in", inItems) },
                                    )
                                },
                            )
                            put("force", JsonPrimitive(true))
                        },
                    )
                },
            )
        }

        val feature = Json.decodeFromJsonElement(
            SerializableGBFeature.serializer(),
            featureJson,
        ).gbDeserialize()
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

        val elapsedMs = measureNanoTime {
            assertEquals(true, evalWith("item-500"), "mid-list hit should match")
            assertEquals(false, evalWith("item-missing"), "missing id should miss")
        } / 1_000_000.0

        assertTrue(
            elapsedMs < maxMs,
            "1000-item \$in eval too slow: ${elapsedMs}ms (limit ${maxMs}ms)",
        )
    }

    companion object {
        private const val FEATURE_KEY = "large-in-flag"
    }
}
