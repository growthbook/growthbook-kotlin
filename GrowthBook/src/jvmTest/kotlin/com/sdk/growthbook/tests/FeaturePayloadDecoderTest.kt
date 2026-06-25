package com.sdk.growthbook.tests

import com.sdk.growthbook.features.FeaturePayloadDecoder
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.FeaturesResult
import com.sdk.growthbook.features.decodeToResult
import com.sdk.growthbook.utils.encryptToFeaturesDataModel
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeaturePayloadDecoderTest {
    private val encKey = "Ns04T5n9+59rl2x3SlNHtQ=="
    private val encBlob =
        "vMSg2Bj/IurObDsWVmvkUg==.L6qtQkIzKDoE2Dix6IAKDcVel8PHUnzJ7JjmLjFZFQDqidRIoCxKmvxvUj2kTuHFTQ3/NJ3D6XhxhXXv2+dsXpw5woQf0eAgqrcxHrbtFORs18tRXRZza7zqgzwvcznx"

    @Test
    fun plainFeatures_returnsApplied() {
        val raw = FeaturesDataModel(
            features = encryptToFeaturesDataModel(
                """{"f1":{"defaultValue":true}}"""
            )
        )

        val result = FeaturePayloadDecoder(encryptionKey = null).decodeToResult(raw)

        assertIs<FeaturesResult.Applied>(result)
        assertTrue(result.features.contains("f1"))
    }

    @Test
    fun encryptedFeatures_correctKey_returnsApplied() {
        val raw = FeaturesDataModel(encryptedFeatures = encBlob)
        val result = FeaturePayloadDecoder(encryptionKey = encKey).decodeToResult(raw)

        assertIs<FeaturesResult.Applied>(result)
        assertTrue(result.features.contains("testfeature1"))
    }

    @Test
    fun encryptedFeatures_wrongKey_returnsFailed() {
        val raw = FeaturesDataModel(encryptedFeatures = encBlob)
        val result = FeaturePayloadDecoder(encryptionKey = "AAAAAAAAAAAAAAAAAAAAAA==")
            .decodeToResult(raw)

        assertIs<FeaturesResult.Failed>(result)
    }

    @Test
    fun encryptedFeatures_noKey_returnsFailed() {
        val raw = FeaturesDataModel(encryptedFeatures = encBlob)
        val result = FeaturePayloadDecoder(encryptionKey = null).decodeToResult(raw)

        assertIs<FeaturesResult.Failed>(result)
    }

    @Test
    fun emptyPayload_returnsFailed() {
        val raw = FeaturesDataModel()
        val result = FeaturePayloadDecoder(encryptionKey = null).decodeToResult(raw)

        assertIs<FeaturesResult.Failed>(result)
    }
}
