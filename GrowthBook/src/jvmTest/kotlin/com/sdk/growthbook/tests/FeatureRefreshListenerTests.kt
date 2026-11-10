package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureRefreshEvent
import com.sdk.growthbook.model.GBFeatureRefreshSource
import com.sdk.growthbook.model.GBFeaturesDiff
import com.sdk.growthbook.sandbox.CachingJvm
import com.sdk.growthbook.sandbox.GBCachingLayer
import com.sdk.growthbook.utils.GBError
import com.sdk.growthbook.utils.GBFeatureRefreshSubscription
import com.sdk.growthbook.utils.GBFeatures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Covers [GrowthBookSDK.addFeatureRefreshListener]: which refresh outcome produces which
 * [GBFeatureRefreshEvent], and how registrations behave around each other.
 *
 * Most cases drive the delegate methods directly rather than through a fetch — the delegate is the
 * seam every source (network, cache, SSE, polling) funnels into, so calling it covers all of them
 * and keeps each outcome independent of fetch timing. [refreshCache_deliversNetworkEventEndToEnd]
 * then checks the wiring really is connected to a real fetch.
 */
class FeatureRefreshListenerTests {

    private val payload = """{"status":200,"features":{"f1":{"defaultValue":true}}}"""
    private val newFeatures: GBFeatures = mapOf("f2" to GBFeature(defaultValue = GBBoolean(true)))

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    // buildSdk disables caching, so no cache is read; tests that enable it still get an empty dir,
    // so a payload left by another test cannot satisfy a fetch and change the event sequence.
    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    private fun TestScope.buildSdk(
        networkResponse: String? = payload,
        networkError: Throwable? = null,
        refreshHandler: ((Boolean, GBError?) -> Unit)? = null,
        featuresChangeHandler: ((GBFeaturesDiff) -> Unit)? = null,
    ): GrowthBookSDK {
        val builder = GBSDKBuilder(
            apiKey = "test-key",
            apiHost = "https://cdn.growthbook.io",
            attributes = emptyMap(),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(networkResponse, networkError),
            cachingEnabled = false,
        ).setCoroutineContext(UnconfinedTestDispatcher(testScheduler))
        if (refreshHandler != null) builder.setRefreshHandler(refreshHandler)
        if (featuresChangeHandler != null) builder.setFeaturesChangeHandler(featuresChangeHandler)
        return builder.initialize()
    }

    /** Registers a recorder and returns the list it fills; the subscription is returned too. */
    private fun GrowthBookSDK.record(): Pair<MutableList<GBFeatureRefreshEvent>, GBFeatureRefreshSubscription> {
        val events = mutableListOf<GBFeatureRefreshEvent>()
        return events to addFeatureRefreshListener { events.add(it) }
    }

