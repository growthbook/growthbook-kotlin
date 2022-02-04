package com.sdk.growthbook

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// 1
internal actual val ApplicationDispatcher: CoroutineDispatcher = Dispatchers.Default