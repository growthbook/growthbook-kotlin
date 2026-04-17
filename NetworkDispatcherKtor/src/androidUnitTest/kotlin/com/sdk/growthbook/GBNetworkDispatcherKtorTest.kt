package com.sdk.growthbook

import com.sdk.growthbook.network.GBNetworkDispatcherKtor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val FEATURES_URL = "http://example.com/api/features/my-key"
private const val NON_FEATURES_URL = "http://example.com/api/other/data"
private const val RESPONSE_BODY = """{"features":{}}"""

class GBNetworkDispatcherKtorTest {

    private fun dispatcherWith(engine: MockEngine) =
        GBNetworkDispatcherKtor(HttpClient(engine))

    /** Client with ContentNegotiation — required for POST with JsonElement body. */
    private fun postDispatcherWith(engine: MockEngine) = GBNetworkDispatcherKtor(
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { isLenient = true; ignoreUnknownKeys = true })
            }
        }
    )

    private fun okEngine(body: String = RESPONSE_BODY): MockEngine = MockEngine {
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    @Test
    fun `test successful get request`() {
        var wasOnSuccessCalled = false

        val job = dispatcherWith(okEngine()).consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = { wasOnSuccessCalled = true },
            onError = {},
        )
        runBlocking { job.join() }

        assertTrue(wasOnSuccessCalled)
    }

    @Test
    fun `test failed get request`() {
        var wasOnErrorCalled = false

        val job = dispatcherWith(okEngine()).consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = { throw SerializationException() },
            onError = { wasOnErrorCalled = true },
        )
        runBlocking { job.join() }

        assertTrue(wasOnErrorCalled)
    }

    @Test
    fun `304 Not Modified does not call onSuccess or onError`() {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NotModified,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        var successCalled = false
        var errorCalled = false

        val job = dispatcherWith(engine).consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = { successCalled = true },
            onError = { errorCalled = true },
        )
        runBlocking { job.join() }

        assertFalse(successCalled)
        assertFalse(errorCalled)
    }

    @Test
    fun `4xx response calls onError`() {
        val engine = MockEngine {
            respondError(HttpStatusCode.BadRequest)
        }
        var errorCalled = false

        val job = dispatcherWith(engine).consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = {},
            onError = { errorCalled = true },
        )
        runBlocking { job.join() }

        assertTrue(errorCalled)
    }

    @Test
    fun `5xx response calls onError`() {
        val engine = MockEngine {
            respondError(HttpStatusCode.InternalServerError)
        }
        var errorCalled = false

        val job = dispatcherWith(engine).consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = {},
            onError = { errorCalled = true },
        )
        runBlocking { job.join() }

        assertTrue(errorCalled)
    }

    @Test
    fun `onSuccess receives the response body text`() {
        val engine = okEngine(body = RESPONSE_BODY)
        var receivedBody: String? = null

        val job = dispatcherWith(engine).consumeGETRequest(
            request = FEATURES_URL,
            onSuccess = { receivedBody = it },
            onError = {},
        )
        runBlocking { job.join() }

        assertEquals(RESPONSE_BODY, receivedBody)
    }

    @Test
    fun `ETag is stored after 200 response for features URL`() {
        val receivedHeaders = mutableListOf<io.ktor.http.Headers>()
        val engine = MockEngine { request ->
            receivedHeaders += request.headers
            respond(
                content = ByteReadChannel(RESPONSE_BODY),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ETag to listOf("\"abc123\"")
                )
            )
        }
        val dispatcher = dispatcherWith(engine)

        runBlocking { dispatcher.consumeGETRequest(FEATURES_URL, {}, {}).join() }
        assertNull(receivedHeaders[0][HttpHeaders.IfNoneMatch])

        runBlocking { dispatcher.consumeGETRequest(FEATURES_URL, {}, {}).join() }
        assertEquals("\"abc123\"", receivedHeaders[1][HttpHeaders.IfNoneMatch])
    }

    @Test
    fun `ETag is NOT stored for non-features URL`() {
        val receivedHeaders = mutableListOf<io.ktor.http.Headers>()
        val engine = MockEngine { request ->
            receivedHeaders += request.headers
            respond(
                content = ByteReadChannel(RESPONSE_BODY),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.ETag to listOf("\"abc123\"")
                )
            )
        }
        val dispatcher = dispatcherWith(engine)

        runBlocking { dispatcher.consumeGETRequest(NON_FEATURES_URL, {}, {}).join() }
        runBlocking { dispatcher.consumeGETRequest(NON_FEATURES_URL, {}, {}).join() }

        assertNull(receivedHeaders[1][HttpHeaders.IfNoneMatch])
    }

    @Test
    fun `Cache-Control header is added for features URL`() {
        val receivedHeaders = mutableListOf<io.ktor.http.Headers>()
        val engine = MockEngine { request ->
            receivedHeaders += request.headers
            respond(
                content = ByteReadChannel(RESPONSE_BODY),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        runBlocking {
            dispatcherWith(engine).consumeGETRequest(FEATURES_URL, {}, {}).join()
        }

        assertEquals("max-age=3600", receivedHeaders[0][HttpHeaders.CacheControl])
    }

    @Test
    fun `Cache-Control header is NOT added for non-features URL`() {
        val receivedHeaders = mutableListOf<io.ktor.http.Headers>()
        val engine = MockEngine { request ->
            receivedHeaders += request.headers
            respond(
                content = ByteReadChannel(RESPONSE_BODY),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        runBlocking {
            dispatcherWith(engine).consumeGETRequest(NON_FEATURES_URL, {}, {}).join()
        }

        assertNull(receivedHeaders[0][HttpHeaders.CacheControl])
    }

    @Test
    fun `POST 200 calls onSuccess with response body`() {
        val latch = CountDownLatch(1)
        var receivedBody: String? = null

        postDispatcherWith(MockEngine {
            respond(
                content = ByteReadChannel("""{"result":"ok"}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }).consumePOSTRequest(
            url = NON_FEATURES_URL,
            bodyParams = mapOf("key" to "value"),
            onSuccess = { receivedBody = it; latch.countDown() },
            onError = { latch.countDown() },
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(receivedBody)
        assertTrue(receivedBody!!.contains("ok"))
    }

    @Test
    fun `POST 201 calls onSuccess`() {
        val latch = CountDownLatch(1)
        var successCalled = false

        postDispatcherWith(MockEngine {
            respond(
                content = ByteReadChannel("""{"created":true}"""),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }).consumePOSTRequest(
            url = NON_FEATURES_URL,
            bodyParams = mapOf("x" to 1),
            onSuccess = { successCalled = true; latch.countDown() },
            onError = { latch.countDown() },
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(successCalled)
    }

    @Test
    fun `POST 400 calls onError with status info in message`() {
        val latch = CountDownLatch(1)
        var errorMessage: String? = null

        postDispatcherWith(MockEngine {
            respond(
                content = ByteReadChannel("bad request"),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }).consumePOSTRequest(
            url = NON_FEATURES_URL,
            bodyParams = emptyMap(),
            onSuccess = { latch.countDown() },
            onError = { errorMessage = it.message; latch.countDown() },
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertNotNull(errorMessage)
        assertTrue(errorMessage!!.contains("400"))
    }
}
