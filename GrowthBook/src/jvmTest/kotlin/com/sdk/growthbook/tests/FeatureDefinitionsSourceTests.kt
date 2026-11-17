package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.model.GBFeatureDefinitionsSource
import com.sdk.growthbook.model.GBFeatureSource
import com.sdk.growthbook.sandbox.CachingJvm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureDefinitionsSourceTests {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    @Test
    fun reportsMissingDefinitionsAndBundledSeed() {
        val sdk = builder(MockNetworkClient(null, null)).initialize()
        try {
            val missing = sdk.featureWithSource("flag")
            assertEquals(GBFeatureDefinitionsSource.None, missing.definitionsSource)
            assertEquals(GBFeatureSource.unknownFeature, missing.result.source)
            assertEquals(0, sdk.getFeaturesStatus().featureCount)
        } finally {
            sdk.close()
        }

        val seeded = builder(MockNetworkClient(null, null))
            .setInitialPayload("""{"features":{"flag":{"defaultValue":true}}}""")
            .initialize()
        try {
            val flag = seeded.featureWithSource("flag")
            assertEquals(GBFeatureDefinitionsSource.Provided, flag.definitionsSource)
            assertEquals(GBFeatureSource.defaultValue, flag.result.source)
            assertEquals(1, seeded.getFeaturesStatus().featureCount)
        } finally {
            seeded.close()
        }
    }

    @Test
    fun distinguishesFreshCacheFromNetworkEvenWhenBothAreAuthoritative() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val live = builder(MockNetworkClient("""{"features":{"flag":{"defaultValue":true}}}""", null))
            .setCoroutineContext(dispatcher)
            .initialize()
        try {
            advanceUntilIdle()
            assertEquals(GBFeatureDefinitionsSource.Network, live.getFeaturesStatus().source)
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
            assertEquals(GBFeatureDefinitionsSource.Cache, cachedFlag.definitionsSource)
            assertEquals(GBFeatureDefinitionsSource.Cache, cached.getFeaturesStatus().source)
            assertTrue(cachedFlag.result.on)

            cached.refreshCache()
            advanceUntilIdle()

            assertEquals(1, network.getCount)
            val refreshedFlag = cached.featureWithSource("flag")
            assertEquals(GBFeatureDefinitionsSource.Network, refreshedFlag.definitionsSource)
            assertEquals(cachedFlag.result.source, refreshedFlag.result.source)
            assertFalse(refreshedFlag.result.on)
        } finally {
            cached.close()
        }
    }

    @Test
    fun notModifiedKeepsTheCacheAsTheDefinitionSource() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val live = builder(MockNetworkClient("""{"features":{"flag":{"defaultValue":true}}}""", null))
            .setCoroutineContext(dispatcher)
            .initialize()
        try {
            advanceUntilIdle()
            assertEquals(GBFeatureDefinitionsSource.Network, live.getFeaturesStatus().source)
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
            assertEquals(GBFeatureDefinitionsSource.Cache, cached.getFeaturesStatus().source)
            assertTrue(cached.featureWithSource("flag").result.on)
        } finally {
            cached.close()
        }
    }

    private fun builder(network: MockNetworkClient) = GBSDKBuilder(
        apiKey = "feature-definitions-source-test",
        apiHost = "https://cdn.growthbook.io",
        attributes = emptyMap(),
        networkDispatcher = network,
        trackingCallback = { _, _ -> },
        cachingEnabled = true,
    )
}
