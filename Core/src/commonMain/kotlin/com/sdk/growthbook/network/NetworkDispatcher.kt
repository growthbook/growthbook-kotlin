package com.sdk.growthbook.network

import com.sdk.growthbook.utils.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * Network Dispatcher Interface for API Consumption
 * Implement this interface to define specific implementation for Network Calls - to be made by SDK
 */
interface NetworkDispatcher {
    fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Job

    fun consumeSSEConnection(
        url: String
    ): Flow<Resource<String>>

    fun consumePOSTRequest(
        url: String,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    )
}
