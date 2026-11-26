package com.sdk.growthbook.network

import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Only an empty heartbeat, then the server closes the stream (proxy `maxStreamDuration`). */
private const val HEARTBEAT_ONLY_STREAM = "data: \n\n"

class GBNetworkDispatcherOkHttpSseRetryTest {

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

    private fun respondAlways(response: () -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = response()
        }
    }

    private fun dispatcher(maxRetries: Int) = GBNetworkDispatcherOkHttp(
        maxRetries = maxRetries,
        initialRetryDelayMs = 1L,
        maxRetryDelayMs = 1L,
    )

    private val sseUrl get() = server.url("/sub/my-key").toString()

    @Test
    fun `healthy connections closed by the server never exhaust the retry budget`() {
        respondAlways {
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(HEARTBEAT_ONLY_STREAM)
        }
        val maxRetries = 3
        val controller = SSEConnectionController().apply { start() }

        // Before the fix, every server-side close consumed a retry because only a non-empty
        // event reset the counter, so the flow gave up (and completed) after maxRetries + 1
        // connections. A live stream never completes, so the timeout must win.
        val completedWith = runBlocking {
            withTimeoutOrNull(3_000L) {
                dispatcher(maxRetries).consumeSSEConnection(sseUrl, controller).toList()
            }
        }
        controller.stop()

        assertNull("SSE stream gave up on healthy connections: $completedWith", completedWith)
        assertTrue(
            "expected reconnects past maxRetries, got ${server.requestCount}",
            server.requestCount > maxRetries + 1,
        )
    }

    @Test
    fun `non-2xx responses still count towards the retry budget`() {
        respondAlways { MockResponse().setResponseCode(503) }
        val maxRetries = 2
        val controller = SSEConnectionController().apply { start() }

        // Completing (rather than timing out) proves the stream gave up.
        runBlocking {
            withTimeout(5_000L) {
                dispatcher(maxRetries).consumeSSEConnection(sseUrl, controller).toList()
            }
        }
        controller.stop()

        assertEquals("expected exactly maxRetries + 1 attempts", maxRetries + 1, server.requestCount)
    }
}
