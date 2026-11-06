package com.sdk.growthbook.openfeature

/** Logs an error with the GrowthBook SDK's tag. The SDK's own logger is internal to its module. */
internal expect fun logError(message: String, throwable: Throwable)
