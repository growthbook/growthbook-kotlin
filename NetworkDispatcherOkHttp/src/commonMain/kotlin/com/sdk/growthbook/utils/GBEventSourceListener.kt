package com.sdk.growthbook.utils

import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

class GBEventSourceListener(
    private val handler: GBEventSourceHandler,
    private val enableLogging: Boolean,
) : EventSourceListener() {

    override fun onOpen(eventSource: EventSource, response: Response) {
        super.onOpen(eventSource, response)
        if (enableLogging) {
            println("GrowthBook SSE: Connection opened, status: ${response.code}")
        }
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        super.onEvent(eventSource, id, type, data)

        if (enableLogging) {
            println("GrowthBook SSE: Received event - ID: $id, Type: $type, Data length: ${data.length}")
        }

        if (data.trim().isEmpty()) {
            if (enableLogging) {
                println("GrowthBook SSE: Empty data received, ignoring")
            }
            return
        }
        try {
            handler.onFeaturesResponse(data)
        } catch (e: Exception) {
            if (enableLogging) {
                println("GrowthBook SSE: Error processing features response: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onClosed(eventSource: EventSource) {
        super.onClosed(eventSource)
        if (enableLogging) {
            println("GrowthBook SSE: Connection closed")
        }
        handler.onClose(eventSource)
    }

    override fun onFailure(
        eventSource: EventSource,
        t: Throwable?,
        response: Response?
    ) {
        super.onFailure(eventSource, t, response)
        if (enableLogging) {
            println("GrowthBook SSE: Connection failed")
            println("Response code: ${response?.code}")
            println("Error: ${t?.message}")
            t?.printStackTrace()
        }
        handler.onClose(eventSource)
    }
}
