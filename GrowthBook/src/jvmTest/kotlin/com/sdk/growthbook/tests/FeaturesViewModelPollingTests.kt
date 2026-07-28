package com.sdk.growthbook.tests

import com.sdk.growthbook.features.FeaturesDataModel
import com.sdk.growthbook.features.FeaturesDataSource
import com.sdk.growthbook.features.FeaturesFlowDelegate
import com.sdk.growthbook.features.FeaturesViewModel
import com.sdk.growthbook.model.GBContext
import com.sdk.growthbook.model.GBOptions
import com.sdk.growthbook.sandbox.CachingLayer
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatures
import com.sdk.growthbook.utils.GBRemoteEvalParams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import com.sdk.growthbook.GBSDKBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Covers the background polling engine and the stale-while-revalidate inner window.
 * Only the network-call count matters here, so the delegate is a no-op; correctness of the
 * decoded payload is exercised by [FeaturesViewModelTests].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeaturesViewModelPollingTests {

    private val encryptionKey = "3tfeoyW0wlo47bDnbWDkxg=="
    private val gbContext = GBContext(
        "Key",
        enabled = true, attributes = HashMap(), forcedVariations = HashMap(),
        qaMode = false,
        trackingCallback = { _, _ -> },
        encryptionKey = null,
        remoteEval = false,
    )
    private val testGbOptions = GBOptions("https://example.com", null)

    private object NoopDelegate : FeaturesFlowDelegate {
        override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) = Unit
        override suspend fun onPayloadReady(model: FeaturesDataModel) = Unit
        override fun featuresFetchFailed(error: GBError, isRemote: Boolean) = Unit
        override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) = Unit
        override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) = Unit
        override fun featuresNotModified() = Unit
    }

    /** Counts GET and POST (remote-eval) rounds; delegates to the real synchronous mock behaviour. */
    private class CountingClient(
        response: String? = MockResponse.successResponse,
        error: Throwable? = null,
    ) : MockNetworkClient(response, error) {
        var getCount = 0
        var postCount = 0
        var lastPostBody: Map<String, Any>? = null

        override fun consumeGETRequestWithNotModified(
            request: String,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit,
            onNotModified: (() -> Unit)
        ): Job {
            getCount++
            return super.consumeGETRequestWithNotModified(request, onSuccess, onError, onNotModified)
        }

        override fun consumePOSTRequest(
            url: String,
            bodyParams: Map<String, Any>,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit
        ) {
            postCount++
            lastPostBody = bodyParams
            super.consumePOSTRequest(url, bodyParams, onSuccess, onError)
        }
    }

    private fun buildViewModel(
        client: MockNetworkClient,
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        cachingLayer: CachingLayer = MockCachingLayer(),
        cacheMaxAge: Long? = null,
        staleTtl: Long? = null,
        serveStaleOnError: Boolean = false,
        remoteEval: Boolean = false,
        remoteEvalPayloadProvider: () -> GBRemoteEvalParams? = { null },
        delegate: FeaturesFlowDelegate = NoopDelegate,
    ): FeaturesViewModel = FeaturesViewModel(
        delegate = delegate,
        dataSource = FeaturesDataSource(client, gbContext, testGbOptions),
        encryptionKey = encryptionKey,
        cachingEnabled = false,
        cachingLayer = cachingLayer,
        cacheMaxAge = cacheMaxAge,
        staleTtl = staleTtl,
        serveStaleOnError = serveStaleOnError,
        remoteEval = remoteEval,
        remoteEvalPayloadProvider = remoteEvalPayloadProvider,
        coroutineContext = UnconfinedTestDispatcher(scheduler),
    )

    @Test
    fun testPollingRefreshesEachInterval() = runTest {
        val client = CountingClient()
        val viewModel = buildViewModel(client, testScheduler)

        viewModel.startPolling(intervalMs = 1000)
        // The loop delays one interval BEFORE the first fetch, so nothing has run yet.
        assertEquals(0, client.getCount, "Polling must wait one interval before the first fetch")

        repeat(3) {
            advanceTimeBy(1000)
            runCurrent()
        }
        viewModel.stopPolling()

        assertEquals(3, client.getCount, "Each elapsed interval must trigger exactly one refresh")
        viewModel.close()
    }

    @Test
    fun testStopPollingHaltsFurtherRefreshes() = runTest {
        val client = CountingClient()
        val viewModel = buildViewModel(client, testScheduler)

        viewModel.startPolling(intervalMs = 1000)
        advanceTimeBy(1000)
        runCurrent()
        val countBeforeStop = client.getCount
        assertEquals(1, countBeforeStop)

        viewModel.stopPolling()
        advanceTimeBy(5000)
        runCurrent()

        assertEquals(countBeforeStop, client.getCount, "No refresh may occur after stopPolling()")
        viewModel.close()
    }

    @Test
    fun testCloseStopsPolling() = runTest {
        val client = CountingClient()
        val viewModel = buildViewModel(client, testScheduler)

        viewModel.startPolling(intervalMs = 1000)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, client.getCount)

        viewModel.close()
        advanceTimeBy(5000)
        runCurrent()

        assertEquals(1, client.getCount, "close() must stop the background poller")
    }

    @Test
    fun testStartPollingIsNoOpWhileSseActive() = runTest {
        val client = CountingClient()
        val viewModel = buildViewModel(client, testScheduler)

        // SSE takes precedence: once streaming is active, startPolling must not launch the loop.
        viewModel.autoRefreshFeatures()
        viewModel.startPolling(intervalMs = 1000)

        advanceTimeBy(3000)
        runCurrent()

        assertEquals(0, client.getCount, "Polling must not run while SSE is active")
        viewModel.close()
    }

    @Test
    fun testPollingCanStartAfterSseStops() = runTest {
        val client = CountingClient()
        val viewModel = buildViewModel(client, testScheduler)

        // SSE active -> polling refused.
        viewModel.autoRefreshFeatures()
        assertFalse(
            viewModel.startPolling(intervalMs = 1000),
            "polling must be refused while SSE is active"
        )

        // Stopping SSE releases the mode, so polling can start again (no permanent lockout).
        viewModel.stopAutoRefresh()
        assertTrue(
            viewModel.startPolling(intervalMs = 1000),
            "polling must be allowed once SSE is stopped"
        )

        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, client.getCount, "the resumed poller must fetch on the next interval")

        viewModel.close()
    }

    @Test
    fun testStartingSseStopsRunningPoller() = runTest {
        val client = CountingClient()
        val viewModel = buildViewModel(client, testScheduler)

        viewModel.startPolling(intervalMs = 1000)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, client.getCount)

        // Switching to SSE must stop the existing poller.
        viewModel.autoRefreshFeatures()
        advanceTimeBy(5000)
        runCurrent()

        assertEquals(1, client.getCount, "Starting SSE must stop the background poller")
        viewModel.close()
    }

    @Test
    fun testPollingUsesRemoteEvalPostWithLiveAttributes() = runTest {
        val client = CountingClient()
        var providerCalls = 0
        // Rebuilt each poll so the payload reflects the current context (mirrors the SDK's
        // ::buildRemoteEvalParams seam).
        val provider: () -> GBRemoteEvalParams? = {
            providerCalls++
            GBRemoteEvalParams(
                attributes = mapOf("id" to "u1"),
                forcedFeatures = emptyMap(),
                forcedVariations = emptyMap(),
            )
        }
        val viewModel = buildViewModel(
            client, testScheduler,
            remoteEval = true,
            remoteEvalPayloadProvider = provider,
        )

        viewModel.startPolling(intervalMs = 1000)
        repeat(3) {
            advanceTimeBy(1000)
            runCurrent()
        }
        viewModel.stopPolling()

        assertEquals(0, client.getCount, "Remote-eval polling must never issue a plain GET")
        assertEquals(3, client.postCount, "Each interval must issue one remote-eval POST")
        assertTrue(providerCalls >= 3, "Each poll must rebuild the payload from the live context")
        val postedAttributes = client.lastPostBody?.get("attributes") as? Map<*, *>
        assertEquals(
            "u1",
            postedAttributes?.get("id"),
            "The POST body must carry the current attributes from the payload provider"
        )
        viewModel.close()
    }

    @Test
    fun testStaleTtlSkipsNetworkWithinInnerWindow() = runTest {
        val client = CountingClient()
        val cacheLayer = MockCachingLayer.fromApiResponse(
            MockResponse.successResponse,
            cachedAt = Clock.System.now().toEpochMilliseconds()
        )
        val viewModel = buildViewModel(
            client, testScheduler,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L,
            staleTtl = 60 * 60 * 1000L, // 1h inner window
        )

        viewModel.fetchFeatures()

        assertEquals(0, client.getCount, "Cache within staleTtl must skip the network")
    }

    @Test
    fun testPastStaleTtlRevalidatesWithinMaxAge() = runTest {
        val client = CountingClient()
        val twoHoursAgo = Clock.System.now().toEpochMilliseconds() - (2 * 60 * 60 * 1000L)
        val cacheLayer = MockCachingLayer.fromApiResponse(
            MockResponse.successResponse,
            cachedAt = twoHoursAgo
        )
        val viewModel = buildViewModel(
            client, testScheduler,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L,
            staleTtl = 60 * 60 * 1000L, // 1h inner window; cache is 2h old -> revalidate
        )

        viewModel.fetchFeatures()

        assertEquals(
            1,
            client.getCount,
            "Cache older than staleTtl (but within cacheMaxAge) must revalidate over the network"
        )
    }

    private fun cacheAppliedRecordingDelegate(record: () -> Unit): FeaturesFlowDelegate =
        object : FeaturesFlowDelegate {
            override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
                if (!isRemote) record()
            }
            override suspend fun onPayloadReady(model: FeaturesDataModel) = Unit
            override fun featuresFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) = Unit
            override fun featuresNotModified() = Unit
        }

    @Test
    fun testExpiredBeyondCacheMaxAgeIsNotServedAsStale() = runTest {
        // Zone 3 with serveStaleOnError = false (default, fail-closed): a cache older than
        // cacheMaxAge must NOT be surfaced even when the network fails — evaluation falls back to
        // code defaults rather than data past its freshness ceiling.
        var appliedFromCache = false
        val client = CountingClient(response = null, error = Throwable("offline"))
        val fiftyHoursAgo = Clock.System.now().toEpochMilliseconds() - (50 * 60 * 60 * 1000L)
        val cacheLayer = MockCachingLayer.fromApiResponse(
            MockResponse.successResponse,
            cachedAt = fiftyHoursAgo
        )
        val viewModel = buildViewModel(
            client, testScheduler,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L, // ceiling 48h; cache is 50h old -> expired
            staleTtl = 60 * 60 * 1000L,         // arms the hard cutoff
            serveStaleOnError = false,
            delegate = cacheAppliedRecordingDelegate { appliedFromCache = true },
        )

        viewModel.fetchFeatures()

        assertEquals(1, client.getCount, "Expired cache must fall through to the network")
        assertTrue(!appliedFromCache, "Cache older than cacheMaxAge must not be served as stale (Zone 3)")
    }

    @Test
    fun testExpiredServedAsStaleWhenServeStaleOnErrorEnabled() = runTest {
        // Zone 3 with serveStaleOnError = true (stale-if-error): the expired cache is NOT surfaced
        // up front, but once the network round fails it is applied as a last-resort fallback so an
        // offline client keeps (stale) flags instead of falling back to code defaults.
        var appliedFromCache = false
        val client = CountingClient(response = null, error = Throwable("offline"))
        val fiftyHoursAgo = Clock.System.now().toEpochMilliseconds() - (50 * 60 * 60 * 1000L)
        val cacheLayer = MockCachingLayer.fromApiResponse(
            MockResponse.successResponse,
            cachedAt = fiftyHoursAgo
        )
        val viewModel = buildViewModel(
            client, testScheduler,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L,
            staleTtl = 60 * 60 * 1000L,
            serveStaleOnError = true,
            delegate = cacheAppliedRecordingDelegate { appliedFromCache = true },
        )

        viewModel.fetchFeatures()

        assertEquals(1, client.getCount, "Expired cache must still attempt the network first")
        assertTrue(appliedFromCache, "On network failure with serveStaleOnError, the expired cache must be served as fallback")
    }

    @Test
    fun testCacheMaxAgeAloneStillServesStaleBeyondWindow() = runTest {
        // Backward-compatibility: with staleTtl UNSET, the hard cutoff is disarmed, so a cache older
        // than cacheMaxAge is still served (non-authoritative) while the network revalidates —
        // preserving the pre-existing 7.3.0 behaviour.
        var appliedFromCache = false
        val recordingDelegate = object : FeaturesFlowDelegate {
            override fun featuresFetchedSuccessfully(features: GBFeatures, isRemote: Boolean) {
                if (!isRemote) appliedFromCache = true
            }
            override suspend fun onPayloadReady(model: FeaturesDataModel) = Unit
            override fun featuresFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchFailed(error: GBError, isRemote: Boolean) = Unit
            override fun savedGroupsFetchedSuccessfully(savedGroups: JsonObject, isRemote: Boolean) = Unit
            override fun featuresNotModified() = Unit
        }
        val client = CountingClient(response = null, error = Throwable("offline"))
        val fiftyHoursAgo = Clock.System.now().toEpochMilliseconds() - (50 * 60 * 60 * 1000L)
        val cacheLayer = MockCachingLayer.fromApiResponse(
            MockResponse.successResponse,
            cachedAt = fiftyHoursAgo
        )
        val viewModel = buildViewModel(
            client, testScheduler,
            cachingLayer = cacheLayer,
            cacheMaxAge = 48 * 60 * 60 * 1000L,
            staleTtl = null, // cutoff disarmed -> legacy serve-stale behaviour
            delegate = recordingDelegate,
        )

        viewModel.fetchFeatures()

        assertEquals(1, client.getCount, "Stale cache still revalidates over the network")
        assertTrue(appliedFromCache, "Without staleTtl, stale cache beyond cacheMaxAge must still be served")
    }

    @Test
    fun testSetRefreshIntervalRejectsNonPositive() {
        val builder = GBSDKBuilder(
            apiKey = "key",
            apiHost = "https://example.com",
            attributes = HashMap(),
            trackingCallback = { _, _ -> },
            networkDispatcher = CountingClient(),
        )
        assertFailsWith<IllegalArgumentException> { builder.setRefreshInterval(0) }
        assertFailsWith<IllegalArgumentException> { builder.setRefreshInterval(-5) }
    }
}
