package com.sdk.growthbook.tests

import com.sdk.growthbook.GBSDKBuilder
import com.sdk.growthbook.model.GBExperiment
import com.sdk.growthbook.model.GBExperimentResult
import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.sandbox.CachingJvm
import com.sdk.growthbook.utils.GBFetchStats
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers [GBSDKBuilder.setFetchStatsHandler]: per-fetch duration and payload size, which only
 * the client can measure because the edge completes a response before the device receives it.
 */
class FetchStatsHandlerTests {

    private val payload = """{"status":200,"features":{"f1":{"defaultValue":true}}}"""

    @Rule
    @JvmField
    var tempFolder = TemporaryFolder()

    // Cache reads happen even when writes are disabled, so point them at an empty dir per test;
    // otherwise a payload left by an earlier run could satisfy the fetch and report no stats.
    @BeforeTest
    fun setUp() {
        CachingJvm.baseDir = tempFolder.newFolder()
    }

    /** Not a NetworkDispatcherWithNotModified, so the plain GET path is exercised too. */
    private class PlainGetDispatcher(private val response: String) : NetworkDispatcher {
        override fun consumeGETRequest(
            request: String,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit
        ): Job {
            onSuccess(response)
            return Job()
        }

        override fun consumeSSEConnection(
            url: String,
            sseController: SSEConnectionController?
        ): Flow<Resource<String>> = emptyFlow()

        override fun consumePOSTRequest(
            url: String,
            bodyParams: Map<String, Any>,
            onSuccess: (String) -> Unit,
            onError: (Throwable) -> Unit
        ) = Unit
    }

    private fun TestScope.statsFrom(dispatcher: NetworkDispatcher): List<GBFetchStats> {
        val seen = mutableListOf<GBFetchStats>()
        GBSDKBuilder(
            "test-key",
            "https://cdn.growthbook.io",
            attributes = emptyMap(),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = dispatcher,
            cachingEnabled = false,
        )
            .setCoroutineContext(UnconfinedTestDispatcher(testScheduler))
            .setFetchStatsHandler { seen.add(it) }
            .initialize()
        return seen
    }

    @Test
    fun successfulFetch_reportsDurationAndDecodedSize() = runTest {
        val stats = statsFrom(MockNetworkClient(payload, null))

        assertEquals(1, stats.size)
        assertTrue(stats[0].success)
        assertEquals(payload.encodeToByteArray().size, stats[0].payloadBytes)
        assertTrue(stats[0].durationMillis >= 0)
    }

    @Test
    fun failedFetch_reportsFailureWithNoSize() = runTest {
        val stats = statsFrom(MockNetworkClient(null, Exception("network down")))

        assertEquals(1, stats.size)
        assertFalse(stats[0].success)
        assertNull(stats[0].payloadBytes)
    }

    @Test
    fun notModified_reportsSuccessWithZeroSize() = runTest {
        val stats = statsFrom(MockNetworkClient(null, null, notModified = true))

        assertEquals(1, stats.size)
        assertTrue(stats[0].success)
        assertEquals(0, stats[0].payloadBytes)
    }

    @Test
    fun plainGetDispatcher_isAlsoReported() = runTest {
        val stats = statsFrom(PlainGetDispatcher(payload))

        assertEquals(1, stats.size)
        assertTrue(stats[0].success)
        assertEquals(payload.encodeToByteArray().size, stats[0].payloadBytes)
    }

    @Test
    fun noHandlerSet_fetchStillSucceeds() = runTest {
        val sdk = GBSDKBuilder(
            "test-key",
            "https://cdn.growthbook.io",
            attributes = emptyMap(),
            encryptionKey = null,
            trackingCallback = { _: GBExperiment, _: GBExperimentResult? -> },
            networkDispatcher = MockNetworkClient(payload, null),
            cachingEnabled = false,
        )
            .setCoroutineContext(UnconfinedTestDispatcher(testScheduler))
            .initialize()

        assertNotNull(sdk.getFeatures()["f1"])
    }
}
