package com.sdk.growthbook.network

import com.sdk.growthbook.utils.Resource
import kotlinx.coroutines.Job
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import com.sdk.growthbook.PlatformDependentIODispatcher
import kotlinx.coroutines.launch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.call.body
import io.ktor.client.statement.HttpStatement
import io.ktor.client.request.headers
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.flow.callbackFlow
import io.ktor.utils.io.ByteReadChannel
import com.sdk.growthbook.utils.readSse
import kotlinx.coroutines.channels.awaitClose
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.core.use
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun createDefaultHttpClient(): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

/**
 * Network Dispatcher based on Ktor
 */
class GBNetworkDispatcherKtor(

    /**
     * Ktor http client instance for sending request
     */
    private val client: HttpClient = createDefaultHttpClient(),

    private var enableLogging: Boolean = false,
) : NetworkDispatcher {

    /**
     * Function that execute API Call to fetch features
     */
    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Job =
        CoroutineScope(PlatformDependentIODispatcher).launch {
            try {
                val result = prepareGetRequest(request).execute()
                try {
                    if (result.status == HttpStatusCode.OK) {
                        onSuccess(result.body())
                    } else {
                        onError(Exception("Response status in not ok: Response is: ${result.body<String>()}"))
                    }
                } catch (exception: Exception) {
                    onError(exception)
                }
            } catch (clientRequestException: ClientRequestException) {
                onError(clientRequestException)
            } catch (serverResponseException: ServerResponseException) {
                onError(serverResponseException)
            } catch (ioException: IOException) {
                onError(ioException)
            } catch (exception: Exception) { // for the case if something was missed
                onError(exception)
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
        CoroutineScope(PlatformDependentIODispatcher).launch {
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
        CoroutineScope(PlatformDependentIODispatcher).launch {
            client.use { okHttpClient ->
                try {
                    val response = okHttpClient.post(url) {
                        headers {
                            append("Content-Type", "application/json")
                            append("Accept", "application/json")
                        }
                        contentType(ContentType.Application.Json)
                        setBody(bodyParams.toJsonElement())
                        if (enableLogging) {
                            println("body = $body")
                        }
                    }
                    if (response.status.value in 200..299) {
                        onSuccess(response.body())
                    } else {
                        onError(
                            Exception(
                                "Response not successful status code is : ${response.status.value} " +
                                    "and description : ${response.status.description}"
                            )
                        )
                    }
                } catch (e: Exception) {
                    if (enableLogging) {
                        println("exception $e")
                    }
                    onError(e)
                }
            }
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        enableLogging = enabled
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

internal fun Map<*, *>.toJsonElement(): JsonElement {
    val map: MutableMap<String, JsonElement> = mutableMapOf()
    this.forEach {
        val key = it.key as? String ?: return@forEach
        val value = it.value ?: return@forEach
        map[key] = when (value) {
            is Map<*, *> -> (value).toJsonElement()
            is List<*> -> value.toJsonElement()
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
    return JsonObject(map)
}

internal fun List<*>.toJsonElement(): JsonElement {
    val list: MutableList<JsonElement> = mutableListOf()
    this.forEach {
        val value = it ?: return@forEach
        when (value) {
            is Map<*, *> -> list.add((value).toJsonElement())
            is List<*> -> list.add(value.toJsonElement())
            is Boolean -> list.add(JsonPrimitive(value))
            is Number -> list.add(JsonPrimitive(value))
            else -> list.add(JsonPrimitive(value.toString()))
        }
    }
    return JsonArray(list)
}
