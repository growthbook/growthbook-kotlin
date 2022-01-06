package com.comllc.growthbook.Network

import com.comllc.growthbook.ApplicationDispatcher
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class APITimeError : Exception() {

}

interface NetworkDispatcher {
    fun consumeGETRequest(request: HttpRequestBuilder, onSuccess: (HttpResponse) -> Unit, onError: (APITimeError) -> Unit)
}

class CoreNetworkClient : NetworkDispatcher {

    val client = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    override fun consumeGETRequest(
        request: HttpRequestBuilder,
        onSuccess: (HttpResponse) -> Unit,
        onError: (APITimeError) -> Unit
    ) {

        GlobalScope.launch(ApplicationDispatcher) {

            try {
                client.get<HttpResponse>(request).also(onSuccess)
            } catch (ex: Exception) {
                onError(APITimeError())
            }

        }

    }


}