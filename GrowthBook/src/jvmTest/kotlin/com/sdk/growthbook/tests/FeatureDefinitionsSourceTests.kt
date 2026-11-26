package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBDefinitionsOrigin
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureRule
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.model.GBString
import com.sdk.growthbook.utils.Constants
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureDefinitionsSourceTests {
    private val cache = FakeCachingLayer()

    @Test
    fun reportsMissingDefinitionsAndBundledSeed() {
        val sdk = builder(MockNetworkClient(null, null)).initialize()
        try {
            val missing = sdk.featureWithSource("flag")
            assertEquals(GBDefinitionsOrigin.None, missing.definitionsSource)
            assertEquals(GBFeatureSource.unknownFeature, missing.result.source)
            assertFalse(missing.definitionPresent)
            assertEquals(0, sdk.getFeaturesStatus().featureCount)

            sdk.setForcedFeatures(mapOf("flag" to GBBoolean(true)))
            val override = sdk.featureWithSource("flag")
            assertFalse(override.definitionPresent)
            assertEquals(GBFeatureSource.override, override.result.source)
            assertTrue(override.result.on)
        } finally {
            sdk.close()
        }

        val seeded = builder(MockNetworkClient(null, null))
            .setInitialPayload(ENABLED_FLAG_PAYLOAD)
            .initialize()
        try {
            val flag = seeded.featureWithSource("flag")
            assertEquals(GBDefinitionsOrigin.Provided, flag.definitionsSource)
            assertEquals(GBFeatureSource.defaultValue, flag.result.source)
            assertTrue(flag.definitionPresent)
            assertEquals(1, seeded.getFeaturesStatus().featureCount)
        } finally {
            seeded.close()
        }
    }

    @Test
    fun distinguishesFreshCacheFromNetworkEvenWhenBothAreAuthoritative() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val live = builder(MockNetworkClient(ENABLED_FLAG_PAYLOAD, null))
            .setCoroutineContext(dispatcher)
            .initialize()
        try {
            advanceUntilIdle()
            assertEquals(GBDefinitionsOrigin.Network, live.getFeaturesStatus().source)
            assertTrue(live.featureWithSource("flag").result.on)
        } finally {
            live.close()
        }

        val network = CountingPostNetworkClient("""{"features":{"flag":{"defaultValue":false}}}""")
        val cached = builder(network)
            .setCoroutineContext(dispatcher)
            .setCacheMaxAge(60_000)
            .initialize()
        try {
            assertEquals(0, network.getCount)
            val cachedFlag = cached.featureWithSource("flag")
            assertEquals(GBDefinitionsOrigin.Cache, cachedFlag.definitionsSource)
            assertEquals(GBDefinitionsOrigin.Cache, cached.getFeaturesStatus().source)
            assertTrue(cachedFlag.result.on)

            cached.refreshCache()
            advanceUntilIdle()

            assertEquals(1, network.getCount)
            val refreshedFlag = cached.featureWithSource("flag")
            assertEquals(GBDefinitionsOrigin.Network, refreshedFlag.definitionsSource)
            assertEquals(cachedFlag.result.source, refreshedFlag.result.source)
            assertFalse(refreshedFlag.result.on)
        } finally {
            cached.close()
        }
    }

    @Test
    fun notModifiedKeepsTheCacheAsTheDefinitionSource() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val live = builder(MockNetworkClient(ENABLED_FLAG_PAYLOAD, null))
            .setCoroutineContext(dispatcher)
            .initialize()
        try {
            advanceUntilIdle()
            assertEquals(GBDefinitionsOrigin.Network, live.getFeaturesStatus().source)
        } finally {
            live.close()
        }

        var validationSucceeded = false
        val cached = builder(MockNetworkClient(null, null, notModified = true))
            .setCoroutineContext(dispatcher)
            .setRefreshHandler { success, _ -> validationSucceeded = success }
            .initialize()
        try {
            advanceUntilIdle()
            assertTrue(validationSucceeded)
            assertEquals(GBDefinitionsOrigin.Cache, cached.getFeaturesStatus().source)
            assertTrue(cached.featureWithSource("flag").result.on)
        } finally {
            cached.close()
        }
    }

    @Test
    fun reportsAPresentDefinitionWhenEvaluationFails() {
        val malformed = GBFeature(
            defaultValue = GBBoolean(false),
            rules = listOf(
                GBFeatureRule(
                    key = "experiment",
                    hashAttribute = "id",
                    coverage = 1.0,
                    variations = listOf(GBBoolean(false), GBBoolean(true)),
                    namespace = JsonArray(listOf(JsonObject(emptyMap()), JsonPrimitive(0), JsonPrimitive(1))),
                ),
            ),
        )
        val sdk = builder(MockNetworkClient(null, null))
            .setInitialFeatures(mapOf("flag" to malformed))
            .initialize()
        try {
            val flag = sdk.featureWithSource("flag")
            assertTrue(flag.definitionPresent)
            assertEquals(GBFeatureSource.unknownFeature, flag.result.source)
        } finally {
            sdk.close()
        }
    }

    @Test
    fun notModifiedAcceptsAnEmptyLoadedFeatureMap() = runTest {
        val refreshResults = mutableListOf<Boolean>()
        val sdk = builder(MockNetworkClient(null, null, notModified = true))
            .setCoroutineContext(StandardTestDispatcher(testScheduler))
            .setInitialFeatures(emptyMap())
            .setRefreshHandler { success, _ -> refreshResults.add(success) }
            .initialize()
        try {
            advanceUntilIdle()
            assertEquals(listOf(true), refreshResults)
            assertEquals(GBDefinitionsOrigin.Provided, sdk.getFeaturesStatus().source)
            assertEquals(0, sdk.getFeaturesStatus().featureCount)
        } finally {
            sdk.close()
        }
    }

    @Test
    fun notModifiedKeepsNetworkDefinitionsAfterExplicitRefresh() = runTest {
        val network = object : MockNetworkClient(ENABLED_FLAG_PAYLOAD, null) {
            var requests = 0

            override fun consumeGETRequestWithNotModified(
                request: String,
                onSuccess: (String) -> Unit,
                onError: (Throwable) -> Unit,
                onNotModified: () -> Unit,
            ): Job {
                requests++
                if (requests == 1) {
                    return super.consumeGETRequestWithNotModified(request, onSuccess, onError, onNotModified)
                }
                onNotModified()
                return Job()
            }
        }
        val refreshResults = mutableListOf<Boolean>()
        val sdk = builder(network)
            .setCoroutineContext(StandardTestDispatcher(testScheduler))
            .setRefreshHandler { success, _ -> refreshResults.add(success) }
            .initialize()
        try {
            advanceUntilIdle()
            assertTrue(cache.store.isNotEmpty(), "The GET must have written a cache entry before revalidation")
            assertEquals(GBDefinitionsOrigin.Network, sdk.getFeaturesStatus().source)

            sdk.refreshCache()
            assertEquals(GBDefinitionsOrigin.Network, sdk.getFeaturesStatus().source)
            advanceUntilIdle()

            assertEquals(2, network.requests)
            assertEquals(listOf(true, true), refreshResults)
            val flag = sdk.featureWithSource("flag")
            assertEquals(GBDefinitionsOrigin.Network, flag.definitionsSource)
            assertTrue(flag.result.on)
        } finally {
            sdk.close()
        }
    }

    @Test
    fun expiredCacheIsReportedAsStaleAfterNetworkFailure() = runTest {
        val expiredAt = Clock.System.now().toEpochMilliseconds() - 60 * 60 * 1000L
        cache.saveContent(
            "${Constants.FEATURE_CACHE}_$API_KEY",
            """{"features":{"flag":{"defaultValue":true}},"cachedAt":$expiredAt}""",
        )
        val sdk = builder(MockNetworkClient(null, IllegalStateException("offline")))
            .setCoroutineContext(StandardTestDispatcher(testScheduler))
            .setStaleTtl(60_000)
            .setCacheMaxAge(120_000)
            .setServeStaleOnError(true)
            .initialize()
        try {
            assertEquals(GBDefinitionsOrigin.None, sdk.getFeaturesStatus().source)
            advanceUntilIdle()
            assertEquals(GBDefinitionsOrigin.Stale, sdk.getFeaturesStatus().source)
            val flag = sdk.featureWithSource("flag")
            assertEquals(GBDefinitionsOrigin.Stale, flag.definitionsSource)
            assertTrue(flag.definitionPresent)
            assertTrue(flag.result.on)
        } finally {
            sdk.close()
        }
    }

    @Test
    fun partialNetworkPayloadKeepsTheOriginOfCachedDefinitions() = runTest {
        cache.saveContent("${Constants.FEATURE_CACHE}_$API_KEY", ENABLED_FLAG_PAYLOAD)
        val sdk = builder(MockNetworkClient(MockResponse.successResponseWithSavedGroups, null))
            .setCoroutineContext(StandardTestDispatcher(testScheduler))
            .initialize()
        try {
            advanceUntilIdle()
            assertTrue(sdk.getGBContext().savedGroups?.containsKey("group1") == true)
            val flag = sdk.featureWithSource("flag")
            assertEquals(GBDefinitionsOrigin.Cache, flag.definitionsSource)
            assertTrue(flag.result.on)
        } finally {
            sdk.close()
        }
    }

    @Test
    fun streamingDefinitionsAreReportedAsNetwork() = runTest {
        val network = object : MockNetworkClient(null, null) {
            override fun consumeSSEConnection(
                url: String,
                sseConnectionController: SSEConnectionController?,
            ): Flow<Resource<String>> = flowOf(Resource.Success(ENABLED_FLAG_PAYLOAD))
        }
        val sdk = builder(network)
            .setCoroutineContext(StandardTestDispatcher(testScheduler))
            .initialize()
        try {
            sdk.startAutoRefreshFeatures().toList()
            val flag = sdk.featureWithSource("flag")
            assertEquals(GBDefinitionsOrigin.Network, flag.definitionsSource)
            assertTrue(flag.result.on)
        } finally {
            sdk.close()
        }
    }

    @Test
    fun remoteEvalDefinitionsAreReportedAsNetwork() = runTest {
        val network = CountingPostNetworkClient(ENABLED_FLAG_PAYLOAD)
        val sdk = builder(network, remoteEval = true)
            .setCoroutineContext(StandardTestDispatcher(testScheduler))
            .initialize()
        try {
            advanceUntilIdle()
            assertEquals(1, network.postCount)
            assertEquals(0, network.getCount)
            val flag = sdk.featureWithSource("flag")
            assertEquals(GBDefinitionsOrigin.Network, flag.definitionsSource)
            assertTrue(flag.result.on)
        } finally {
            sdk.close()
        }
    }

    private fun builder(network: MockNetworkClient, remoteEval: Boolean = false) = GBSDKBuilder(
        apiKey = API_KEY,
        apiHost = "https://cdn.growthbook.io",
        attributes = mapOf("id" to GBString("user-123")),
        networkDispatcher = network,
        trackingCallback = { _, _ -> },
        cachingEnabled = true,
        remoteEval = remoteEval,
    ).setCachingLayer(cache)

    private companion object {
        const val API_KEY = "feature-definitions-source-test"
        const val ENABLED_FLAG_PAYLOAD = """{"features":{"flag":{"defaultValue":true}}}"""
    }
}
