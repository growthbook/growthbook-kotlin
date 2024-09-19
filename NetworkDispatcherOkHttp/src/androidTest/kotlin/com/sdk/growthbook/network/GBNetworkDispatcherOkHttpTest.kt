package com.sdk.growthbook.network

import com.sdk.growthbook.utils.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GBNetworkDispatcherOkHttpTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var networkDispatcher: GBNetworkDispatcherOkHttp

    @Before
    fun setUp() {
        // Initialize MockWebServer
        mockWebServer = MockWebServer()

        networkDispatcher = GBNetworkDispatcherOkHttp()
        // Start MockWebServer
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        // Shutdown the MockWebServer after the test
        mockWebServer.shutdown()
    }

    @Test
    fun `test eTag is set after GET request`() = runBlocking {
        // Prepare the mock response with an ETag
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Test Response Body")
                .addHeader("ETag", "12345") // Mock the ETag header
        )

        // Trigger the GET request in the networkDispatcher
        networkDispatcher.consumeGETRequest(
            mockWebServer.url("/test").toString(),
            onSuccess = { body ->
                // Access the private eTag field using reflection
                val eTagValue = GBNetworkDispatcherOkHttp::class.java
                    .getDeclaredField("eTag")
                    .apply { isAccessible = true }
                    .get(networkDispatcher) as String?

                // Assert that the eTag was set correctly
                assertEquals("12345", eTagValue)

                // Assert that the response body matches the mocked response
                assertEquals("Test Response Body", body)
            },
            onError = { error -> throw error }
        ).join()
    }

    @Test
    fun `test If-None-Match header is set with eTag`() = runBlocking {
        // Prepare the mock response with an ETag for the first request
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Test Response Body")
                .addHeader("ETag", "09876") // Mock the ETag header
        )

        // First GET request to capture the ETag
        networkDispatcher.consumeGETRequest(
            mockWebServer.url("/test").toString(),
            onSuccess = { body ->
                // After this request, eTag should be set - Use reflection to access private field
                val eTagValue = GBNetworkDispatcherOkHttp::class.java
                    .getDeclaredField("eTag")
                    .apply { isAccessible = true }
                    .get(networkDispatcher) as String?

                // Assert that the eTag was set correctly
                assertEquals("09876", eTagValue)

                // Launch a coroutine to perform the second API call
                launch {
                    // Mock a second response (for the second request)
                    mockWebServer.enqueue(MockResponse().setResponseCode(304)) // Simulate 304 Not Modified

                    // Trigger the second GET request that should use If-None-Match header
                    networkDispatcher.consumeGETRequest(
                        mockWebServer.url("/test").toString(),
                        onSuccess = { body ->
                            // Capture the second request sent by the client
                            val recordedRequest = mockWebServer.takeRequest()
                            println(recordedRequest.headers)

                            // Assert that the If-None-Match header was sent with the correct eTag value
                            assertEquals("09876", recordedRequest.getHeader("If-None-Match"))
                        },
                        onError = { error -> throw error }
                    ).join()
                }

            },
            onError = { error -> throw error }
        ).join()
    }

    @Test
    fun `test consumeSSEConnection receives SSE events and and captures ETag`() = runBlocking {
        // Prepare the mock response to simulate an SSE event
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("data: Test SSE Event\n\n")
                .setHeader("Content-Type", "text/event-stream")
                .setHeader("ETag", "12345") // Mock the ETag header
        )
        // Call the consumeSSEConnection method
        val sseFlow = networkDispatcher.consumeSSEConnection(mockWebServer.url("/sse").toString())

        // Collect the first SSE event from the flow
        val sseEvent = sseFlow.first()

        // Extract the data from the Resource.Success object
        val eventData = if (sseEvent is Resource.Success) sseEvent.data else null

        // Access the private eTag field using reflection
        val eTagValue = GBNetworkDispatcherOkHttp::class.java
            .getDeclaredField("eTag")
            .apply { isAccessible = true }
            .get(networkDispatcher) as String?

        // Assert that the ETag was captured
        assertEquals("12345", eTagValue)

        // Assert that the SSE event was correctly received
        assertEquals("Test SSE Event", eventData)
    }
}