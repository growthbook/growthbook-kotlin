package com.sdk.growthbook

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * IO Dispatcher
 */
internal actual val PlatformDependentIODispatcher: CoroutineDispatcher =
    Dispatchers.IO
