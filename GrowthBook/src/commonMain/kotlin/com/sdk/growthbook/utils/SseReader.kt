package com.sdk.growthbook.utils

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

/**
 * Extension method for ByteReadChannel that read SSE response data,
 * validate it and send it to onSseEvent callback
 */
internal suspend fun ByteReadChannel.readSse(
    onSseEvent: (Resource<String>) -> (Unit)
) {
    var data: String? = null

    while (!isClosedForRead) {
        parseSseLine(
            line = readUTF8Line(),
            onSseResponse = { sseRawEvent ->
                when (sseRawEvent) {
                    SseResponse.End -> {
                        if (!data.isNullOrEmpty()) {
                            onSseEvent(Resource.Success(data ?: ""))
                            data = null
                        }
                    }

                    is SseResponse.Data -> {
                        if (sseRawEvent.value.isNotEmpty()) {
                            data = sseRawEvent.value
                        }
                    }

                    else -> {
                        // unnecessary or invalid field handling
                    }
                }
            }
        )
    }
}

/**
 * A helper method to parse SSE string line into a field and value.
 * Then depending on the value of the field - wrapped it
 * to SSeResponse and send it to the onSseResponse callback
 */
private fun parseSseLine(
    line: String?,
    onSseResponse: (SseResponse) -> (Unit)
) {
    val parts = line.takeIf { !it.isNullOrBlank() }?.split(":", limit = 2)
    val field = parts?.getOrNull(0)?.trim()
    val value = parts?.getOrNull(1)?.trim().orEmpty()
    onSseResponse(
        when (field) {
            null -> SseResponse.End
            "data" -> SseResponse.Data(value)
            else -> SseResponse.UnNecessaryOrInvalidField(field, value)
        }
    )
}