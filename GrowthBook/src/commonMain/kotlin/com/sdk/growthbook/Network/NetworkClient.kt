package com.sdk.growthbook.Network

import com.sdk.growthbook.ApplicationDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Network Dispatcher Interface for API Consumption
 * Implement this interface to define specific implementation for Network Calls - to be made by SDK
 */
interface NetworkDispatcher {
    @DelicateCoroutinesApi
    fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    )
}

/**
 * Default Ktor Implementation for Network Dispatcher
 */
internal class CoreNetworkClient : NetworkDispatcher {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    @DelicateCoroutinesApi
    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {

        GlobalScope.launch(ApplicationDispatcher) {

            try {
                val result = client.get(request)
                onSuccess(result.body())
            } catch (ex: Exception) {
                onError(ex)
            }

        }
    }
}