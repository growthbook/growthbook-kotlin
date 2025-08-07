package com.sdk.growthbook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher

// 1
actual val PlatformDependentIODispatcher: CoroutineDispatcher =
    Dispatchers.Default

actual fun <T> platformDependentRunBlocking(block: suspend () -> T): T? =
    null // has no ability to use threads in iosArm64 target
