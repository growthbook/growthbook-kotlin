package com.sdk.growthbook

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Android Application Dispatcher
 */
internal actual val ApplicationDispatcher: CoroutineDispatcher = Dispatchers.Default