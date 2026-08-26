package com.sdk.growthbook.tests

import com.sdk.growthbook.evaluators.GBFeatureEvaluator
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.kotlinx.serialization.from
import com.sdk.growthbook.kotlinx.serialization.gbSerialize
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import com.sdk.growthbook.utils.GBUtils
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GBContextualBanditTest {
    private lateinit var cases: JsonArray

    @BeforeTest
    fun setUp() {
        cases = GBTestHelper.getContextualBanditData()
    }

    @Test
    fun testContextualBandits() {
        val failed = ArrayList<String>()
        var skipped = 0
        for (item in cases) {
            if (item !is JsonArray) continue

            // Kotlin (mobile-first) has no querystring/URL-based experiment override; skip that case.
            if (item[0].jsonPrimitive.content == "querystring force overrides CB routing") {
                skipped++
                continue
            }

            val testData = GBTestHelper.jsonParser
                .decodeFromJsonElement(GBFeaturesTest.serializer(), item[1])
            val attributes = testData.attributes.jsonObject.mapValues { GBValue.from(it.value) }

            val evalContext = GBTestHelper.createTestScopeEvaluationContext(
                features = testData.features?.mapValues { it.value.gbDeserialize() } ?: emptyMap(),
                attributes = attributes,
                savedGroups = testData.savedGroups?.jsonObject?.mapValues { GBValue.from(it.value) },
                contextualBandits = testData.contextualBandits?.mapValues { it.value.gbDeserialize() },
                forcedVariations = testData.forcedVariations
                    ?.mapValues { it.value.jsonPrimitive.intOrNull ?: 0 } ?: emptyMap(),
                qaMode = testData.qaMode,
                enabled = testData.enabled,
            )

            val actual = GBFeatureEvaluator(evalContext).evaluateFeature(
                item[2].jsonPrimitive.content,
                attributes
            )
            val expected = GBTestHelper.jsonParser.decodeFromJsonElement(
                BanditExpected.serializer(), item[3]
            )
            val actualExperimentResult = actual.experimentResult
            val expectedExperimentResult = expected.experimentResult

            val actualValue = actual.gbValue?.gbSerialize()
            val ok = actualValue == expected.value
                && actual.on == expected.on
                && actual.source.toString() == expected.source
                && actualExperimentResult?.variationId == expectedExperimentResult?.variationId
                && actualExperimentResult?.leafId == expectedExperimentResult?.leafId
                && actualExperimentResult?.banditVersion == expectedExperimentResult?.banditVersion
                && actualExperimentResult?.variationWeights == expectedExperimentResult?.variationWeights

            if (!ok) {
                failed.add(
                    "${item[0]}: expected leaf=${expectedExperimentResult?.leafId}, " +
                        "variationId=${expectedExperimentResult?.variationId}, " +
                        "BUT got leaf=${actualExperimentResult?.leafId}, " +
                        "variationId=${actualExperimentResult?.variationId}")
            }
        }
        println("CB TESTS: ${cases.size}, skipped(no URL override): $skipped, failed: ${failed.size}\n$failed")
        assertTrue(failed.isEmpty())
    }

    /**
     * A contextual bandit rule carries its variations under `contextualVariations` (so older SDKs
     * skip it), but it is still an experiment rule and its identifiers must be registered for
     * sticky bucketing — otherwise no assignment doc is ever loaded for the feature and sticky
     * bucketing silently does nothing. Mirrors deriveStickyBucketIdentifierAttributes in the TS SDK.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testStickyBucketIdentifiersDerivedFromContextualVariations() = runTest {
        var requestedAttributes: Map<String, String> = emptyMap()
        val service = object : GBStickyBucketService {
            override val coroutineScope = TestScope(testScheduler)
            override suspend fun getAssignments(
                attributeName: String,
                attributeValue: String
            ): GBStickyAssignmentsDocument? = null

            override suspend fun saveAssignments(doc: GBStickyAssignmentsDocument) = Unit

            override suspend fun getAllAssignments(
                attributes: Map<String, String>
            ): Map<String, GBStickyAssignmentsDocument> {
                requestedAttributes = attributes
                return emptyMap()
            }
        }

        val context = GBContext(
            apiKey = "Key",
            enabled = true,
            attributes = mapOf("id" to GBString("u1"), "deviceId" to GBString("d1")),
            forcedVariations = emptyMap(),
            qaMode = false,
            trackingCallback = { _, _ -> },
            encryptionKey = null,
            stickyBucketService = service,
        )

        val payload = FeaturesDataModel(
            features = mapOf(
                "cb_feature" to GBFeature(
                    rules = listOf(
                        GBFeatureRule(
                            hashAttribute = "deviceId",
                            contextualBanditRef = "bandit_1",
                            contextualVariations = listOf(GBString("control"), GBString("variant")),
                        )
                    )
                )
            )
        )

        GBUtils.refreshStickyBuckets(
            context = context,
            data = payload,
            attributeOverrides = emptyMap(),
        )

        assertEquals(listOf("deviceId"), context.stickyBucketIdentifierAttributes)
        assertEquals(mapOf("deviceId" to "d1"), requestedAttributes)
    }
}

/** Expected feature result for a bandit case; value is a [JsonElement] so object values decode. */
@Serializable
class BanditExpected(
    val value: JsonElement = JsonObject(emptyMap()),
    val on: Boolean = false,
    val off: Boolean = false,
    val source: String = "",
    val experimentResult: GBExperimentResultTest? = null,
)