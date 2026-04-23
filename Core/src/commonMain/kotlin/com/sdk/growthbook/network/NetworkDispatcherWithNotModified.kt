package com.sdk.growthbook.network

import kotlinx.coroutines.Job

/** Optional capability interface for dispatchers that support HTTP 304 Not Modified.
 * Implement this alongside [NetworkDispatcher] to enable ETag-based caching.
 * Built-in Ktor and OkHttp dispatchers implement this interface.
 * Custom dispatcher implementations are not required to implement it.
 */
interface NetworkDispatcherWithNotModified : NetworkDispatcher {
    fun consumeGETRequestWithNotModified(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onNotModified: () -> Unit
    ): Job
}
