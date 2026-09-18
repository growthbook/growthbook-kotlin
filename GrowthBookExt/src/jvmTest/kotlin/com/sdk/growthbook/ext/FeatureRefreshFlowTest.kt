package com.sdk.growthbook.ext

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBBoolean
import com.sdk.growthbook.model.GBFeature
import com.sdk.growthbook.model.GBFeatureRefreshEvent
import com.sdk.growthbook.model.GBFeatureRefreshSource
import com.sdk.growthbook.utils.GBFeatures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers [featureRefreshFlow]: that it relays the SDK's refresh events, and — the part a listener
 * user has to get right by hand — that ending collection removes the underlying registration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureRefreshFlowTest {

    private val features: GBFeatures = mapOf("f1" to GBFeature(defaultValue = GBBoolean(true)))

    private fun sdk(): GrowthBookSDK = GBSDKBuilder(
        apiKey = "",
        apiHost = "",
        networkDispatcher = MockNetworkDispatcher(),
        attributes = emptyMap(),
        trackingCallback = { _, _ -> },
        // Off, so the suite never touches the real per-user cache directory.
        cachingEnabled = false,
    ).initialize()

    @Test
    fun relaysEventsToTheCollector() = runTest {
        val sdk = sdk()
        val collected = mutableListOf<GBFeatureRefreshEvent>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            sdk.featureRefreshFlow().take(2).toList(collected)
        }

        sdk.payloadFetchedSuccessfully(features, null, null, isRemote = true)
        sdk.payloadFetchedSuccessfully(features, null, null, isRemote = false)
        job.join()

        assertEquals(2, collected.size)
        assertEquals(GBFeatureRefreshSource.Network, collected[0].source)
        assertEquals(GBFeatureRefreshSource.Cache, collected[1].source)
        assertTrue(collected.all { it.success })
    }

    @Test
    fun cancellingCollectionRemovesTheSubscription() = runTest {
        val sdk = sdk()
        val collected = mutableListOf<GBFeatureRefreshEvent>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            sdk.featureRefreshFlow().collect { collected.add(it) }
        }
        sdk.payloadFetchedSuccessfully(features, null, null, isRemote = true)
        assertEquals(1, collected.size)

        job.cancel()
        job.join()

        // awaitClose must have cancelled the listener: further refreshes reach nobody.
        sdk.payloadFetchedSuccessfully(features, null, null, isRemote = true)
        assertEquals(1, collected.size)
    }

    /**
     * A closed SDK will never refresh again and refuses the registration, so the flow must
     * complete rather than leave the collector suspended in `awaitClose` forever.
     */
    @Test
    fun collectingAnAlreadyClosedSdk_completesImmediately() = runTest {
        val sdk = sdk()
        sdk.close()

        val collected = sdk.featureRefreshFlow().toList()

        assertTrue(collected.isEmpty())
    }

    @Test
    fun twoCollectorsAreIndependent() = runTest {
        val sdk = sdk()
        val first = mutableListOf<GBFeatureRefreshEvent>()
        val second = mutableListOf<GBFeatureRefreshEvent>()

        val firstJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            sdk.featureRefreshFlow().collect { first.add(it) }
        }
        val secondJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            sdk.featureRefreshFlow().collect { second.add(it) }
        }

        sdk.payloadFetchedSuccessfully(features, null, null, isRemote = true)
        assertEquals(1, first.size)
        assertEquals(1, second.size)

        // Each collector owns its own registration, so one ending leaves the other collecting.
        firstJob.cancel()
        firstJob.join()
        sdk.payloadFetchedSuccessfully(features, null, null, isRemote = true)

        assertEquals(1, first.size)
        assertEquals(2, second.size)

        secondJob.cancel()
        secondJob.join()
    }
}
