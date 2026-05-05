package com.sdk.growthbook.network

import kotlinx.serialization.json.JsonElement

interface TrackingNetworkDispatcher {
    fun consumePOSTRequest(
        url: String,
        headers: Map<String, String>,
        body: JsonElement,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    )
}
