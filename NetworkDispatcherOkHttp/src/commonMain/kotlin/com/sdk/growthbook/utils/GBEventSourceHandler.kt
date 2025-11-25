package com.sdk.growthbook.utils

import okhttp3.sse.EventSource

/**
 * Interface of handling SSE events
 */
interface GBEventSourceHandler {
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
    fun onFailure(eventSource: EventSource?, error: Throwable?, errorType: SSEErrorType)
}
