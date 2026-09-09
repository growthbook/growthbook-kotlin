package com.sdk.growthbook.utils

/**
 * Thrown when the SDK is configured with invalid or contradictory options, so a misconfiguration
 * is reported up front at build time instead of surfacing later as an opaque fetch failure.
 *
 * Every independent problem is collected and reported together in [violations], so a caller fixing
 * several misconfigured options sees them all at once rather than one per restart.
 *
 * Extends [IllegalArgumentException], the exception the rest of [com.sdk.growthbook.GBSDKBuilder]
 * already throws for bad arguments, so existing `catch (e: IllegalArgumentException)` around
 * `initialize()` keeps working.
 *
 * Never contains header values — only header names — because values may carry credentials.
 */
class GBInvalidOptionsException(
    message: String,

    /**
     * Human-readable description of each problem found, in the order they were checked.
     */
    val violations: List<String>,
) : IllegalArgumentException(message)
