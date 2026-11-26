package com.sdk.growthbook.network

import com.sdk.growthbook.PlatformDependentIODispatcher
import com.sdk.growthbook.utils.GBEventSourceHandler
import com.sdk.growthbook.utils.GBEventSourceListener
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.SSEConnectionController
import com.sdk.growthbook.utils.SSEConnectionState
import com.sdk.growthbook.utils.SSERetryManager
import com.sdk.growthbook.utils.toJsonElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
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

    /**
     * Total time budget for a single feature GET or POST request, in milliseconds. Applied as
     * OkHttp's callTimeout (whole-call ceiling) and readTimeout. Without it, OkHttp's default
     * 10s per-read timeout governs the fetch — too tight for a large payload on a slow
     * network, and invisible to the caller. `null` leaves the [client]'s own configuration.
     * SSE is unaffected: it uses its own connection with streaming-appropriate timeouts.
     */
    private val fetchTimeoutMillis: Long? = DEFAULT_FETCH_TIMEOUT_MILLIS,

    ) : NetworkDispatcherWithNotModified, TrackingNetworkDispatcher {

    companion object {
        const val DEFAULT_FETCH_TIMEOUT_MILLIS: Long = 30_000L
    }

    // Regex to match the desired URL pattern: "/api/features/<clientKey>"
    private val featuresPathPattern = Regex(".*/api/features/[^/]+")

    // Client for feature GET/POST: shares [client]'s pool and dispatcher, adds the bounded
    // total deadline. Lazy so a `null` opt-out costs nothing.
    private val fetchClient: OkHttpClient by lazy {
        fetchTimeoutMillis?.let { millis ->
            client.newBuilder()
                .callTimeout(millis, TimeUnit.MILLISECONDS)
                .readTimeout(millis, TimeUnit.MILLISECONDS)
                .build()
        } ?: client
    }

    // Thread-safe LRU cache with max 100 entries to prevent unbounded growth
    private val eTagCache = OkHttpLruETagCache(maxSize = 100)

    /**
     * Function that execute API Call to fetch features
     */
    override fun consumeGETRequestWithNotModified(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onNotModified: (() -> Unit)
    ): Job =
        handleGetRequest(request, emptyMap(), onSuccess, onError, onNotModified)

    override fun consumeGETRequestWithNotModified(
        request: String,
        headers: Map<String, String>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onNotModified: () -> Unit
    ): Job = handleGetRequest(request, headers, onSuccess, onError, onNotModified)

    override fun consumeGETRequest(
        request: String,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Job = handleGetRequest(request, emptyMap(), onSuccess, onError)

    override fun consumeGETRequest(
        request: String,
        headers: Map<String, String>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Job = handleGetRequest(request, headers, onSuccess, onError)

    /**
     * Method that make POST request to server for remote feature evaluation
     */
    override fun consumePOSTRequest(
        url: String,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) = consumePOSTRequest(url, emptyMap(), bodyParams, onSuccess, onError)

    /**
     * Same as [consumePOSTRequest], with consumer-supplied [headers] (typically
     * `apiHostRequestHeaders`) applied to the remote-evaluation request.
     */
    override fun consumePOSTRequest(
        url: String,
        headers: Map<String, String>,
        bodyParams: Map<String, Any>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        CoroutineScope(PlatformDependentIODispatcher).launch {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody: RequestBody =
                    bodyParams.toJsonElement().toString().toRequestBody(mediaType)

                val postRequest = Request.Builder()
                    .url(url)
                    .applyCustomHeaders(headers)
                    // header(), not addHeader(): the SDK-managed values replace any
                    // consumer-supplied ones instead of adding a second header value.
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

                fetchClient.newCall(postRequest).enqueue(object : Callback {
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
            } catch (t: Throwable) {
                // Building the request can throw before the call is ever enqueued — a malformed
                // `url` (no scheme) makes Request.Builder.url() raise IllegalArgumentException.
                // Inside launch, not around it: `launch` returns before the body runs, so a `try`
                // around the call would never see this.
                if (enableLogging) {
                    println("exception: $t")
                }
                onError(t)
            }
        }
    }

    private fun handleGetRequest(
        request: String,
        headers: Map<String, String>,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onNotModified: (() -> Unit)? = null
    ): Job {
        return CoroutineScope(PlatformDependentIODispatcher).launch {
            val getRequest = try {
                Request.Builder()
                    .url(request)
                    .applyCustomHeaders(headers)
                    // header(), not addHeader(): the SDK-managed cache directive replaces any
                    // consumer-supplied one instead of adding a second, conflicting value.
                    .header("Cache-Control", "max-age=3600")
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
            } catch (t: Throwable) {
                // Building the request can throw before the call is ever enqueued — a malformed
                // header value, or a `request` without a scheme, makes Request.Builder raise
                // IllegalArgumentException. Inside launch, not around it: `launch` returns before
                // the body runs, so a `try` around the call would never see this. Deliberately not
                // logged — the message embeds the offending header value, which may be a credential.
                onError(t)
                return@launch
            }

            fetchClient.newCall(getRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        when (resp.code) {
                            in 200..299 -> {
                                // Store the ETag only if the URL matches featuresPathPattern
                                if (featuresPathPattern.matches(request)) {
                                    eTagCache.put(request, resp.headers["ETag"])
                                }

                                resp.body?.string()?.let { body ->
                                    onSuccess(body)
                                } ?: onError(Exception("Response body is null: ${resp.body}"))
                            }

                            304 -> {
                                if (enableLogging) {
                                    println("GrowthBook: 304 Not Modified for $request")
                                }
                                onNotModified?.invoke()
                            }

                            else -> {
                                onError(IOException("Unexpected code $resp"))
                            }
                        }
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
    ): Flow<Resource<String>> = consumeSSEConnection(url, emptyMap(), sseController)

    /**
     * Same as [consumeSSEConnection], with consumer-supplied [headers] (typically
     * `streamingHostRequestHeaders`) applied to the streaming request. The request is built once
     * and reused, so the headers are re-sent on every reconnection attempt.
     */
    override fun consumeSSEConnection(
        url: String,
        headers: Map<String, String>,
        sseController: SSEConnectionController?
    ): Flow<Resource<String>> {
        val sseHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()

        return callbackFlow {
            var eventSource: EventSource? = null

            // Built inside the flow, not around it: a malformed header value or a scheme-less
            // `url` makes Request.Builder raise IllegalArgumentException, and outside the flow
            // that throw would propagate synchronously out of the public
            // GrowthBookSDK.autoRefreshFeatures() instead of being reported as Resource.Error
            // like every other SSE failure.
            val request = try {
                Request.Builder()
                    .url(url)
                    .applyCustomHeaders(headers)
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .build()
            } catch (t: Throwable) {
                // Not logged — the message embeds the offending header value.
                trySend(Resource.Error(t as? Exception ?: Exception(t)))
                close()
                awaitClose { }
                return@callbackFlow
            }

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
                                    error: Throwable?
                                ) {
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

    override fun consumePOSTRequest(
        url: String,
        headers: Map<String, String>,
        body: JsonElement,
        onSuccess: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        CoroutineScope(PlatformDependentIODispatcher).launch {
            try {
                // Honour the caller's Content-Type (the tracking plugin sends text/plain, matching
                // JS/Python); default to application/json when none is supplied.
                val contentTypeValue = headers["Content-Type"] ?: "application/json; charset=utf-8"
                val mediaType = contentTypeValue.toMediaTypeOrNull()
                val requestBody: RequestBody = body.toString().toRequestBody(mediaType)
                val postRequest = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .apply {
                        headers.forEach { (key, value) ->
                            if (!key.equals("Content-Type", ignoreCase = true)) addHeader(
                                key,
                                value
                            )
                        }
                    }
                    .post(requestBody)
                    .build()
                fetchClient.newCall(postRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        onError(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            if (!resp.isSuccessful || resp.code !in 200..299) {
                                val errorBody = resp.body?.string() ?: ""
                                onError(IOException("Unexpected code ${resp.code}: $errorBody"))
                                return
                            }
                            resp.body?.string()?.let { onSuccess(it) }
                                ?: onError(IOException("Response body is null"))
                        }
                    }
                })
            } catch (t: Throwable) {
                if (enableLogging) {
                    println("exception: $t")
                }
                onError(t)
            }
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        enableLogging = enabled
    }

    /**
     * Applies consumer-supplied headers to a request, dropping the SDK-managed names
     * ([GBRequestHeaders.RESERVED]). Called before the SDK sets its own headers, so those always
     * take priority. Header values may contain credentials and are never logged.
     */
    private fun Request.Builder.applyCustomHeaders(
        headers: Map<String, String>
    ): Request.Builder = apply {
        GBRequestHeaders.sanitize(headers).forEach { (name, value) -> header(name, value) }
    }
}
