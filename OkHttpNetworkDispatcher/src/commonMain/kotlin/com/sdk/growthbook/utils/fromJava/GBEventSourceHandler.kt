package com.sdk.growthbook.utils.fromJava

import okhttp3.sse.EventSource

interface GBEventSourceHandler {
    fun onClose(eventSource: EventSource?)

    fun onFeaturesResponse(featuresJsonResponse: String?)
}
