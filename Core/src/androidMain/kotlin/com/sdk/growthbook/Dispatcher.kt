package com.sdk.growthbook

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher

/**
 * IO Dispatcher
 */
actual val PlatformDependentIODispatcher: CoroutineDispatcher =
    Dispatchers.IO
