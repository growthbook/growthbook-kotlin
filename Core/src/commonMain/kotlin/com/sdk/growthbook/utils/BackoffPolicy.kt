package com.sdk.growthbook.utils

import kotlin.math.min

/**
 * Capped exponential backoff, shared by the background polling engine and `suspendFeature()`'s retry
 * loop. Pure and stateless: the caller owns the attempt counter and passes it in, so the same policy
 * instance can serve independent retry sequences.
 *
 * @property initialDelayMs delay (ms) for attempt 0; each subsequent attempt doubles it.
 * @property maxDelayMs upper bound (ms) the doubling is clamped to.
 * @property maxAttempts how many attempts [shouldRetry] permits before giving up; defaults to
 *   effectively unbounded ([Int.MAX_VALUE]).
 */
class BackoffPolicy(
    private val initialDelayMs: Long = 1000L,
    private val maxDelayMs: Long = 30000L,
    private val maxAttempts: Int = Int.MAX_VALUE,
) {
    /**
     * Delay for the (0-based) [attempt]: `initialDelayMs * 2^attempt`, capped at [maxDelayMs]. The
     * shift amount is clamped to `[0, 30]` so a large or negative [attempt] can never overflow `Long`
     * or shift by a negative amount (it degrades to the initial/capped delay instead).
     */
    fun delayFor(attempt: Int): Long = min(initialDelayMs shl attempt.coerceIn(0, 30), maxDelayMs)

    /** Whether a further retry is allowed after the (0-based) [attempt] — i.e. [maxAttempts] not yet reached. */
    fun shouldRetry(attempt: Int): Boolean = attempt < maxAttempts
}
