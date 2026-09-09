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

    /**
     * Same as [consumeGETRequestWithNotModified] with consumer-supplied [headers] applied to the
     * request. Defaults to the header-less variant, so existing implementations keep working — they
     * just ignore the custom headers. The SDK-managed `If-None-Match` / `Cache-Control` headers
     * ([GBRequestHeaders.RESERVED]) always win over [headers].
     */
    fun consumeGETRequestWithNotModified(
        request: String,
        headers: Map<String, String>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onNotModified: () -> Unit
    ): Job = consumeGETRequestWithNotModified(request, onSuccess, onError, onNotModified)
}
