package com.sdk.growthbook.utils

import kotlinx.coroutines.channels.ClosedSendChannelException
import okhttp3.Response
import okhttp3.internal.http2.StreamResetException
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Listener for SSE events from OkHttp EventSource
 */
class GBEventSourceListener(
    private val handler: GBEventSourceHandler,
    private val enableLogging: Boolean
) : EventSourceListener() {

    override fun onOpen(eventSource: EventSource, response: Response) {
        super.onOpen(eventSource, response)
        if (enableLogging) {
            println("GrowthBook SSE (OkHttp): Connection opened, status: ${response.code}")
        }
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        super.onEvent(eventSource, id, type, data)

        if (enableLogging) {
            println("GrowthBook SSE (OkHttp): Received event - ID: $id, Type: $type, Data length: ${data.length}")
        }

        if (data.trim().isEmpty()) {
            if (enableLogging) {
                println("GrowthBook SSE (OkHttp): Empty data received, ignoring")
            }
            return
        }
        try {
            handler.onFeaturesResponse(data)
        } catch (e: Exception) {
            if (enableLogging) {
                println("GrowthBook SSE (OkHttp): Error processing features response: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    override fun onClosed(eventSource: EventSource) {
        super.onClosed(eventSource)
        if (enableLogging) {
            println("GrowthBook SSE (OkHttp): Connection closed")
        }
        handler.onClose(eventSource)
    }

    override fun onFailure(
        eventSource: EventSource,
        t: Throwable?,
        response: Response?
    ) {
        super.onFailure(eventSource, t, response)

        if (enableLogging) {
            println("GrowthBook SSE (OkHttp): Connection failed")
            response?.let { println("GrowthBook SSE (OkHttp): Response code: ${it.code}") }
            println("GrowthBook SSE (OkHttp): Failure message: ${t?.message}")
            t?.printStackTrace()
        }

        val errorType = classifyError(t)
        handler.onFailure(eventSource, t, errorType)
    }

    /**
     * Classifies the error type based on the throwable
     */
    private fun classifyError(throwable: Throwable?): SSEErrorType {
        return when (throwable) {
            is UnknownHostException -> SSEErrorType.NETWORK_UNAVAILABLE
            is SocketTimeoutException -> SSEErrorType.TIMEOUT
            is SocketException -> SSEErrorType.SOCKET_CLOSED
            is ClosedSendChannelException -> SSEErrorType.CONNECTION_CLOSED
            is StreamResetException -> SSEErrorType.UNKNOWN
            is IOException -> {
                when {
                    throwable.message?.contains("Unable to resolve host") == true ->
                        SSEErrorType.NETWORK_UNAVAILABLE

                    else -> SSEErrorType.SERVER_ERROR
                }
            }

            else -> SSEErrorType.UNKNOWN
        }
    }
}
