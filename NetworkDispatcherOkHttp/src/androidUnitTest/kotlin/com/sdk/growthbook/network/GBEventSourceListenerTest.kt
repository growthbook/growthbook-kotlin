package com.sdk.growthbook.network

import com.sdk.growthbook.utils.GBEventSourceHandler
import com.sdk.growthbook.utils.GBEventSourceListener
import okhttp3.Request
import okhttp3.sse.EventSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private val fakeEventSource: EventSource = object : EventSource {
    override fun request(): Request = Request.Builder().url("http://localhost/").build()
    override fun cancel() {}
}

/**
 * Tests for [GBEventSourceListener] — verifies routing of SSE events
 * to [GBEventSourceHandler] callbacks.
 */
class GBEventSourceListenerTest {

    // -------------------------------------------------------------------------
    // Fake handler that records calls
    // -------------------------------------------------------------------------

    private class RecordingHandler : GBEventSourceHandler {
        val closeCalls = mutableListOf<EventSource?>()
        val featureResponses = mutableListOf<String?>()
        val failures = mutableListOf<Pair<EventSource?, Throwable?>>()

        override fun onClose(eventSource: EventSource?) {
            closeCalls += eventSource
        }

        override fun onFeaturesResponse(featuresJsonResponse: String?) {
            featureResponses += featuresJsonResponse
        }

        override fun onFailure(eventSource: EventSource?, error: Throwable?) {
            failures += eventSource to error
        }
    }

    private fun listenerWith(handler: RecordingHandler, logging: Boolean = false) =
        GBEventSourceListener(handler, logging)

    // -------------------------------------------------------------------------
    // onEvent — data routing
    // -------------------------------------------------------------------------

    @Test
    fun `onEvent with valid data calls onFeaturesResponse`() {
        val handler = RecordingHandler()
        val listener = listenerWith(handler)
        val json = """{"features":{}}"""

        listener.onEvent(fakeEventSource, id = null, type = null, data = json)

        assertEquals(1, handler.featureResponses.size)
        assertEquals(json, handler.featureResponses[0])
    }

    @Test
    fun `onEvent with blank data does NOT call onFeaturesResponse`() {
        val handler = RecordingHandler()
        val listener = listenerWith(handler)

        listener.onEvent(fakeEventSource, id = null, type = null, data = "   ")

        assertTrue(handler.featureResponses.isEmpty())
    }

    @Test
    fun `onEvent with empty string does NOT call onFeaturesResponse`() {
        val handler = RecordingHandler()
        val listener = listenerWith(handler)

        listener.onEvent(fakeEventSource, id = null, type = null, data = "")

        assertTrue(handler.featureResponses.isEmpty())
    }

    @Test
    fun `onEvent exception is caught and does not propagate`() {
        val crashingHandler = object : GBEventSourceHandler {
            override fun onClose(eventSource: EventSource?) {}
            override fun onFeaturesResponse(featuresJsonResponse: String?) {
                throw RuntimeException("handler crash")
            }
            override fun onFailure(eventSource: EventSource?, error: Throwable?) {}
        }
        val listener = GBEventSourceListener(crashingHandler, enableLogging = false)

        // Should not throw
        listener.onEvent(fakeEventSource, id = null, type = null, data = """{"x":1}""")
    }

    // -------------------------------------------------------------------------
    // onClosed
    // -------------------------------------------------------------------------

    @Test
    fun `onClosed calls handler onClose`() {
        val handler = RecordingHandler()
        val listener = listenerWith(handler)

        listener.onClosed(fakeEventSource)

        assertEquals(1, handler.closeCalls.size)
    }

    // -------------------------------------------------------------------------
    // onFailure
    // -------------------------------------------------------------------------

    @Test
    fun `onFailure calls handler onFailure with error`() {
        val handler = RecordingHandler()
        val listener = listenerWith(handler)
        val error = IOException("connection lost")

        listener.onFailure(fakeEventSource, error, response = null)

        assertEquals(1, handler.failures.size)
        assertEquals(error, handler.failures[0].second)
    }

    @Test
    fun `onFailure with null error calls handler onFailure`() {
        val handler = RecordingHandler()
        val listener = listenerWith(handler)

        listener.onFailure(fakeEventSource, t = null, response = null)

        assertEquals(1, handler.failures.size)
        assertNull(handler.failures[0].second)
    }
}
