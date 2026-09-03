package com.sdk.growthbook.utils

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

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
    maxRetries: Int = 10,
    initialRetryDelayMs: Long = 1000L,
    maxRetryDelayMs: Long = 30_000L,
) {
    // The backoff maths (exponential growth + cap + attempt limit) live in the shared
    // [BackoffPolicy] so there is a single source of truth across the SDK (poller, suspendFeature,
    // SSE reconnect). This class only owns the thread-safe reconnection counter and the
    // SSE-specific reset/give-up semantics on top of it.
    private val backoffPolicy = BackoffPolicy(
        initialDelayMs = initialRetryDelayMs,
        maxDelayMs = maxRetryDelayMs,
        maxAttempts = maxRetries,
    )
    private val retryCount = AtomicInt(0)

    /**
     * Calculates the delay before the next retry based on exponential backoff.
     *
     * @return Delay in milliseconds, guaranteed not to exceed the configured maximum.
     */
    fun getBackoffDelay(): Long = backoffPolicy.delayFor(retryCount.load())

    /**
     * Indicates whether another retry attempt is allowed.
     *
     * @return `true` if the retry count is below the configured limit, otherwise `false`.
     */
    fun shouldRetry(): Boolean = backoffPolicy.shouldRetry(retryCount.load())

    /**
     * Increments the retry attempt counter.
     */
    fun incrementRetry() {
        retryCount.incrementAndFetch()
    }

    /**
     * Checks if the retry attempts have reached the configured limit.
     */
    fun isMaxRetriesReached(): Boolean = !backoffPolicy.shouldRetry(retryCount.load())

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
