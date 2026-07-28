package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.SSERetryManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the SSE reconnection backoff behaviour after delegating the maths to the shared
 * [com.sdk.growthbook.utils.BackoffPolicy]. The counter/give-up/reset semantics used by the Ktor and
 * OkHttp dispatchers must be unchanged.
 */
class SSERetryManagerTest {

    @Test
    fun testBackoffSequenceAndLimit() {
        val manager = SSERetryManager(
            maxRetries = 4,
            initialRetryDelayMs = 1000,
            maxRetryDelayMs = 30_000,
        )

        // attempt 0..3 → 1s, 2s, 4s, 8s
        assertEquals(1000, manager.getBackoffDelay())
        assertTrue(manager.shouldRetry())
        assertFalse(manager.isMaxRetriesReached())

        manager.incrementRetry()
        assertEquals(2000, manager.getBackoffDelay())
        manager.incrementRetry()
        assertEquals(4000, manager.getBackoffDelay())
        manager.incrementRetry()
        assertEquals(8000, manager.getBackoffDelay())
        assertTrue(manager.shouldRetry()) // 3 < 4

        manager.incrementRetry() // now 4
        assertFalse(manager.shouldRetry())
        assertTrue(manager.isMaxRetriesReached())
    }

    @Test
    fun testResetRestoresInitialState() {
        val manager = SSERetryManager(maxRetries = 4, initialRetryDelayMs = 1000, maxRetryDelayMs = 30_000)
        repeat(4) { manager.incrementRetry() }
        assertTrue(manager.isMaxRetriesReached())

        manager.reset()

        assertEquals(0, manager.getCurrentRetry())
        assertEquals(1000, manager.getBackoffDelay())
        assertTrue(manager.shouldRetry())
        assertFalse(manager.isMaxRetriesReached())
    }

    @Test
    fun testDelayIsCapped() {
        val manager = SSERetryManager(maxRetries = 100, initialRetryDelayMs = 1000, maxRetryDelayMs = 30_000)
        repeat(10) { manager.incrementRetry() } // 1000 * 2^10 = 1_024_000 -> capped
        assertEquals(30_000, manager.getBackoffDelay())
    }
}
