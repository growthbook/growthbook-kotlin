package com.sdk.growthbook.network

import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import com.sdk.growthbook.utils.Resource
import kotlinx.coroutines.Job
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import com.sdk.growthbook.PlatformDependentIODispatcher
import kotlinx.coroutines.launch
import io.ktor.client.request.get
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
import io.ktor.utils.io.errors.IOException
import javax.net.ssl.SSLHandshakeException

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
    private val client: HttpClient = createDefaultHttpClient()

) : NetworkDispatcher {

    // Regex to match the desired URL pattern: "/api/features/<clientKey>"
    private val featuresPathPattern = Regex(".*/api/features/[^/]+")
    
    // Thread-safe LRU cache with max 100 entries to prevent unbounded growth
    private val eTagCache = LruETagCache(maxSize = 100)

    /**
     * Function that execute API Call to fetch features
     */
    override fun consumeGETRequest(
        url: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Job =
        CoroutineScope(PlatformDependentIODispatcher).launch {
            try {
                val result = prepareGetRequest(url).execute()
                try {
                    // Store the ETag only if the URL matches featuresPathPattern
                    if (featuresPathPattern.matches(url)) {
                        eTagCache.put(url, result.headers["ETag"])
                    }
                    onSuccess(result.body())
                } catch (exception: Exception) {
                    onError(exception)
                }
            } catch (unknownHostException: UnknownHostException) {
                onError(unknownHostException)
            } catch (clientRequestException: ClientRequestException) {
                onError(clientRequestException)
            } catch (serverResponseException: ServerResponseException) {
                onError(serverResponseException)
            } catch (timeoutException: TimeoutException) {
                onError(timeoutException)
            } catch (sslHandshakeException: SSLHandshakeException) {
                onError(sslHandshakeException)
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
                // Only add If-None-Match header if URL matches featuresPathPattern
                if (featuresPathPattern.matches(url)) {
                    // Add If-None-Match header if ETag is present
                    eTagCache.get(url)?.let {
                        append("If-None-Match", it)
                    }
                }
                append("Cache-Control", "max-age=3600")
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
            try {
                val response = client.post(url) {
                    headers {
                        append("Content-Type", "application/json")
                        append("Accept", "application/json")
                    }
                    contentType(ContentType.Application.Json)
                    //setBody(bodyParams.toJsonElement())
                    //println("body = $body")
                }
                onSuccess(response.body())
            } catch (e: Exception) {
                //println("exception $e")
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
