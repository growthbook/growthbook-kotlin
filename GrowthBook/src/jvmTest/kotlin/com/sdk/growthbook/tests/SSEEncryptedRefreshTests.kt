package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SSEEncryptedRefreshTests {
    private val host = "https://cdn.test"
    private val apiKey = "key"
    private val encKey = "Ns04T5n9+59rl2x3SlNHtQ=="
    private val encBlob =
        "vMSg2Bj/IurObDsWVmvkUg==.L6qtQkIzKDoE2Dix6IAKDcVel8PHUnzJ7JjmLjFZFQDqidRIoCxKmvxvUj2kTuHFTQ3/NJ3D6XhxhXXv2+dsXpw5woQf0eAgqrcxHrbtFORs18tRXRZza7zqgzwvcznx"
    private val encPayload = """{"status":200,"encryptedFeatures":"$encBlob"}"""
    private val attrs = mapOf("id" to GBString("user-1"))

    @Test
    fun plainFetch_appliesFeatures() {
        val sdk = GBSDKBuilder(
            apiKey,
            host,
            encryptionKey = null,
            attributes = attrs,
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(
                MockResponse.successResponse, null
            ),
            remoteEval = false
        ).initialize()

        assertTrue(sdk.getFeatures().contains("onboarding"))
    }

    @Test
    fun encryptedFetch_appliesDecryptedFeatures() {
        val sdk = GBSDKBuilder(
            apiKey,
            host,
            encryptionKey = encKey,
            attributes = attrs,
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(encPayload, null),
            remoteEval = false
        ).initialize()

        assertTrue(sdk.getFeatures().contains("testfeature1"))
    }

    @Test
    fun sseEncrypted_emitsDecryptedFeaturesToFlow() = runTest {
        val sseDispatcher = object: MockNetworkClient(null, null) {
            override fun consumeSSEConnection(
                url: String,
                sseConnectionController: SSEConnectionController?
            ): Flow<Resource<String>> {
                return flowOf(Resource.Success(encPayload))
            }
        }

        val sdk = GBSDKBuilder(
            apiKey,
            host,
            encryptionKey = encKey,
            attributes = attrs,
            trackingCallback = { _, _ -> },
            networkDispatcher = sseDispatcher,
            remoteEval = false
        ).initialize()

        val emissions = sdk.startAutoRefreshFeatures().toList()
        val first = emissions.first() as Resource.Success

        assertTrue(sdk.getFeatures().contains("testfeature1"))
        assertNotNull(first.data, "Flow should emit decrypted feature, not null")
        assertTrue(first.data!!.contains("testfeature1"))
    }
}