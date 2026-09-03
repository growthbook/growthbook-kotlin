package com.sdk.growthbook.tests

import com.sdk.growthbook.utils.BackoffPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackoffPolicyTest {

    @Test
    fun testExponentialSequence() {
        val policy = BackoffPolicy(initialDelayMs = 1000, maxDelayMs = 30_000)
        assertEquals(1000, policy.delayFor(0))
        assertEquals(2000, policy.delayFor(1))
        assertEquals(4000, policy.delayFor(2))
        assertEquals(8000, policy.delayFor(3))
        assertEquals(16000, policy.delayFor(4))
    }

    @Test
    fun testDelayIsCappedAndHolds() {
        val policy = BackoffPolicy(initialDelayMs = 1000, maxDelayMs = 30_000)
        // 1000 * 2^5 = 32000 -> capped to 30000, then stays there.
        assertEquals(30_000, policy.delayFor(5))
        assertEquals(30_000, policy.delayFor(6))
    }

    @Test
    fun testLargeAttemptDoesNotOverflow() {
        // The shl guard (coerceAtMost(30)) must keep huge attempt counts from overflowing Long;
        // the result stays capped rather than going negative or throwing.
        val policy = BackoffPolicy(initialDelayMs = 1000, maxDelayMs = 30_000)
        assertEquals(30_000, policy.delayFor(100))
        assertEquals(30_000, policy.delayFor(Int.MAX_VALUE))
    }

    @Test
    fun testNegativeAttemptClampedToInitialDelay() {
        // A negative attempt (e.g. from an overflowed counter) must not produce a negative/garbage
        // delay via a negative left-shift — coerceIn(0, 30) clamps it to the initial delay.
        val policy = BackoffPolicy(initialDelayMs = 1000, maxDelayMs = 30_000)
        assertEquals(1000, policy.delayFor(-1))
        assertEquals(1000, policy.delayFor(Int.MIN_VALUE))
    }

    @Test
    fun testShouldRetryUnboundedByDefault() {
        val policy = BackoffPolicy()
        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(1_000_000))
    }

    @Test
    fun testShouldRetryHonoursMaxAttempts() {
        val policy = BackoffPolicy(maxAttempts = 5)
        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(4))
        assertFalse(policy.shouldRetry(5))
        assertFalse(policy.shouldRetry(6))
    }

    @Test
    fun testSuspendFeatureEquivalentConfig() {
        // Mirrors GrowthBookSDK.suspendFeature: initial 1s, cap 60s, 5 attempts. The sequence must
        // match the pre-refactor inline backoff (1s, 2s, 4s, 8s, 16s) exactly.
        val policy = BackoffPolicy(initialDelayMs = 1000, maxDelayMs = 60_000, maxAttempts = 5)
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L), (0..4).map { policy.delayFor(it) })
        assertFalse(policy.shouldRetry(5))
    }
}
