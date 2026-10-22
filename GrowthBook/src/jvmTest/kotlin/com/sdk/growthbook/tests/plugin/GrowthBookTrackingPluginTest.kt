package com.sdk.growthbook.tests.plugin

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBNumber
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin
import com.sdk.growthbook.plugin.tracking.SdkMetadata
import com.sdk.growthbook.plugin.GBTrackingEventData
import com.sdk.growthbook.plugin.GBTrackingEventNames
import com.sdk.growthbook.plugin.TrackingPluginConfig
import com.sdk.growthbook.network.TrackingNetworkDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GrowthBookTrackingPluginTest {

    private class CapturingDispatcher(
        expectedPosts: Int = 1,
        private val responseError: Throwable? = null,
    ) : TrackingNetworkDispatcher {
        private val latch = CountDownLatch(expectedPosts)
        val posts = mutableListOf<JsonElement>()

        fun waitForPost(timeoutSeconds: Long = 5): JsonArray? {
            latch.await(timeoutSeconds, TimeUnit.SECONDS)
            return posts.firstOrNull() as? JsonArray
        }

        fun receivedNoPost(timeoutMs: Long = 500): Boolean =
            !latch.await(timeoutMs, TimeUnit.MILLISECONDS)

        override fun consumePOSTRequest(
            url: String, headers: Map<String, String>, body: JsonElement,
            onSuccess: (String) -> Unit, onError: (Throwable) -> Unit,
        ) {
            posts.add(body)
            latch.countDown()
            if (responseError != null) onError(responseError) else onSuccess("{}")
        }
    }

    // Builds through the recommended entry point, so the suite exercises what consumers are told to
    // use. The legacy TrackingPluginConfig path has its own tests below.
    private fun plugin(
        dispatcher: TrackingNetworkDispatcher,
        clientKey: String? = "sdk-test",
        ingestorHost: String? = null,
        batchSize: Int? = null,
        batchTimeout: Duration? = null,
        enableFeatureUsageEvents: Boolean = true,
        dedupeKeyAttributes: List<String> = emptyList(),
        enable: Boolean = true,
        eventFilter: ((GBTrackingEventData) -> Boolean)? = null,
    ) = GrowthBookTrackingPlugin.Builder()
        .setEnable(enable)
        .setEventFilter(eventFilter)
        .setClientKey(clientKey)
        .setNetworkDispatcher(dispatcher)
        .setIngestorHost(ingestorHost)
        .setBatchSize(batchSize)
        .setBatchTimeout(batchTimeout)
        .setEnableFeatureUsageEvents(enableFeatureUsageEvents)
        .setDedupeKeyAttributes(dedupeKeyAttributes)
        .build()

    private fun experiment(key: String) = GBExperiment(key = key)
    private fun experimentResult(variation: Int = 0) = GBExperimentResult(
        value = GBNull,
        variationId = variation,
        hashAttribute = "id",
        hashValue = "u-$variation",
    )
    private fun featureResult() = GBFeatureResult(
        gbValue = GBString("v"),
        source = GBFeatureSource.defaultValue,
    )

    @Test
    fun banditExposure_carriesAttributionInExperimentViewed() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher)
        plugin.init()

        plugin.onExperimentViewed(
            experiment("cb_exp"),
            GBExperimentResult(
                value = GBNull,
                variationId = 1,
                hashAttribute = "id",
                hashValue = "u1",
                leafId = 7,
                variationWeights = listOf(0.25, 0.75),
                banditVersion = 3,
            ),
            attributes = null,
        )
        plugin.close()

        val batch = assertNotNull(dispatcher.waitForPost())
        val properties = batch[0].jsonObject["properties_json"]!!.jsonObject
        assertEquals("7", properties["leafId"]?.jsonPrimitive?.content)
        assertEquals("3", properties["banditVersion"]?.jsonPrimitive?.content)
        assertEquals(
            listOf(0.25, 0.75),
            (properties["variationWeights"] as JsonArray).map { it.jsonPrimitive.content.toDouble() }
        )
    }

    @Test
    fun ordinaryExposure_hasNoBanditProperties() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher)
        plugin.init()

        plugin.onExperimentViewed(experiment("plain_exp"), experimentResult(), attributes = null)
        plugin.close()

        val batch = assertNotNull(dispatcher.waitForPost())
        val properties = batch[0].jsonObject["properties_json"]!!.jsonObject
        assertFalse("leafId" in properties)
        assertFalse("variationWeights" in properties)
        assertFalse("banditVersion" in properties)
    }

    @Test
    fun flushesWhenBatchSizeReached() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 2, batchTimeout = 30.seconds)
        plugin.init()

        plugin.onExperimentViewed(experiment("exp1"), experimentResult(0))
        plugin.onExperimentViewed(experiment("exp2"), experimentResult(1))

        val events = dispatcher.waitForPost()
        assertNotNull(events, "should flush on batch size threshold")
        assertEquals(2, events.size)

        val experimentIds = events.map {
            it.jsonObject["properties_json"]?.jsonObject?.get("experimentId")?.jsonPrimitive?.content
        }
        assertTrue("exp1" in experimentIds)
        assertTrue("exp2" in experimentIds)

        plugin.close()
    }

    @Test
    fun flushesWhenTimerFires() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 200.milliseconds)
        plugin.init()

        plugin.onFeatureEvaluated("flag1", featureResult())

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events, "timer-based flush should fire within 3s")
        assertEquals(1, events.size)
        assertEquals("Feature Evaluated", events[0].jsonObject["event_name"]?.jsonPrimitive?.content)
        assertEquals("flag1", events[0].jsonObject["properties_json"]?.jsonObject?.get("feature")?.jsonPrimitive?.content)

        plugin.close()
    }

    @Test
    fun closeFlushesRemainingEvents() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 60.seconds)
        plugin.init()

        plugin.onExperimentViewed(experiment("exp"), experimentResult())
        plugin.close()

        val events = dispatcher.waitForPost()
        assertNotNull(events, "close() should flush the final batch")
        assertEquals(1, events.size)
    }

    @Test
    fun closeIsIdempotent() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher)
        plugin.close()
        plugin.close()
    }

    @Test
    fun flushSendsBufferedEventsAndLeavesThePluginUsable() = runBlocking {
        val dispatcher = CapturingDispatcher(expectedPosts = 2)
        // A timeout long enough that nothing but flush() can produce a batch.
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 60.seconds)
        plugin.init()

        plugin.onExperimentViewed(experiment("before_background"), experimentResult())
        plugin.flush()
        // Still alive: the second event must be accepted and flushable, unlike after close().
        plugin.onExperimentViewed(experiment("after_background"), experimentResult(1))
        plugin.flush()

        // No waiting: flush() suspends until the batch has reached the dispatcher, so the posts are
        // already there. That is the guarantee the method exists for — a drain that merely schedules
        // itself is worth nothing against a process about to be killed.
        assertEquals(2, dispatcher.posts.size, "flush() must return only once the batch was sent")
        val experimentIds = dispatcher.posts.flatMap { batch ->
            (batch as JsonArray).map {
                it.jsonObject["properties_json"]?.jsonObject?.get("experimentId")?.jsonPrimitive?.content
            }
        }
        assertEquals(listOf("before_background", "after_background"), experimentIds)

        plugin.close()
    }

    @Test
    fun flushRearmsTheBatchTimerForLaterEvents() = runBlocking {
        val dispatcher = CapturingDispatcher(expectedPosts = 2)
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 300.milliseconds)
        plugin.init()

        plugin.onFeatureEvaluated("flag1", featureResult())
        plugin.flush()  // cancels the armed timer

        // Guards the `pendingFlush = null` in flush(): cancelling the job without clearing the
        // handle would leave enqueue() thinking a timer is still armed, and this event would sit in
        // the buffer until a size-triggered flush that never comes.
        plugin.onFeatureEvaluated("flag2", featureResult())

        assertNotNull(dispatcher.waitForPost(timeoutSeconds = 3), "timer must re-arm after flush()")
        assertEquals(2, dispatcher.posts.size)

        plugin.close()
    }

    @Test
    fun flushWithNothingBufferedSendsNothing() = runBlocking {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 60.seconds)
        plugin.init()

        plugin.flush()
        plugin.flush()

        assertTrue(dispatcher.receivedNoPost(), "an empty buffer must not produce a POST")
        plugin.close()
    }

    @Test
    fun flushAfterCloseIsNoOp() = runBlocking {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 60.seconds)
        plugin.init()
        plugin.close()

        plugin.flush()

        assertTrue(dispatcher.receivedNoPost(), "flush() must not revive a closed plugin")
    }

    @Test
    fun flushOnDisabledPluginSendsNothing() = runBlocking {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, enable = false, batchSize = 100, batchTimeout = 60.seconds)
        plugin.init()

        plugin.onExperimentViewed(experiment("exp"), experimentResult())
        plugin.flush()

        assertTrue(dispatcher.receivedNoPost(), "setEnable(false) must survive an explicit flush()")
        plugin.close()
    }

    @Test
    fun noClientKeyDisablesPlugin() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, clientKey = null, batchSize = 1)
        plugin.init()

        plugin.onExperimentViewed(experiment("exp"), experimentResult())
        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.close()

        assertTrue(dispatcher.receivedNoPost(), "disabled plugin must not hit the network")
    }

    @Test
    fun emptyClientKeyDisablesPlugin() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, clientKey = "", batchSize = 1)
        plugin.init()
        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.close()

        assertTrue(dispatcher.receivedNoPost(), "empty clientKey must not disable plugin")
    }

    @Test
    fun httpErrorDoesNotThrow() {
        val dispatcher = CapturingDispatcher(responseError = RuntimeException("network error"))
        val plugin = plugin(dispatcher, batchSize = 1)
        plugin.init()

        plugin.onExperimentViewed(experiment("exp"), experimentResult())

        dispatcher.waitForPost()
        plugin.close()
    }

    @Suppress("DEPRECATION")
    @Test
    fun ingestorHostTrailingSlashStripped() {
        val cfg = TrackingPluginConfig(
            ingestorHost = "https://example.test/",
            clientKey = "k",
        )
        assertEquals("https://example.test", cfg.resolvedIngestorHost())
        assertFalse(cfg.resolvedIngestorHost().endsWith("/"))
    }

    @Suppress("DEPRECATION")
    @Test
    fun defaultIngestorHostIsUsedWhenNotSet() {
        val cfg = TrackingPluginConfig(clientKey = "k")
        assertEquals(TrackingPluginConfig.DEFAULT_INGESTOR_HOST, cfg.resolvedIngestorHost())
    }

    @Test
    fun sdkMetadataVersionIsNotEmpty() {
        assertTrue(SdkMetadata.VERSION.isNotEmpty())
        assertFalse(SdkMetadata.VERSION == "unknown")
    }

    @Test
    fun identityAttributesArePromotedAndRestGoToContext() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1)
        plugin.init()

        val attributes = mapOf("id" to GBString("u1") as GBValue, "plan" to GBString("pro") as GBValue)
        plugin.onFeatureEvaluated("flag", featureResult(), attributes)

        val events = dispatcher.waitForPost()
        assertNotNull(events)
        val event = events[0].jsonObject
        // `id` is promoted to the top-level device_id field (device_id ?: anonymous_id ?: id)
        assertEquals("u1", event["device_id"]?.jsonPrimitive?.content)
        // non-identity attributes land in context_json
        val context = event["context_json"]?.jsonObject
        assertNotNull(context, "context_json should be present in event")
        assertEquals("pro", context["plan"]?.jsonPrimitive?.content)
        // promoted identity keys are not duplicated into context_json
        assertFalse("id" in context, "promoted keys must not appear in context_json")

        plugin.close()
    }

    @Test
    fun sdkMetadataIsTopLevel() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1)
        plugin.init()
        plugin.onFeatureEvaluated("flag", featureResult())

        val events = dispatcher.waitForPost()
        assertNotNull(events)
        val event = events[0].jsonObject
        assertEquals(SdkMetadata.LANGUAGE, event["sdk_language"]?.jsonPrimitive?.content)
        assertEquals(SdkMetadata.VERSION, event["sdk_version"]?.jsonPrimitive?.content)

        plugin.close()
    }

    @Test
    fun dedupesRepeatedFeatureEvaluated() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 200.milliseconds)
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.onFeatureEvaluated("flag", featureResult())   // duplicate → skipped
        plugin.onFeatureEvaluated("other", featureResult())

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(2, events.size, "identical repeated feature events must be de-duplicated")

        plugin.close()
    }

    @Test
    fun differentFeatureValueIsNotDeduped() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 200.milliseconds)
        plugin.init()

        plugin.onFeatureEvaluated("flag", GBFeatureResult(gbValue = GBString("a"), source = GBFeatureSource.defaultValue))
        plugin.onFeatureEvaluated("flag", GBFeatureResult(gbValue = GBString("b"), source = GBFeatureSource.defaultValue))

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(2, events.size, "a changed feature value must produce a new event")

        plugin.close()
    }

    @Test
    fun dedupesRepeatedExperimentViewed() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 200.milliseconds)
        plugin.init()

        plugin.onExperimentViewed(experiment("exp"), experimentResult(0))
        plugin.onExperimentViewed(experiment("exp"), experimentResult(0))   // duplicate → skipped

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(1, events.size, "identical repeated experiment events must be de-duplicated")

        plugin.close()
    }

    @Test
    fun bodyIsPlainJsonArray() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1)
        plugin.init()
        plugin.onFeatureEvaluated("flag", featureResult())

        val events = dispatcher.waitForPost()
        assertNotNull(events)
        assertTrue(events is JsonArray, "body must be a plain JsonArray, not a wrapped object")

        plugin.close()
    }

    @Test
    fun postUrlUsesTrackEndpointWithClientKey() {
        val capturedUrl = AtomicReference<String>()
        val latch = CountDownLatch(1)
        val dispatcher = object : TrackingNetworkDispatcher {
            override fun consumePOSTRequest(url: String, headers: Map<String, String>,
                body: JsonElement, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
                capturedUrl.set(url)
                latch.countDown()
                onSuccess("{}")
            }
        }

        val plugin = plugin(
            dispatcher,
            clientKey = "k",
            ingestorHost = "https://ingest.example.com",
            batchSize = 1,
        )
        plugin.init()
        plugin.onFeatureEvaluated("flag", featureResult())

        latch.await(5, TimeUnit.SECONDS)
        assertEquals("https://ingest.example.com/track?client_key=k", capturedUrl.get())
        plugin.close()
    }

    @Test
    fun postSendsTextPlainContentType() {
        val capturedHeaders = AtomicReference<Map<String, String>>()
        val latch = CountDownLatch(1)
        val dispatcher = object : TrackingNetworkDispatcher {
            override fun consumePOSTRequest(url: String, headers: Map<String, String>,
                body: JsonElement, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
                capturedHeaders.set(headers)
                latch.countDown()
                onSuccess("{}")
            }
        }

        val plugin = plugin(dispatcher, clientKey = "k", batchSize = 1)
        plugin.init()
        plugin.onFeatureEvaluated("flag", featureResult())

        latch.await(5, TimeUnit.SECONDS)
        // Mirrors JS/Python: tracking JSON is posted as text/plain.
        assertEquals("text/plain", capturedHeaders.get()?.get("Content-Type"))
        plugin.close()
    }

    @Test
    fun disablingFeatureUsageEventsDropsThem() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1, enableFeatureUsageEvents = false)
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.close()

        assertTrue(dispatcher.receivedNoPost(), "Feature Evaluated must not reach the ingest endpoint")
    }

    @Test
    fun disablingFeatureUsageEventsKeepsExperimentViews() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1, enableFeatureUsageEvents = false)
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.onExperimentViewed(experiment("exp"), experimentResult())

        val events = dispatcher.waitForPost()
        assertNotNull(events, "Experiment Viewed must still be tracked")
        assertEquals(1, events.size, "only the experiment exposure should have been enqueued")
        assertEquals(
            "Experiment Viewed",
            events[0].jsonObject["event_name"]?.jsonPrimitive?.content,
        )

        plugin.close()
    }

    @Test
    fun featureUsageEventsAreOnByDefault() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1)
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())

        val events = dispatcher.waitForPost()
        assertNotNull(events, "feature usage events are enabled unless explicitly disabled")
        assertEquals(
            "Feature Evaluated",
            events[0].jsonObject["event_name"]?.jsonPrimitive?.content,
        )

        plugin.close()
    }

    @Suppress("DEPRECATION")
    @Test
    fun legacyConfigKeepsFeatureUsageEvents() {
        // The frozen config cannot express the option, so the adapter must leave it at its default
        // rather than silently suppressing events for callers written against the old entry point.
        val dispatcher = CapturingDispatcher()
        val plugin = GrowthBookTrackingPlugin(
            TrackingPluginConfig(clientKey = "sdk-test", networkDispatcher = dispatcher, batchSize = 1)
        )
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())

        assertNotNull(dispatcher.waitForPost(), "legacy config must keep tracking feature usage")
        plugin.close()
    }

    @Test
    fun dedupeKeyAttributesKeepDifferentUsersApart() {
        // The reviewer's case: one instance, the user switches, the flag resolves the same way.
        // "Feature Evaluated" properties carry no identity, so without the option the second
        // user's event is suppressed as a duplicate (see the test below).
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(
            dispatcher,
            batchSize = 100,
            batchTimeout = 200.milliseconds,
            dedupeKeyAttributes = listOf("id"),
        )
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("ivan")))
        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("maria")))

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(2, events.size, "the same evaluation by two users must produce two events")

        plugin.close()
    }

    @Test
    fun dedupeKeyAttributesStillDedupeTheSameUser() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(
            dispatcher,
            batchSize = 100,
            batchTimeout = 200.milliseconds,
            dedupeKeyAttributes = listOf("id"),
        )
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("ivan")))
        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("ivan")))

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(1, events.size, "repeats by the same user must still collapse")

        plugin.close()
    }

    @Test
    fun withoutDedupeKeyAttributesDifferentUsersCollapse() {
        // Documents the default *for two evaluations with no user change in between*: identity is
        // not part of the key unless asked for. A real user switch goes through setAttributes,
        // which clears the cache — see userChangeClearsDedupeCache.
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 200.milliseconds)
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("ivan")))
        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("maria")))

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(1, events.size, "without the option the second user's event is de-duplicated")

        plugin.close()
    }

    @Test
    fun userChangeClearsDedupeCache() {
        // Login/logout on one instance: the same flag resolving the same way for the next user is a
        // new exposure, not a duplicate. The clear runs on the same single-threaded tracking
        // dispatcher as the enqueues, so it lands between the two evaluations.
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 200.milliseconds)
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("ivan")))
        plugin.onAttributesChanged(mapOf("id" to GBString("maria")))
        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("maria")))

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(2, events.size, "the new user's identical evaluation must not be suppressed")

        plugin.close()
    }

    @Test
    fun userChangeDoesNotStopDedupingAfterwards() {
        // The cache is cleared, not disabled: repeats by the new user still collapse.
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 200.milliseconds)
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("ivan")))
        plugin.onAttributesChanged(mapOf("id" to GBString("maria")))
        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("maria")))
        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("maria")))

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(2, events.size, "only the repeat by the same user collapses")

        plugin.close()
    }

    @Test
    fun dedupeKeyAttributesApplyToExperimentViewedToo() {
        // Exposures already carry hashAttribute/hashValue, so this only fires when the identity
        // lives in an attribute the result does not expose — but the configured keys must apply to
        // both event types, as they do in the TS plugin.
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(
            dispatcher,
            batchSize = 100,
            batchTimeout = 200.milliseconds,
            dedupeKeyAttributes = listOf("tenant"),
        )
        plugin.init()

        plugin.onExperimentViewed(experiment("exp"), experimentResult(0), mapOf("tenant" to GBString("a")))
        plugin.onExperimentViewed(experiment("exp"), experimentResult(0), mapOf("tenant" to GBString("b")))

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(2, events.size, "configured attributes must separate exposures as well")

        plugin.close()
    }

    @Test
    fun missingDedupeKeyAttributeIsOmittedNotNull() {
        // TS drops undefined fields in JSON.stringify, so an absent attribute contributes nothing.
        // Two events that differ only by an attribute nobody set must still collapse.
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(
            dispatcher,
            batchSize = 100,
            batchTimeout = 200.milliseconds,
            dedupeKeyAttributes = listOf("id"),
        )
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult(), attributes = null)
        plugin.onFeatureEvaluated("flag", featureResult(), attributes = emptyMap())

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(1, events.size, "an absent attribute must not change the key")

        plugin.close()
    }

    @Test
    fun setEnableFalseSilencesEveryEventKind() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1, enable = false)
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.onExperimentViewed(experiment("exp"), experimentResult())
        plugin.onEvent("Signed Up", emptyMap(), null)
        plugin.close() // must not flush anything either

        assertTrue(dispatcher.receivedNoPost(), "a disabled plugin must not hit the network")
    }

    @Test
    fun customEventIsTrackedWithTheSameWireShape() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1)
        plugin.init()

        plugin.onEvent(
            "Checkout Completed",
            mapOf("plan" to GBString("pro"), "amount" to GBNumber(42)),
            mapOf("id" to GBString("u1")),
        )

        val events = dispatcher.waitForPost()
        assertNotNull(events)
        val event = events[0].jsonObject
        assertEquals("Checkout Completed", event["event_name"]?.jsonPrimitive?.content)
        val properties = assertNotNull(event["properties_json"]?.jsonObject)
        assertEquals("pro", properties["plan"]?.jsonPrimitive?.content)
        assertEquals("42", properties["amount"]?.jsonPrimitive?.content)
        // Same envelope as the auto-tracked events: identity promoted, metadata at the top level.
        assertEquals("u1", event["device_id"]?.jsonPrimitive?.content)
        assertEquals(SdkMetadata.LANGUAGE, event["sdk_language"]?.jsonPrimitive?.content)

        plugin.close()
    }

    @Test
    fun customEventsAreNotDeduplicated() {
        // The JS plugin de-dupes only Feature Evaluated / Experiment Viewed: logging the same
        // custom event twice means it happened twice.
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 200.milliseconds)
        plugin.init()

        plugin.onEvent("Signed Up", emptyMap(), null)
        plugin.onEvent("Signed Up", emptyMap(), null)

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(2, events.size, "repeated custom events must both be sent")

        plugin.close()
    }

    @Test
    fun customEventNamedAfterAnSdkEventIsDeduplicated() {
        // De-duplication follows the event name, not the call that produced it — the reference JS
        // plugin branches on the name alone, so a caller who borrows one of ours gets its rules.
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 100, batchTimeout = 200.milliseconds)
        plugin.init()

        plugin.onEvent(GBTrackingEventNames.FEATURE_EVALUATED, emptyMap(), null)
        plugin.onEvent(GBTrackingEventNames.FEATURE_EVALUATED, emptyMap(), null)

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events)
        assertEquals(1, events.size, "an SDK event name carries the SDK's de-duplication")

        plugin.close()
    }

    @Test
    fun customEventsAreNotGatedByFeatureUsageOption() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1, enableFeatureUsageEvents = false)
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.onEvent("Signed Up", emptyMap(), null)

        val events = dispatcher.waitForPost()
        assertNotNull(events, "custom events are unrelated to enableFeatureUsageEvents")
        assertEquals("Signed Up", events[0].jsonObject["event_name"]?.jsonPrimitive?.content)

        plugin.close()
    }

    @Test
    fun eventFilterDropsWhatItRejects() {
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(dispatcher, batchSize = 1, eventFilter = { false })
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.onExperimentViewed(experiment("exp"), experimentResult())
        plugin.onEvent("Signed Up", emptyMap(), null)
        plugin.close()

        assertTrue(dispatcher.receivedNoPost(), "a rejecting filter must drop every event kind")
    }

    @Test
    fun eventFilterSeesTheEventAboutToBeSent() {
        val seen = mutableListOf<GBTrackingEventData>()
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(
            dispatcher,
            batchSize = 1,
            eventFilter = { event -> seen.add(event); true },
        )
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult(), mapOf("id" to GBString("u1")))
        assertNotNull(dispatcher.waitForPost(), "an accepting filter must let the event through")

        assertEquals(1, seen.size)
        val event = seen.first()
        assertEquals(GBTrackingEventNames.FEATURE_EVALUATED, event.eventName)
        // The filter sees the exact properties that are serialized into properties_json.
        assertEquals(GBString("flag"), event.properties["feature"])
        assertEquals(GBString("v"), event.properties["value"])
        assertEquals(GBString("u1"), event.attributes?.get("id"))

        plugin.close()
    }

    @Test
    fun filteredEventDoesNotOccupyTheDedupeCache() {
        // The JS plugin filters before de-duplicating. If the order were reversed, the first event
        // would claim the cache slot and the second would be dropped as its duplicate, sending
        // nothing at all.
        var rejectNext = true
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(
            dispatcher,
            batchSize = 100,
            batchTimeout = 200.milliseconds,
            eventFilter = { if (rejectNext) { rejectNext = false; false } else true },
        )
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())  // rejected by the filter
        plugin.onFeatureEvaluated("flag", featureResult())  // identical, must still be sent

        val events = dispatcher.waitForPost(timeoutSeconds = 3)
        assertNotNull(events, "the second event must not be treated as a duplicate of a dropped one")
        assertEquals(1, events.size)

        plugin.close()
    }

    @Test
    fun throwingEventFilterDropsTheEvent() {
        // Fail closed: privacy is the point of this hook, and sending is the only outcome that
        // can leak.
        val dispatcher = CapturingDispatcher()
        val plugin = plugin(
            dispatcher,
            batchSize = 1,
            eventFilter = { throw RuntimeException("filter blew up") },
        )
        plugin.init()

        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.close()

        assertTrue(dispatcher.receivedNoPost(), "a throwing filter must not let the event through")
    }

    @Test
    fun builderResolvesDefaultsLikeTheConfig() {
        // Both entry points share TrackingOptionDefaults; this guards against them drifting apart.
        val capturedUrl = AtomicReference<String>()
        val latch = CountDownLatch(1)
        val dispatcher = object : TrackingNetworkDispatcher {
            override fun consumePOSTRequest(url: String, headers: Map<String, String>,
                body: JsonElement, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
                capturedUrl.set(url)
                latch.countDown()
                onSuccess("{}")
            }
        }

        val plugin = GrowthBookTrackingPlugin.Builder()
            .setIngestorHost("https://ingest.example.com/")
            .setClientKey("k")
            .setNetworkDispatcher(dispatcher)
            .setBatchSize(0) // out of range -> falls back to the default, does not flush eagerly
            .build()
        plugin.init()
        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.close() // close() flushes, since the invalid batch size did not trigger one

        latch.await(5, TimeUnit.SECONDS)
        assertEquals("https://ingest.example.com/track?client_key=k", capturedUrl.get())
    }
}
