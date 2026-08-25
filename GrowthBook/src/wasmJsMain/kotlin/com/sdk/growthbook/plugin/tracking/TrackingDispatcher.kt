package com.sdk.growthbook.plugin.tracking

import com.sdk.growthbook.PlatformDependentIODispatcher
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Browser runtime: single-threaded already, and Dispatchers.Unconfined (what
 * PlatformDependentIODispatcher resolves to here) rejects limitedParallelism outright.
 */
internal actual val TrackingDispatcher: CoroutineDispatcher = PlatformDependentIODispatcher
