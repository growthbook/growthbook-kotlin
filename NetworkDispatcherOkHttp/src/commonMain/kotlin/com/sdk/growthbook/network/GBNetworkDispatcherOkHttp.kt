package com.sdk.growthbook.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

import com.sdk.growthbook.PlatformDependentIODispatcher
import com.sdk.growthbook.utils.Resource
import com.sdk.growthbook.utils.GBEventSourceHandler
import com.sdk.growthbook.utils.GBEventSourceListener
import kotlinx.coroutines.cancel

/**
 * Default Ktor Implementation for Network Dispatcher
 */
class GBNetworkDispatcherOkHttp(

    /**
     * Ktor http client instance for sending request
     */
    private val client: OkHttpClient = OkHttpClient()

) : NetworkDispatcher {
    // Regex to match the desired URL pattern: "/api/features/<clientKey>"
    private val featuresPathPattern = Regex(".*/api/features/[^/]+")
    private val eTagMap = mutableMapOf<String, String?>() // Store ETag per URI


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
                .apply {
                    // Only add If-None-Match header if URL matches featuresPathPattern
                    if (featuresPathPattern.matches(request)) {
                        // Add If-None-Match header if ETag is present
                        eTagMap[request]?.let {
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
                    response.use {
                        if (response.isSuccessful) {
                            // Store the ETag only if the URL matches featuresPathPattern
                            if (featuresPathPattern.matches(request)) {
                                eTagMap[request] = response.headers["ETag"]
                            }
                            response.body?.string()?.let {
                                onSuccess(it)
                            }
                        } else {
                            onError(IOException("Unexpected code $response"))
                        }
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
            val formBody = FormBody.Builder()
            bodyParams.forEach { (key, value) -> formBody.add(key, value.toString()) }
            val requestBody = formBody.build()

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
                    response.use {
                        if (!response.isSuccessful) {
                            onError(IOException("Unexpected code $response"))
                        }
                        onSuccess(response.body?.string() ?: "")
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
            .addHeader("Cache-Control", "max-age=3600")
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
                    })
                )
            awaitClose()
        }
    }
}
