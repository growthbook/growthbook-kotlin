package com.sdk.growthbook

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Expect Application Dispatcher - from respective iOS & Android counter parts
 */
internal expect val ApplicationDispatcher: CoroutineDispatcher