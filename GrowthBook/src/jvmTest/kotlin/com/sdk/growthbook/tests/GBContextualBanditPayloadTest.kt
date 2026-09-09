package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.features.FeaturePayloadDecoder
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.gbSerialize
import com.sdk.growthbook.integration.buildSDK
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.serializable_model.SerializableFeaturesDataModel
import com.sdk.growthbook.serializable_model.gbDeserialize
import com.sdk.growthbook.utils.DefaultCrypto
import com.sdk.growthbook.utils.decodeBase64
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.intellij.lang.annotations.Language
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Covers the contextual-bandit payload path end to end — decryption, cache round-trip, ingestion
 * from the network and from a bundled seed. The shared spec cases in [GBContextualBanditTest] only
 * exercise the evaluator directly, so none of this is reached by them.
 */
class GBContextualBanditPayloadTest {

    private val encryptionKey = "Ns04T5n9+59rl2x3SlNHtQ=="

    @Language("json")
    private val banditsJson = """
        {
          "bandit_1": {
            "banditVersion": 3,
            "contexts": [
              { "leafId": 7, "condition": { "country": "UA" }, "weights": [0.0, 1.0] },
              { "leafId": 9, "condition": {}, "weights": [1.0, 0.0] }
            ]
          }
        }
    """.trimIndent()

    /**
     * A feature whose only rule is a contextual bandit. The leaf weights are 0/1, so the variation
     * a user lands on is decided purely by which leaf matched — no dependence on hash luck.
     */
    @Language("json")
    private val payloadJson = """
        {
          "status": 200,
          "features": {
            "cb_feature": {
              "defaultValue": "default",
              "rules": [
                {
                  "key": "cb_exp",
                  "hashAttribute": "id",
                  "coverage": 1,
                  "weights": [0.5, 0.5],
                  "contextualBanditRef": "bandit_1",
                  "contextualVariations": ["control", "variant"]
                }
              ]
            }
          },
          "contextualBandits": $banditsJson
        }
    """.trimIndent()

    /**
     * Builds an SDK whose ONLY source of data is [seed]: the network always fails and the disk cache
     * is off. Caching must be disabled explicitly — it is keyed by API key alone, so a shared key
     * would let a payload cached by an earlier test satisfy the assertions and the seed would never
     * actually be exercised. The unique [key] keeps this test's cache file out of other suites too.
     */
    private fun seededSDK(
        seed: String,
        key: String,
        attributes: Map<String, GBValue>,
        encryptionKey: String = "",
        dispatcher: CoroutineContext,
    ) = GBSDKBuilder(
        apiKey = key,
        apiHost = "http://host.com",
        attributes = attributes,
        remoteEval = false,
        encryptionKey = encryptionKey,
        trackingCallback = { _, _ -> },
        networkDispatcher = MockNetworkClient(null, Throwable("offline")),
        cachingEnabled = false,
    )
        .setCoroutineContext(dispatcher)
        .setInitialPayload(seed)
        .initialize()

    @OptIn(ExperimentalEncodingApi::class)
    private fun encrypt(plain: String): String {
        val iv = ByteArray(16) { it.toByte() } // fixed IV: deterministic test vector, not production
        val cipher = DefaultCrypto().encrypt(
            inputText = plain.encodeToByteArray(),
            key = decodeBase64(encryptionKey),
            iv = iv,
        )
        return "${Base64.encode(iv)}.${Base64.encode(cipher)}"
    }

    @Test
    fun encryptedContextualBandits_correctKey_decodes() {
        val model = FeaturesDataModel(encryptedContextualBandits = encrypt(banditsJson))

        val bandits = FeaturePayloadDecoder(encryptionKey).decode(model).contextualBandits

        assertNotNull(bandits)
        val definition = assertNotNull(bandits["bandit_1"])
        assertEquals(3, definition.banditVersion)
        assertEquals(listOf(7, 9), definition.contexts?.map { it.leafId })
        assertEquals(listOf(0f, 1f), definition.contexts?.first()?.weights)
    }

