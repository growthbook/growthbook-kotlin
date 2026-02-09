package com.sdk.growthbook.network

import com.sdk.growthbook.PlatformDependentIODispatcher
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import com.sdk.growthbook.utils.SSEConnectionState
import com.sdk.growthbook.utils.SSERetryManager
import com.sdk.growthbook.utils.readSse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig.Companion.INFINITE_TIMEOUT_MS
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.use
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Creates default Ktor HTTP client configured for:
 * - SSE consumption
 * - unlimited request/socket timeout for streaming connections
 * - JSON parsing with lenient mode & unknown key ignore
 */
internal fun createDefaultHttpClient(): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        install(SSE)

        install(HttpTimeout) {
            socketTimeoutMillis = INFINITE_TIMEOUT_MS
            requestTimeoutMillis = INFINITE_TIMEOUT_MS
            connectTimeoutMillis = 30_000
        }
    }

/**
 * Network dispatcher implementation using Ktor to perform:
 * - GET requests
 * - POST requests
 * - SSE long-lived streaming connections with reconnection support
 */
class GBNetworkDispatcherKtor(

    /**
     * Ktor http client instance for sending request
     */
    private val client: HttpClient = createDefaultHttpClient(),

    private var enableLogging: Boolean = false,
    private val maxRetries: Int = 10,
    private val initialRetryDelayMs: Long = 1000L,
    private val maxRetryDelayMs: Long = 30_000L
) : NetworkDispatcher {

    // Regex to match the desired URL pattern: "/api/features/<clientKey>"
    private val featuresPathPattern = Regex(".*/api/features/[^/]+")
    
    // Thread-safe LRU cache with max 100 entries to prevent unbounded growth
    private val eTagCache = LruETagCache(maxSize = 100)

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
                    // Store the ETag only if the URL matches featuresPathPattern
                    if (featuresPathPattern.matches(request)) {
                        eTagCache.put(request, result.headers["ETag"])
                    }
                    
                    if (result.status == HttpStatusCode.OK) {
                        onSuccess(result.body())
                    } else {
                        onError(
                            Exception(
                                "Response status in not ok: " +
                                    "Response is: ${result.body<String>()}"
                            )
                        )
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
                
                // Only add cache headers if URL matches featuresPathPattern
                if (featuresPathPattern.matches(url)) {
                    // Add If-None-Match header if ETag is present
                    eTagCache.get(url)?.let {
                        append("If-None-Match", it)
                    }
                    append("Cache-Control", "max-age=3600")
                }
            }
            queryParams.forEach { (key, value) -> addOrReplaceParameter(key, value) }
        }

    /**
     * Opens SSE connection and handles reconnection logic with exponential backoff.
     *
     * Emits:
     *  - Resource.Success(String) — when event received
     *  - Resource.Error(Throwable) — on errors or max retries
     */
    override fun consumeSSEConnection(
        url: String,
        sseController: SSEConnectionController?
    ) = callbackFlow {
        val scope = this
        val retryManager = SSERetryManager(maxRetries, initialRetryDelayMs, maxRetryDelayMs)
        val controller = sseController ?: SSEConnectionController()
        var connectionJob: Job? = null

        fun startSseConnection() {
            // Check state before starting
            when {
                controller.isStopped() -> {
                    if (enableLogging) println("GrowthBook SSE (Ktor): STOPPED, closing")
                    close()
                    return
                }
            }

            fun handleConnectionEnd(error: Exception? = null) {
                if (controller.isStopped()) {
                    if (enableLogging) {
                        println("GrowthBook SSE (Ktor): Connection end while STOPPED, not retrying.")
                    }
                    return
                }

                if (retryManager.isMaxRetriesReached()) {
                    if (enableLogging) {
                        println("GrowthBook SSE (Ktor): Max retries reached, STOPPING connection.")
                    }
                    controller.stop()
                    trySend(
                        Resource.Error(
                            Exception("Max SSE reconnection retries exceeded", error)
                        )
                    )
                } else {
                    val delayMs = retryManager.getBackoffDelay()
                    if (enableLogging) {
                        val msg = if (error != null) "error = ${error.message}" else "connection closed"
                        println(
                            "GrowthBook SSE (Ktor): $msg," +
                                " retry ${retryManager.getCurrentRetry() + 1}/$maxRetries in ${delayMs}ms"
                        )
                    }
                    retryManager.incrementRetry()
                    launch {
                        delay(delayMs)
                        startSseConnection()
                    }
                }
            }

            if (enableLogging) println("GrowthBook SSE (Ktor): Starting SSE connection...")

            connectionJob?.cancel()
            connectionJob = scope.launch(PlatformDependentIODispatcher) {
                try {
                    prepareGetRequest(url).execute { response ->
                        val channel: ByteReadChannel = response.body()
                        channel.readSse(
                            onSseEvent = { sseEvent ->
                                retryManager.reset()
                                if (enableLogging) {
                                    println("GrowthBook SSE (Ktor): Features received")
                                }
                                trySend(sseEvent)
                            }
                        )
                    }

                    handleConnectionEnd();
                } catch (ex: Exception) {
                    if (enableLogging) ex.printStackTrace()
                    handleConnectionEnd(ex)
                }
            }
        }

        // Listen to controller state changes
        launch {
            controller.connectionState.collect { state ->
                if (enableLogging) {
                    println("GrowthBook SSE (Ktor): State changed to $state")
                }

                when (state) {
                    SSEConnectionState.ACTIVE -> {
                        retryManager.reset()
                        connectionJob?.cancel()
                        startSseConnection()
                    }

                    SSEConnectionState.STOPPED -> {
                        connectionJob?.cancel()
                        close()
                    }
                }
            }
        }

        awaitClose {
            if (enableLogging) {
                println("GrowthBook SSE (Ktor): flow closed, stop reconnecting")
            }
            connectionJob?.cancel()
        }
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
