package com.sdk.growthbook

import com.sdk.growthbook.network.DefaultGBNetworkDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.junit.Test
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertTrue

private const val FEATURES_ENDPOINT = "/api/features/"

class DefaultGBNetworkDispatcherTest {
    private val classUnderTest: DefaultGBNetworkDispatcher

    init {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel("some content"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        classUnderTest = DefaultGBNetworkDispatcher(
            HttpClient(mockEngine)
        )
    }

    @Test
    fun `test successful get request`() {
        var wasOnSuccessCalled = false

        val job = classUnderTest.consumeGETRequest(
            request = FEATURES_ENDPOINT,
            onSuccess = { _ ->
                wasOnSuccessCalled = true
            },
            onError = {},
        )

        runBlocking {
            job.join()
        }

        assertTrue(wasOnSuccessCalled)
    }

    @Test
    fun `test failed get request`() {
        var wasOnErrorCalled = false

        val job = classUnderTest.consumeGETRequest(
            request = FEATURES_ENDPOINT,
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
}
