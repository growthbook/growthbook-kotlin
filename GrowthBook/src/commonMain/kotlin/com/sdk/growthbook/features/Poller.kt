@file:OptIn(ExperimentalAtomicApi::class)

package com.sdk.growthbook.features

import com.sdk.growthbook.logger.GB
import com.sdk.growthbook.utils.BackoffPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Background timer that runs one [round] every interval on [scope], applying capped exponential
 * backoff (via [backoff]) after a failed round before resuming the normal cadence. It is a suspend
 * loop, not a dedicated thread, so it is cheap while idle.
 *
 * At most one loop runs at a time: [start] claims the slot atomically (CAS), so concurrent
 * [start]/[stop] calls cannot leave a second, uncancellable loop running.
 *
 * @param jitterFactor fraction (0.0 = off) of random spread applied to each backoff delay, so many
 *   clients that failed together do not all retry in lockstep (thundering herd). A factor of 0.5
 *   spreads a delay across ±50% of its nominal value.
 */
internal class Poller(
    private val scope: CoroutineScope,
    private val backoff: BackoffPolicy = BackoffPolicy(),
    private val jitterFactor: Double = 0.0,
    private val random: Random = Random.Default,
) {
    private val job = AtomicReference<Job?>(null)

    // Bumped by [stop]. [start] samples it before installing its job and re-checks after: a stop() that
    // interleaves between the two (so its exchange(null) saw no job to cancel) is detected and undone,
    // instead of leaving a loop running after a stop was requested.
    private val stopEpoch = AtomicLong(0)

    /** True while a poll loop is running. */
    fun isActive(): Boolean = job.load()?.isActive == true

    /**
     * Starts the loop, delaying one [intervalMs] before the first [round]. Returns false (no-op) if a
     * loop is already running. [round] performs a single refresh; a [FetchResult.Failed] result adds a
     * backoff delay before the next attempt, any other result resets the backoff.
     */
    fun start(intervalMs: Long, round: suspend () -> FetchResult): Boolean {
        val epoch = stopEpoch.load()
        val existing = job.load()
        if (existing?.isActive == true) return false
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            var attempt = 0
            while (isActive) {
                delay(intervalMs.milliseconds)
                // A round is expected to report failures as FetchResult.Failed rather than throw, but a
                // misbehaving delegate/dispatcher could still let something escape. Treat any throw as a
                // failed round (log + backoff) so one transient error can never permanently kill the
                // loop. Cancellation is NOT an error — it must propagate so stop()/close() still work.
                val failed = try {
                    round() == FetchResult.Failed
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    GB.error("Poller: poll round threw; treating as a failed round", t)
                    true
                }
                if (failed && backoff.shouldRetry(attempt)) {
                    delay(jittered(backoff.delayFor(attempt)).milliseconds)
                    attempt++
                } else {
                    attempt = 0
                }
            }
        }
        // Claim the slot atomically; if another starter won the race, cancel our (not-yet-started) job.
        if (!job.compareAndSet(existing, newJob)) {
            newJob.cancel()
            return false
        }
        // Close the start/stop race: a stop() that ran between our epoch sample and the CAS above could
        // not cancel a job that wasn't installed yet. Detect it via the epoch and undo, so a requested
        // stop is never lost and no loop keeps running past it.
        if (stopEpoch.load() != epoch) {
            job.compareAndSet(newJob, null)
            newJob.cancel()
            return false
        }
        newJob.start()
        return true
    }

    /** Stops the running loop, if any. Safe to call when not polling. */
    fun stop() {
        // Bump the epoch BEFORE clearing the slot so a concurrent start() (which re-checks the epoch
        // after its CAS) observes the stop even if its job wasn't installed when exchange ran.
        stopEpoch.incrementAndFetch()
        job.exchange(null)?.cancel()
    }

    /** Applies ±[jitterFactor] random spread to [delayMs] (no-op when jitter is off or delay is 0). */
    private fun jittered(delayMs: Long): Long {
        if (jitterFactor <= 0.0 || delayMs <= 0L) return delayMs
        val spread = delayMs * jitterFactor
        val delta = random.nextDouble(-spread, spread)
        return (delayMs + delta).toLong().coerceAtLeast(0L)
    }
}
