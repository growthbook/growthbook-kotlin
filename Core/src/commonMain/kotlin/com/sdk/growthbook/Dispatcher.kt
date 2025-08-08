package com.sdk.growthbook

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Expect IO Dispatcher - from respective iOS & Android counter parts
 */
expect val PlatformDependentIODispatcher: CoroutineDispatcher

/**
 * Use it when you need the result of the block,
 * otherwise don't use
 */
expect fun <T> platformDependentRunBlocking(block: suspend () -> T): T?
