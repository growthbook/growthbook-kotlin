package com.sdk.growthbook

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Expect IO Dispatcher - from respective iOS & Android counter parts
 */
expect val PlatformDependentIODispatcher: CoroutineDispatcher
