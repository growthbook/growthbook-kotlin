package com.sdk.growthbook.utils

data class SSEEventData(
    val status: STATUS? = null,
    val data: String? = null
)

enum class STATUS {
    SUCCESS,
    ERROR,
    NONE,
    CLOSED,
    OPEN
}
