package com.sdk.growthbook.openfeature

internal actual fun logError(message: String, throwable: Throwable) {
    println("GrowthBook error: $message")
    throwable.printStackTrace()
}
