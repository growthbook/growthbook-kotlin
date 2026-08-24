package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.model.GBFeaturesDiff
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import com.sdk.growthbook.sandbox.CachingJvm
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
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

    // Isolate the JVM disk cache per test: without this, every SDK here shares
    // <user.home>/.growthbook/…/FeatureCache_key.txt, so a payload persisted by one test is read
    // back on the next SDK's init — e.g. the change-handler test would then see no diff (features
    // already present) and its handler would never fire. Mirrors GrowthBookSDKBuilderTests.
    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    @Test
    fun plainFetch_appliesFeatures() = runTest {
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
        ).setCoroutineContext(UnconfinedTestDispatcher(testScheduler)).initialize()

        assertTrue(sdk.getFeatures().contains("onboarding"))
    }

    @Test
    fun encryptedFetch_appliesDecryptedFeatures() = runTest {
        val sdk = GBSDKBuilder(
            apiKey,
            host,
            encryptionKey = encKey,
            attributes = attrs,
            trackingCallback = { _, _ -> },
            networkDispatcher = MockNetworkClient(encPayload, null),
            remoteEval = false
        ).setCoroutineContext(UnconfinedTestDispatcher(testScheduler)).initialize()

        assertTrue(sdk.getFeatures().contains("testfeature1"))
    }

    @Test
    fun sseEncrypted_emitsDecryptedFeaturesToFlow() = runTest {
        val sseDispatcher = object : MockNetworkClient(null, null) {
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
        ).setCoroutineContext(UnconfinedTestDispatcher(testScheduler)).initialize()

        val emissions = sdk.startAutoRefreshFeatures().toList()
        val first = emissions.first() as Resource.Success

        assertTrue(sdk.getFeatures().contains("testfeature1"))
        assertNotNull(first.data, "Flow should emit decrypted feature, not null")
        assertTrue(first.data!!.contains("testfeature1"))
    }

    @Test
    fun sseEncrypted_invokeFeaturesChangeHandlerWithDiff() = runTest {
        val sseDispatcher = object : MockNetworkClient(null, null) {
            override fun consumeSSEConnection(
                url: String,
                sseConnectionController: SSEConnectionController?
            ): Flow<Resource<String>> = flowOf(Resource.Success(encPayload))
        }

        var captured: GBFeaturesDiff? = null

        val sdk = GBSDKBuilder(
            apiKey,
            host,
            encryptionKey = encKey,
            attributes = attrs,
            trackingCallback = { _, _ -> },
            networkDispatcher = sseDispatcher,
            remoteEval = false
        )
            .setFeaturesChangeHandler { diff -> captured = diff }
            .setCoroutineContext(UnconfinedTestDispatcher(testScheduler))
            .initialize()

        sdk.startAutoRefreshFeatures().toList()

        assertNotNull(captured, "change handler should fire on SSE update")
        assertTrue(captured!!.changedKeys.contains("testfeature1"))
        assertTrue(captured!!.hasChanges)
    }

    @Test
    fun sseMalformedPayload_emitsErrorNotCrash() = runTest {
        val sseDispatcher = object : MockNetworkClient(null, null) {
            override fun consumeSSEConnection(
                url: String, sseConnectionController: SSEConnectionController?
            ): Flow<Resource<String>> = flowOf(Resource.Success("{ this is not valid json"))
        }

        val sdk = GBSDKBuilder(
            apiKey, host,
            encryptionKey = null,
            attributes = attrs,
            trackingCallback = { _, _ -> },
            networkDispatcher = sseDispatcher,
            remoteEval = false
        ).setCoroutineContext(UnconfinedTestDispatcher(testScheduler)).initialize()

        // A malformed payload must degrade to Resource.Error, not throw out of the Flow.
        val emissions = sdk.startAutoRefreshFeatures().toList()

        assertTrue(emissions.first() is Resource.Error)
    }

    @Test
    fun sseEmptyFeatures_appliesEmptyMapNotError() = runTest {
        val emptyPayload = """{"status":200,"features":{}}"""
        val sseDispatcher = object : MockNetworkClient(null, null) {
            override fun consumeSSEConnection(
                url: String, sseConnectionController: SSEConnectionController?
            ): Flow<Resource<String>> = flowOf(Resource.Success(emptyPayload))
        }

        val sdk = GBSDKBuilder(
            apiKey, host,
            encryptionKey = null,
            attributes = attrs,
            trackingCallback = { _, _ -> },
            networkDispatcher = sseDispatcher,
            remoteEval = false
        ).setCoroutineContext(UnconfinedTestDispatcher(testScheduler)).initialize()

        val emissions = sdk.startAutoRefreshFeatures().toList()

        assertTrue(emissions.first() is Resource.Success)   // Empty features is not Error
        assertTrue(sdk.getFeatures().isEmpty())             // empty, but applied
    }
}