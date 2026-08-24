package com.sdk.growthbook.tests

import com.sdk.growthbook.features.FeaturePayloadDecoder
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.encryptToFeaturesDataModel
import org.junit.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeaturePayloadDecoderTest {
    private val encKey = "Ns04T5n9+59rl2x3SlNHtQ=="
    private val encBlob =
        "vMSg2Bj/IurObDsWVmvkUg==.L6qtQkIzKDoE2Dix6IAKDcVel8PHUnzJ7JjmLjFZFQDqidRIoCxKmvxvUj2kTuHFTQ3/NJ3D6XhxhXXv2+dsXpw5woQf0eAgqrcxHrbtFORs18tRXRZza7zqgzwvcznx"

    /**
     * Decodes [raw] and returns the resulting features, or null when decoding yields nothing
     * (empty/undecodable payload) or throws (e.g. wrong key). Mirrors how [handleNetworkModel]
     * treats a null-features [com.sdk.growthbook.features.DecodedPayload] as a failed fetch.
     */
    private fun decodedFeatures(key: String?, raw: FeaturesDataModel): GBFeatures? =
        runCatching { FeaturePayloadDecoder(key).decode(raw).features }.getOrNull()

    @Test
    fun plainFeatures_decodesFeatures() {
        val raw = FeaturesDataModel(
            features = encryptToFeaturesDataModel(
                """{"f1":{"defaultValue":true}}"""
            )
        )

        val features = decodedFeatures(key = null, raw = raw)

        assertNotNull(features)
        assertTrue(features.contains("f1"))
    }

    @Test
    fun encryptedFeatures_correctKey_decodesFeatures() {
        val raw = FeaturesDataModel(encryptedFeatures = encBlob)

        val features = decodedFeatures(key = encKey, raw = raw)

        assertNotNull(features)
        assertTrue(features.contains("testfeature1"))
    }

    @Test
    fun encryptedFeatures_wrongKey_returnsNull() {
        val raw = FeaturesDataModel(encryptedFeatures = encBlob)

        assertNull(decodedFeatures(key = "AAAAAAAAAAAAAAAAAAAAAA==", raw = raw))
    }

    @Test
    fun encryptedFeatures_noKey_returnsNull() {
        val raw = FeaturesDataModel(encryptedFeatures = encBlob)

        assertNull(decodedFeatures(key = null, raw = raw))
    }

    @Test
    fun emptyPayload_returnsNull() {
        val raw = FeaturesDataModel()

        assertNull(decodedFeatures(key = null, raw = raw))
    }
}
