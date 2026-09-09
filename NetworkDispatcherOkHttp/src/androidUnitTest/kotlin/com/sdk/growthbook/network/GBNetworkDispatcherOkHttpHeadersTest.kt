package com.sdk.growthbook.network

import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val RESPONSE_BODY = """{"features":{}}"""

/**
 * Verifies `apiHostRequestHeaders` / `streamingHostRequestHeaders` on the wire against an embedded
 * HTTP server: they are present on the features GET, the remote-eval POST and the SSE request, that
 * SDK-managed headers (`If-None-Match`, `Cache-Control`) still win, that unusable names and values
 * are dropped rather than thrown, and that ETag revalidation is unaffected.
 */
class GBNetworkDispatcherOkHttpHeadersTest {

    private lateinit var server: MockWebServer
    private lateinit var dispatcher: GBNetworkDispatcherOkHttp

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dispatcher = GBNetworkDispatcherOkHttp(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun featuresUrl() = server.url("/api/features/my-key").toString()

    private fun getSync(url: String, headers: Map<String, String>) {
        val latch = CountDownLatch(1)
        dispatcher.consumeGETRequest(
            request = url,
            headers = headers,
            onSuccess = { latch.countDown() },
            onError = { latch.countDown() },
        )
        assertTrue("GET timed out", latch.await(5, TimeUnit.SECONDS))
    }

    private fun getWithNotModifiedSync(url: String, headers: Map<String, String>) {
        val latch = CountDownLatch(1)
        dispatcher.consumeGETRequestWithNotModified(
            request = url,
            headers = headers,
            onSuccess = { latch.countDown() },
            onError = { latch.countDown() },
            onNotModified = { latch.countDown() },
        )
        assertTrue("GET timed out", latch.await(5, TimeUnit.SECONDS))
    }

    private fun enqueueOk() {
        server.enqueue(MockResponse().setBody(RESPONSE_BODY).setResponseCode(200))
    }

    @Test
    fun `custom headers are sent on the features GET request`() {
        enqueueOk()

        getSync(featuresUrl(), mapOf("Authorization" to "Bearer token", "X-Tenant" to "acme"))

        val request: RecordedRequest = server.takeRequest()
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("acme", request.getHeader("X-Tenant"))
    }

    @Test
    fun `custom headers are sent on the remote eval POST request`() {
        enqueueOk()

        val latch = CountDownLatch(1)
        dispatcher.consumePOSTRequest(
            url = server.url("/api/eval/my-key").toString(),
            headers = mapOf("Authorization" to "Bearer t"),
            bodyParams = mapOf("attributes" to emptyMap<String, Any>()),
            onSuccess = { latch.countDown() },
            onError = { latch.countDown() },
        )
        assertTrue("POST timed out", latch.await(5, TimeUnit.SECONDS))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("Bearer t", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `custom headers cannot override SDK managed headers`() {
        enqueueOk()

        getSync(
            featuresUrl(),
            mapOf(
                "Cache-Control" to "no-store",
                "If-None-Match" to "\"forged\"",
                // Not SDK-managed: the SDK sets no User-Agent on this request, so a consumer
                // behind a gateway that requires one can supply it.
                "User-Agent" to "acme-mobile/1.0",
            ),
        )

        val request = server.takeRequest()
        assertEquals("max-age=3600", request.getHeader("Cache-Control"))
        // No ETag has been observed yet, so the SDK sends no If-None-Match — and the forged one was
        // dropped rather than passed through.
        assertNull(request.getHeader("If-None-Match"))
        assertEquals("acme-mobile/1.0", request.getHeader("User-Agent"))
    }

    @Test
    fun `ETag revalidation still works alongside custom headers`() {
        server.enqueue(
            MockResponse().setBody(RESPONSE_BODY).setResponseCode(200).setHeader("ETag", "\"v1\"")
        )
        server.enqueue(MockResponse().setResponseCode(304))

        val headers = mapOf("Authorization" to "Bearer token")
        getWithNotModifiedSync(featuresUrl(), headers)
        getWithNotModifiedSync(featuresUrl(), headers)

        server.takeRequest()
        val second = server.takeRequest()
        assertEquals("\"v1\"", second.getHeader("If-None-Match"))
        assertEquals("Bearer token", second.getHeader("Authorization"))
    }

    @Test
    fun `header-less overload still sends the SDK cache directive`() {
        enqueueOk()

        val latch = CountDownLatch(1)
        dispatcher.consumeGETRequest(
            request = featuresUrl(),
            onSuccess = { latch.countDown() },
            onError = { latch.countDown() },
        )
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        assertEquals("max-age=3600", server.takeRequest().getHeader("Cache-Control"))
    }

    @Test
    fun `streaming headers are applied to the SSE request`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("event: features\ndata: $RESPONSE_BODY\n\n")
        )

        val controller = SSEConnectionController()
        val flow = dispatcher.consumeSSEConnection(
            url = server.url("/sub/my-key").toString(),
            headers = mapOf("Authorization" to "Bearer stream-token"),
            sseController = controller,
        )

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            scope.launch { flow.collect { } }
            // The flow closes immediately unless the controller is ACTIVE.
            controller.start()

            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) {
                "SSE request was never made"
            }
            assertEquals("Bearer stream-token", request.getHeader("Authorization"))
            assertEquals("text/event-stream", request.getHeader("Accept"))
            assertEquals("no-cache", request.getHeader("Cache-Control"))
        } finally {
            controller.stop()
            scope.cancel()
        }
    }

    /**
     * A value OkHttp would reject is dropped by `GBRequestHeaders.sanitize` before it reaches the
     * builder, so the request still goes out — just without that header. Dropping rather than
     * throwing also keeps the value out of OkHttp's exception message, which the SDK logs.
     */
    @Test
    fun `an illegal custom header value is dropped instead of failing the request`() {
        enqueueOk()

        getSync(
            featuresUrl(),
            mapOf("X-Token" to "abc\ndef", "X-Kept" to "kept"),
        )

        val request = server.takeRequest()
        assertNull(request.getHeader("X-Token"))
        assertEquals("kept", request.getHeader("X-Kept"))
    }

    @Test
    fun `a header value with a trailing newline is trimmed rather than dropped`() {
        enqueueOk()

        getSync(featuresUrl(), mapOf("Authorization" to "Bearer token\n"))

        assertEquals("Bearer token", server.takeRequest().getHeader("Authorization"))
    }

    /**
     * `Request.Builder` still throws on a URL it cannot parse, and that happens while the request
     * is being assembled — before the call is enqueued. It must surface through `onError` rather
     * than escaping the dispatcher's CoroutineScope, where nothing would ever complete the caller
     * and the default uncaught handler would take down the app.
     */
    @Test
    fun `a scheme-less url is reported through onError`() {
        val error = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)

        dispatcher.consumeGETRequest(
            request = "cdn.example.com/api/features/my-key",
            headers = emptyMap(),
            onSuccess = { latch.countDown() },
            onError = {
                error.set(it)
                latch.countDown()
            },
        )

        assertTrue("onError was never invoked", latch.await(5, TimeUnit.SECONDS))
        assertTrue(
            "expected IllegalArgumentException, got ${error.get()}",
            error.get() is IllegalArgumentException
        )
        assertEquals("no request should have been made", 0, server.requestCount)
    }

    @Test
    fun `a scheme-less url on the not-modified overload is reported through onError`() {
        val error = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)

        dispatcher.consumeGETRequestWithNotModified(
            request = "cdn.example.com/api/features/my-key",
            headers = emptyMap(),
            onSuccess = { latch.countDown() },
            onError = {
                error.set(it)
                latch.countDown()
            },
            onNotModified = { latch.countDown() },
        )

        assertTrue("onError was never invoked", latch.await(5, TimeUnit.SECONDS))
        assertTrue(error.get() is IllegalArgumentException)
    }

    /**
     * The SSE request is built inside the flow, so the same failure arrives as [Resource.Error]
     * instead of propagating synchronously out of `consumeSSEConnection` — which would escape
     * `FeaturesViewModel.autoRefreshFeatures()` and the public `autoRefreshFeatures()` API.
     */
    @Test
    fun `an unbuildable SSE request surfaces as Resource Error`() {
        val controller = SSEConnectionController()
        val flow = dispatcher.consumeSSEConnection(
            url = "cdn.example.com/sub/my-key",
            headers = emptyMap(),
            sseController = controller,
        )

        val result = runBlocking { withTimeout(5_000) { flow.first() } }

        assertTrue("expected Resource.Error, got $result", result is Resource.Error)
        assertEquals(0, server.requestCount)
    }
}
