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
            it.jsonObject["properties"]?.jsonObject?.get("experimentId")?.jsonPrimitive?.content
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
        assertEquals("flag1", events[0].jsonObject["properties"]?.jsonObject?.get("feature")?.jsonPrimitive?.content)

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
    fun attributesAreIncludedInEvent() {
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
        val attrs = event["attributes"]?.jsonObject
        assertNotNull(attrs, "attributes should be present in event")
        assertEquals("u1", attrs["id"]?.jsonPrimitive?.content)
        assertEquals("pro", attrs["plan"]?.jsonPrimitive?.content)

        plugin.close()
    }

    @Test
    fun sdkAttributesAreMergedIntoEvent() {
        val dispatcher = CapturingDispatcher()
        val plugin = GrowthBookTrackingPlugin(
            config(dispatcher).copy(batchSize = 1)
        )
        plugin.init()
        plugin.onFeatureEvaluated("flag", featureResult())

        val events = dispatcher.waitForPost()
        assertNotNull(events)
        val attrs = events[0].jsonObject["attributes"]?.jsonObject
        assertNotNull(attrs)
        assertEquals(SdkMetadata.LANGUAGE, attrs["sdk_language"]?.jsonPrimitive?.content)
        assertEquals(SdkMetadata.VERSION, attrs["sdk_version"]?.jsonPrimitive?.content)

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
}
