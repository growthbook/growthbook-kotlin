package com.sdk.growthbook.plugin.tracking

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher backing [GrowthBookTrackingPlugin]'s default coroutine scope.
 *
 * On multi-threaded platforms this is `PlatformDependentIODispatcher.limitedParallelism(1)`: a
 * single-worker queue, so buffered events keep their submission order.
 *
 * It cannot simply be that expression everywhere. On js and wasmJs
 * `PlatformDependentIODispatcher` resolves to `Dispatchers.Unconfined`, and
 * `Dispatchers.Unconfined.limitedParallelism()` throws `UnsupportedOperationException`
 * unconditionally — evaluating it in the plugin's default argument crashed the constructor on
 * both browser targets. Those runtimes are single-threaded, so the unmodified dispatcher already
 * gives the ordering the limit was there to provide.
 *
 * Mutual exclusion never depended on this either way: the plugin's buffer, de-dupe cache and
 * pending-flush handle are all guarded by its `Mutex`.
 */
internal expect val TrackingDispatcher: CoroutineDispatcher
