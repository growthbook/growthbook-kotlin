package com.sdk.growthbook.utils

/**
 * Wrapper for SSE Response result depending on if data present
 * or end or if filed value unnecessary or invalid
 */
internal sealed interface SseResponse {

    /**
     * Child wrapper for response if data present
     */
    data class Data(val value: String) : SseResponse

    /**
     * Child wrapper for response if field in data unnecessary or invalid
     */
    data class UnNecessaryOrInvalidField(val field: String, val value: String?): SseResponse

    /**
     * Child wrapper for response if data end or absent
     */
    object End : SseResponse
}