    @Test
    fun encryptedContextualBandits_wrongKey_yieldsNull() {
        val model = FeaturesDataModel(encryptedContextualBandits = encrypt(banditsJson))

        val bandits = runCatching {
            FeaturePayloadDecoder("AAAAAAAAAAAAAAAAAAAAAA==").decode(model).contextualBandits
        }.getOrNull()

        assertNull(bandits)
    }

    /**
     * The disk cache stores the serialized [FeaturesDataModel], so bandits have to survive both
     * directions of that conversion — otherwise an offline start silently loses them and every
     * bandit rule falls back to its marginal weights.
     */
    @Test
    fun contextualBandits_surviveCacheSerializationRoundTrip() {
        val json = Json { isLenient = true; ignoreUnknownKeys = true }
        val original = json
            .decodeFromString(SerializableFeaturesDataModel.serializer(), payloadJson)
            .gbDeserialize()

        val restored = json
            .decodeFromString(
                SerializableFeaturesDataModel.serializer(),
                json.encodeToString(SerializableFeaturesDataModel.serializer(), original.gbSerialize()),
            )
            .gbDeserialize()

        assertEquals(original.contextualBandits, restored.contextualBandits)
        assertEquals(3, restored.contextualBandits?.get("bandit_1")?.banditVersion)
    }

    @Test
    fun networkPayload_routesUserIntoMatchingLeaf() = runTest {
        val sdk = buildSDK(
            json = payloadJson,
            attributes = mapOf("id" to GBString("u1"), "country" to GBString("UA")),
        )

        val result = sdk.feature("cb_feature")

        assertEquals(GBString("variant"), result.gbValue)
        assertEquals(7, result.experimentResult?.leafId)
        assertEquals(3, result.experimentResult?.banditVersion)
        assertEquals(listOf(0f, 1f), result.experimentResult?.variationWeights)
    }

    @Test
    fun networkPayload_fallsThroughToCatchAllLeaf() = runTest {
        val sdk = buildSDK(
            json = payloadJson,
            attributes = mapOf("id" to GBString("u1"), "country" to GBString("PL")),
        )

        val result = sdk.feature("cb_feature")

        assertEquals(GBString("control"), result.gbValue)
        assertEquals(9, result.experimentResult?.leafId)
    }

