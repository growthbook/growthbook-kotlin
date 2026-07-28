package com.sdk.growthbook.tests

import com.sdk.growthbook.features.FetchResult
import com.sdk.growthbook.features.Poller
import com.sdk.growthbook.utils.BackoffPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PollerTest {

    @Test
    fun testRunsEachIntervalOnSuccess() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val poller = Poller(scope)
        var rounds = 0

        poller.start(intervalMs = 1000) { rounds++; FetchResult.Success }
        repeat(3) { advanceTimeBy(1000); runCurrent() }

        assertEquals(3, rounds, "each elapsed interval must run exactly one round on success")
        poller.stop()
        scope.cancel()
    }

    @Test
    fun testBackoffDelaysNextRoundAfterFailure() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        // initial backoff 100ms so a failed round inserts a 100ms wait ON TOP of the 1000ms interval.
        val poller = Poller(scope, BackoffPolicy(initialDelayMs = 100, maxDelayMs = 1000))
        var rounds = 0

        poller.start(intervalMs = 1000) { rounds++; FetchResult.Failed }

        advanceTimeBy(1000); runCurrent()          // t=1000: first round fails
        assertEquals(1, rounds)

        // t=2000: the bare interval has elapsed, but the 100ms backoff pushed the next round to
        // t=2100 — so no round yet. This is what proves the backoff is actually applied in the loop.
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, rounds, "backoff must delay the next round past the bare interval")

        advanceTimeBy(200); runCurrent()           // t=2200 > 2100: next round fires
        assertEquals(2, rounds)

        poller.stop()
        scope.cancel()
    }

    @Test
    fun testStartReturnsFalseWhenAlreadyRunning() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val poller = Poller(scope)

        assertTrue(poller.start(1000) { FetchResult.Success }, "first start should launch the loop")
        assertFalse(
            poller.start(1000) { FetchResult.Success },
            "a second start while a loop is running must be a no-op"
        )

        poller.stop()
        scope.cancel()
    }

    @Test
    fun testStartAfterStopRestartsTheLoop() = runTest {
        // The start/stop epoch guard (which undoes a start raced by a stop) must NOT break a
        // legitimate restart: after stop(), a fresh start() samples the new epoch and runs normally.
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val poller = Poller(scope)
        var rounds = 0

        assertTrue(poller.start(1000) { rounds++; FetchResult.Success }, "first start should launch")
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, rounds)

        poller.stop()
        advanceTimeBy(3000); runCurrent()
        assertEquals(1, rounds, "no rounds run after stop")

        assertTrue(
            poller.start(1000) { rounds++; FetchResult.Success },
            "restart after stop must succeed (epoch is re-sampled on the new start)"
        )
        advanceTimeBy(1000); runCurrent()
        assertEquals(2, rounds, "the restarted loop must run again")

        poller.stop()
        scope.cancel()
    }

    @Test
    fun testLoopSurvivesRoundException() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        // 100ms backoff, no jitter, so timing is deterministic.
        val poller = Poller(scope, BackoffPolicy(initialDelayMs = 100, maxDelayMs = 100))
        var rounds = 0

        poller.start(intervalMs = 1000) {
            rounds++
            if (rounds == 1) throw IllegalStateException("boom") // a thrown round must not kill the loop
            FetchResult.Success
        }

        advanceTimeBy(1000); runCurrent()          // t=1000: round 1 throws -> treated as failed
        assertEquals(1, rounds)

        // The thrown round is treated as a failure, so the next round is pushed by the 100ms backoff
        // (interval 1000 + backoff 100 -> t=2100), proving the loop kept running.
        advanceTimeBy(1100); runCurrent()
        assertEquals(2, rounds, "a thrown round must not permanently stop the poll loop")
        assertTrue(poller.isActive(), "the loop must still be active after a thrown round")

        poller.stop()
        scope.cancel()
    }

    // A 5s wall-clock cap so a mistaken assertion can never leave the orphaned poll loop spinning
    // the shared virtual clock forever (advanceUntilIdle on cleanup) and hang the whole suite.
    @Test
    fun testBackoffJitterStaysWithinBounds() = runTest(timeout = 5.seconds) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        // Base backoff fixed at 1000ms, jitter ±50% -> backoff in [500, 1500]. Each loop iteration is
        // delay(interval) -> round -> delay(backoff), so round 2 lands at
        //   interval(1000) + backoff + interval(1000) = 2000 + backoff  -> in [2500, 3500].
        // A seeded Random keeps the single draw deterministic; the assertions bound it either side.
        val poller = Poller(
            scope,
            BackoffPolicy(initialDelayMs = 1000, maxDelayMs = 1000),
            jitterFactor = 0.5,
            random = Random(42),
        )
        var rounds = 0

        poller.start(intervalMs = 1000) { rounds++; FetchResult.Failed }

        advanceTimeBy(1000); runCurrent()          // t=1000: round 1 fails
        assertEquals(1, rounds)

        // Just below the earliest possible round 2 (2500) -> must not have fired yet.
        advanceTimeBy(1499); runCurrent()          // t=2499
        assertEquals(1, rounds, "jittered backoff must not let round 2 fire before its lower bound")

        // Past the latest possible round 2 (3500) -> must have fired exactly once more (round 3 can't
        // arrive until >= 4000, so the count is unambiguous).
        advanceTimeBy(1001); runCurrent()          // t=3500
        assertEquals(2, rounds, "jittered backoff must let round 2 fire by its upper bound")

        poller.stop()
        scope.cancel()
    }

    @Test
    fun testStopHaltsFurtherRounds() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val poller = Poller(scope)
        var rounds = 0

        poller.start(intervalMs = 1000) { rounds++; FetchResult.Success }
        advanceTimeBy(1000); runCurrent()
        assertEquals(1, rounds)

        poller.stop()
        advanceTimeBy(5000); runCurrent()
        assertEquals(1, rounds, "no round may run after stop()")

        assertFalse(poller.isActive())
        scope.cancel()
    }
}
