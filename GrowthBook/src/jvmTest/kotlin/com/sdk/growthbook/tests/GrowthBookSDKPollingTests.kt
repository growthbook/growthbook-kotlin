package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.GrowthBookSDK
import com.sdk.growthbook.model.GBValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SDK-level integration coverage for the public polling entry points, exercising the wiring that the
 * VM-level [FeaturesViewModelPollingTests] cannot: builder -> SDK ctor -> view model, and
 * GrowthBookSDK.startPolling()'s "no refreshInterval configured" branch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GrowthBookSDKPollingTests {

    private val apiKey = "key"
    private val hostURL = "https://example.com"

    private class CountingClient : MockNetworkClient(MockResponse.successResponse, null) {
        var getCount = 0
        override fun consumeGETRequestWithNotModified(
            request: String,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit,
            onNotModified: (() -> Unit)
        ): Job {
            getCount++
            return super.consumeGETRequestWithNotModified(request, onSuccess, onError, onNotModified)
        }
    }

    private fun buildSdk(
        client: MockNetworkClient,
        scheduler: TestCoroutineScheduler,
        refreshInterval: Long? = null,
    ): GrowthBookSDK {
        var builder = GBSDKBuilder(
            apiKey = apiKey,
            apiHost = hostURL,
            attributes = HashMap<String, GBValue>(),
            trackingCallback = { _, _ -> },
            networkDispatcher = client,
            cachingEnabled = false,
        ).setCoroutineContext(UnconfinedTestDispatcher(scheduler))
        refreshInterval?.let { builder = builder.setRefreshInterval(it) }
        return builder.initialize()
    }

    @Test
    fun testStartPollingWithoutIntervalIsNoOp() = runTest {
        val client = CountingClient()
        val sdk = buildSdk(client, testScheduler, refreshInterval = null)
        runCurrent()
        val baseline = client.getCount // the initial fetch from initialize()

        sdk.startPolling() // no refreshInterval configured -> must be a no-op
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(baseline, client.getCount, "startPolling() without a configured interval must not poll")
        sdk.close()
    }

    @Test
    fun testStartPollingWithIntervalPollsThenStopHalts() = runTest {
        val client = CountingClient()
        val sdk = buildSdk(client, testScheduler, refreshInterval = 1000)
        runCurrent()
        val baseline = client.getCount

        sdk.startPolling()
        repeat(3) {
            advanceTimeBy(1000)
            runCurrent()
        }
        assertEquals(baseline + 3, client.getCount, "each configured interval must trigger one poll")

        sdk.stopPolling()
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(baseline + 3, client.getCount, "stopPolling() must halt further polls")

        sdk.close()
    }
}
