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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
        )

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
    }

    @Test
    fun testHandleFetchFeaturesWithoutRemoteEvalPlainFeatures() {
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
            coroutineContext = Dispatchers.Unconfined,
        )

        viewModel.fetchFeatures()

        assertTrue(
            receivedFromCache,
            "Expected featuresFetchedSuccessfully(isRemote=false) from cache"
        )
        assertTrue(hasFeatures)
    }

    @Test
    fun testHandleFetchFeaturesWithoutRemoteEvalEncryptedFeatures() {
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
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
            coroutineContext = Dispatchers.Unconfined,
        )

        val flow = viewModel.autoRefreshFeatures()

        assertNotNull(flow)
    }

    /**
     * Proves the race-condition fix: features are applied to context only AFTER the sticky-bucket
     * refresh (onPayloadReady) has fully completed — verified under a NON-immediate dispatcher so
     * the ordering is enforced by code, not masked by Dispatchers.Unconfined running everything inline.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testFeaturesAppliedOnlyAfterStickyRefreshCompletes() = runTest {
        val events = mutableListOf<String>()

        // A delegate whose onPayloadReady actually suspends, mimicking sticky-bucket IO that
        // does not complete synchronously (real GBStickyBucketService reads from storage).
        val orderingDelegate = object : FeaturesFlowDelegate {
            override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
                events += "featuresApplied"
            }

            override suspend fun onPayloadReady(model: FeaturesDataModel) {
                events += "stickyRefreshStart"
                delay(100) // suspends — refresh is in flight
                events += "stickyRefreshDone"
            }

            override fun featuresFetchFailed(error: GBError, isRemote: Boolean) {
                events += "fetchFailed"
            }

            override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) = Unit
            override fun featuresNotModified() = Unit
        }

        val viewModel = FeaturesViewModel(
            delegate = orderingDelegate,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            // Non-immediate: launched work is queued on the scheduler, not run inline.
            coroutineContext = StandardTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        // With a non-immediate dispatcher nothing has run yet — proves the work is now async,
        // unlike the old synchronous fire-and-forget that applied results inside fetchFeatures().
        assertTrue(
            events.isEmpty(),
            "Payload processing must be queued, not executed inline, on a non-immediate dispatcher"
        )

        // Run up to the first suspension point: refresh has started but NOT finished.
        runCurrent()
        assertEquals(
            listOf("stickyRefreshStart"),
            events,
            "Features must not be applied while the sticky-bucket refresh is still suspended"
        )

        // Let the refresh's suspension resolve.
        advanceUntilIdle()
        assertEquals(
            listOf("stickyRefreshStart", "stickyRefreshDone", "featuresApplied"),
            events,
            "featuresFetchedSuccessfully must fire strictly after onPayloadReady completes"
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

    override suspend fun onPayloadReady(model: FeaturesDataModel) {
        isSuccess = true
        isError = false
        hasFeatures = !model.features.isNullOrEmpty()
        featuresAPIModelCalled = true
    }
}
