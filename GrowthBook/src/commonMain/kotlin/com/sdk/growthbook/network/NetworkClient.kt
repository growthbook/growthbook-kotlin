package com.sdk.growthbook.network

import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.readSse
import com.sdk.growthbook.utils.toJsonElement
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    )

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

/**
 * Default Ktor Implementation for Network Dispatcher
 */
class DefaultGBNetworkClient : NetworkDispatcher {

    /**
     * Ktor http client instance for sending request
     */
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    /**
     * Function that execute API Call to fetch features
     */
    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {

            try {
                val result = client.get(request)
                onSuccess(result.body())
            } catch (ex: Exception) {
                onError(ex)
            }

        }
    }

    /**
     * Supportive method for preparing GET request for consuming SSE connection
     */
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

    /**
     * Method that produce SSE connection
     */
    override fun consumeSSEConnection(
        url: String
    ) = callbackFlow {
        CoroutineScope(Dispatchers.IO).launch {
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

    /**
     * Method that make POST request to server for remote feature evaluation
     */
    override fun consumePOSTRequest(
        url: String,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.post(url) {
                    headers {
                        append("Content-Type", "application/json")
                        append("Accept", "application/json")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(bodyParams.toJsonElement())
                    println("body = $body")
                }
                onSuccess(response.body())
            } catch (e: Exception) {
                println("exception $e")
                onError(e)
            }
        }
    }

    /**
     * Supportive extensions method, for HttpRequestBuilder object,
     * that replace parameter in prepare get request method
     */
    private fun HttpRequestBuilder.addOrReplaceParameter(key: String, value: String?): Unit =
        value?.let {
            url.parameters.remove(key)
            url.parameters.append(key, it)
        } ?: Unit
}