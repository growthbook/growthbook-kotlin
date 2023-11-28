package com.sdk.growthbook.Network

import com.sdk.growthbook.ApplicationDispatcher
import com.sdk.growthbook.Utils.Resource
import com.sdk.growthbook.Utils.readSse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpStatement
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    fun consumeSSEConnection(
        url: String
    ): Flow<Resource<String>>
}

/**
 * Default Ktor Implementation for Network Dispatcher
 */
@Suppress("unused")
class DefaultGBNetworkClient : NetworkDispatcher {

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

    private suspend fun prepareGetRequest(
        url: String,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap(),
    ): HttpStatement =
        client.prepareGet(url) {
            headers {
                headers.forEach { (key, value) -> append(key, value) }
            }
            queryParams.forEach { (key, value) -> addOrReplaceParameter(key, value) }
        }

    @OptIn(DelicateCoroutinesApi::class)
    override fun consumeSSEConnection(
        url: String
    ) = callbackFlow {
        GlobalScope.launch(ApplicationDispatcher) {
            try {
                prepareGetRequest(url).execute { response ->
                    val channel: ByteReadChannel = response.body()
                    channel.readSse(
                        onSseEvent = { sseEvent ->
                            trySend(sseEvent)
                        },
                    )
                }
            } catch (ex: Exception) {
                trySend(Resource.Error(ex))
            } finally {
                close()
            }
        }
        awaitClose()
    }

    private fun HttpRequestBuilder.addOrReplaceParameter(key: String, value: String?): Unit =
        value?.let {
            url.parameters.remove(key)
            url.parameters.append(key, it)
        } ?: Unit
}