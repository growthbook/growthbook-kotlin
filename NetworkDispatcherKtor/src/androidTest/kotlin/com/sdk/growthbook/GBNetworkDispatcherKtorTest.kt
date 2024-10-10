package com.sdk.growthbook

import com.sdk.growthbook.network.GBNetworkDispatcherKtor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.Pair
import kotlin.test.assertEquals

private const val FEATURES_ENDPOINT = "https://some.domain/api/features/abc-client-123"

class GBNetworkDispatcherKtorTest {
    private val classUnderTest: GBNetworkDispatcherKtor

    init {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("some content"),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    Pair(HttpHeaders.ContentType, listOf("application/json")),
                    Pair(HttpHeaders.ETag, listOf("12345")) // Add ETag header
                )
            )
        }

        classUnderTest = GBNetworkDispatcherKtor(
            HttpClient(mockEngine)
        )
    }

    @Test
    fun `test successful get request`() {
        var wasOnSuccessCalled = false
        var eTagValue: String? = null

        val job = classUnderTest.consumeGETRequest(
            url = FEATURES_ENDPOINT,
            onSuccess = { _ ->
                wasOnSuccessCalled = true

                // Access the private eTagMap using reflection
                val eTagMapField = GBNetworkDispatcherKtor::class.java
                    .getDeclaredField("eTagMap")
                    .apply { isAccessible = true }

                val eTagMap = eTagMapField.get(classUnderTest) as Map<*, *>
                eTagValue = eTagMap[FEATURES_ENDPOINT]?.toString()
            },
            onError = {},
        )

        runBlocking {
            job.join()
        }

        assertTrue(wasOnSuccessCalled)
        assertEquals("12345", eTagValue)
    }

    @Test
    fun `test failed get request`() {
        var wasOnErrorCalled = false

        val job = classUnderTest.consumeGETRequest(
            url = FEATURES_ENDPOINT,
            onSuccess = {
                // typically in onSuccess callback JSON is parsed
                throw SerializationException()
            },
            onError = {
                wasOnErrorCalled = true
            },
        )

        runBlocking {
            job.join()
        }

        assertTrue(wasOnErrorCalled)
    }

    @Test
    fun `test If-None-Match header is sent in the subsequent requests`() = runBlocking {
        var firstRequest = true
        val mockEngine = MockEngine { request ->
            if (firstRequest) {
                firstRequest = false
                respond(
                    content = ByteReadChannel("some content"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        Pair(HttpHeaders.ContentType, listOf("application/json")),
                        Pair(HttpHeaders.ETag, listOf("98765")) // Add ETag header
                    )
                )
            } else {
                // Capture the If-None-Match header in the second request
                val ifNoneMatchHeader = request.headers[HttpHeaders.IfNoneMatch]
                assertEquals("98765", ifNoneMatchHeader) // Assert that it's the expected value
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.NotModified
                )
            }
        }

        val client = HttpClient(mockEngine)
        val networkDispatcher = GBNetworkDispatcherKtor(client)

        // First request to capture the ETag
        networkDispatcher.consumeGETRequest(
            url = FEATURES_ENDPOINT,
            onSuccess = { },
            onError = { throw it }
        ).join()

        // Second request to verify If-None-Match header is sent
        networkDispatcher.consumeGETRequest(
            url = FEATURES_ENDPOINT,
            onSuccess = { },
            onError = { throw it }
        ).join()
    }
}
