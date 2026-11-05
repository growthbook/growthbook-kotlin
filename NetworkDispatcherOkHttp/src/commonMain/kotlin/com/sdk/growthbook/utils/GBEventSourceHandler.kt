package com.sdk.growthbook.utils

import okhttp3.sse.EventSource

/**
 * Interface of handling SSE events
 */
interface GBEventSourceHandler {
    /**
     * Invoke when the stream has been opened successfully (2xx, `text/event-stream`).
     *
     * Has a default no-op body so existing implementations keep compiling.
     */
    fun onOpen() {}

    /**
     * Invoke when connection is closed
     */
    fun onClose(eventSource: EventSource?)

    /**
     * Invoke when fetching features from response
     */
    fun onFeaturesResponse(featuresJsonResponse: String?)

    /**
     * Invoke when error occur
     */
    fun onFailure(eventSource: EventSource?, error: Throwable?)
}
