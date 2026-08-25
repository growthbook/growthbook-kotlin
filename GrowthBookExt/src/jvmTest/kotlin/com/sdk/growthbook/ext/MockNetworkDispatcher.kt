package com.sdk.growthbook.ext

import com.sdk.growthbook.network.NetworkDispatcher
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * No-op network dispatcher for tests: never performs real requests,
 * so the SDK relies solely on features we set manually.
 */
class MockNetworkDispatcher : NetworkDispatcher {

    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Job = Job()

    override fun consumeSSEConnection(
        url: String,
        sseController: SSEConnectionController?
    ): Flow<Resource<String>> = emptyFlow()

    override fun consumePOSTRequest(
        url: String,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        // no-op
    }
}
