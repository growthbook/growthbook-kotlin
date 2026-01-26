package com.sdk.growthbook.network

import com.sdk.growthbook.PlatformDependentIODispatcher
import com.sdk.growthbook.utils.GBEventSourceHandler
import com.sdk.growthbook.utils.GBEventSourceListener
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import com.sdk.growthbook.utils.SSEConnectionState
import com.sdk.growthbook.utils.SSEErrorType
import com.sdk.growthbook.utils.SSERetryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Default OkHttp-based implementation of [NetworkDispatcher].
 *
 * Provides GET, POST and SSE (Server-Sent Events) request handling
 * used by the GrowthBook SDK for fetching features and subscribing
 * to real-time updates.
 *
 * This dispatcher is optimized for long-lived SSE connections and
 * includes built-in retry logic with exponential backoff.
 *
 * Also implements ETag-based HTTP caching with an LRU cache to reduce
 * bandwidth and improve performance for feature fetching.
 */
class GBNetworkDispatcherOkHttp(

    /**
     * OkHttp client instance for sending request
     */
    private val client: OkHttpClient = OkHttpClient(),

    private var enableLogging: Boolean = false,
    private val maxRetries: Int = 10,
    private val initialRetryDelayMs: Long = 1000L,
    private val maxRetryDelayMs: Long = 30_000L,

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
            val getRequest = Request.Builder()
                .url(request)
                .addHeader("Cache-Control", "max-age=3600")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .apply {
                    // Only add If-None-Match header if URL matches featuresPathPattern
                    if (featuresPathPattern.matches(request)) {
                        // Add If-None-Match header if ETag is present
                        eTagCache.get(request)?.let {
                            header("If-None-Match", it)
                        }
                    }
                }
                .build()
            client.newCall(getRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful || resp.code !in 200..299) {
                            onError(IOException("Unexpected code $resp"))
                            return
                        }
                        
                        // Store the ETag only if the URL matches featuresPathPattern
                        if (featuresPathPattern.matches(request)) {
                            eTagCache.put(request, resp.headers["ETag"])
                        }
                        
                        resp.body?.string()?.let { body ->
                            onSuccess(body)
                        } ?: onError(Exception("Response body is null: ${resp.body}"))
                    }
                }
            })
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
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody: RequestBody =
                bodyParams.toJsonElement().toString().toRequestBody(mediaType)

            val postRequest = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()

            client.newCall(postRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful || resp.code !in 200..299) {
                            onError(IOException("Unexpected code $resp"))
                            return
                        }
                        resp.body?.string()?.let { body ->
                            onSuccess(body)
                        } ?: onError(IOException("Response body is null: ${resp.body}"))
                    }
                }
            })
        }
    }

    /**
     * Opens a Server-Sent Events (SSE) connection and emits updates as a [Flow].
     *
     * The connection:
     *  - Supports automatic reconnection with exponential backoff.
     *  - Respects external control via [SSEConnectionController] (pause/resume/stop).
     *  - Uses an internal [SSERetryManager] to limit retry attempts.
     *
     * Flow emits:
     *  - [Resource.Success] with raw JSON string when features update.
     *  - [Resource.Error] when retries are exhausted or a fatal error occurs.
     *
     * The returned Flow is cold and starts the connection on collection.
     * Cancelling the Flow automatically closes the SSE connection.
     *
     * @param url URL to open SSE connection against.
     * @param sseController Optional controller to manage the SSE lifecycle externally.
     */
    override fun consumeSSEConnection(
        url: String,
        sseController: SSEConnectionController?
    ): Flow<Resource<String>> {
        val sseHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
            .build()

        return callbackFlow {
            var eventSource: EventSource? = null
            val retryManager = SSERetryManager(maxRetries, initialRetryDelayMs, maxRetryDelayMs)
            val controller = sseController ?: SSEConnectionController()

            fun startEventSource() {
                when {
                    controller.isStopped() -> {
                        if (enableLogging) println("GrowthBook SSE (OkHttp): STOPPED, closing")
                        close()
                        return
                    }
                }

                if (enableLogging) println("GrowthBook SSE (OkHttp): starting EventSource…")

                eventSource = EventSources
                    .createFactory(sseHttpClient)
                    .newEventSource(
                        request,
                        GBEventSourceListener(
                            handler = object : GBEventSourceHandler {
                                override fun onClose(eventSource: EventSource?) {
                                    if (controller.isStopped()) {
                                        if (enableLogging) {
                                            println("GrowthBook SSE (OkHttp): Connection closed, STOPPED. No retry.")
                                        }
                                        return
                                    }

                                    if (retryManager.isMaxRetriesReached()) {
                                        if (enableLogging) {
                                            println("GrowthBook SSE (OkHttp): Max retries reached, STOPPING connection.")
                                        }
                                        controller.stop()
                                        trySend(
                                            Resource.Error(
                                                Exception("Max SSE reconnection retries exceeded")
                                            )
                                        )
                                    } else {
                                        val delayMs = retryManager.getBackoffDelay()
                                        if (enableLogging) {
                                            println(
                                                "GrowthBook SSE (OkHttp): " +
                                                    "Retry ${retryManager.getCurrentRetry() + 1}/$maxRetries " +
                                                    "in ${delayMs}ms"
                                            )
                                        }
                                        retryManager.incrementRetry()
                                        launch {
                                            delay(delayMs)
                                            startEventSource()
                                        }
                                    }
                                }

                                override fun onFeaturesResponse(featuresJsonResponse: String?) {
                                    featuresJsonResponse?.let {
                                        retryManager.reset()
                                        if (enableLogging) {
                                            println("GrowthBook SSE (OkHttp): Features received (${it.length} bytes)")
                                        }
                                        trySend(Resource.Success(it))
                                    }
                                }

                                override fun onFailure(
                                    eventSource: EventSource?,
                                    error: Throwable?) {
                                    if (enableLogging) {
                                        println("GrowthBook SSE (OkHttp): onFailure ${error?.message}")
                                    }
                                    onClose(eventSource)
                                }
                            },
                            enableLogging = enableLogging
                        )
                    )
            }

            launch {
                controller.connectionState.collect { state ->
                    if (enableLogging) {
                        println("GrowthBook SSE (OkHttp): State changed to $state")
                    }

                    when (state) {
                        SSEConnectionState.ACTIVE -> {
                            retryManager.reset()
                            eventSource?.cancel()
                            startEventSource()
                        }

                        SSEConnectionState.STOPPED -> {
                            eventSource?.cancel()
                            close()
                        }
                    }
                }
            }

            awaitClose {
                if (enableLogging) println("GrowthBook SSE (OkHttp): Flow closed")
                eventSource?.cancel()
            }
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        enableLogging = enabled
    }
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