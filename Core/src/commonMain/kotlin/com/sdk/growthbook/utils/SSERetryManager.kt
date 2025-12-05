package com.sdk.growthbook.utils

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.min
import kotlin.math.pow

/**
 * Utility class responsible for handling retry logic for SSE reconnection attempts.
 *
 * This manager provides:
 * - exponential backoff calculation
 * - retry attempt tracking
 * - retry limit enforcement
 * - state reset between successful connections
 *
 * It is used internally by the SDK to prevent aggressive reconnection loops
 * and ensure network-friendly behavior when the SSE stream fails.
 *
 * Backoff formula:
 * ```
 * delay = min(initialRetryDelayMs * 2^attempt, maxRetryDelayMs)
 * ```
 *
 * Example:
 * attempt=0 → 1s
 * attempt=1 → 2s
 * attempt=2 → 4s
 * attempt=3 → 8s
 * ...
 *
 * @param maxRetries Maximum number of reconnection attempts before giving up.
 * @param initialRetryDelayMs Delay for the first retry attempt.
 * @param maxRetryDelayMs Upper bound for exponential backoff delay.
 */
@OptIn(ExperimentalAtomicApi::class)
class SSERetryManager(
    private val maxRetries: Int = 10,
    private val initialRetryDelayMs: Long = 1000L,
    private val maxRetryDelayMs: Long = 30_000L,
) {
    private val retryCount = AtomicInt(0)

    /**
     * Calculates the delay before the next retry based on exponential backoff.
     *
     * @return Delay in milliseconds, guaranteed not to exceed [maxRetryDelayMs].
     */
    fun getBackoffDelay(): Long {
        val attempt = retryCount.load()
        val exponentialDelay = initialRetryDelayMs * (2.0.pow(attempt.toDouble())).toLong()
        return min(exponentialDelay, maxRetryDelayMs)
    }

    /**
     * Indicates whether another retry attempt is allowed.
     *
     * @return `true` if retry count is below [maxRetries], otherwise `false`.
     */
    fun shouldRetry(): Boolean {
        return retryCount.load() < maxRetries
    }

    /**
     * Increments the retry attempt counter.
     */
    fun incrementRetry() {
        retryCount.incrementAndFetch()
    }

    /**
     * Checks if the retry attempts have reached the configured limit.
     */
    fun isMaxRetriesReached(): Boolean = retryCount.load() >= maxRetries

    /**
     * Returns the current retry attempt number.
     */
    fun getCurrentRetry(): Int = retryCount.load()

    /**
     * Resets retry counters after a successful connection or manual reset.
     */
    fun reset() {
        retryCount.store(0)
    }
}
