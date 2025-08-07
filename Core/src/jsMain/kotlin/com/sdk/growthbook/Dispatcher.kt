package com.sdk.growthbook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher

// 1
actual val PlatformDependentIODispatcher: CoroutineDispatcher =
    Dispatchers.Unconfined

actual fun <T> platformDependentRunBlocking(block: suspend () -> T): T? =
    null // runBlocking {} is not available here
