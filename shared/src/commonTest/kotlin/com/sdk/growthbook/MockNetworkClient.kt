package com.sdk.growthbook

import com.sdk.growthbook.Network.NetworkDispatcher
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MockNetworkClient (val succesResponse : String?, val error: Throwable?) : NetworkDispatcher {
    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {

        try {
            if (succesResponse != null) {
                onSuccess(succesResponse)
            } else if (error != null) {
                onError(error)
            }
        } catch (ex: Exception) {
            onError(ex)
        }


    }
}