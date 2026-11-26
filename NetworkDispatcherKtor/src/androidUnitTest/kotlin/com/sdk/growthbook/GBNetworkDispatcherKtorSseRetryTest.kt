package com.sdk.growthbook

import com.sdk.growthbook.network.GBNetworkDispatcherKtor
import com.sdk.growthbook.utils.SSEConnectionController
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val SSE_URL = "http://example.com/sub/my-key"

/** Only an empty heartbeat, then the server closes the stream (proxy `maxStreamDuration`). */
private const val HEARTBEAT_ONLY_STREAM = "data: \n\n"

class GBNetworkDispatcherKtorSseRetryTest {

    private fun dispatcherWith(engine: MockEngine, maxRetries: Int) = GBNetworkDispatcherKtor(
        client = HttpClient(engine),
        maxRetries = maxRetries,
        initialRetryDelayMs = 1L,
        maxRetryDelayMs = 1L,
    )

    @Test
    fun `healthy connections closed by the server never exhaust the retry budget`() {
        val connections = AtomicInteger(0)
        val engine = MockEngine {
            connections.incrementAndGet()
            respond(
                content = ByteReadChannel(HEARTBEAT_ONLY_STREAM),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
        }
        val maxRetries = 3
        val controller = SSEConnectionController().apply { start() }

        // Before the fix, every server-side close consumed a retry because only a non-empty
        // event reset the counter, so the flow gave up (and completed) after maxRetries + 1
        // connections. A live stream never completes, so the timeout must win.
        val completedWith = runBlocking {
            withTimeoutOrNull(2_000L) {
                dispatcherWith(engine, maxRetries)
                    .consumeSSEConnection(SSE_URL, controller)
                    .toList()
            }
        }
        controller.stop()

        assertNull("SSE stream gave up on healthy connections: $completedWith", completedWith)
        assertTrue(
            "expected reconnects past maxRetries, got ${connections.get()}",
            connections.get() > maxRetries + 1,
        )
    }

    @Test
    fun `non-2xx responses still count towards the retry budget`() {
        val connections = AtomicInteger(0)
        val engine = MockEngine {
            connections.incrementAndGet()
            respond(content = ByteReadChannel(""), status = HttpStatusCode.ServiceUnavailable)
        }
        val maxRetries = 2
        val controller = SSEConnectionController().apply { start() }

        // Completing (rather than timing out) proves the stream gave up.
        runBlocking {
            withTimeout(3_000L) {
                dispatcherWith(engine, maxRetries)
                    .consumeSSEConnection(SSE_URL, controller)
                    .toList()
            }
        }
        controller.stop()

        assertEquals(
            "expected exactly maxRetries + 1 attempts",
            maxRetries + 1,
            connections.get(),
        )
    }
}
