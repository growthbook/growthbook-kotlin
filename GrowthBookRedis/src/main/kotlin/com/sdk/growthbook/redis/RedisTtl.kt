package com.sdk.growthbook.redis

import kotlin.time.Duration

/**
 * Converts a public `Duration` option to the whole seconds Redis expiry takes.
 *
 * Validated at construction rather than at write time, so a bad value fails where it was
 * configured instead of once per write from a background coroutine. The floor is one second, not
 * "greater than zero": Redis rejects `EX 0`, so a sub-second duration truncating to `0` would
 * otherwise turn every write into an error.
 */
internal fun Duration?.toTtlSeconds(): Long? {
    if (this == null) return null

    val seconds = inWholeSeconds
    require(seconds >= 1) { "ttl must be at least 1 second, but was $this" }
    return seconds
}
