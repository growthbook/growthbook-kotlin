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
