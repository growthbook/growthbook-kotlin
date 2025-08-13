package com.sdk.growthbook

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// 1
actual val PlatformDependentIODispatcher: CoroutineDispatcher =
    Dispatchers.Default
