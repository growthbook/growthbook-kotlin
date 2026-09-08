package com.sdk.growthbook

import com.sdk.growthbook.network.GBNetworkDispatcherKtor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig.Companion.INFINITE_TIMEOUT_MS
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val FEATURES_URL = "http://example.com/api/features/my-key"
private const val RESPONSE_BODY = """{"features":{}}"""

/**
 * Covers `fetchTimeoutMillis`: the default client's request/socket timeouts are infinite for
 * the SSE stream's sake, so without the per-request bound a stalled feature fetch hangs forever.
 */
class GBNetworkDispatcherKtorTimeoutTest {

    /** Engine that stalls for [stallMillis] before responding — a hung CDN in miniature. */
    private fun stalledEngine(stallMillis: Long): MockEngine = MockEngine {
        delay(stallMillis)
        respond(
            content = ByteReadChannel(RESPONSE_BODY),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    /** Client with the same infinite `HttpTimeout` config as `createDefaultHttpClient()`. */
    private fun clientWith(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                socketTimeoutMillis = INFINITE_TIMEOUT_MS
                requestTimeoutMillis = INFINITE_TIMEOUT_MS
            }
        }

    @Test
    fun `stalled fetch fails at fetchTimeout despite the client's infinite timeouts`() {
        var error: Throwable? = null
        val dispatcher = GBNetworkDispatcherKtor(
            client = clientWith(stalledEngine(stallMillis = 60_000)),
            fetchTimeoutMillis = 200,
        )

        val job = dispatcher.consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = {},
            onError = { error = it },
        )
        runBlocking { job.join() }

        assertTrue(
            "expected HttpRequestTimeoutException, got $error",
            error is HttpRequestTimeoutException
        )
    }

    @Test
    fun `fetch slower than the old OkHttp-style tight default still succeeds within fetchTimeout`() {
        var error: Throwable? = null
        var body: String? = null
        val dispatcher = GBNetworkDispatcherKtor(
            client = clientWith(stalledEngine(stallMillis = 300)),
            fetchTimeoutMillis = 10_000,
        )

        val job = dispatcher.consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = { body = it },
            onError = { error = it },
        )
        runBlocking { job.join() }

        assertNull(error)
        assertTrue(body == RESPONSE_BODY)
    }

    @Test
    fun `null fetchTimeout opts out and leaves the client's own configuration`() {
        var error: Throwable? = null
        var succeeded = false
        val dispatcher = GBNetworkDispatcherKtor(
            client = clientWith(stalledEngine(stallMillis = 700)),
            fetchTimeoutMillis = null,
        )

        val job = dispatcher.consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = { succeeded = true },
            onError = { error = it },
        )
        runBlocking { job.join() }

        assertNull(error)
        assertTrue(succeeded)
    }

    @Test
    fun `stalled remote-eval POST is bounded too`() {
        var error: Throwable? = null
        val latch = CountDownLatch(1)
        val dispatcher = GBNetworkDispatcherKtor(
            client = clientWith(stalledEngine(stallMillis = 60_000)),
            fetchTimeoutMillis = 200,
        )

        dispatcher.consumePOSTRequest(
            url = FEATURES_URL,
            bodyParams = mapOf("attributes" to emptyMap<String, Any>()),
            onSuccess = { latch.countDown() },
            onError = { error = it; latch.countDown() },
        )

        assertTrue("POST never called back", latch.await(5, TimeUnit.SECONDS))
        assertTrue(
            "expected HttpRequestTimeoutException, got $error",
            error is HttpRequestTimeoutException
        )
    }
}