    /**
     * Bundled-seed path: the network fails, so everything the SDK evaluates comes from
     * [GBSDKBuilder.setInitialPayload]. Without bandit definitions in the seed the rule would fall
     * back to its 50/50 marginal weights and carry no leaf metadata.
     */
    @Test
    fun initialPayload_seedsContextualBanditsOffline() = runTest {
        val sdk = seededSDK(
            seed = payloadJson,
            key = "seed_plain_key",
            attributes = mapOf("id" to GBString("u1"), "country" to GBString("UA")),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = sdk.feature("cb_feature")

        assertEquals(GBString("variant"), result.gbValue)
        assertEquals(7, result.experimentResult?.leafId)
        assertEquals(3, result.experimentResult?.banditVersion)
    }

    @Test
    fun initialPayload_seedsEncryptedContextualBandits() = runTest {
        @Language("json")
        val encryptedPayload = """
            {
              "features": {
                "cb_feature": {
                  "defaultValue": "default",
                  "rules": [
                    {
                      "key": "cb_exp",
                      "hashAttribute": "id",
                      "coverage": 1,
                      "contextualBanditRef": "bandit_1",
                      "contextualVariations": ["control", "variant"]
                    }
                  ]
                }
              },
              "encryptedContextualBandits": "${encrypt(banditsJson)}"
            }
        """.trimIndent()

        val sdk = seededSDK(
            seed = encryptedPayload,
            key = "seed_encrypted_key",
            attributes = mapOf("id" to GBString("u1"), "country" to GBString("UA")),
            encryptionKey = encryptionKey,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = sdk.feature("cb_feature")

        assertEquals(GBString("variant"), result.gbValue)
        assertEquals(7, result.experimentResult?.leafId)
    }

    @Test
    fun malformedLeaf_degradesToFallbackWithoutDiscardingThePayload() = runTest {
        // "bandit_bad"'s only leaf is missing leafId. With leafId required, this threw
        // MissingFieldException out of the whole-payload decode — discarding the features too.
        @Language("json")
        val payload = """
            {
              "features": {
                "plain_feature": { "defaultValue": "still here" },
                "cb_feature": {
                  "defaultValue": "default",
                  "rules": [
                    {
                      "key": "cb_exp",
                      "hashAttribute": "id",
                      "coverage": 1,
                      "weights": [1.0, 0.0],
                      "contextualBanditRef": "bandit_bad",
                      "contextualVariations": ["control", "variant"]
                    }
                  ]
                }
              },
              "contextualBandits": {
                "bandit_bad": {
                  "banditVersion": 5,
                  "contexts": [ { "condition": {}, "weights": [0.0, 1.0] } ]
                }
              }
            }
        """.trimIndent()

        val sdk = seededSDK(
            seed = payload,
            key = "seed_malformed_leaf_key",
            attributes = mapOf("id" to GBString("u1")),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // The rest of the payload survives the malformed leaf...
        assertEquals(GBString("still here"), sdk.feature("plain_feature").gbValue)

        // ...and the leaf itself cannot describe the assignment, so the rule's aggregate
        // weights apply under the fallback sentinel rather than the leaf's 0/1 weights.
        val result = sdk.feature("cb_feature")
        assertEquals(GBString("control"), result.gbValue)
        assertEquals(-1, result.experimentResult?.leafId)
        assertEquals(5, result.experimentResult?.banditVersion)
    }

    @Test
    fun corruptLeafCondition_failsClosedInsteadOfMatchingEveryone() = runTest {
        // Leaf 1's condition is a JSON array, not an object. Coercing it to an empty (catch-all)
        // condition would route every user into the corrupt leaf and shadow the real catch-all.
        @Language("json")
        val payload = """
            {
              "features": {
                "cb_feature": {
                  "defaultValue": "default",
                  "rules": [
                    {
                      "key": "cb_exp",
                      "hashAttribute": "id",
                      "coverage": 1,
                      "contextualBanditRef": "bandit_corrupt",
                      "contextualVariations": ["control", "variant"]
                    }
                  ]
                }
              },
              "contextualBandits": {
                "bandit_corrupt": {
                  "contexts": [
                    { "leafId": 1, "condition": ["not", "an", "object"], "weights": [0.0, 1.0] },
                    { "leafId": 2, "condition": {}, "weights": [1.0, 0.0] }
                  ]
                }
              }
            }
        """.trimIndent()

        val sdk = seededSDK(
            seed = payload,
            key = "seed_corrupt_condition_key",
            attributes = mapOf("id" to GBString("u1")),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = sdk.feature("cb_feature")

        assertEquals(2, result.experimentResult?.leafId)
        assertEquals(GBString("control"), result.gbValue)
    }

    @Test
    fun invalidLeafWeightVector_fallsBackAndReportsTheWeightsActuallyUsed() = runTest {
        // The matched leaf's vector has the wrong length for 2 variations, so the bucketer
        // would substitute equal weights while the metadata claimed [0,1,0] — corrupting
        // training data. It must degrade to the -1 fallback reporting the rule's weights.
        @Language("json")
        val payload = """
            {
              "features": {
                "cb_feature": {
                  "defaultValue": "default",
                  "rules": [
                    {
                      "key": "cb_exp",
                      "hashAttribute": "id",
                      "coverage": 1,
                      "weights": [1.0, 0.0],
                      "contextualBanditRef": "bandit_badvector",
                      "contextualVariations": ["control", "variant"]
                    }
                  ]
                }
              },
              "contextualBandits": {
                "bandit_badvector": {
                  "contexts": [ { "leafId": 4, "condition": {}, "weights": [0.0, 1.0, 0.0] } ]
                }
              }
            }
        """.trimIndent()

        val sdk = seededSDK(
            seed = payload,
            key = "seed_bad_vector_key",
            attributes = mapOf("id" to GBString("u1")),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = sdk.feature("cb_feature")

        assertEquals(GBString("control"), result.gbValue)
        assertEquals(-1, result.experimentResult?.leafId)
        assertEquals(listOf(1.0f, 0.0f), result.experimentResult?.variationWeights)
    }

    @Test
    fun invalidRuleWeightsInFallback_reportEqualWeightsLikeTheBucketer() = runTest {
        // Leaf weights sum to 10 (invalid) and the rule's own weights sum to 0.4 (also
        // invalid), so the bucketer substitutes equal weights. The reported propensities
        // must be that same substitution, not the raw invalid vector.
        @Language("json")
        val payload = """
            {
              "features": {
                "cb_feature": {
                  "defaultValue": "default",
                  "rules": [
                    {
                      "key": "cb_exp",
                      "hashAttribute": "id",
                      "coverage": 1,
                      "weights": [0.2, 0.2],
                      "contextualBanditRef": "bandit_badsum",
                      "contextualVariations": ["control", "variant"]
                    }
                  ]
                }
              },
              "contextualBandits": {
                "bandit_badsum": {
                  "contexts": [ { "leafId": 4, "condition": {}, "weights": [5.0, 5.0] } ]
                }
              }
            }
        """.trimIndent()

        val sdk = seededSDK(
            seed = payload,
            key = "seed_bad_sum_key",
            attributes = mapOf("id" to GBString("u1")),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = sdk.feature("cb_feature")

        assertEquals(-1, result.experimentResult?.leafId)
        assertEquals(listOf(0.5f, 0.5f), result.experimentResult?.variationWeights)
    }

    @Test
    fun explicitRanges_takePrecedenceAndSuppressBanditMetadata() = runTest {
        // Ranges override weights entirely in the bucketer, so no leaf's weight vector can
        // describe the assignment — the exposure must carry no bandit metadata (matches Python).
        // Ranges route everyone to variation 0; the leaf's 0/1 weights would have picked 1.
        @Language("json")
        val payload = """
            {
              "features": {
                "cb_feature": {
                  "defaultValue": "default",
                  "rules": [
                    {
                      "key": "cb_exp",
                      "hashAttribute": "id",
                      "coverage": 1,
                      "ranges": [[0.0, 1.0], [0.0, 0.0]],
                      "contextualBanditRef": "bandit_1",
                      "contextualVariations": ["control", "variant"]
                    }
                  ]
                }
              },
              "contextualBandits": {
                "bandit_1": {
                  "banditVersion": 3,
                  "contexts": [ { "leafId": 9, "condition": {}, "weights": [0.0, 1.0] } ]
                }
              }
            }
        """.trimIndent()

        val sdk = seededSDK(
            seed = payload,
            key = "seed_ranges_key",
            attributes = mapOf("id" to GBString("u1")),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = sdk.feature("cb_feature")

        assertEquals(GBString("control"), result.gbValue)
        assertNull(result.experimentResult?.leafId)
        assertNull(result.experimentResult?.variationWeights)
        assertNull(result.experimentResult?.banditVersion)
    }

    @Test
    fun initialPayload_malformedJsonIsIgnoredRatherThanFatal() = runTest {
        val sdk = seededSDK(
            seed = "{ not json at all",
            key = "seed_malformed_key",
            attributes = mapOf("id" to GBString("u1")),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Seed skipped, network down: the SDK simply has no features, exactly as if no seed was set.
        val result = sdk.feature("cb_feature")
        assertNull(result.gbValue)
        assertEquals(GBFeatureSource.unknownFeature, result.source)
    }
}
