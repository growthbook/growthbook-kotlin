package com.sdk.growthbook.network

import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * Network Dispatcher Interface for API Consumption
 * Implement this interface to define specific implementation for Network Calls - to be made by SDK
 *
 * The SDK always calls the `headers`-carrying overloads, which pass along the consumer's
 * `apiHostRequestHeaders` / `streamingHostRequestHeaders`. They default to delegating to the
 * header-less variants, so an existing Kotlin or Java implementation keeps compiling and working
 * unchanged — it simply ignores the custom headers. Override them to support authenticated
 * gateways/proxies. Values may contain credentials: apply them to the request, never log them.
 *
 * Not so on Apple targets: Kotlin interfaces are exported to Objective-C with every member
 * `@required`, so a Swift class conforming to this protocol does not inherit the default bodies and
 * will not compile until it implements the `headers` overloads too.
 */
interface NetworkDispatcher {
    fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Job

    /**
     * GET with consumer-supplied [headers] applied to the request. SDK-managed headers
     * ([GBRequestHeaders.RESERVED]) take priority and must not be overridden by [headers].
     */
    fun consumeGETRequest(
        request: String,
        headers: Map<String, String>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Job = consumeGETRequest(request, onSuccess, onError)

    fun consumeSSEConnection(
        url: String,
        sseController: SSEConnectionController? = null
    ): Flow<Resource<String>>

    /**
     * SSE connection with consumer-supplied [headers] applied to the streaming request.
     */
    fun consumeSSEConnection(
        url: String,
        headers: Map<String, String>,
        sseController: SSEConnectionController? = null
    ): Flow<Resource<String>> = consumeSSEConnection(url, sseController)

    fun consumePOSTRequest(
        url: String,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    )

    /**
     * Remote-evaluation POST with consumer-supplied [headers] applied to the request.
     */
    fun consumePOSTRequest(
        url: String,
        headers: Map<String, String>,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) = consumePOSTRequest(url, bodyParams, onSuccess, onError)
}
