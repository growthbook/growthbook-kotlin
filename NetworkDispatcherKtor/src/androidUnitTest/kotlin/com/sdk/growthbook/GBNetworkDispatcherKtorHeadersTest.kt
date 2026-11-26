package com.sdk.growthbook

import com.sdk.growthbook.network.GBNetworkDispatcherKtor
import com.sdk.growthbook.utils.SSEConnectionController
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val FEATURES_URL = "http://example.com/api/features/my-key"
private const val EVAL_URL = "http://example.com/api/eval/my-key"
private const val SSE_URL = "http://example.com/sub/my-key"
private const val RESPONSE_BODY = """{"features":{}}"""

/**
 * Verifies `apiHostRequestHeaders` / `streamingHostRequestHeaders` reach the Ktor request, and that
 * the SDK-managed headers (`If-None-Match`, `Cache-Control`) are never overridden by them.
 */
class GBNetworkDispatcherKtorHeadersTest {

    /** Records every request the engine sees, so headers can be asserted after the fact. */
    private class RecordingEngine(
        private val etag: String? = null,
        private val status: HttpStatusCode = HttpStatusCode.OK,
    ) {
        /**
         * Appended from the engine's coroutine and read from the JUnit thread, hence concurrent.
         * Awaiting [requestReceived] is what gives the read a happens-before edge on the write.
         */
        val requests = CopyOnWriteArrayList<HttpRequestData>()

        /** Signals the first request, so a test never has to busy-poll [requests]. */
        val requestReceived = CountDownLatch(1)

        val engine = MockEngine { request ->
            requests.add(request)
            requestReceived.countDown()
            respond(
                content = ByteReadChannel(RESPONSE_BODY),
                status = status,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    *listOfNotNull(etag?.let { HttpHeaders.ETag to listOf(it) }).toTypedArray(),
                ),
            )
        }
    }

    private fun dispatcher(engine: MockEngine) = GBNetworkDispatcherKtor(
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
        }
    )

    private fun awaitLatch(latch: CountDownLatch) =
        assertTrue("request timed out", latch.await(5, TimeUnit.SECONDS))

    @Test
    fun `custom headers are sent on the features GET request`() {
        val recorder = RecordingEngine()
        val latch = CountDownLatch(1)

        dispatcher(recorder.engine).consumeGETRequest(
            request = FEATURES_URL,
            headers = mapOf("Authorization" to "Bearer token", "X-Tenant" to "acme"),
            onSuccess = { latch.countDown() },
            onError = { latch.countDown() },
        )
        awaitLatch(latch)

        val headers = recorder.requests.single().headers
        assertEquals("Bearer token", headers["Authorization"])
        assertEquals("acme", headers["X-Tenant"])
        assertEquals("max-age=3600", headers["Cache-Control"])
    }

    @Test
    fun `custom headers are sent on the remote eval POST request`() {
        val recorder = RecordingEngine()
        val latch = CountDownLatch(1)

        dispatcher(recorder.engine).consumePOSTRequest(
            url = EVAL_URL,
            headers = mapOf("Authorization" to "Bearer t"),
            bodyParams = mapOf("attributes" to emptyMap<String, Any>()),
            onSuccess = { latch.countDown() },
            onError = { latch.countDown() },
        )
        awaitLatch(latch)

        val request = recorder.requests.single()
        assertEquals("Bearer t", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
    }

    @Test
    fun `custom headers cannot override SDK managed headers`() {
        val recorder = RecordingEngine()
        val latch = CountDownLatch(1)

        dispatcher(recorder.engine).consumeGETRequest(
            request = FEATURES_URL,
            headers = mapOf(
                "Cache-Control" to "no-store",
                "If-None-Match" to "\"forged\"",
                // Not SDK-managed: the SDK sets no User-Agent on this request.
                "user-agent" to "acme-mobile/1.0",
                // Dropped by GBRequestHeaders.sanitize — Ktor would otherwise throw
                // IllegalHeaderValueException, quoting the value back in the message.
                "X-Bad" to "abc\ndef",
                "X-Kept" to "kept",
            ),
            onSuccess = { latch.countDown() },
            onError = { latch.countDown() },
        )
        awaitLatch(latch)

        val headers = recorder.requests.single().headers
        // Exactly one Cache-Control value, and it is the SDK's.
        assertEquals(listOf("max-age=3600"), headers.getAll("Cache-Control"))
        assertNull(headers["If-None-Match"])
        assertNull(headers["X-Bad"])
        assertEquals("acme-mobile/1.0", headers["User-Agent"])
        assertEquals("kept", headers["X-Kept"])
    }

    @Test
    fun `ETag revalidation still works alongside custom headers`() {
        val recorder = RecordingEngine(etag = "\"v1\"")
        val dispatcher = dispatcher(recorder.engine)
        val headers = mapOf("Authorization" to "Bearer token")

        repeat(2) {
            val latch = CountDownLatch(1)
            dispatcher.consumeGETRequestWithNotModified(
                request = FEATURES_URL,
                headers = headers,
                onSuccess = { latch.countDown() },
                onError = { latch.countDown() },
                onNotModified = { latch.countDown() },
            )
            awaitLatch(latch)
        }

        val second = recorder.requests[1].headers
        assertEquals(listOf("\"v1\""), second.getAll("If-None-Match"))
        assertEquals("Bearer token", second["Authorization"])
    }

    @Test
    fun `streaming headers are applied to the SSE request`() {
        val recorder = RecordingEngine()
        val controller = SSEConnectionController()
        val flow = dispatcher(recorder.engine).consumeSSEConnection(
            url = SSE_URL,
            headers = mapOf("Authorization" to "Bearer stream-token"),
            sseController = controller,
        )

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            scope.launch { flow.collect { } }
            // The flow closes immediately unless the controller is ACTIVE.
            controller.start()

            assertTrue(
                "SSE request was never made",
                recorder.requestReceived.await(5, TimeUnit.SECONDS),
            )
            assertEquals("Bearer stream-token", recorder.requests.first().headers["Authorization"])
        } finally {
            controller.stop()
            scope.cancel()
        }
    }

    @Test
    fun `header-less overloads keep working`() {
        val recorder = RecordingEngine()
        val latch = CountDownLatch(1)

        dispatcher(recorder.engine).consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = { latch.countDown() },
            onError = { latch.countDown() },
        )
        awaitLatch(latch)

        assertEquals("max-age=3600", recorder.requests.single().headers["Cache-Control"])
    }
}
