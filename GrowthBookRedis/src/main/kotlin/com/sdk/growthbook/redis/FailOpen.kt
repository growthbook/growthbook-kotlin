package com.sdk.growthbook.redis

import kotlinx.coroutines.CancellationException

/**
 * Runs [block], degrading to [fallback] on any failure and reporting it to [onError].
 *
 * Both Redis-backed components fail open rather than throwing: assignments are read on the
 * evaluation path and written from a fire-and-forget coroutine, where an escaping exception would
 * cancel the caller's scope. `CancellationException` is rethrown so structured concurrency keeps
 * working.
 */
internal suspend fun <T> failOpen(
    fallback: T,
    onError: ((Throwable) -> Unit)?,
    block: suspend () -> T
): T =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        report(onError, error)
        fallback
    }

/**
 * Hands [error] to [onError] without letting the callback break the fail-open contract.
 *
 * [onError] is consumer code. A callback that throws would propagate out of [failOpen] and cancel
 * the very scope fail-open exists to protect, turning a reporting hook into the outage it reports.
 * A throw from the callback is therefore dropped — there is nowhere left to report it to.
 */
internal fun report(onError: ((Throwable) -> Unit)?, error: Throwable) {
    if (onError == null) return

    try {
        onError(error)
    } catch (cancellation: CancellationException) {
        // Not the callback misbehaving: the surrounding scope is being cancelled.
        throw cancellation
    } catch (_: Throwable) {
        // Deliberately swallowed; see above.
    }
}
