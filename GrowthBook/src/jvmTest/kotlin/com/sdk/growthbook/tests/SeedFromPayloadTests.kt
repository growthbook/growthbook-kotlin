package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.encryptToFeaturesDataModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers [GBSDKBuilder.setInitialPayload]: a build-time snapshot bundled exactly as the API
 * returns it, so an encrypted payload need not be decrypted into the app bundle.
 */
class SeedFromPayloadTests {

    // Same fixture as FeaturePayloadDecoderTest; the blob decrypts to a "testfeature1" flag.
    private val encKey = "Ns04T5n9+59rl2x3SlNHtQ=="
    private val encBlob =
        "vMSg2Bj/IurObDsWVmvkUg==.L6qtQkIzKDoE2Dix6IAKDcVel8PHUnzJ7JjmLjFZFQDqidRIoCxKmvxvUj2kTuHFTQ3/NJ3D6XhxhXXv2+dsXpw5woQf0eAgqrcxHrbtFORs18tRXRZza7zqgzwvcznx"

    // Caching stays off so the assertions see the seed alone, not a cache read racing it.
    private fun seed(
        payload: String,
        encryptionKey: String? = null,
        initialFeatures: GBFeatures? = null,
    ): GrowthBookSDK = GBSDKBuilder(
        "test-key",
        "https://cdn.growthbook.io",
        attributes = emptyMap(),
        encryptionKey = encryptionKey,
        trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
        networkDispatcher = MockNetworkClient(null, null),
        cachingEnabled = false,
    )
        .setInitialPayload(payload)
        .apply { initialFeatures?.let { setInitialFeatures(it) } }
        .initialize()

    @Test
    fun encryptedPayload_isDecryptedAndSeeded() {
        val sdk = seed(
            payload = """{"status":200,"features":{},"encryptedFeatures":"$encBlob"}""",
            encryptionKey = encKey,
        )

        assertTrue(sdk.getFeatures().containsKey("testfeature1"))
    }

    @Test
    fun plainPayload_isSeeded() {
        val sdk = seed("""{"status":200,"features":{"f1":{"defaultValue":true}}}""")

        assertTrue(sdk.getFeatures().containsKey("f1"))
    }

    @Test
    fun savedGroupsInPayload_areSeeded() {
        val sdk = seed(
            """{"features":{"f1":{"defaultValue":true}},"savedGroups":{"admins":["a","b"]}}"""
        )

        assertEquals(setOf("admins"), sdk.getGBContext().savedGroups?.keys)
    }

    @Test
    fun explicitInitialFeatures_winOverPayload() {
        val sdk = seed(
            payload = """{"features":{"fromPayload":{"defaultValue":true}}}""",
            initialFeatures = encryptToFeaturesDataModel("""{"fromMap":{"defaultValue":true}}""")!!,
        )

        assertTrue(sdk.getFeatures().containsKey("fromMap"))
        assertFalse(sdk.getFeatures().containsKey("fromPayload"))
    }

    @Test
    fun undecryptablePayload_isIgnoredAndInitializationSucceeds() {
        val sdk = seed(
            payload = """{"encryptedFeatures":"$encBlob"}""",
            encryptionKey = "AAAAAAAAAAAAAAAAAAAAAA==",
        )

        assertTrue(sdk.getFeatures().isEmpty())
    }

    @Test
    fun malformedPayload_isIgnoredAndInitializationSucceeds() {
        val sdk = seed("not json at all")

        assertTrue(sdk.getFeatures().isEmpty())
    }
}
