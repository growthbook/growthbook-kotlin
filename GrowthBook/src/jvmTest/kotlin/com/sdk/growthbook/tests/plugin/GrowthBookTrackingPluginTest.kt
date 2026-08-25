package com.sdk.growthbook.tests.plugin

import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBFeatureResult
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBNull
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import com.sdk.growthbook.plugin.tracking.GrowthBookTrackingPlugin
import com.sdk.growthbook.plugin.tracking.SdkMetadata
import com.sdk.growthbook.plugin.TrackingPluginConfig
import com.sdk.growthbook.network.TrackingNetworkDispatcher
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

    private fun config(dispatcher: TrackingNetworkDispatcher) = TrackingPluginConfig(
        clientKey = "sdk-test",
        networkDispatcher = dispatcher,
    )

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
    fun flushesWhenBatchSizeReached() {
        val dispatcher = CapturingDispatcher()
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 2, batchTimeout = 30.seconds)
        )
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
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 100, batchTimeout = 200.milliseconds)
        )
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
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 100, batchTimeout = 60.seconds)
        )
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
        val plugin = GrowthBookTrackingPlugin(config(dispatcher))
        plugin.close()
        plugin.close()
    }

    @Test
    fun noClientKeyDisablesPlugin() {
        val dispatcher = CapturingDispatcher()
        val plugin = GrowthBookTrackingPlugin(
            TrackingPluginConfig(
                clientKey = null,
                networkDispatcher = dispatcher,
                batchSize = 1,
            )
        )
        plugin.init()

        plugin.onExperimentViewed(experiment("exp"), experimentResult())
        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.close()

        assertTrue(dispatcher.receivedNoPost(), "disabled plugin must not hit the network")
    }

    @Test
    fun emptyClientKeyDisablesPlugin() {
        val dispatcher = CapturingDispatcher()
        val plugin = GrowthBookTrackingPlugin(
            TrackingPluginConfig(clientKey = "", networkDispatcher = dispatcher, batchSize = 1)
        )
        plugin.init()
        plugin.onFeatureEvaluated("flag", featureResult())
        plugin.close()

        assertTrue(dispatcher.receivedNoPost(), "empty clientKey must not disable plugin")
    }

    @Test
    fun httpErrorDoesNotThrow() {
        val dispatcher = CapturingDispatcher(responseError = RuntimeException("network error"))
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 1)
        )
        plugin.init()

        plugin.onExperimentViewed(experiment("exp"), experimentResult())

        dispatcher.waitForPost()
        plugin.close()
    }

    @Test
    fun ingestorHostTrailingSlashStripped() {
        val cfg = TrackingPluginConfig(
            ingestorHost = "https://example.test/",
            clientKey = "k",
        )
        assertEquals("https://example.test", cfg.resolvedIngestorHost())
        assertFalse(cfg.resolvedIngestorHost().endsWith("/"))
    }

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
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 1)
        )
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
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 1)
        )
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
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 100, batchTimeout = 200.milliseconds)
        )
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
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 100, batchTimeout = 200.milliseconds)
        )
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
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 100, batchTimeout = 200.milliseconds)
        )
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
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 1)
        )
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

        val plugin = GrowthBookTrackingPlugin(
            TrackingPluginConfig(
                ingestorHost = "https://ingest.example.com",
                clientKey = "k",
                networkDispatcher = dispatcher,
                batchSize = 1,
            )
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

        val plugin = GrowthBookTrackingPlugin(
            TrackingPluginConfig(clientKey = "k", networkDispatcher = dispatcher, batchSize = 1)
        )
        plugin.init()
        plugin.onFeatureEvaluated("flag", featureResult())

        latch.await(5, TimeUnit.SECONDS)
        // Mirrors JS/Python: tracking JSON is posted as text/plain.
        assertEquals("text/plain", capturedHeaders.get()?.get("Content-Type"))
        plugin.close()
    }
}
