package com.sdk.growthbook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher

// 1
actual val PlatformDependentIODispatcher: CoroutineDispatcher =
    Dispatchers.Unconfined
