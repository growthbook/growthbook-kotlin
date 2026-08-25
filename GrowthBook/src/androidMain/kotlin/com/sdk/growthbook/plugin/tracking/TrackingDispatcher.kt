package com.sdk.growthbook.plugin.tracking

import com.sdk.growthbook.PlatformDependentIODispatcher
import kotlinx.coroutines.CoroutineDispatcher

/** Multi-threaded platform: confine the queue to one worker so event order is preserved. */
internal actual val TrackingDispatcher: CoroutineDispatcher =
    PlatformDependentIODispatcher.limitedParallelism(1)