    @Test
    fun remotePayload_emitsNetworkSuccess() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()

        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(1, events.size)
        assertEquals(GBFeatureRefreshSource.Network, events[0].source)
        assertTrue(events[0].success)
        assertNull(events[0].error)
        assertNotNull(events[0].features["f2"])
    }

    @Test
    fun cachePayload_emitsCacheEvent_whileRefreshHandlerStaysSilent() = runTest {
        var handlerCalls = 0
        val sdk = buildSdk(refreshHandler = { _, _ -> handlerCalls++ })
        val (events, _) = sdk.record()
        val callsBefore = handlerCalls

        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = false)

        assertEquals(1, events.size)
        assertEquals(GBFeatureRefreshSource.Cache, events[0].source)
        assertTrue(events[0].success)
        // The whole point of the listener API: a cache load is invisible to the refresh handler.
        assertEquals(callsBefore, handlerCalls)
    }

    @Test
    fun staleFallback_emitsStaleEventCarryingTheNetworkError() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()
        val cause = GBError(Exception("network down"))

        // What FeaturesViewModel dispatches for stale-if-error: a non-authoritative payload plus
        // the failure that forced it.
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = false, staleError = cause)

        assertEquals(1, events.size)
        assertEquals(GBFeatureRefreshSource.Stale, events[0].source)
        assertEquals(false, events[0].success)
        assertSame(cause, events[0].error)
    }

    @Test
    fun notModifiedWithLoadedPayload_emitsNotModifiedSuccess() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()

        sdk.featuresNotModified()

        assertEquals(1, events.size)
        assertEquals(GBFeatureRefreshSource.NotModified, events[0].source)
        assertTrue(events[0].success)
        assertNull(events[0].error)
    }

    @Test
    fun notModifiedBeforeAnyPayload_emitsFailedNetworkEvent() = runTest {
        // A dispatcher that only fails leaves the instance without features, so the 304 cannot be
        // trusted to mean "what you have is current".
        val sdk = buildSdk(networkResponse = null, networkError = Exception("offline"))
        val (events, _) = sdk.record()

        sdk.featuresNotModified()

        assertEquals(1, events.size)
        assertEquals(GBFeatureRefreshSource.Network, events[0].source)
        assertEquals(false, events[0].success)
        assertNotNull(events[0].error)
    }

    @Test
    fun fetchFailure_emitsForRemoteAndForCacheAlike() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()
        val error = GBError(Exception("boom"))

        sdk.featuresFetchFailed(error, isRemote = true)
        sdk.featuresFetchFailed(error, isRemote = false)

        assertEquals(2, events.size)
        assertEquals(GBFeatureRefreshSource.Network, events[0].source)
        assertEquals(GBFeatureRefreshSource.Cache, events[1].source)
        assertTrue(events.none { it.success })
        assertTrue(events.all { it.error === error })
    }

    @Test
    fun savedGroupsFetchFailure_emitsNothing() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()

        sdk.savedGroupsFetchFailed(GBError(Exception("boom")), isRemote = true)

        // Saved groups are not feature definitions; the features in effect did not change.
        assertEquals(0, events.size)
    }

    @Test
    fun payloadWithFeaturesAndSavedGroups_emitsExactlyOneEvent() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()

        sdk.payloadFetchedSuccessfully(
            features = newFeatures,
            savedGroups = buildJsonObject { put("premium", JsonPrimitive(true)) },
            contextualBandits = null,
            isRemote = true,
        )

        // The refresh handler fires twice here (once per field); the listener must not.
        assertEquals(1, events.size)
    }

    @Test
    fun payloadWithOnlySavedGroups_stillEmitsOneEventWithTheFeaturesInEffect() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()

        sdk.payloadFetchedSuccessfully(
            features = null,
            savedGroups = buildJsonObject { put("premium", JsonPrimitive(true)) },
            contextualBandits = null,
            isRemote = true,
        )

        assertEquals(1, events.size)
        assertEquals(sdk.getFeatures(), events[0].features)
    }

    @Test
    fun eventFeatures_areTheOnesTheSdkEvaluatesAgainst() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()

        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(sdk.getFeatures(), events[0].features)
    }

    @Test
    fun cancellingOneSubscription_leavesTheOtherAlone() = runTest {
        val sdk = buildSdk()
        val (first, firstSub) = sdk.record()
        val (second, _) = sdk.record()

        firstSub.cancel()
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(0, first.size)
        assertEquals(1, second.size)
    }

    @Test
    fun cancellingTwice_isHarmless() = runTest {
        val sdk = buildSdk()
        val (events, sub) = sdk.record()

        sub.cancel()
        sub.cancel()
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(0, events.size)
    }

    @Test
    fun sameLambdaRegisteredTwice_yieldsIndependentSubscriptions() = runTest {
        val sdk = buildSdk()
        val events = mutableListOf<GBFeatureRefreshEvent>()
        val listener: (GBFeatureRefreshEvent) -> Unit = { events.add(it) }

        val first = sdk.addFeatureRefreshListener(listener)
        sdk.addFeatureRefreshListener(listener)

        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)
        assertEquals(2, events.size, "both registrations should fire")

        // Cancelling one handle must not take the other registration of the same lambda with it.
        first.cancel()
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)
        assertEquals(3, events.size)
    }

    @Test
    fun throwingListener_stopsNeitherTheOthersNorTheRefresh() = runTest {
        val sdk = buildSdk()
        val seen = mutableListOf<GBFeatureRefreshEvent>()
        sdk.addFeatureRefreshListener { throw IllegalStateException("listener blew up") }
        sdk.addFeatureRefreshListener { seen.add(it) }

        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(1, seen.size)
        assertNotNull(sdk.getFeatures()["f2"], "the payload must still be applied")
    }

    /**
     * The features-change handler runs between applying the payload and notifying the listeners,
     * so an unguarded throw there would both rob them of the event and unwind into the
     * payload-processing pipeline, which re-reports an already-applied payload as a failed fetch.
     */
    @Test
    fun throwingFeaturesChangeHandler_costsNeitherTheListenersNorThePayload() = runTest {
        var changeHandlerCalls = 0
        val sdk = buildSdk(featuresChangeHandler = {
            changeHandlerCalls++
            throw IllegalStateException("change handler blew up")
        })
        val (events, _) = sdk.record()
        // initialize() already applied the mock payload, throwing once on the way.
        val callsBefore = changeHandlerCalls

        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(callsBefore + 1, changeHandlerCalls, "the handler must still be called")
        assertEquals(1, events.size, "a throwing handler must not swallow the listener event")
        assertTrue(events[0].success)
        assertNotNull(sdk.getFeatures()["f2"], "the payload must still be applied")
    }

    @Test
    fun listenerCancellingItselfMidNotification_isSafe() = runTest {
        val sdk = buildSdk()
        val seen = mutableListOf<GBFeatureRefreshEvent>()
        var sub: GBFeatureRefreshSubscription? = null
        sub = sdk.addFeatureRefreshListener {
            seen.add(it)
            sub?.cancel()
        }
        val (other, _) = sdk.record()

        // Iterating a snapshot is what makes this safe: mutating the registry mid-notification
        // must neither throw nor skip the listeners after it.
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(1, seen.size, "the self-cancelling listener hears only the first event")
        assertEquals(2, other.size)
    }

    @Test
    fun clearFeatureRefreshListeners_dropsEveryRegistration() = runTest {
        val sdk = buildSdk()
        val (first, _) = sdk.record()
        val (second, _) = sdk.record()

        sdk.clearFeatureRefreshListeners()
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(0, first.size)
        assertEquals(0, second.size)
    }

    @Test
    fun close_stopsNotifications() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()

        sdk.close()
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(0, events.size)
    }

    /**
     * Registering on a dead instance used to succeed silently, leaving the listener retained for
     * the lifetime of an SDK that will never refresh again.
     */
    @Test
    fun registeringAfterClose_isARejectedNoOp() = runTest {
        val sdk = buildSdk()
        sdk.close()
        assertTrue(sdk.isClosed)

        val (events, _) = sdk.record()
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(0, events.size)
    }

    /** [GrowthBookSDK.clearFeatureRefreshListeners] empties the registry; it must not close it. */
    @Test
    fun clearingListeners_leavesTheInstanceOpenForNewOnes() = runTest {
        val sdk = buildSdk()
        sdk.record()

        sdk.clearFeatureRefreshListeners()
        assertFalse(sdk.isClosed)
        val (afterClear, _) = sdk.record()
        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(1, afterClear.size)
    }

    /**
     * [notifyFeatureRefresh][GrowthBookSDK] does not suspend, so a `CancellationException` reaching
     * it was raised by the listener body, not by our own coroutine being torn down. Rethrowing it
     * would propagate a foreign cancellation as ours and unwind into the payload-processing catch,
     * which re-reports an already-applied payload as a failed fetch.
     */
    @Test
    fun listenerThrowingCancellationException_isTreatedLikeAnyOtherFailure() = runTest {
        val sdk = buildSdk()
        val seen = mutableListOf<GBFeatureRefreshEvent>()
        sdk.addFeatureRefreshListener { throw CancellationException("listener's own cancellation") }
        sdk.addFeatureRefreshListener { seen.add(it) }

        sdk.payloadFetchedSuccessfully(newFeatures, null, null, isRemote = true)

        assertEquals(1, seen.size, "the next listener must still be called")
        assertNotNull(sdk.getFeatures()["f2"], "the payload must still be applied")
    }

    @Test
    fun refreshCache_deliversNetworkEventEndToEnd() = runTest {
        val sdk = buildSdk()
        val (events, _) = sdk.record()

        sdk.refreshCache()

        assertEquals(1, events.size)
        assertEquals(GBFeatureRefreshSource.Network, events[0].source)
        assertTrue(events[0].success)
        assertNotNull(events[0].features["f1"])
    }

    // --- registration before the instance exists ---------------------------------------------

    /** In-memory cache seeded with a payload, as if a previous session had written it. */
    private class SeededCache(private val content: String) : GBCachingLayer {
        override fun saveContent(fileName: String, content: String) = Unit
        override fun getContent(fileName: String): String = content
    }

    // The cache stores a serialized FeaturesDataModel, not the raw API response: it is read with a
    // strict Json, so an extra "status" field (which the API does send) would fail the decode.
    private fun cachedPayload() =
        """{"features":{"cached-flag":{"defaultValue":true}},""" +
            """"cachedAt":${Clock.System.now().toEpochMilliseconds()}}"""

    private fun TestScope.builderWithSeededCache() = GBSDKBuilder(
        apiKey = "test-key",
        apiHost = "https://cdn.growthbook.io",
        attributes = emptyMap(),
        encryptionKey = null,
        trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
        networkDispatcher = MockNetworkClient(payload, null),
        // Caching on: with it off the seeded cache is never read (SeededCache ignores writes anyway).
        cachingEnabled = true,
    )
        .setCoroutineContext(UnconfinedTestDispatcher(testScheduler))
        .setCachingLayer(SeededCache(cachedPayload()))

    @Test
    fun builderRegisteredListener_seesTheColdStartCacheLoad() = runTest {
        val events = mutableListOf<GBFeatureRefreshEvent>()

        builderWithSeededCache()
            .addFeatureRefreshListener { events.add(it) }
            .initialize()

        // The cache is served synchronously from inside initialize(), so only a listener attached
        // on the builder can observe it — which is the whole reason that entry point exists.
        assertTrue(events.isNotEmpty(), "the cold-start cache load must reach a builder listener")
        assertEquals(GBFeatureRefreshSource.Cache, events.first().source)
        assertNotNull(events.first().features["cached-flag"])
    }

    @Test
    fun instanceRegisteredListener_missesTheColdStartCacheLoad() = runTest {
        val sdk = builderWithSeededCache().initialize()

        val (events, _) = sdk.record()

        // Documents the limitation rather than hiding it: by the time a caller holds the instance,
        // initialize() has already applied the cached payload.
        assertEquals(0, events.size)
    }

    @Test
    fun freshCacheServedWithoutNetwork_isReportedAsCacheNotNetwork() = runTest {
        val events = mutableListOf<GBFeatureRefreshEvent>()

        builderWithSeededCache()
            // Inside the freshness window the cache is served as *authoritative* and the network is
            // skipped entirely. Deriving the source from that authority would label a disk read
            // "Network", so it has to follow where the payload actually came from.
            .setCacheMaxAge(60_000)
            .addFeatureRefreshListener { events.add(it) }
            .initialize()

        assertEquals(1, events.size, "a fresh cache skips the network, so there is exactly one event")
        assertEquals(GBFeatureRefreshSource.Cache, events[0].source)
        assertTrue(events[0].success)
    }
}
