package com.sdk.growthbook.caffeine

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.caffeine.cache.GBCaffeineCachingLayer
import com.sdk.growthbook.caffeine.stickybucket.GBCaffeineStickyBucketService
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.model.GBValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the real SDK against these adapters, which is the only place the contract between them
 * can be proven: the key shape the SDK asks its caching layer for is built inside `GrowthBookSDK`
 * from an internal constant, so nothing but an end-to-end run would catch it changing.
 */
class GBCaffeineSdkIntegrationTests {

    private val scopeErrors = mutableListOf<Throwable>()

    private fun layer() = GBCaffeineCachingLayer(onError = { scopeErrors += it })

    private fun builder(dispatcher: FakeNetworkDispatcher) =
        GBSDKBuilder(
            apiKey = API_KEY,
            apiHost = "https://host.growthbook.io",
            attributes = mapOf<String, GBValue>("id" to GBString("user-42")),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = dispatcher
        )

    @Test
    fun `a fetched payload is cached under the key the SDK asks for`() {
        val layer = layer()

        builder(FakeNetworkDispatcher(successResponse = PAYLOAD))
            .setCachingLayer(layer)
            .initialize()

        // The payload is applied on a background dispatcher, so the write lands shortly after
        // initialize() returns rather than within it.
        val cached = awaitCached(layer)
        assertTrue(
            cached.contains("onboarding"),
            "the cached payload should carry the fetched features, but was: $cached"
        )
        assertTrue(scopeErrors.isEmpty(), "the SDK asked for a key outside the layer's scope")
    }

    @Test
    fun `a cold start serves features from the cache when the network is down`() {
        val layer = layer()
        // What a previous run would have left behind: the serialised model, without the "status"
        // wrapper the network response carries.
        layer.saveContent(
            "FeatureCache_$API_KEY",
            """{"features":{"onboarding":{"defaultValue":"top"}}}"""
        )

        val sdk = builder(FakeNetworkDispatcher(error = Throwable("no network")))
            .setCachingLayer(layer)
            .initialize()

        assertEquals(GBString("top"), sdk.feature("onboarding").gbValue)
    }

    /**
     * The wiring the documentation recommends: the caching layer for features, the dedicated
     * service for sticky bucketing. The layer must see nothing but feature cache keys.
     */
    @Test
    fun `the recommended wiring keeps sticky bucketing off the caching layer`() {
        val layer = layer()

        builder(FakeNetworkDispatcher(successResponse = PAYLOAD))
            .setStickyBucketService(
                GBCaffeineStickyBucketService(CoroutineScope(Dispatchers.IO), API_KEY)
            )
            .setCachingLayer(layer)
            .initialize()

        awaitCached(layer)
        assertTrue(scopeErrors.isEmpty(), "sticky bucketing reached the feature caching layer")
    }

    /** Polls rather than sleeping a fixed time, so the test is neither flaky nor slow. */
    private fun awaitCached(layer: GBCaffeineCachingLayer): String {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            layer.getContent("FeatureCache_$API_KEY")?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("the SDK never wrote FeatureCache_$API_KEY to the caching layer")
    }

    private companion object {
        const val API_KEY = "sdk-caffeine-test"
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
        val PAYLOAD = """{"status":200,"features":{"onboarding":{"defaultValue":"top"}}}"""
    }
}
