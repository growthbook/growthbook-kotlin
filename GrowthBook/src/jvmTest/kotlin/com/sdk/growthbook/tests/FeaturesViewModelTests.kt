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
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.stickybucket.GBStickyBucketService
import com.sdk.growthbook.utils.GBStickyAssignmentsDocument
import com.sdk.growthbook.utils.GBUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    fun testSuccess() = runTest {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this@FeaturesViewModelTests,
            FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext,
                testGbOptions
            ),
            "3tfeoyW0wlo47bDnbWDkxg==", false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testSuccessForEncryptedFeatures() = runTest {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this@FeaturesViewModelTests,
            FeaturesDataSource(
                MockNetworkClient(
                    MockResponse.successResponseEncryptedFeatures, null
                ),
                gbContext, testGbOptions,
            ),
            "3tfeoyW0wlo47bDnbWDkxg==", false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testError() = runTest {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(
                    null, Throwable("UNKNOWN", null)
                ),
                gbContext, testGbOptions,
            ),
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
        assertTrue(!hasFeatures)
    }

    @Test
    fun testInvalid() = runTest {

        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(
                    MockResponse.ERROR_RESPONSE, null
                ),
                gbContext, testGbOptions,
            ),
            encryptionKey = "",
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )
        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
        assertTrue(!hasFeatures)
    }

    @Test
    fun testForRemoteEvalSuccess() = runTest {
        isSuccess = false
        isError = true

        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
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
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
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
    fun testForRemoteEvalFailed() = runTest {
        isSuccess = false
        isError = true

        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
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
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
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
    fun testNotModified() = runTest {
        isSuccess = false
        isError = false
        isNotModified = false

        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(successResponse = null, error = null, notModified = true),
                gbContext, testGbOptions,
            ),
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(isNotModified)
        assertTrue(!isSuccess)
        assertTrue(!isError)
    }

    @Test
    fun testSuccessWithCachingEnabled() = runTest {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            this@FeaturesViewModelTests,
            FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext,
                testGbOptions
            ),
            "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = true,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
        assertTrue(hasFeatures)
    }

    @Test
    fun testFeaturesAPIModelSuccessfullyCalled() = runTest {
        featuresAPIModelCalled = false
        val viewModel = FeaturesViewModel(
            this@FeaturesViewModelTests,
            FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext,
                testGbOptions
            ),
            "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(featuresAPIModelCalled)
    }

    @Test
    fun testSavedGroupsFetchedSuccessfully() = runTest {
        isSuccess = false
        isError = true

        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseWithSavedGroups, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "",
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess)
        assertTrue(!isError)
    }

    @Test
    fun testSavedGroupsFetchFailed() = runTest {
        isSuccess = false
        isError = true
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseWithEncryptedFeaturesOnly, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "",
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(!isSuccess)
        assertTrue(isError)
    }

    @Test
    fun testHandleFetchFeaturesWithoutRemoteEvalPlainFeatures() = runTest {
        receivedFromCache = false
        val cacheLayer = MockCachingLayer.fromApiResponse(MockResponse.successResponse)
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(
            receivedFromCache,
            "Expected featuresFetchedSuccessfully(isRemote=false) from cache"
        )
        assertTrue(hasFeatures)
    }

    @Test
    fun testHandleFetchFeaturesWithoutRemoteEvalEncryptedFeatures() = runTest {
        receivedFromCache = false
        val cacheLayer =
            MockCachingLayer.fromApiResponse(MockResponse.successResponseEncryptedFeatures)
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseEncryptedFeatures, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertTrue(
            receivedFromCache,
            "Expected featuresFetchedSuccessfully(isRemote=false) from encrypted cache"
        )
        assertTrue(hasFeatures)
    }

    @Test
    fun testFetchFeaturesWithCacheException() = runTest {
        receivedCacheError = false
        val cacheLayer = MockCachingLayer(throwOnGet = true)
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(null, Throwable("Network error")),
                gbContext, testGbOptions,
            ),
            cachingEnabled = false,
            cachingLayer = cacheLayer,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
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
    fun testCacheWriteFailureDoesNotDiscardFetchedFeatures() = runTest {
        isSuccess = false
        isError = false
        val cacheLayer = MockCachingLayer(throwOnPut = true)
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = true,
            cachingLayer = cacheLayer,
            coroutineContext = UnconfinedTestDispatcher(testScheduler)
        )

        viewModel.fetchFeatures()

        assertTrue(isSuccess, "Features should be applied even when cache write fails")
        assertTrue(!isError, "A cache write failure should not be reported as a fetch failure")
        assertTrue(hasFeatures)
    }

    @Test
    fun testAutoRefreshFeaturesReturnsFlow() = runTest {
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        val flow = viewModel.autoRefreshFeatures()

        assertNotNull(flow)
    }

    /**
     * Proves the race-condition fix: features are applied to context only AFTER the sticky-bucket
     * refresh (onPayloadReady) has fully completed — verified under a NON-immediate dispatcher so
     * the ordering is enforced by code, not masked by UnconfinedTestDispatcher(testScheduler) running everything inline.
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

    /**
     * Integration-level companion to [testFeaturesAppliedOnlyAfterStickyRefreshCompletes]: instead of a
     * fake onPayloadReady that merely delays, this drives the REAL [GBUtils.refreshStickyBuckets] over the
     * actual payload (mirroring GrowthBookSDK.onPayloadReady) and asserts that, by the time features are
     * applied, the user's sticky-bucket docs are already written into the context. Only the storage leaf
     * (getAllAssignments) is faked — and it suspends on the test scheduler, so the ordering is enforced by
     * the production code under a NON-immediate dispatcher, not masked by UnconfinedTestDispatcher(testScheduler).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testStickyDocsPopulatedBeforeFeaturesAppliedWithRealRefresh() = runTest {
        // Fake only the storage IO. It suspends (delay) on the test scheduler to mimic a read that does
        // not complete synchronously, and returns a doc for whichever identifier attributes the real
        // refresh derived from the payload.
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
                delay(100) // storage read in flight — refresh has NOT completed yet
                return attributes.entries.associate { (name, value) ->
                    "$name||$value" to GBStickyAssignmentsDocument(
                        attributeName = name,
                        attributeValue = value,
                        assignments = mapOf("exp__0" to "control"),
                    )
                }
            }
        }

        val ctx = GBContext(
            apiKey = "Key",
            enabled = true,
            attributes = mapOf("id" to GBString("u1")),
            forcedVariations = emptyMap(),
            qaMode = false,
            trackingCallback = { _, _ -> },
            encryptionKey = null,
            stickyBucketService = service,
        )

        // Snapshot of the context's sticky docs at the exact moment features are applied.
        var docsAtApplyTime: Map<String, GBStickyAssignmentsDocument>? = null

        val delegate = object : FeaturesFlowDelegate {
            // Mirrors GrowthBookSDK.onPayloadReady: real refresh against the freshly fetched payload.
            override suspend fun onPayloadReady(model: FeaturesDataModel) {
                GBUtils.refreshStickyBuckets(
                    context = ctx,
                    data = model,
                    attributeOverrides = emptyMap(),
                )
            }

            override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
                docsAtApplyTime = ctx.stickyBucketAssignmentDocs
            }

            override fun featuresFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) = Unit
            override fun featuresNotModified() = Unit
        }

        val viewModel = FeaturesViewModel(
            delegate = delegate,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponse, null),
                ctx, testGbOptions,
            ),
            cachingEnabled = false,
            coroutineContext = StandardTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        // Queued, not run: features have not been applied while the refresh is still pending.
        assertEquals(null, docsAtApplyTime)

        advanceUntilIdle()

        // featuresFetchedSuccessfully fired only after the real refresh wrote the current user's docs.
        assertNotNull(
            docsAtApplyTime,
            "stickyBucketAssignmentDocs must be populated before features are applied",
        )
        assertTrue(
            docsAtApplyTime!!.containsKey("id||u1"),
            "Refresh must have loaded the current user's assignment doc before features applied",
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
