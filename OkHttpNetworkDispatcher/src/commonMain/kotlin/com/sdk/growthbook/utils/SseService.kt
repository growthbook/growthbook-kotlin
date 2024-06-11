package com.sdk.growthbook.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class SseService {
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    private val sseRequest = Request.Builder()
        .url(EVENTSURL)
        .header("Accept", "application/json")
        .addHeader("Accept", "text/event-stream")
        .build()

    var sseEventsFlow = MutableStateFlow(SSEEventData(STATUS.NONE))
        private set

    private val sseEventSourceListener = object : EventSourceListener() {
        override fun onClosed(eventSource: EventSource) {
            super.onClosed(eventSource)
            val event = SSEEventData(STATUS.CLOSED)
            sseEventsFlow.tryEmit(event)
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            super.onEvent(eventSource, id, type, data)
            if (data.isNotEmpty()) {
                if (data.startsWith("[") && data.endsWith("]")) {
                    val listType: Type = object : TypeToken<List<SSEEventData>>() {}.type
                    val imageData = Gson().fromJson<List<SSEEventData>>(data, listType).asReversed()
                    val event = SSEEventData(STATUS.SUCCESS, data = imageData.firstOrNull()?.data)
                    sseEventsFlow.tryEmit(event)
                } else if (data.startsWith("{") && data.endsWith("}")) {
                    val imageData = Gson().fromJson(data, SSEEventData::class.java)
                    val event = SSEEventData(STATUS.SUCCESS, data = imageData.data)
                    sseEventsFlow.tryEmit(event)
                }
            }
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            super.onFailure(eventSource, t, response)
            t?.printStackTrace()
            val event = SSEEventData(STATUS.ERROR)
            sseEventsFlow.tryEmit(event)
        }

        override fun onOpen(eventSource: EventSource, response: Response) {
            super.onOpen(eventSource, response)
            val event = SSEEventData(STATUS.OPEN)
            sseEventsFlow.tryEmit(event)
        }
    }

    init {
        initEventSource()
    }

    private fun initEventSource() {
        EventSources.createFactory(sseClient)
            .newEventSource(request = sseRequest, listener = sseEventSourceListener)
    }

    companion object {
        private const val TAG = "SSERepository"
        private const val EVENTSURL = "http://10.0.2.2:3000/images"
    }
}
