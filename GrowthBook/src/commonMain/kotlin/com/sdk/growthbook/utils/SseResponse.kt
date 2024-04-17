package com.sdk.growthbook.utils

internal sealed interface SseResponse {
    data class Data(val value: String) : SseResponse
    data class UnNecessaryOrInvalidField(val field: String, val value: String?): SseResponse
    object End : SseResponse
}
