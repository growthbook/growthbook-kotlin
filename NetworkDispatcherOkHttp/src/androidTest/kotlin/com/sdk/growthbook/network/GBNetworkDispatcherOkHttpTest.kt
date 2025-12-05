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

    private fun getETagVal(url: String): String? {
        // Access the private eTagCache using reflection
        val eTagCacheField = GBNetworkDispatcherOkHttp::class.java
            .getDeclaredField("eTagCache")
            .apply { isAccessible = true }

        val eTagCache = eTagCacheField.get(networkDispatcher)
        val getMethod = eTagCache::class.java.getDeclaredMethod("get", String::class.java)
        return getMethod.invoke(eTagCache, url) as? String
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

        val endpoint = "/api/features/sdk1232"

        // Trigger the GET request in the networkDispatcher
        networkDispatcher.consumeGETRequest(
            mockWebServer.url(endpoint).toString(),
            onSuccess = { body ->
                // Assert that the eTag was set correctly
                assertEquals("12345", getETagVal(endpoint))

                // Assert that the response body matches the mocked response
                assertEquals("Test Response Body", body)
            },
            onError = { error -> throw error }
        ).join()
    }

    @Test
    fun `test If-None-Match header is set with eTag`() = runBlocking {
        val endpoint = "/api/features/sdk3452"

        // Prepare the mock response with an ETag for the first request
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("Test Response Body")
                .addHeader("ETag", "09876") // Mock the ETag header
        )

        // First GET request to capture the ETag
        networkDispatcher.consumeGETRequest(
            mockWebServer.url(endpoint).toString(),
            onSuccess = { body ->
                // Assert that the eTag was set correctly
                assertEquals("09876", getETagVal(endpoint))

                // Launch a coroutine to perform the second API call
                launch {
                    // Mock a second response (for the second request)
                    mockWebServer.enqueue(MockResponse().setResponseCode(304)) // Simulate 304 Not Modified

                    // Trigger the second GET request that should use If-None-Match header
                    networkDispatcher.consumeGETRequest(
                        mockWebServer.url(endpoint).toString(),
                        onSuccess = { body ->
                            // Capture the second request sent by the client
                            val recordedRequest = mockWebServer.takeRequest()
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

    //@Test
    fun `test consumeSSEConnection receives SSE events and and captures ETag`() = runBlocking {
        val endpoint = "/api/features/sdkcon231"


        // Prepare the mock response to simulate an SSE event
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("data: Test SSE Event\n\n")
                .setHeader("Content-Type", "text/event-stream")
                .setHeader("ETag", "12345") // Mock the ETag header
        )
        // Call the consumeSSEConnection method
        val sseFlow = networkDispatcher.consumeSSEConnection(mockWebServer.url(endpoint).toString())

        // Collect the first SSE event from the flow
        val sseEvent = sseFlow.first()

        // Extract the data from the Resource.Success object
        val eventData = if (sseEvent is Resource.Success) sseEvent.data else null

        // Assert that the ETag was captured
        assertEquals("12345", getETagVal(endpoint))

        // Assert that the SSE event was correctly received
        assertEquals("Test SSE Event", eventData)
    }
}