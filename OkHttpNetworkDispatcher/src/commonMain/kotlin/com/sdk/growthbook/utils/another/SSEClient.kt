package com.sdk.growthbook.utils.another

import com.launchdarkly.eventsource.EventHandler
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.internal.closeQuietly
import java.net.URI
import java.time.Duration

class SSEClient {

    private var sseHandlers: SSEHandler? = null
    private var eventSourceSse: EventSource? = null

    fun initSse(sseHandler: SSEHandler, errorCallback: (Throwable) -> Unit) {
      
        this.sseHandlers = sseHandler
        val eventHandler = sseHandlers?.let { DefaultEventHandler(it) }
        val baseUrl = "https://api.example.com" 
        val PATH = "/q3/events" // Replace with the SSE endpoint path
        try {

                eventSourceSse = EventSource.Builder(
                    eventHandler, URI.create(baseUrl.plus(PATH))
                )
                    .connectTimeout(Duration.ofSeconds(3))
                    .backoffResetThreshold(Duration.ofSeconds(3))
                    .build()

                eventSourceSse?.let {
                    it.start()
                }

        } catch (e: Exception) {
            errorCallback(e)
        }
    }

    private class DefaultEventHandler(private val sseHandler: SSEHandler) : EventHandler {

        override fun onOpen() {
            sseHandler.onSSEConnectionOpened()
        }

        override fun onClosed() {
            sseHandler.onSSEConnectionClosed()
        }

        override fun onMessage(event: String, messageEvent: MessageEvent) {
           sseHandler.onSSEEventReceived(event,messageEvent)
        }

        override fun onError(t: Throwable) {
            sseHandler.onSSEError(t)
        }

        override fun onComment(comment: String) {
            println(comment)
        }
    }

    fun disconnect() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                eventSourceSse?.closeQuietly()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}