package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class FeaturesViewModelTests : FeaturesFlowDelegate {

    private var isSuccess: Boolean = false
    private var isError: Boolean = false
    private var hasFeatures: Boolean = false
    private var isNotModified: Boolean = false
    private var featuresAPIModelCalled: Boolean = false
    private var receivedFromCache: Boolean = false
    private var receivedCacheError: Boolean = false

    private val gbContext = GBContext(
        "Key",
        enabled = true, attributes = HashMap(), forcedVariations = HashMap(),
        qaMode = false,
        trackingCallback = { _, _ ->

        },
        encryptionKey = null,
        remoteEval = false,
    )
    private val testGbOptions = GBOptions("https://example.com", null)

    @Test
    fun testSuccess() {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext,
                testGbOptions
            ),
            "3tfeoyW0wlo47bDnbWDkxg==", false,
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testSuccessForEncryptedFeatures() {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(
                MockNetworkClient(
                    MockResponse.successResponseEncryptedFeatures, null
                ),
                gbContext, testGbOptions,
            ),
            "3tfeoyW0wlo47bDnbWDkxg==", false,
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testError() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(
                    null, Throwable("UNKNOWN", null)
                ),
                gbContext, testGbOptions,
            ),
            cachingEnabled = false,
        )

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
        assertTrue(!hasFeatures)
    }

    @Test
    fun testInvalid() {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(
                    MockResponse.ERROR_RESPONSE, null
                ),
                gbContext, testGbOptions,
            ),
            encryptionKey = "",
            cachingEnabled = false,
        )
        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
        assertTrue(!hasFeatures)
    }

    @Test
    fun testForRemoteEvalSuccess() {
        isSuccess = false
        isError = true

        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource =
                FeaturesDataSource(
                    dispatcher = MockNetworkClient(
                        successResponse = MockResponse.successResponse,
                        error = null
                    ),
                    gbContext, testGbOptions
                ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
        )
        val forcedFeature = mapOf("feature" to GBNumber(123))
        val forcedVariation = mapOf("feature" to 123)
        val attributes = emptyMap<String, Any>()
        val payload = GBRemoteEvalParams(
            attributes = attributes,
            forcedFeatures = forcedFeature,
            forcedVariations = forcedVariation,
        )

        viewModel.fetchFeatures(remoteEval = true, payload = payload)
        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testForRemoteEvalFailed() {
        isSuccess = false
        isError = true

        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource =
                FeaturesDataSource(
                    dispatcher = MockNetworkClient(
                        successResponse = null,
                        error = Error()
                    ),
                    gbContext, testGbOptions,
                ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
        )
        val forcedFeature = mapOf("feature" to GBNumber(123))
        val forcedVariation = mapOf("feature" to 123)
        val attributes = emptyMap<String, Any>()
        val payload = GBRemoteEvalParams(
            attributes = attributes,
            forcedFeatures = forcedFeature,
            forcedVariations = forcedVariation
        )

        viewModel.fetchFeatures(remoteEval = true, payload = payload)

        assertTrue(!isSuccess)
        assertTrue(isError)
        assertTrue(!hasFeatures)
    }

    @Test
    fun testNotModified() {
        isSuccess = false
        isError = false
        isNotModified = false

        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(successResponse = null, error = null, notModified = true),
                gbContext, testGbOptions,
            ),
            cachingEnabled = false,
        )

        viewModel.fetchFeatures()

        assertTrue(isNotModified)
        assertTrue(!isSuccess)
        assertTrue(!isError)
    }

    @Test
    fun testSuccessWithCachingEnabled() {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext,
                testGbOptions
            ),
            "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = true,
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testFeaturesAPIModelSuccessfullyCalled() {
        featuresAPIModelCalled = false
        val viewModel = FeaturesViewModel(
            this,
            FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext,
                testGbOptions
            ),
            "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
        )

        viewModel.fetchFeatures()

        assertTrue(featuresAPIModelCalled)
    }

    @Test
    fun testSavedGroupsFetchedSuccessfully() {
        isSuccess = false
        isError = true

        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseWithSavedGroups, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "",
            cachingEnabled = false,
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
    }

    @Test
    fun testSavedGroupsFetchFailed() {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseWithEncryptedFeaturesOnly, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "",
            cachingEnabled = false,
        )

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
    }

    @Test
    fun testApplyCachedFeaturesForPlainFeatures() {
        receivedFromCache = false
        val cacheLayer = MockCachingLayer.fromApiResponse(MockResponse.successResponse)
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
        )

        viewModel.fetchFeatures()

        assertTrue(
            receivedFromCache,
            "Expected featuresFetchedSuccessfully(isRemote=false) from cache"
        )
        assertTrue(hasFeatures)
    }

    @Test
    fun testApplyCachedFeaturesForEncryptedFeatures() {
        receivedFromCache = false
        val cacheLayer =
            MockCachingLayer.fromApiResponse(MockResponse.successResponseEncryptedFeatures)
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseEncryptedFeatures, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
        )

        viewModel.fetchFeatures()

        assertTrue(
            receivedFromCache,
            "Expected featuresFetchedSuccessfully(isRemote=false) from encrypted cache"
        )
        assertTrue(hasFeatures)
    }

    @Test
    fun testFetchFeaturesWithCacheException() {
        receivedCacheError = false
        val cacheLayer = MockCachingLayer(throwOnGet = true)
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(null, Throwable("Network error")),
                gbContext, testGbOptions,
            ),
            cachingEnabled = false,
            cachingLayer = cacheLayer,
        )

        viewModel.fetchFeatures()

        assertTrue(
            receivedCacheError,
            "Expected featuresFetchFailed(isRemote=false) from cache exception"
        )
        assertTrue(isError)
        assertTrue(!isSuccess)
    }

    @Test
    fun testCacheWriteFailureDoesNotDiscardFetchedFeatures() {
        isSuccess = false
        isError = false
        val cacheLayer = MockCachingLayer(throwOnPut = true)
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = true,
            cachingLayer = cacheLayer,
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess, "Features should be applied even when cache write fails")
        assertTrue(!isError, "A cache write failure should not be reported as a fetch failure")
        assertTrue(hasFeatures)
    }

    @Test
    fun testAutoRefreshFeaturesReturnsFlow() {
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
        )

        val flow = viewModel.autoRefreshFeatures()

        assertNotNull(flow)
    }

    @Test
    fun testSkipsNetworkWhenCacheIsFresh() {
        var networkCallCount = 0
        val mockClient = object : MockNetworkClient(MockResponse.successResponse, null) {
            override fun consumeGETRequestWithNotModified(
                request: String,
                onSuccess: (String) -> Unit,
                onError: (Throwable) -> Unit,
                onNotModified: (() -> Unit)
            ): Job {
                networkCallCount++
                return super.consumeGETRequestWithNotModified(request, onSuccess, onError, onNotModified)
            }
        }
        val cacheLayer = MockCachingLayer.fromApiResponse(
            MockResponse.successResponse,
            cachedAt = Clock.System.now().toEpochMilliseconds()
        )
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(mockClient, gbContext, testGbOptions),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L,
        )

        viewModel.fetchFeatures()

        assertEquals(0, networkCallCount, "Network should not be called when cache is fresh")
    }

    @Test
    fun testFetchesNetworkWhenCacheIsStale() {
        var networkCallCount = 0
        val mockClient = object : MockNetworkClient(MockResponse.successResponse, null) {
            override fun consumeGETRequestWithNotModified(
                request: String,
                onSuccess: (String) -> Unit,
                onError: (Throwable) -> Unit,
                onNotModified: (() -> Unit)
            ): Job {
                networkCallCount++
                return super.consumeGETRequestWithNotModified(request, onSuccess, onError, onNotModified)
            }
        }
        val staleTime = Clock.System.now().toEpochMilliseconds() - (49 * 60 * 60 * 1000L)
        val cacheLayer = MockCachingLayer.fromApiResponse(
            MockResponse.successResponse,
            cachedAt = staleTime
        )
        val viewModel = FeaturesViewModel(
            delegate = this,
            dataSource = FeaturesDataSource(mockClient, gbContext, testGbOptions),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L,
        )

        viewModel.fetchFeatures()

        assertEquals(1, networkCallCount, "Network should be called when cache is stale")
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testConcurrentAwaitRefreshSharesSingleNetworkCall() = runTest {
        var networkCallCount = 0
        val pending = mutableListOf<(String) -> Unit>()
        // Captures the request callback instead of resolving it, so the refresh
        // stays "in flight" while all callers arrive.
        val mockClient = object : MockNetworkClient(MockResponse.successResponse, null) {
            override fun consumeGETRequestWithNotModified(
                request: String,
                onSuccess: (String) -> Unit,
                onError: (Throwable) -> Unit,
                onNotModified: (() -> Unit)
            ): Job {
                networkCallCount++
                pending.add(onSuccess)
                return Job()
            }
        }
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(mockClient, gbContext, testGbOptions),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            scope = backgroundScope,
        )

        // Five callers race into awaitRefresh() while no request has completed yet.
        repeat(5) { backgroundScope.launch { viewModel.awaitRefresh() } }
        runCurrent()

        assertEquals(
            1,
            networkCallCount,
            "Concurrent awaitRefresh() callers must share a single in-flight network request"
        )

        // Complete the shared request, freeing the slot.
        pending.forEach { it(MockResponse.successResponse) }
        runCurrent()
        assertTrue(isSuccess)

        // A fresh refresh after completion starts a new request (slot was cleared).
        backgroundScope.launch { viewModel.awaitRefresh() }
        runCurrent()
        assertEquals(
            2,
            networkCallCount,
            "A refresh after the in-flight one completes must start a new request"
        )
    }

    override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
        isSuccess = true
        isError = false
        hasFeatures = features.isNotEmpty()
        if (!isRemote) receivedFromCache = true
    }

    override fun featuresNotModified() {
        isNotModified = true
    }

    override fun featuresFetchFailed(error: GBError, isRemote: Boolean) {
        isSuccess = false
        isError = true
        hasFeatures = false
        if (!isRemote) receivedCacheError = true
    }

    override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) {
        isSuccess = false
        isError = true
    }

    override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) {
        isSuccess = true
        isError = false
    }

    override fun featuresAPIModelSuccessfully(model: FeaturesDataModel) {
        isSuccess = true
        isError = false
        hasFeatures = !model.features.isNullOrEmpty()
        featuresAPIModelCalled = true
    }
}
