package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.features.FetchResult
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
            remoteEval = true,
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

        viewModel.fetchFeatures(payload = payload)
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
            remoteEval = true,
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

        viewModel.fetchFeatures(payload = payload)

        assertTrue(!isSuccess)
        assertTrue(isError)
        assertTrue(!hasFeatures)
    }

    @Test
    fun testRemoteEvalPostBodyEncodesNativeValuesAndForcedFeaturesArray() = runTest {
        // Regression: the remote-eval POST body must carry real JSON, not GBValue.toString().
        //   - attributes: GBNumber(8490047) -> 8490047 (NOT the string "GBNumber(value=8490047)")
        //   - forcedFeatures: an array of [key, value] pairs (mirroring sdk-js), NOT a JSON object,
        //     otherwise the GrowthBook proxy rejects the request with 400 Bad Request.
        val client = CapturingPostNetworkClient(MockResponse.successResponse)
        val payload = GBRemoteEvalParams(
            attributes = mapOf(
                "user_id" to GBNumber(8490047),
                "os_platform" to GBString("Android"),
            ),
            forcedFeatures = mapOf("demo-forced-flag" to GBBoolean(true)),
            forcedVariations = emptyMap(),
        )
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(client, gbContext, testGbOptions),
            cachingEnabled = false,
            remoteEval = true,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures(payload = payload)

        val body = assertNotNull(client.lastBodyParams)

        @Suppress("UNCHECKED_CAST")
        val attrs = body["attributes"] as Map<String, Any?>
        // Real JSON primitives — never the GBValue.toString() fallback.
        assertEquals(JsonPrimitive(8490047), attrs["user_id"])
        assertEquals(JsonPrimitive("Android"), attrs["os_platform"])

        val forced = body["forcedFeatures"]
        assertTrue(forced is JsonArray, "forcedFeatures must be a JsonArray, was ${forced?.let { it::class }}")
        assertEquals(1, forced.size)
        val pair = forced[0]
        assertTrue(pair is JsonArray, "each forcedFeatures entry must be a [key, value] JsonArray")
        assertEquals(JsonPrimitive("demo-forced-flag"), pair[0])
        assertEquals(JsonPrimitive(true), pair[1])
    }

    @Test
    fun testAwaitRefreshUsesRemoteEvalPostWhenRemoteEval() = runTest {
        // #8: in remote-eval mode the coalesced retry (awaitRefresh, driven by suspendFeature)
        // must issue a remote-eval POST, never a bare GET that could surface unevaluated features.
        val client = CountingPostNetworkClient(MockResponse.successResponse)
        val payload = GBRemoteEvalParams(
            attributes = mapOf("id" to "1"),
            forcedFeatures = emptyMap(),
            forcedVariations = emptyMap(),
        )
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(client, gbContext, testGbOptions),
            cachingEnabled = false,
            remoteEval = true,
            remoteEvalPayloadProvider = { payload },
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        val result = viewModel.awaitRefresh()

        assertEquals(FetchResult.Success, result)
        assertEquals(1, client.postCount)
        assertEquals(0, client.getCount)
        assertTrue(isSuccess)
        assertTrue(hasFeatures)
    }

    @Test
    fun testAwaitRefreshUsesGetWhenNotRemoteEval() = runTest {
        val client = CountingPostNetworkClient(MockResponse.successResponse)
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(client, gbContext, testGbOptions),
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        val result = viewModel.awaitRefresh()

        assertEquals(FetchResult.Success, result)
        assertEquals(0, client.postCount)
        assertEquals(1, client.getCount)
    }

    @Test
    fun testRemoteEvalOutOfOrderResponseIsDiscarded() = runTest {
        // #2: two remote-eval POSTs are in flight; the OLDER one completing last must NOT overwrite
        // the newer one's evaluated features (generation guard).
        val client = DeferredPostNetworkClient()
        var appliedFeatures: GBFeatures? = null
        val delegate = object : FeaturesFlowDelegate {
            override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
                appliedFeatures = features
            }
            override suspend fun onPayloadReady(model: FeaturesDataModel) = Unit
            override fun featuresFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) = Unit
            override fun featuresNotModified() = Unit
        }
        val viewModel = FeaturesViewModel(
            delegate = delegate,
            dataSource = FeaturesDataSource(client, gbContext, testGbOptions),
            cachingEnabled = false,
            remoteEval = true,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )
        val respOld = """{"status":200,"features":{"old_feature":{"defaultValue":true}}}"""
        val respNew = """{"status":200,"features":{"new_feature":{"defaultValue":true}}}"""

        viewModel.fetchFeatures() // generation 1 (older)
        viewModel.fetchFeatures() // generation 2 (newer)
        assertEquals(2, client.pendingPosts.size)

        // Respond newest first, then the stale older one arrives late.
        client.pendingPosts[1](respNew)
        client.pendingPosts[0](respOld)

        assertNotNull(appliedFeatures)
        assertTrue(
            appliedFeatures!!.containsKey("new_feature"),
            "the newest remote-eval response must be applied"
        )
        assertTrue(
            !appliedFeatures!!.containsKey("old_feature"),
            "a stale older remote-eval response must be discarded, not applied last"
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testStaleRemoteEvalRoundReportsSupersededNotSuccess() = runTest {
        // P1 (awaiter contract): a remote-eval round whose response is discarded by the generation
        // guard must NOT resolve awaitRefresh() as Success — otherwise a suspendFeature() awaiting
        // the OLD attributes would return a stale evaluation while a newer request is still pending.
        // It must resolve as Superseded so the caller re-joins the current generation. Companion to
        // testRemoteEvalOutOfOrderResponseIsDiscarded, which only checks the final applied payload.
        val client = DeferredPostNetworkClient()
        val emptyParams = GBRemoteEvalParams(emptyMap(), emptyMap(), emptyMap())
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(client, gbContext, testGbOptions),
            cachingEnabled = false,
            cachingLayer = MockCachingLayer(), // empty store, keeps the test off disk
            remoteEval = true,
            remoteEvalPayloadProvider = { emptyParams },
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        // Coalesced round for the OLD attributes (generation 1); it suspends awaiting its POST.
        var awaiterResult: FetchResult? = null
        backgroundScope.launch { awaiterResult = viewModel.awaitRefresh() }
        runCurrent()

        // A newer state-change fetch bumps the generation (generation 2), superseding the awaiter.
        viewModel.fetchFeatures(payload = emptyParams)
        runCurrent()
        assertEquals(2, client.pendingPosts.size)

        // The OLD round's response arrives while the newer one is still pending.
        client.pendingPosts[0]("""{"status":200,"features":{"old_feature":{"defaultValue":true}}}""")
        runCurrent()

        assertEquals(
            FetchResult.Superseded,
            awaiterResult,
            "a discarded (superseded) remote-eval round must resolve awaitRefresh() as Superseded, not Success",
        )

        // Complete the still-pending latest round so runTest sees no leaked coroutine.
        client.pendingPosts[1]("""{"status":200,"features":{"new_feature":{"defaultValue":true}}}""")
        runCurrent()
    }

    @Test
    fun testSynchronousDispatcherThrowIsReportedAsFailure() = runTest {
        // #3: a dispatcher that throws synchronously while enqueuing must be reported as a fetch
        // failure — not swallowed by the coroutine machinery, and not rethrown into the caller.
        var failed = false
        val delegate = object : FeaturesFlowDelegate {
            override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) = Unit
            override suspend fun onPayloadReady(model: FeaturesDataModel) = Unit
            override fun featuresFetchFailed(error: GBError, isRemote: Boolean) { failed = true }
            override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) = Unit
            override fun featuresNotModified() = Unit
        }
        val viewModel = FeaturesViewModel(
            delegate = delegate,
            dataSource = FeaturesDataSource(SynchronouslyThrowingNetworkClient(), gbContext, testGbOptions),
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        // Must return (not throw) and surface the error through the delegate.
        val result = viewModel.awaitRefresh()

        assertEquals(FetchResult.Failed, result)
        assertTrue(failed, "a synchronous dispatcher throw must be reported via featuresFetchFailed")
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
    fun testApplyCachedFeaturesForPlainFeatures() = runTest {
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
    fun testApplyCachedFeaturesForEncryptedFeatures()= runTest {
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

    @Test
    fun testSkipsNetworkWhenCacheIsFresh() =runTest {
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
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(mockClient, gbContext, testGbOptions),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L,
            coroutineContext = UnconfinedTestDispatcher(testScheduler)
        )

        viewModel.fetchFeatures()

        assertEquals(0, networkCallCount, "Network should not be called when cache is fresh")
    }

    @Test
    fun testFetchesNetworkWhenCacheIsStale() = runTest {
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
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(mockClient, gbContext, testGbOptions),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L,
            coroutineContext = UnconfinedTestDispatcher(testScheduler)
        )

        viewModel.fetchFeatures()

        assertEquals(1, networkCallCount, "Network should be called when cache is stale")
    }

    @Test
    fun testFetchesNetworkWhenFreshCacheIsUndecodable() = runTest {
        // A fresh (within cacheMaxAge) but undecodable cache: encrypted-only payload with an
        // empty encryption key, so the decoder yields (features=null, savedGroups=null). Such a
        // cache must NOT be treated as authoritative — serveCache should fall through to the
        // network instead of silently serving nothing for the whole freshness window.
        isSuccess = false
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
            MockResponse.successResponseWithEncryptedFeaturesOnly,
            cachedAt = Clock.System.now().toEpochMilliseconds()
        )
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(mockClient, gbContext, testGbOptions),
            encryptionKey = "",
            cachingEnabled = false,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L,
            coroutineContext = UnconfinedTestDispatcher(testScheduler)
        )

        viewModel.fetchFeatures()

        assertEquals(
            1,
            networkCallCount,
            "Network must be called when a fresh cache decodes to nothing usable"
        )
        assertTrue(isSuccess, "Network payload must be applied after falling through the broken cache")
        assertTrue(hasFeatures)
    }

    @Test
    fun testOnPayloadReadyReceivesDecodedFeaturesForEncryptedPayload() = runTest {
        // Regression for the sticky-bucket-on-encrypted-payload bug: onPayloadReady (which drives
        // the sticky-bucket refresh) must receive the DECODED payload, not the raw encrypted model.
        // Otherwise model.features == null, deriveStickyBucketIdentifierAttributes falls back to the
        // empty cold-start context, and sticky identifiers are derived from nothing. Mirrors the
        // reference TS SDK, which decrypts before refreshStickyBuckets.
        var featuresSeenByPayloadReady: GBFeatures? = null
        val capturingDelegate = object : FeaturesFlowDelegate {
            override suspend fun onPayloadReady(model: FeaturesDataModel) {
                featuresSeenByPayloadReady = model.features
            }

            override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) = Unit
            override fun featuresFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) = Unit
            override fun featuresNotModified() = Unit
        }
        val viewModel = FeaturesViewModel(
            delegate = capturingDelegate,
            dataSource = FeaturesDataSource(
                MockNetworkClient(MockResponse.successResponseEncryptedFeatures, null),
                gbContext, testGbOptions,
            ),
            encryptionKey = "3tfeoyW0wlo47bDnbWDkxg==",
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.fetchFeatures()

        assertNotNull(
            featuresSeenByPayloadReady,
            "onPayloadReady must receive decoded features for an encrypted payload so sticky-bucket " +
                "identifier attributes are derived from real features, not the empty raw model"
        )
        assertTrue(featuresSeenByPayloadReady!!.isNotEmpty())
    }

    @Test
    fun testAwaitRefreshTimesOutWhenDispatcherNeverResponds() = runTest {
        // A dispatcher that accepts the request but never invokes any callback — mimics a hung
        // connection (both recommended dispatchers default to an INFINITE read timeout). The
        // network round must time out to Failed so suspendFeature()'s bounded retry can escape
        // instead of hanging forever.
        isError = false
        val hungClient = object : MockNetworkClient(MockResponse.successResponse, null) {
            override fun consumeGETRequestWithNotModified(
                request: String,
                onSuccess: (String) -> Unit,
                onError: (Throwable) -> Unit,
                onNotModified: (() -> Unit)
            ): Job = Job() // never resumes any callback
        }
        val viewModel = FeaturesViewModel(
            delegate = this@FeaturesViewModelTests,
            dataSource = FeaturesDataSource(hungClient, gbContext, testGbOptions),
            encryptionKey = "",
            cachingEnabled = false,
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )

        // runTest auto-advances virtual time, so the 30s withTimeoutOrNull fires deterministically.
        val result = viewModel.awaitRefresh()

        assertEquals(
            FetchResult.Failed,
            result,
            "A hung network round must time out to Failed, not hang"
        )
        assertTrue(isError, "Timeout must be surfaced as a fetch failure")
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
            coroutineContext = UnconfinedTestDispatcher(testScheduler)
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
