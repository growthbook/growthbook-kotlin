package com.sdk.growthbook.utils

import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener

class GBEventSourceListener(private val handler: GBEventSourceHandler): EventSourceListener() {
    override fun onClosed(eventSource: EventSource) {
        super.onClosed(eventSource)
        handler.onClose(eventSource)
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        super.onEvent(eventSource, id, type, data)
        if (data.trim().isEmpty()) {
            return
        }
        try {
            handler.onFeaturesResponse(data)
        } catch (e: Exception) {
            println(e)
        }
    }
}