package com.sdk.growthbook.caffeine

import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Answers every feature fetch with the same canned outcome, so a test can drive the SDK without a
 * network. The SDK never hardcodes a client, so implementing the interface is all it takes.
 */
internal class FakeNetworkDispatcher(
    private val successResponse: String? = null,
    private val error: Throwable? = null
) : NetworkDispatcher {

    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Job {
        respond(onSuccess, onError)
        return Job()
    }

    override fun consumePOSTRequest(
        url: String,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        respond(onSuccess, onError)
    }

    /** No SSE in these tests: an empty flow completes instead of holding a connection open. */
    override fun consumeSSEConnection(
        url: String,
        sseController: SSEConnectionController?
    ): Flow<Resource<String>> = emptyFlow()

    private fun respond(onSuccess: (String) -> Unit, onError: (Throwable) -> Unit) {
        when {
            successResponse != null -> onSuccess(successResponse)
            error != null -> onError(error)
        }
    }
}
