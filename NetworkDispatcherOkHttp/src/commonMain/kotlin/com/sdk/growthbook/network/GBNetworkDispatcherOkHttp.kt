package com.sdk.growthbook.network

import com.sdk.growthbook.PlatformDependentIODispatcher
import com.sdk.growthbook.utils.GBEventSourceHandler
import com.sdk.growthbook.utils.GBEventSourceListener
import com.sdk.growthbook.utils.Resource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
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
 * Default Ktor Implementation for Network Dispatcher
 */
class GBNetworkDispatcherOkHttp(

    /**
     * Ktor http client instance for sending request
     */
    private val client: OkHttpClient = OkHttpClient(),

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
            val getRequest = Request.Builder()
                .url(request)
                .build()
            client.newCall(getRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onError(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful || resp.code !in 200 .. 299) {
                            onError(IOException("Unexpected code $resp"))
                            return
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
            val requestBody: RequestBody = bodyParams.toJsonElement().toString().toRequestBody(mediaType)

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
                        if (!resp.isSuccessful || resp.code !in 200 .. 299) {
                            // throw IOException("Unexpected code $response")
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

    override fun consumeSSEConnection(url: String): Flow<Resource<String>> {
        val sseHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json; q=0.5")
            .addHeader("Accept", "text/event-stream")
            .build()

        return callbackFlow {
            EventSources
                .createFactory(sseHttpClient)
                .newEventSource(
                    request = request,
                    listener = GBEventSourceListener(handler = object : GBEventSourceHandler {
                        override fun onClose(eventSource: EventSource?) {
                            eventSource?.cancel()
                            cancel()
                        }

                        override fun onFeaturesResponse(featuresJsonResponse: String?) {
                            featuresJsonResponse?.let {
                                trySend(Resource.Success(it))
                            }
                        }
                    },
                        enableLogging = enableLogging,
                    )
                )
            awaitClose()
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

