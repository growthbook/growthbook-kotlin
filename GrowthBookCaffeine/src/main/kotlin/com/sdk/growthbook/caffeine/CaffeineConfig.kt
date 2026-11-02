package com.sdk.growthbook.caffeine

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Builds the Caffeine instance both adapters in this module sit on, so they share one set of
 * knobs and one place where options are validated.
 *
 * Size bounding is left to the caller: the feature cache bounds by payload weight, while sticky
 * bucket assignments bound by entry count.
 */
internal fun caffeineBuilder(
    expireAfterWrite: Duration?,
    expireAfterAccess: Duration?,
    recordStats: Boolean,
    ticker: Ticker?
): Caffeine<Any, Any> {
    val builder = Caffeine.newBuilder()

    expireAfterWrite.requirePositive("expireAfterWrite")?.let {
        builder.expireAfterWrite(it.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }
    expireAfterAccess.requirePositive("expireAfterAccess")?.let {
        builder.expireAfterAccess(it.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }
    if (recordStats) {
        builder.recordStats()
    }
    ticker?.let(builder::ticker)

    return builder
}

/**
 * Validated at construction rather than at use, so a bad value fails where it was configured
 * instead of on the first eviction.
 */
internal fun Duration?.requirePositive(name: String): Duration? {
    if (this == null) return null

    require(this > Duration.ZERO) { "$name must be greater than 0, but was $this" }
    return this
}

/** As [requirePositive], for the entry-count and weight bounds. */
internal fun Long.requirePositive(name: String): Long {
    require(this > 0) { "$name must be greater than 0, but was $this" }
    return this
}
