package com.sdk.growthbook.network

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val RESPONSE_BODY = """{"features":{}}"""

class GBNetworkDispatcherOkHttpTest {

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

    // Resolves a path against the running MockWebServer
    private fun url(path: String) = server.url(path).toString()

    private fun featuresUrl() = url("/api/features/my-key")
    private fun otherUrl() = url("/api/other/data")

    /** Runs a GET and waits for either callback to fire (max 5 s). */
    private fun getSync(
        url: String,
        onSuccess: (String) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val latch = CountDownLatch(1)
        dispatcher.consumeGETRequest(
            request = url,
            onSuccess = { onSuccess(it); latch.countDown() },
            onError = { onError(it); latch.countDown() },
        )
        assertTrue("GET timed out", latch.await(5, TimeUnit.SECONDS))
    }

    /** Runs a POST and waits for either callback to fire (max 5 s). */
    private fun postSync(
        url: String,
        body: Map<String, Any> = emptyMap(),
        onSuccess: (String) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val latch = CountDownLatch(1)
        dispatcher.consumePOSTRequest(
            url = url,
            bodyParams = body,
            onSuccess = { onSuccess(it); latch.countDown() },
            onError = { onError(it); latch.countDown() },
        )
        assertTrue("POST timed out", latch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `GET 200 calls onSuccess with response body`() {
        server.enqueue(MockResponse().setBody(RESPONSE_BODY).setResponseCode(200))

        var received: String? = null
        getSync(featuresUrl(), onSuccess = { received = it })

        assertEquals(RESPONSE_BODY, received)
    }

    @Test
    fun `GET 201 calls onSuccess`() {
        server.enqueue(MockResponse().setBody(RESPONSE_BODY).setResponseCode(201))

        var successCalled = false
        getSync(otherUrl(), onSuccess = { successCalled = true })

        assertTrue(successCalled)
    }

    @Test
    fun `GET 304 calls neither onSuccess nor onError`() {
        // MockWebServer: for 304 we need a plain enqueue — no latch helper here
        server.enqueue(MockResponse().setResponseCode(304))

        val latch = CountDownLatch(1)
        var successCalled = false
        var errorCalled = false

        dispatcher.consumeGETRequest(
            request = featuresUrl(),
            onSuccess = { successCalled = true; latch.countDown() },
            onError = { errorCalled = true; latch.countDown() },
        )

        // 304 path fires neither callback — latch should NOT count down
        assertFalse("onSuccess must not be called on 304", latch.await(1, TimeUnit.SECONDS))
        assertFalse(successCalled)
        assertFalse(errorCalled)
    }

    @Test
    fun `GET 400 calls onError`() {
        server.enqueue(MockResponse().setResponseCode(400))

        var errorCalled = false
        getSync(featuresUrl(), onError = { errorCalled = true })

        assertTrue(errorCalled)
    }

    @Test
    fun `GET 500 calls onError`() {
        server.enqueue(MockResponse().setResponseCode(500))

        var errorCalled = false
        getSync(featuresUrl(), onError = { errorCalled = true })

        assertTrue(errorCalled)
    }

    @Test
    fun `GET network failure calls onError`() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))

        var error: Throwable? = null
        getSync(featuresUrl(), onError = { error = it })

        assertNotNull(error)
    }

    @Test
    fun `GET always adds Cache-Control max-age=3600 header`() {
        server.enqueue(MockResponse().setBody(RESPONSE_BODY))

        getSync(otherUrl())

        val request = server.takeRequest()
        assertEquals("max-age=3600", request.getHeader("Cache-Control"))
    }

    @Test
    fun `first GET to features URL sends no If-None-Match`() {
        server.enqueue(MockResponse().setBody(RESPONSE_BODY).addHeader("ETag", "\"v1\""))

        getSync(featuresUrl())

        val request = server.takeRequest()
        assertNull(request.getHeader("If-None-Match"))
    }

    @Test
    fun `second GET to features URL sends If-None-Match from cached ETag`() {
        server.enqueue(MockResponse().setBody(RESPONSE_BODY).addHeader("ETag", "\"v1\""))
        server.enqueue(MockResponse().setResponseCode(304))

        getSync(featuresUrl())     // first — stores ETag, no latch for 304 second call
        // For the 304 second request we drive it manually
        val latch = CountDownLatch(1)
        dispatcher.consumeGETRequest(featuresUrl(), {}, { latch.countDown() })
        latch.await(2, TimeUnit.SECONDS) // 304 fires no callback, so we just wait briefly

        server.takeRequest() // discard first request
        val second = server.takeRequest()
        assertEquals("\"v1\"", second.getHeader("If-None-Match"))
    }

    @Test
    fun `ETag is NOT cached for non-features URL`() {
        server.enqueue(MockResponse().setBody(RESPONSE_BODY).addHeader("ETag", "\"v1\""))
        server.enqueue(MockResponse().setBody(RESPONSE_BODY))

        getSync(otherUrl())
        getSync(otherUrl())

        server.takeRequest()
        val second = server.takeRequest()
        assertNull(second.getHeader("If-None-Match"))
    }

    @Test
    fun `POST 200 calls onSuccess with response body`() {
        server.enqueue(MockResponse().setBody("""{"ok":true}""").setResponseCode(200))

        var received: String? = null
        postSync(
            url = otherUrl(),
            body = mapOf("key" to "value"),
            onSuccess = { received = it },
        )

        assertNotNull(received)
        assertTrue(received!!.contains("ok"))
    }

    @Test
    fun `POST 201 calls onSuccess`() {
        server.enqueue(MockResponse().setBody("""{"created":true}""").setResponseCode(201))

        var successCalled = false
        postSync(otherUrl(), onSuccess = { successCalled = true })

        assertTrue(successCalled)
    }

    @Test
    fun `POST 400 calls onError`() {
        server.enqueue(MockResponse().setResponseCode(400))

        var errorCalled = false
        postSync(otherUrl(), onError = { errorCalled = true })

        assertTrue(errorCalled)
    }

    @Test
    fun `POST 500 calls onError`() {
        server.enqueue(MockResponse().setResponseCode(500))

        var errorCalled = false
        postSync(otherUrl(), onError = { errorCalled = true })

        assertTrue(errorCalled)
    }

    @Test
    fun `POST network failure calls onError`() {
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START)
        )

        var error: Throwable? = null
        postSync(otherUrl(), onError = { error = it })

        assertNotNull(error)
    }

    @Test
    fun `POST sends Content-Type and Accept headers`() {
        server.enqueue(MockResponse().setBody("{}").setResponseCode(200))

        postSync(otherUrl(), body = mapOf("x" to 1))

        val request = server.takeRequest()
        assertTrue(request.getHeader("Content-Type")!!.contains("application/json"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun `POST body is serialized to JSON`() {
        server.enqueue(MockResponse().setBody("{}").setResponseCode(200))

        postSync(
            url = otherUrl(),
            body = mapOf("name" to "test", "value" to 42),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"name\""))
        assertTrue(requestBody.contains("\"test\""))
        assertTrue(requestBody.contains("42"))
    }
}
