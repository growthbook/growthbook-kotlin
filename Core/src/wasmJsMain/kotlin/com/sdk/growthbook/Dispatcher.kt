package com.sdk.growthbook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher

// 1
actual val PlatformDependentIODispatcher: CoroutineDispatcher =
    Dispatchers.Unconfined

/**
 * Use it when you need the result of the block,
 * otherwise don't use
 */
actual fun <T> platformDependentRunBlocking(block: suspend () -> T): T? =
    null // runBlocking {} is not available here
