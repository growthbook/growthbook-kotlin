package com.sdk.growthbook.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val RESPONSE_BODY = """{"features":{}}"""

/**
 * Covers `fetchTimeoutMillis`: a bare `OkHttpClient()` carries a silent 10s per-read timeout —
 * too tight for a large payload on a slow network and invisible to the caller. The knob replaces
 * it with an explicit whole-call ceiling (and matching read timeout) on the fetch path only.
 * The tests emulate the tight default with a 300ms read timeout so they stay fast.
 */
class GBNetworkDispatcherOkHttpTimeoutTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun featuresUrl() = server.url("/api/features/my-key").toString()

    private fun stalledResponse(stallMillis: Long): MockResponse =
        MockResponse()
            .setBody(RESPONSE_BODY)
            .setHeadersDelay(stallMillis, TimeUnit.MILLISECONDS)

    private fun getSync(
        dispatcher: GBNetworkDispatcherOkHttp,
        onSuccess: (String) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        val latch = CountDownLatch(1)
        dispatcher.consumeGETRequest(
            request = featuresUrl(),
            onSuccess = { onSuccess(it); latch.countDown() },
            onError = { onError(it); latch.countDown() },
        )
        assertTrue("GET never called back", latch.await(10, TimeUnit.SECONDS))
    }

    private fun tightClient() = OkHttpClient.Builder()
        .readTimeout(300, TimeUnit.MILLISECONDS)
        .build()

    @Test
    fun `fetchTimeout relaxes the client's tight read timeout`() {
        server.enqueue(stalledResponse(stallMillis = 800))
        var error: Throwable? = null
        var body: String? = null

        val dispatcher = GBNetworkDispatcherOkHttp(
            client = tightClient(),
            fetchTimeoutMillis = 10_000,
        )
        getSync(dispatcher, onSuccess = { body = it }, onError = { error = it })

        assertNull("read timeout should have been raised to fetchTimeout, got $error", error)
        assertTrue(body == RESPONSE_BODY)
    }

    @Test
    fun `stalled fetch fails at fetchTimeout`() {
        server.enqueue(stalledResponse(stallMillis = 3_000))
        var error: Throwable? = null

        val dispatcher = GBNetworkDispatcherOkHttp(fetchTimeoutMillis = 300)
        getSync(dispatcher, onError = { error = it })

        // callTimeout and readTimeout both surface as InterruptedIOException subtypes.
        assertTrue("expected a timeout, got $error", error is InterruptedIOException)
    }

    @Test
    fun `null fetchTimeout respects the caller's client configuration`() {
        server.enqueue(stalledResponse(stallMillis = 800))
        var error: Throwable? = null

        val dispatcher = GBNetworkDispatcherOkHttp(
            client = tightClient(),
            fetchTimeoutMillis = null,
        )
        getSync(dispatcher, onError = { error = it })

        assertTrue("expected the client's own 300ms read timeout, got $error", error is InterruptedIOException)
    }

    @Test
    fun `stalled remote-eval POST is bounded too`() {
        server.enqueue(stalledResponse(stallMillis = 3_000))
        var error: Throwable? = null
        val latch = CountDownLatch(1)

        val dispatcher = GBNetworkDispatcherOkHttp(fetchTimeoutMillis = 300)
        dispatcher.consumePOSTRequest(
            url = featuresUrl(),
            bodyParams = mapOf("attributes" to emptyMap<String, Any>()),
            onSuccess = { latch.countDown() },
            onError = { error = it; latch.countDown() },
        )

        assertTrue("POST never called back", latch.await(10, TimeUnit.SECONDS))
        assertTrue("expected a timeout, got $error", error is InterruptedIOException)
    }
}
