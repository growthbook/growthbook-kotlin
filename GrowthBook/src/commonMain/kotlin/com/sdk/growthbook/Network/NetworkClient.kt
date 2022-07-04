package com.sdk.growthbook.Network

import com.sdk.growthbook.ApplicationDispatcher
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
    fun consumeGETRequest(request: String, onSuccess: (String) -> Unit, onError: (Throwable) -> Unit)
}

/**
 * Default Ktor Implementation for Network Dispatcher
 */
internal class CoreNetworkClient : NetworkDispatcher {

    val client = HttpClient {
        install(JsonFeature){
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
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
                val result = client.get<HttpResponse>(request)
                onSuccess(result.receive())
            } catch (ex: Exception) {
                onError(ex)
            }

        }

    }

}
